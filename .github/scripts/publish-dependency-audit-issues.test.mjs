import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";

import {
    publishFindings,
    renderIssueBody,
    selectActionableFindings,
} from "./publish-dependency-audit-issues.mjs";

function audit(overrides = {}) {
    return {
        generatedAt: "2026-08-21T00:00:00.000Z",
        commitSha: "abc123",
        runUrl: "https://github.com/1hyok/SlowClock/actions/runs/1",
        entries: [],
        vulnerabilities: [],
        consistencyFindings: [],
        compatibility: { exitCode: 0 },
        ...overrides,
    };
}

test("creates findings for security, actionable consistency, core major, and compatibility failures", () => {
    const findings = selectActionableFindings(
        audit({
            entries: [
                {
                    kind: "library",
                    alias: "okhttp",
                    coordinate: "com.squareup.okhttp3:okhttp",
                    currentVersion: "5.4.0",
                    latestStable: "6.0.0",
                    latestInChannel: "5.5.0",
                    updateKind: "major",
                    metadata: { url: "https://repo1.maven.org/example" },
                },
                {
                    kind: "library",
                    alias: "googleid",
                    coordinate: "com.google.android.libraries.identity.googleid:googleid",
                    currentVersion: "1.1.1",
                    latestStable: "1.2.0",
                    updateKind: "minor",
                },
            ],
            vulnerabilities: [
                {
                    coordinate: "org.example:vulnerable",
                    version: "1.0.0",
                    aliases: [],
                    vulnerabilities: [{ id: "GHSA-abcd-efgh-ijkl" }],
                },
            ],
            consistencyFindings: [
                {
                    type: "declared-resolved-mismatch",
                    alias: "androidx-compose-runtime",
                    coordinate: "androidx.compose.runtime:runtime",
                    message: "선언 1.10.6이 1.11.4로 해석됩니다.",
                },
                {
                    type: "unresolved-version",
                    alias: "firebase-messaging",
                    coordinate: "com.google.firebase:firebase-messaging",
                    message: "수집 공백",
                },
            ],
            compatibility: { exitCode: 1 },
        }),
    );
    assert.deepEqual(
        findings.map((finding) => finding.key),
        [
            "compatibility:dependency-audit",
            "consistency:androidx.compose.runtime:runtime",
            "major:com.squareup.okhttp3:okhttp:6",
            "security:org.example:vulnerable",
        ],
    );
});

test("does not create issues for ordinary patch or minor updates", () => {
    const findings = selectActionableFindings(
        audit({
            entries: [
                {
                    kind: "library",
                    alias: "okhttp",
                    coordinate: "com.squareup.okhttp3:okhttp",
                    currentVersion: "5.4.0",
                    latestStable: "5.5.0",
                    updateKind: "minor",
                },
            ],
        }),
    );
    assert.deepEqual(findings, []);
});

test("renders dependency issues with the same structured metadata fields as the issue form", () => {
    const [finding] = selectActionableFindings(
        audit({
            consistencyFindings: [
                {
                    type: "declared-resolved-mismatch",
                    alias: "runtime",
                    coordinate: "androidx.compose.runtime:runtime",
                    message: "버전 불일치",
                },
            ],
        }),
    );
    const body = renderIssueBody(finding);
    assert.match(body, /### 작업 유형\n\nbug — 버그·오동작/);
    assert.match(body, /### 주 담당 모듈\n\nplatform —/);
    assert.match(body, /dependency-audit-key: consistency:androidx\.compose\.runtime:runtime/);
    assert.match(body, /### 자식 이슈\n\n_No response_/);
});

const TRACKING_LABEL = "dependency-audit";
const AREA_LABEL = "area:platform";

function securityFinding() {
    const [finding] = selectActionableFindings(
        audit({
            vulnerabilities: [
                {
                    coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
                    version: "2.4.0",
                    aliases: ["kotlin-gradlePlugin"],
                    vulnerabilities: [{ id: "GHSA-r937-wjx7-w2jp" }],
                },
            ],
        }),
    );
    return finding;
}

function auditIssue({ number = 986, state, body }) {
    return {
        number,
        node_id: `NODE_${number}`,
        html_url: `https://example.test/${number}`,
        state,
        body,
        labels: [{ name: "bug" }, { name: TRACKING_LABEL }, { name: AREA_LABEL }],
        assignees: [],
    };
}

function markedBody(finding, fingerprint = finding.fingerprint) {
    return [
        `<!-- dependency-audit-key: ${finding.key} -->`,
        `<!-- dependency-audit-fingerprint: ${fingerprint} -->`,
    ].join("\n");
}

function fakeApi(issues, commentsByNumber = {}) {
    const calls = [];
    let nextNumber = 900;
    const api = async (apiPath, options = {}) => {
        const method = options.method ?? "GET";
        const body = options.body ? JSON.parse(options.body) : null;
        calls.push({ path: apiPath, method, body });
        if (apiPath === "/graphql") {
            return { data: {} };
        }
        const [pathname, search] = apiPath.split("?");
        const params = new URLSearchParams(search ?? "");
        const commentsMatch = pathname.match(/\/issues\/(\d+)\/comments$/);
        if (commentsMatch) {
            return method === "POST" ? {} : (commentsByNumber[commentsMatch[1]] ?? []);
        }
        if (pathname.endsWith("/issues") && method === "GET") {
            if (params.get("page") !== "1") {
                return [];
            }
            const state = params.get("state");
            const label = params.get("labels");
            return issues.filter(
                (issue) =>
                    (state === "all" || issue.state === state) &&
                    (!label || (issue.labels ?? []).some((item) => item.name === label)),
            );
        }
        if (pathname.endsWith("/issues") && method === "POST") {
            const created = {
                number: nextNumber,
                node_id: `NODE_${nextNumber}`,
                html_url: `https://example.test/${nextNumber}`,
                state: "open",
                body: body.body,
                labels: body.labels.map((name) => ({ name })),
                assignees: [],
            };
            nextNumber += 1;
            issues.push(created);
            return created;
        }
        return {};
    };
    return { api, calls };
}

async function publish(finding, issues, commentsByNumber) {
    const { api, calls } = fakeApi(issues, commentsByNumber);
    const results = await publishFindings({
        audit: audit(),
        findings: [finding],
        token: "test-token",
        repository: "1hyok/SlowClock",
        assignee: "1hyok",
        api,
    });
    return { actions: results.map((item) => item.action), calls };
}

const writes = (calls) => calls.filter((call) => call.method !== "GET");
const created = (calls) =>
    calls.filter((call) => call.method === "POST" && call.path.endsWith("/issues"));

test("keeps a closed audit issue closed while the finding is unchanged", async () => {
    const finding = securityFinding();
    const issues = [auditIssue({ state: "closed", body: markedBody(finding) })];

    const { actions, calls } = await publish(finding, issues);

    // 사람이 내린 «이번 회차는 대응하지 않는다» 판정이 매주 새 번호로 부활하면 안 된다 (#1191).
    assert.deepEqual(actions, ["suppressed"]);
    assert.deepEqual(writes(calls), []);
});

test("reopens a closed audit issue once the finding changes", async () => {
    const finding = securityFinding();
    const issues = [auditIssue({ state: "closed", body: markedBody(finding, "0000stale0000") })];

    const { actions, calls } = await publish(finding, issues, { 986: [] });

    assert.deepEqual(actions, ["reopened"]);
    assert.ok(
        calls.some((call) => call.method === "PATCH" && call.body?.state === "open"),
        "해석 버전이나 취약점 목록이 바뀌면 다시 열어야 한다",
    );
    assert.ok(
        calls.some((call) => call.method === "POST" && call.path === "/repos/1hyok/SlowClock/issues/986/comments"),
        "무엇이 달라졌는지 코멘트로 남아야 한다",
    );
    assert.deepEqual(created(calls), []);
});

test("recognizes a closed issue whose fingerprint only appears in a comment", async () => {
    const finding = securityFinding();
    const issues = [auditIssue({ state: "closed", body: markedBody(finding, "0000stale0000") })];
    const comments = { 986: [{ body: `<!-- dependency-audit-fingerprint: ${finding.fingerprint} -->` }] };

    const { actions, calls } = await publish(finding, issues, comments);

    assert.deepEqual(actions, ["suppressed"]);
    assert.deepEqual(writes(calls), []);
});

test("leaves an open audit issue untouched when the fingerprint already matches", async () => {
    const finding = securityFinding();
    const issues = [auditIssue({ state: "open", body: markedBody(finding) })];

    const { actions, calls } = await publish(finding, issues);

    assert.deepEqual(actions, ["unchanged"]);
    assert.deepEqual(created(calls), []);
    assert.ok(!calls.some((call) => call.method === "POST" && call.path.endsWith("/comments")));
});

test("comments on an open audit issue when the fingerprint changed", async () => {
    const finding = securityFinding();
    const issues = [auditIssue({ state: "open", body: markedBody(finding, "0000stale0000") })];

    const { actions, calls } = await publish(finding, issues, { 986: [] });

    assert.deepEqual(actions, ["commented"]);
    assert.ok(!calls.some((call) => call.method === "PATCH" && call.body?.state === "open"));
});

test("labels newly created issues so they stay discoverable after being closed", async () => {
    const finding = securityFinding();

    const { actions, calls } = await publish(finding, []);

    assert.deepEqual(actions, ["created"]);
    assert.deepEqual(created(calls)[0].body.labels, ["bug", TRACKING_LABEL, AREA_LABEL]);
});

test("scopes the closed-issue lookup to the tracking label but lists open issues in full", async () => {
    const finding = securityFinding();

    const { calls } = await publish(finding, []);
    const listings = calls.filter((call) => call.method === "GET" && call.path.includes("/issues?"));

    const closed = listings.find((call) => call.path.includes("state=closed"));
    assert.ok(closed?.path.includes(`labels=${TRACKING_LABEL}`), "닫힌 이슈는 추적 라벨로 좁힌다");
    assert.ok(
        listings.some((call) => call.path.includes("state=open") && !call.path.includes("labels=")),
        "라벨 도입 전에 만들어진 열린 이슈도 계속 찾아야 한다",
    );
});

function kgpAudit({ firstPatchedStable = null, stableFixVersion = null } = {}) {
    return audit({
        vulnerabilities: [
            {
                coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
                version: "2.4.10",
                aliases: ["kotlin-gradlePlugin"],
                latestStable: stableFixVersion ?? "2.4.10",
                stableFixVersion,
                vulnerabilities: [
                    { id: "GHSA-r937-wjx7-w2jp", firstPatched: "2.4.20-Beta1", firstPatchedStable },
                ],
            },
        ],
    });
}

test("keeps the fingerprint stable while the only patched releases are prereleases", () => {
    // 이 필드를 도입한 것만으로 fingerprint 가 흔들리면, 정식판이 없어 «대응 보류» 로 닫아 둔
    // 이슈가 아무 상황 변화 없이 다시 열린다 (#1191 이 세운 보류 존중이 무너진다).
    // 정식 패치판이 없을 때의 해시는 이 필드가 없던 시절과 같아야 한다.
    const [finding] = selectActionableFindings(kgpAudit());
    const legacy = createHash("sha256")
        .update(
            JSON.stringify({
                key: "security:org.jetbrains.kotlin:kotlin-gradle-plugin",
                versions: ["2.4.10"],
                vulnerabilities: ["GHSA-r937-wjx7-w2jp"],
            }),
        )
        .digest("hex")
        .slice(0, 16);
    assert.equal(finding.fingerprint, legacy);
});

test("changes the fingerprint the moment a stable patched release appears", () => {
    // #986 을 닫으며 «정식판 출시 자체는 자동으로 감지되지 않는다» 를 남은 구멍으로 적어 뒀다.
    // 이 단언이 그 구멍이 닫혀 있음을 고정한다 — 해시가 바뀌어야 닫힌 이슈가 다시 열린다.
    const held = selectActionableFindings(kgpAudit())[0];
    const released = selectActionableFindings(
        kgpAudit({ firstPatchedStable: "2.4.20", stableFixVersion: "2.4.20" }),
    )[0];
    assert.notEqual(held.fingerprint, released.fingerprint);
});

test("tells the reader whether there is a stable version to move to", () => {
    const held = renderIssueBody(selectActionableFindings(kgpAudit())[0]);
    assert.match(held, /최초 패치 버전은 `2\.4\.20-Beta1`/);
    assert.match(held, /아직 정식\(stable\) 릴리스가 없습니다/);
    assert.match(held, /현재 정식 최신 `2\.4\.10`/);

    const released = renderIssueBody(
        selectActionableFindings(kgpAudit({ firstPatchedStable: "2.4.20", stableFixVersion: "2.4.20" }))[0],
    );
    assert.match(released, /정식 패치판 `2\.4\.20` 이 배포돼 있습니다/);
});
