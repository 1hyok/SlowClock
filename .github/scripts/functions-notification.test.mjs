import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

const source = await readFile(new URL("../../functions/index.js", import.meta.url), "utf8");

function fixture({ tokens = ["private-token"], sendError, readError, failed = 0 } = {}) {
    const messages = [];
    const logs = [];
    const codes = [];
    const exports = {};
    const admin = {
        initializeApp() {},
        firestore: () => ({ collection: (name) => {
            assert.equal(name, "shareCodeWatchers");
            return { doc: (code) => {
                codes.push(code);
                return { collection: (nested) => {
                    assert.equal(nested, "tokens");
                    return { get: async () => {
                        if (readError) throw readError;
                        return { forEach: (visit) => tokens.forEach((fcmToken) => visit({ data: () => ({ fcmToken }) })) };
                    } };
                } };
            } };
        } }),
        messaging: () => ({ sendEachForMulticast: async (message) => {
            messages.push(JSON.parse(JSON.stringify(message)));
            if (sendError) throw sendError;
            return { successCount: message.tokens.length - failed, failureCount: failed };
        } }),
    };
    vm.runInNewContext(source, {
        exports,
        console: { log: (...values) => logs.push(values) },
        require: (name) => {
            if (name === "firebase-admin") return admin;
            assert.equal(name, "firebase-functions/v2/firestore");
            return { onDocumentWritten: (options, handler) => {
                assert.equal(options.document, "schedules/{scheduleId}");
                return handler;
            } };
        },
    }, { filename: "functions/index.js" });
    return { handle: exports.sendFcmToShareCodeWatchers, messages, logs, codes };
}

const schedule = { sharedCode: "ABC123", title: "private schedule", completed: false };
function change(before, after) {
    const snapshot = (data) => ({ exists: data !== undefined, data: () => data });
    return { params: { scheduleId: "schedule-id" }, data: { before: snapshot(before), after: snapshot(after) } };
}

for (const [name, before, after, title] of [
    ["creation", undefined, schedule, "일정이 추가되었습니다"],
    ["deletion", schedule, undefined, "일정이 삭제되었습니다"],
    ["completion", schedule, { ...schedule, completed: true }, "완료되었습니다"],
    ["undo", { ...schedule, completed: true }, schedule, "상태 미완료로 바꿨습니다"],
    ["edit", schedule, { ...schedule, title: "updated title" }, "일정이 변경되었습니다"],
]) {
    test(`actual notification handler handles ${name}`, async () => {
        const f = fixture();
        await f.handle(change(before, after));
        assert.deepEqual(f.codes, ["ABC123"]);
        assert.deepEqual(f.messages, [{ notification: { title, body: (after || before).title }, tokens: ["private-token"] }]);
        const logged = JSON.stringify(f.logs);
        assert.doesNotMatch(logged, /private-token|private schedule|updated title|ABC123/);
    });
}

test("missing event, unshared schedule and no recipients do not send", async () => {
    for (const event of [{}, change(undefined, undefined), change(undefined, { title: "unshared" })]) {
        const f = fixture();
        assert.equal(await f.handle(event), null);
        assert.equal(f.messages.length, 0);
        assert.equal(f.codes.length, 0);
    }
    const f = fixture({ tokens: [undefined, "", "   ", 42] });
    assert.equal(await f.handle(change(undefined, schedule)), null);
    assert.equal(f.messages.length, 0);
});

test("deduplicates registration tokens and splits at the real multicast limit", async () => {
    const tokens = Array.from({ length: 1001 }, (_, index) => `token-${index}`);
    const f = fixture({ tokens: [...tokens, tokens[0], tokens[1000]] });
    const result = await f.handle(change(undefined, schedule));
    assert.deepEqual(f.messages.map((message) => message.tokens.length), [500, 500, 1]);
    assert.deepEqual(f.messages.flatMap((message) => message.tokens), tokens);
    assert.equal(result.successCount, 1001);
    assert.equal(result.failureCount, 0);
});

test("records partial delivery counts without exposing token or payload", async () => {
    const f = fixture({ tokens: ["one", "two"], failed: 1 });
    const result = await f.handle(change(undefined, schedule));
    assert.equal(result.successCount, 1);
    assert.equal(result.failureCount, 1);
    assert.doesNotMatch(JSON.stringify(f.logs), /one|two|private schedule|ABC123/);
});

test("Firestore and total FCM failures reject instead of claiming success", async () => {
    for (const options of [{ readError: new Error("read failed") }, { sendError: new Error("send failed") }]) {
        const f = fixture(options);
        await assert.rejects(f.handle(change(undefined, schedule)), /failed/);
        assert.equal(f.logs.length, 0);
    }
});
