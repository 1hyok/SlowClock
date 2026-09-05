import assert from "node:assert/strict";
import test from "node:test";

import {
    INTERNAL_TRACK,
    MAX_RELEASE_NOTES_LENGTH,
    createPlayClient,
    maxVersionCode,
    parseIssueReferences,
    publishInternalBundle,
    readLatestVersionCode,
    renderInternalReleaseNotes,
} from "./play-internal-track.mjs";

const PACKAGE_NAME = "com.ilhyok.slowclock";

function response(status, body) {
    return {
        ok: status >= 200 && status < 300,
        status,
        text: async () => (typeof body === "string" ? body : JSON.stringify(body ?? {})),
    };
}

/**
 * 실제 Play 서버 대신 이 fake 가 edit 수명주기를 그대로 흉내낸다. 각 라우트가 무엇을 돌려줄지
 * 테스트가 정하고, 호출 순서는 calls 로 확인한다.
 */
function fakePlay({ bundles = [], tracks = [], failAt, uploadVersionCode } = {}) {
    const calls = [];
    const state = { committed: false, deleted: false };

    const fetchImpl = async (url, init) => {
        const method = init.method;
        const route = `${method} ${url.replace(/^https:\/\/androidpublisher\.googleapis\.com/, "")}`;
        calls.push(route);
        if (failAt && route.includes(failAt)) {
            return response(403, { error: { message: "denied" } });
        }
        if (/POST \/androidpublisher\/v3\/applications\/[^/]+\/edits$/.test(route)) {
            return response(200, { id: "edit-1" });
        }
        if (route.startsWith("DELETE ")) {
            state.deleted = true;
            return response(204, "");
        }
        if (route.endsWith("/bundles")) {
            return response(200, { bundles });
        }
        if (route.endsWith("/tracks")) {
            return response(200, { tracks });
        }
        if (route.includes("/bundles?uploadType=media")) {
            return response(200, { versionCode: uploadVersionCode ?? 101 });
        }
        if (method === "PUT" && route.includes("/tracks/")) {
            return response(200, JSON.parse(init.body));
        }
        if (route.endsWith(":commit")) {
            state.committed = true;
            return response(200, { id: "edit-1" });
        }
        return response(404, { error: { message: `unmapped ${route}` } });
    };

    return {
        calls,
        state,
        client: createPlayClient({ accessToken: "token", packageName: PACKAGE_NAME, fetchImpl }),
    };
}

const silent = () => {};

test("이슈 번호는 등장 순서대로 중복 없이 추린다", () => {
    const body = "## 포함 이슈\n- #852\n- #1539\n- #852\n\n본문에 섞인 #12 도 센다";
    assert.deepEqual(parseIssueReferences(body), ["#852", "#1539", "#12"]);
    assert.deepEqual(parseIssueReferences(undefined), []);
});

test("release note 는 source SHA 를 먼저 적고 Play 한계 안으로 잘린다", () => {
    const notes = renderInternalReleaseNotes({
        sourceRef: "refs/heads/main",
        sourceSha: "0123456789abcdef",
        versionCode: 101,
        issues: ["#852"],
    });
    assert.match(notes, /^SlowClock 내부 테스트 101\n기준: main @ 0123456\n포함 이슈: #852$/);

    const long = renderInternalReleaseNotes({
        sourceRef: "main",
        sourceSha: "0123456789abcdef",
        versionCode: 101,
        issues: Array.from({ length: 200 }, (_, index) => `#${index + 1}`),
    });
    assert.equal(long.length, MAX_RELEASE_NOTES_LENGTH);
    assert.ok(long.endsWith("…"), "잘렸다는 사실이 Console 에서 보여야 한다");
});

test("최대 versionCode 는 bundle 과 모든 track 을 함께 본다", () => {
    // Console 에서 사람이 직접 올린 산출물은 bundle 목록에만, 승격된 release 는 track 에만
    // 보이는 경우가 있다. 한쪽만 보면 «단조 증가» 판정이 낮은 값을 기준으로 돌아간다.
    assert.equal(maxVersionCode({ bundles: [{ versionCode: 4 }] }, { tracks: [] }), 4);
    assert.equal(
        maxVersionCode(
            { bundles: [{ versionCode: 4 }] },
            { tracks: [{ track: "internal", releases: [{ versionCodes: ["9"] }] }] },
        ),
        9,
    );
    assert.equal(maxVersionCode({}, {}), 0);
});

test("최대 versionCode 조회는 읽기 전용 edit 를 반드시 되돌린다", async () => {
    const play = fakePlay({ bundles: [{ versionCode: 101 }] });
    const latest = await readLatestVersionCode(play.client, { log: silent });

    assert.equal(latest, 101);
    assert.equal(play.state.committed, false);
    assert.equal(play.state.deleted, true);
});

test("조회가 실패해도 edit 는 남지 않는다", async () => {
    const play = fakePlay({ failAt: "/tracks" });
    await assert.rejects(() => readLatestVersionCode(play.client, { log: silent }), /403/);
    assert.equal(play.state.deleted, true);
});

test("게시는 edit → upload → internal track → commit 순서로만 진행한다", async () => {
    const play = fakePlay({ uploadVersionCode: 202 });
    const result = await publishInternalBundle(play.client, {
        bundle: Buffer.from("aab"),
        versionCode: 202,
        releaseNotes: "note",
        log: silent,
    });

    assert.deepEqual(result, { editId: "edit-1", versionCode: 202, track: INTERNAL_TRACK });
    assert.equal(play.state.committed, true);
    assert.equal(play.state.deleted, false);

    const uploadIndex = play.calls.findIndex((call) => call.includes("uploadType=media"));
    const trackIndex = play.calls.findIndex((call) => call.startsWith("PUT "));
    const commitIndex = play.calls.findIndex((call) => call.endsWith(":commit"));
    assert.ok(uploadIndex > 0);
    assert.ok(uploadIndex < trackIndex);
    assert.ok(trackIndex < commitIndex);
});

test("같은 versionCode 재업로드는 업로드 전에 막힌다", async () => {
    const play = fakePlay({ bundles: [{ versionCode: 202 }] });
    await assert.rejects(
        () =>
            publishInternalBundle(play.client, {
                bundle: Buffer.from("aab"),
                versionCode: 202,
                releaseNotes: "note",
                log: silent,
            }),
        /이미 Play 에 올라가 있다/,
    );

    assert.ok(!play.calls.some((call) => call.includes("uploadType=media")), "업로드까지 가면 안 된다");
    assert.equal(play.state.deleted, true);
});

test("업로드된 versionCode 가 빌드한 값과 다르면 track 을 건드리지 않는다", async () => {
    const play = fakePlay({ uploadVersionCode: 999 });
    await assert.rejects(
        () =>
            publishInternalBundle(play.client, {
                bundle: Buffer.from("aab"),
                versionCode: 202,
                releaseNotes: "note",
                log: silent,
            }),
        /빌드한 202 와 다르다/,
    );

    assert.ok(!play.calls.some((call) => call.startsWith("PUT ")));
    assert.equal(play.state.committed, false);
    assert.equal(play.state.deleted, true);
});

test("commit 이 실패하면 미완료 edit 를 정리한 뒤 실패를 그대로 올린다", async () => {
    const play = fakePlay({ uploadVersionCode: 202, failAt: ":commit" });
    await assert.rejects(
        () =>
            publishInternalBundle(play.client, {
                bundle: Buffer.from("aab"),
                versionCode: 202,
                releaseNotes: "note",
                log: silent,
            }),
        /403/,
    );

    assert.equal(play.state.committed, false);
    assert.equal(play.state.deleted, true);
});

test("track 갱신은 internal 경로로만 나간다", async () => {
    const play = fakePlay({ uploadVersionCode: 202 });
    await publishInternalBundle(play.client, {
        bundle: Buffer.from("aab"),
        versionCode: 202,
        releaseNotes: "note",
        log: silent,
    });

    const trackCalls = play.calls.filter((call) => call.includes("/tracks/"));
    assert.deepEqual(
        trackCalls.map((call) => call.split("/tracks/")[1]),
        [INTERNAL_TRACK],
    );
});

test("빈 AAB 는 edit 를 열지도 않는다", async () => {
    const play = fakePlay();
    await assert.rejects(
        () =>
            publishInternalBundle(play.client, {
                bundle: Buffer.alloc(0),
                versionCode: 202,
                releaseNotes: "note",
                log: silent,
            }),
        /AAB 가 비어 있다/,
    );
    assert.deepEqual(play.calls, []);
});

test("자격이나 패키지 이름 없이 client 를 만들 수 없다", () => {
    assert.throws(
        () => createPlayClient({ accessToken: "", packageName: PACKAGE_NAME }),
        /PLAY_ACCESS_TOKEN/,
    );
    assert.throws(() => createPlayClient({ accessToken: "token", packageName: "" }), /PLAY_PACKAGE_NAME/);
});
