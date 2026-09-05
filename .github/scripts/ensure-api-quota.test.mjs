import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
    evaluateProbe,
    evaluateQuota,
    formatProbeSummary,
    formatSummary,
    parseAllRateLimits,
    parseRateLimit,
} from "./ensure-api-quota.mjs";

const NOW = 1_700_000_000;

test("core 한도를 읽는다", () => {
    const quota = parseRateLimit({
        resources: { core: { limit: 15000, remaining: 4200, reset: NOW + 600 } },
    });
    assert.deepEqual(quota, { remaining: 4200, limit: 15000, resetAt: NOW + 600 });
});

test("core 가 없으면 rate 폴백을 읽는다", () => {
    const quota = parseRateLimit({ rate: { limit: 5000, remaining: 10, reset: NOW } });
    assert.equal(quota.remaining, 10);
});

test("graphql 한도는 core 나 최상위 rate 로 폴백하지 않고 따로 읽는다", () => {
    const payload = {
        resources: {
            core: { limit: 15000, remaining: 14000, reset: NOW + 600 },
            graphql: { limit: 5000, remaining: 25, reset: NOW + 300 },
        },
        rate: { limit: 15000, remaining: 14000, reset: NOW + 600 },
    };

    assert.deepEqual(parseRateLimit(payload, "graphql"), {
        remaining: 25,
        limit: 5000,
        resetAt: NOW + 300,
    });
    assert.throws(
        () => parseRateLimit({ rate: payload.rate }, "graphql"),
        /graphql 한도를 읽지 못했습니다/,
    );
});

test("알 수 없는 API 자원은 core 로 조용히 접지 않는다", () => {
    assert.throws(() => parseRateLimit({}, "search"), /지원하지 않는 GitHub API 자원/);
});

test("한도를 못 읽으면 조용히 통과시키지 않는다", () => {
    // 조회 실패를 «여유 있음» 으로 접으면 이 게이트가 통째로 무력해진다.
    assert.throws(() => parseRateLimit({}), /core 한도를 읽지 못했습니다/);
    assert.throws(() => parseRateLimit({ resources: { core: {} } }), /core 한도를 읽지 못했습니다/);
});

test("여유가 있으면 그대로 진행한다", () => {
    const decision = evaluateQuota(
        { remaining: 900, limit: 15000, resetAt: NOW + 600 },
        { minRemaining: 200, maxWaitSeconds: 240, nowSeconds: NOW },
    );
    assert.deepEqual(decision, { action: "proceed", waitSeconds: 0 });
});

test("부족하지만 리셋이 가까우면 기다린다", () => {
    // 즉시 실패시키면 사람이 재실행을 걸고, 그 재실행이 남은 quota 를 더 태운다.
    const decision = evaluateQuota(
        { remaining: 10, limit: 15000, resetAt: NOW + 100 },
        { minRemaining: 200, maxWaitSeconds: 240, nowSeconds: NOW },
    );
    assert.equal(decision.action, "wait");
    // 리셋 경계에서 다시 0 을 보지 않도록 여유를 둔다.
    assert.ok(decision.waitSeconds > 100);
});

test("리셋이 잡 timeout 보다 멀면 기다리지 않는다", () => {
    const decision = evaluateQuota(
        { remaining: 10, limit: 15000, resetAt: NOW + 1800 },
        { minRemaining: 200, maxWaitSeconds: 240, nowSeconds: NOW },
    );
    assert.equal(decision.action, "exhausted");
    assert.equal(decision.waitSeconds, 1800);
});

test("이미 리셋 시각이 지났으면 대기는 0 이다", () => {
    const decision = evaluateQuota(
        { remaining: 0, limit: 15000, resetAt: NOW - 30 },
        { minRemaining: 200, maxWaitSeconds: 240, nowSeconds: NOW },
    );
    assert.equal(decision.action, "wait");
    assert.equal(decision.waitSeconds, 5);
});

test("요약에 남은 호출과 리셋까지 남은 시간이 드러난다", () => {
    // 관측 가능성이 이 스크립트의 절반이다. 숫자가 요약에 없으면 「왜 빨간가」를 다시 파야 한다.
    const summary = formatSummary({ remaining: 4200, limit: 15000, resetAt: NOW + 125 }, { nowSeconds: NOW });
    assert.match(summary, /남은 호출: \*\*4200\*\* \/ 15000/);
    assert.match(summary, /사용 10800/);
    assert.match(summary, /2분 5초/);
    assert.match(summary, /GitHub API 한도 \(core\)/);
});

// default branch 사본으로 도는 게이트는 이 저장소에 없다(리뷰·머지 순서 가드는 1인 운영이라 이식하지 않았다).
const TRUSTED_COPY_GATES = [];
// PR 사본으로 도는 게이트. 이미 PR 사본의 분류·검증 스크립트를 실행하는 자리라 새 신뢰 경계가
// 열리지 않고, quota 스텝은 관측·대기라 무력화해도 통과시킬 판정이 없다.
const PULL_REQUEST_COPY_GATES = ["repository-quality.yml", "codeql.yml"];

function workflow(name) {
    return readFile(new URL(`../workflows/${name}`, import.meta.url), "utf8");
}

test("게이트 워크플로가 quota 확인과 실패 분류를 모두 건다", async () => {
    // 스크립트만 있고 워크플로가 부르지 않으면 아무것도 달라지지 않는다. 8/29 에 실제로
    // 빨개진 잡은 guard·Repository Quality·Classify CodeQL impact 셋이었다 — 하나라도
    // 빠지면 그 자리는 여전히 원인을 로그까지 들어가야 안다 (#1465).
    for (const name of [...TRUSTED_COPY_GATES, ...PULL_REQUEST_COPY_GATES]) {
        const source = await workflow(name);
        assert.match(source, /ensure-api-quota\.mjs ensure/, `${name} 이 사전 quota 확인을 걸지 않았다`);
        assert.match(source, /ensure-api-quota\.mjs classify/, `${name} 이 실패 원인 분류를 걸지 않았다`);
        // 분류는 실패했을 때만 의미가 있다.
        assert.match(
            source,
            /if: failure\(\)[^\n]*\n(?:.*\n)*?\s+(?:run: )?node \.github\/scripts\/ensure-api-quota\.mjs classify/,
            `${name} 의 분류가 failure() 에 걸려 있지 않다`,
        );
    }
});



test("PR 사본 게이트는 좁은 job timeout 안에서만 기다린다", async () => {
    // 이 두 잡은 timeout 이 3분이다. 기본 대기 상한(240초)을 그대로 쓰면 기다리다 timeout 으로
    // 죽어, 「한도 소진」이라는 판정을 남기지 못한 채 원인 불명 실패가 된다.
    for (const name of PULL_REQUEST_COPY_GATES) {
        assert.match(await workflow(name), /ensure-api-quota\.mjs ensure --max-wait 60/, `${name} 이 대기 상한을 좁히지 않았다`);
    }
});

test("core 와 graphql 을 함께 읽고, 아는 자원이 없으면 조용히 통과시키지 않는다", () => {
    const quotas = parseAllRateLimits({
        resources: {
            core: { limit: 15000, remaining: 900, reset: NOW + 300 },
            graphql: { limit: 5000, remaining: 4000, reset: NOW + 300 },
            search: { limit: 30, remaining: 30, reset: NOW + 60 },
        },
    });
    assert.deepEqual(
        quotas.map(({ name, remaining }) => [name, remaining]),
        [
            ["core", 900],
            ["graphql", 4000],
        ],
    );
    assert.deepEqual(parseAllRateLimits({ resources: {} }), []);
    assert.throws(() => evaluateProbe([]), /관측할 자원을 하나도 읽지 못했습니다/);
});

test("주기 관측은 한도 대비 비율로 판정한다", () => {
    // 절대값으로 자르면 installation 한도(15,000)와 GITHUB_TOKEN 한도(1,000)에서 판정이 갈린다.
    const ok = [{ name: "core", remaining: 9000, limit: 15000, resetAt: NOW }];
    const warn = [{ name: "core", remaining: 3000, limit: 15000, resetAt: NOW }];
    const critical = [
        { name: "core", remaining: 9000, limit: 15000, resetAt: NOW },
        { name: "graphql", remaining: 40, limit: 5000, resetAt: NOW },
    ];

    assert.equal(evaluateProbe(ok).level, "ok");
    assert.equal(evaluateProbe(warn).level, "warn");
    // 자원 하나만 말라도 게이트는 죽는다 — 가장 나쁜 쪽이 판정을 정한다.
    const verdict = evaluateProbe(critical);
    assert.equal(verdict.level, "critical");
    assert.deepEqual(verdict.offenders.map(({ name }) => name), ["graphql"]);
});

test("주기 관측 요약에 자원별 남은 호출과 비율이 드러난다", () => {
    const summary = formatProbeSummary(
        [
            { name: "core", remaining: 3000, limit: 15000, resetAt: NOW + 65 },
            { name: "graphql", remaining: 5000, limit: 5000, resetAt: NOW + 65 },
        ],
        { nowSeconds: NOW },
    );
    assert.match(summary, /\| core \| 3000 \/ 15000 \| 20% \| 1분 5초 \|/);
    assert.match(summary, /\| graphql \| 5000 \/ 5000 \| 100% \| 1분 5초 \|/);
});

