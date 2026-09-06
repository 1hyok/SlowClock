// 실제 transport를 끊는다. disableNetwork만으로는 transaction의 직접 RPC가 차단되지 않는다.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import net from "node:net";
import { after, before, beforeEach, describe, it } from "node:test";
import { initializeTestEnvironment, assertFails } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, runTransaction, setDoc, updateDoc, waitForPendingWrites } from "firebase/firestore";

let env;
let proxy;
let connected = true;
const sockets = new Set();
const schedule = { userId: "owner", title: "약", sharedCode: "ABC123", completed: false };

before(async () => {
    proxy = net.createServer((client) => {
        if (!connected) { client.destroy(); return; }
        const upstream = net.connect(8080, "127.0.0.1");
        sockets.add(client); sockets.add(upstream);
        client.on("error", () => upstream.destroy());
        upstream.on("error", () => client.destroy());
        client.on("close", () => { sockets.delete(client); upstream.destroy(); });
        upstream.on("close", () => { sockets.delete(upstream); client.destroy(); });
        client.pipe(upstream); upstream.pipe(client);
    });
    await new Promise((resolve) => proxy.listen(0, "127.0.0.1", resolve));
    env = await initializeTestEnvironment({
        projectId: "slowclock-transactions-test",
        firestore: { host: "127.0.0.1", port: proxy.address().port, rules: await readFile(new URL("../firestore.rules", import.meta.url), "utf8") },
    });
});
after(async () => {
    connected = true;
    await env?.cleanup();
    for (const socket of sockets) socket.destroy();
    await new Promise((resolve) => proxy.close(resolve));
});
beforeEach(async () => {
    connected = true;
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), "users/owner"), { shareCode: "ABC123" });
        await setDoc(doc(ctx.firestore(), "shareCodeWatchers/ABC123/tokens/watcher"), { userId: "watcher" });
    });
});
const owner = () => env.authenticatedContext("owner").firestore();
async function stored(path) {
    let snapshot;
    await env.withSecurityRulesDisabled(async (ctx) => { snapshot = await getDoc(doc(ctx.firestore(), path)); });
    return snapshot;
}
async function save(db, id, value = schedule, beforeCreate = async () => {}) {
    return runTransaction(db, async (tx) => {
        const ref = doc(db, "schedules", id);
        const old = await tx.get(ref);
        if (old.exists()) {
            if (old.data().userId !== value.userId || old.data().title !== value.title) throw new Error("Existing schedule differs");
            return old.data();
        }
        const user = await tx.get(doc(db, "users", value.userId));
        await beforeCreate();
        const code = user.data()?.shareCode ?? "";
        if (code) tx.set(doc(db, "shareCodes", code), { userId: value.userId });
        tx.set(ref, { ...value, sharedCode: code });
        return value;
    });
}

describe("온라인 일정 transaction", () => {
    it("누락 등록부 복구와 생성이 원자적으로 성공하고 등록부 읽기는 닫혀 있다", async () => {
        const db = owner();
        await save(db, "s1");
        assert.equal((await stored("shareCodes/ABC123")).data().userId, "owner");
        assert.equal((await getDoc(doc(db, "schedules/s1"))).data().sharedCode, "ABC123");
        await assertFails(getDoc(doc(db, "shareCodes/ABC123")));
        await assertFails(getDocs(collection(db, "shareCodes")));
    });

    it("등록부 충돌은 일정 생성과 기존 문서 변경을 모두 거절한다", async () => {
        await env.withSecurityRulesDisabled(async (ctx) => {
            await setDoc(doc(ctx.firestore(), "shareCodes/ABC123"), { userId: "other" });
        });
        await assertFails(save(owner(), "s1"));
        assert.equal((await stored("schedules/s1")).exists(), false);
        assert.equal((await stored("shareCodes/ABC123")).data().userId, "other");
    });

    it("동일 ID의 뒤늦은 첫 commit은 재시도되어 이미 저장된 내용을 덮지 않는다", async () => {
        const db = owner();
        let resume;
        let ready;
        const started = new Promise((resolve) => { ready = resolve; });
        const gate = new Promise((resolve) => { resume = resolve; });
        const first = save(db, "stable", schedule, async () => { ready(); await gate; });
        await started;
        await save(db, "stable");
        await updateDoc(doc(db, "schedules/stable"), { completed: true });
        resume();
        const returned = await first;
        assert.equal(returned.completed, true);
        assert.equal((await stored("schedules/stable")).data().completed, true);
        await env.withSecurityRulesDisabled(async (ctx) => {
            assert.equal((await getDocs(collection(ctx.firestore(), "schedules"))).size, 1);
        });
        await assert.rejects(save(db, "stable", { ...schedule, title: "다른 내용" }), /differs/);
        assert.equal((await stored("schedules/stable")).data().title, "약");
    });

    it("연결이 실제로 끊긴 생성 편집 삭제 완료는 복구 뒤에도 큐에서 재생되지 않는다", async () => {
        const db = owner();
        await save(db, "existing");
        connected = false;
        for (const socket of sockets) socket.destroy();
        const ref = doc(db, "schedules/existing");
        const results = await Promise.allSettled([
            save(db, "offline-new"),
            runTransaction(db, async (tx) => { await tx.get(ref); tx.update(ref, { title: "offline edit" }); }),
            runTransaction(db, async (tx) => { await tx.get(ref); tx.delete(ref); }),
            runTransaction(db, async (tx) => { await tx.get(ref); tx.update(ref, { completed: true }); }),
        ]);
        assert.ok(results.every((result) => result.status === "rejected"));
        for (const result of results) assert.equal(result.reason.code, "unavailable");
        connected = true;
        await waitForPendingWrites(db);
        assert.equal((await stored("schedules/offline-new")).exists(), false);
        assert.deepEqual((await stored("schedules/existing")).data(), schedule);
        await save(db, "offline-new");
        assert.equal((await stored("schedules/offline-new")).exists(), true);
    });

    it("감시자 완료 transaction은 등록부를 쓰지 않고 완료 필드만 바꾼다", async () => {
        await save(owner(), "s1");
        const db = env.authenticatedContext("watcher").firestore();
        await runTransaction(db, async (tx) => {
            const ref = doc(db, "schedules/s1");
            await tx.get(ref);
            tx.update(ref, { completed: true });
        });
        assert.equal((await stored("schedules/s1")).data().completed, true);
        await assertFails(runTransaction(db, async (tx) => {
            const ref = doc(db, "schedules/s1"); await tx.get(ref); tx.update(ref, { title: "변경" });
        }));
    });

    it("미존재 get은 로그인 요청만 허용하고 타인 기존 일정과 목록은 계속 닫는다", async () => {
        const outsider = env.authenticatedContext("outsider").firestore();
        assert.equal((await getDoc(doc(outsider, "schedules/missing"))).exists(), false);
        await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "schedules/missing")));
        await save(owner(), "private");
        await assertFails(getDoc(doc(outsider, "schedules/private")));
        await assertFails(getDocs(collection(outsider, "schedules")));
    });
});
