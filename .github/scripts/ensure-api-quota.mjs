#!/usr/bin/env node

// 게이트 잡이 GitHub API 한도에 부딪히는 것을 관측 가능하게 만들고, 한도 소진을 일반 실패와
// 구분한다 (#1465).
//
// 이 저장소의 게이트들은 잡마다 `gh api` 를 여러 번 부른다 — impact 분류는 PR 메타와 파일 목록을,
// Issue 링크 게이트는 참조된 Issue 마다 조회한다. PR 수 × 잡 수 × 잡당 호출수가 곱해져 installation 한도를 넘기면, 저장소 전체의
// 게이트가 코드와 무관하게 빨갛게 된다. 그때 재실행은 남은 quota 를 더 태울 뿐이다.
//
// 명령:
//   ensure  [--resource core|graphql] [--min N] [--max-wait S]
//                                           게이트 실행 전. 해당 quota 를 기록하고, 부족하면 리셋까지 기다린다
//   classify [--resource core|graphql]      게이트 실패 후. 그 실패가 해당 한도 소진인지 판정해 표시한다
//   report                            주기 관측. core·graphql 남은 quota 를 찍고 바닥에 가까우면 실패한다

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import process from "node:process";

// 게이트 한 번이 쓰는 호출 수의 상한을 여유 있게 잡은 값(파일 목록 페이지네이션 + Issue 조회).
const DEFAULT_MIN_REMAINING = 200;
// job timeout(대개 5분) 안에서 기다릴 수 있는 상한. 리셋이 이보다 멀면 기다리지 않고 실패한다.
const DEFAULT_MAX_WAIT_SECONDS = 240;

export function parseRateLimit(payload, resourceName = "core") {
    if (!PROBE_RESOURCES.includes(resourceName)) {
        throw new TypeError(`지원하지 않는 GitHub API 자원입니다: ${resourceName}`);
    }
    // 최상위 rate 폴백은 REST core 의 레거시 표현이다. GraphQL 로 폴백하면 core 가
    // 충분한데 GraphQL 만 소진된 상태를 통과시키므로, core 에만 허용한다.
    const resource = payload?.resources?.[resourceName] ??
        (resourceName === "core" ? payload?.rate : undefined);
    if (!resource || typeof resource.remaining !== "number" || typeof resource.limit !== "number") {
        throw new Error(`rate_limit 응답에서 ${resourceName} 한도를 읽지 못했습니다.`);
    }
    return {
        remaining: resource.remaining,
        limit: resource.limit,
        // reset 은 epoch 초다. 없으면 «지금» 으로 두어 대기 계산이 0 이 되게 한다.
        resetAt: typeof resource.reset === "number" ? resource.reset : 0,
    };
}

// 주기 관측이 «경고» 와 «위험» 을 가르는 경계. 절대값이 아니라 한도 대비 비율로 잡는다 —
// installation 한도(15,000)와 GITHUB_TOKEN 한도(1,000)가 자리마다 다르고, 어느 쪽이 걸려도
// 같은 판정이 나와야 한다.
const PROBE_WARN_RATIO = 0.25;
const PROBE_FAIL_RATIO = 0.1;

// 주기 관측은 core 만 보면 반쪽이다 — 이 저장소의 게이트는 REST 와 GraphQL 을 함께 쓴다.
const PROBE_RESOURCES = ["core", "graphql"];

export function parseAllRateLimits(payload) {
    return PROBE_RESOURCES.flatMap((name) => {
        const resource = payload?.resources?.[name];
        if (!resource || typeof resource.remaining !== "number" || typeof resource.limit !== "number") {
            return [];
        }
        return [
            {
                name,
                remaining: resource.remaining,
                limit: resource.limit,
                resetAt: typeof resource.reset === "number" ? resource.reset : 0,
            },
        ];
    });
}

/**
 * 주기 관측의 판정. 가장 나쁜 자원 하나가 전체 판정을 정한다 — 하나만 말라도 게이트는 죽는다.
 *
 * - ok       여유가 있다.
 * - warn     줄어드는 것이 보인다. 요약과 annotation 으로 남기되 run 은 초록으로 둔다.
 * - critical 곧 저장소 전체의 게이트가 빨개진다. run 자체를 빨갛게 만들어 눈에 띄게 한다.
 */
export function evaluateProbe(quotas, { warnRatio = PROBE_WARN_RATIO, failRatio = PROBE_FAIL_RATIO } = {}) {
    if (quotas.length === 0) {
        // 조회는 됐는데 아는 자원이 하나도 없다 — 응답 형식이 바뀐 것이다. 조용히 초록으로
        // 두면 이 관측이 통째로 무력해진다.
        throw new Error("rate_limit 응답에서 관측할 자원을 하나도 읽지 못했습니다.");
    }
    const ratioOf = (quota) => (quota.limit > 0 ? quota.remaining / quota.limit : 0);
    const critical = quotas.filter((quota) => ratioOf(quota) < failRatio);
    const warning = quotas.filter((quota) => ratioOf(quota) < warnRatio && !critical.includes(quota));
    if (critical.length > 0) return { level: "critical", offenders: critical };
    if (warning.length > 0) return { level: "warn", offenders: warning };
    return { level: "ok", offenders: [] };
}

export function formatProbeSummary(quotas, { nowSeconds }) {
    const rows = quotas.map((quota) => {
        const resetInSeconds = Math.max(0, quota.resetAt - nowSeconds);
        const percent = quota.limit > 0 ? Math.round((quota.remaining / quota.limit) * 100) : 0;
        return `| ${quota.name} | ${quota.remaining} / ${quota.limit} | ${percent}% | ${Math.floor(resetInSeconds / 60)}분 ${resetInSeconds % 60}초 |`;
    });
    return ["### GitHub API 한도 관측", "", "| 자원 | 남은 호출 | 비율 | 리셋까지 |", "| --- | --- | --- | --- |", ...rows, ""].join("\n");
}

/**
 * 남은 quota 로 무엇을 할지 정한다.
 *
 * - proceed   여유가 있다. 그대로 진행한다.
 * - wait      부족하지만 리셋이 가까워 기다릴 수 있다.
 * - exhausted 부족하고 리셋이 멀다. 기다려도 잡 timeout 안에 못 푼다.
 */
export function evaluateQuota(quota, { minRemaining, maxWaitSeconds, nowSeconds }) {
    if (quota.remaining >= minRemaining) {
        return { action: "proceed", waitSeconds: 0 };
    }
    const waitSeconds = Math.max(0, quota.resetAt - nowSeconds);
    if (waitSeconds <= maxWaitSeconds) {
        // 리셋 직후 경계에서 다시 0 을 보지 않도록 몇 초 더 둔다.
        return { action: "wait", waitSeconds: waitSeconds + 5 };
    }
    return { action: "exhausted", waitSeconds };
}

export function formatSummary(quota, { nowSeconds, resourceName = "core" }) {
    const used = quota.limit - quota.remaining;
    const resetInSeconds = Math.max(0, quota.resetAt - nowSeconds);
    const minutes = Math.floor(resetInSeconds / 60);
    const seconds = resetInSeconds % 60;
    return [
        `### GitHub API 한도 (${resourceName})`,
        "",
        `- 남은 호출: **${quota.remaining}** / ${quota.limit} (사용 ${used})`,
        `- 리셋까지: ${minutes}분 ${seconds}초`,
        "",
    ].join("\n");
}

function readRateLimitPayload() {
    // rate_limit 엔드포인트 자체는 한도를 소비하지 않는다 — 관측이 문제를 키우지 않는다.
    const result = spawnSync("gh", ["api", "rate_limit"], { encoding: "utf8" });
    if (result.status !== 0) {
        throw new Error(`rate_limit 조회에 실패했습니다: ${(result.stderr || "").trim()}`);
    }
    return JSON.parse(result.stdout);
}

function readRateLimit(resourceName = "core") {
    return parseRateLimit(readRateLimitPayload(), resourceName);
}

function appendSummary(text) {
    const target = process.env.GITHUB_STEP_SUMMARY;
    if (!target) return;
    try {
        fs.appendFileSync(target, `${text}\n`);
    } catch {
        // 요약을 못 쓰는 것으로 게이트를 실패시키지 않는다. 로그에는 이미 남는다.
    }
}

function sleepSeconds(seconds) {
    // 워크플로 스텝이라 동기 대기로 충분하다. 타이머를 걸면 프로세스가 먼저 끝난다.
    spawnSync(process.execPath, ["-e", `Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ${seconds * 1000})`]);
}

function numericOption(argv, name, fallback) {
    const index = argv.indexOf(name);
    if (index === -1) return fallback;
    const parsed = Number(argv[index + 1]);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function resourceOption(argv) {
    const index = argv.indexOf("--resource");
    const resourceName = index === -1 ? "core" : argv[index + 1];
    if (!PROBE_RESOURCES.includes(resourceName)) {
        throw new TypeError(`--resource 는 ${PROBE_RESOURCES.join(" 또는 ")} 여야 합니다.`);
    }
    return resourceName;
}

function ensure(argv) {
    const resourceName = resourceOption(argv);
    const minRemaining = numericOption(argv, "--min", DEFAULT_MIN_REMAINING);
    const maxWaitSeconds = numericOption(argv, "--max-wait", DEFAULT_MAX_WAIT_SECONDS);

    let quota = readRateLimit(resourceName);
    let nowSeconds = Math.floor(Date.now() / 1000);
    appendSummary(formatSummary(quota, { nowSeconds, resourceName }));
    console.log(`${resourceName} 남은 API 호출 ${quota.remaining}/${quota.limit}`);

    const decision = evaluateQuota(quota, { minRemaining, maxWaitSeconds, nowSeconds });
    if (decision.action === "proceed") {
        return 0;
    }
    if (decision.action === "exhausted") {
        console.log(
            `::error::GitHub API ${resourceName} 한도가 소진됐습니다 (남은 ${quota.remaining}/${quota.limit}). ` +
                "코드 문제가 아니라 저장소 전체에 걸린 한도이며, 재실행은 남은 quota 를 더 태웁니다. " +
                "리셋 뒤에 다시 실행하세요.",
        );
        return 1;
    }

    console.log(`::notice::GitHub API ${resourceName} 한도가 부족해 리셋까지 ${decision.waitSeconds}초 기다립니다.`);
    sleepSeconds(decision.waitSeconds);

    quota = readRateLimit(resourceName);
    nowSeconds = Math.floor(Date.now() / 1000);
    appendSummary(formatSummary(quota, { nowSeconds, resourceName }));
    if (quota.remaining < minRemaining) {
        console.log(
            `::error::리셋을 기다렸는데도 GitHub API ${resourceName} 한도가 부족합니다 (남은 ${quota.remaining}/${quota.limit}). ` +
                "다른 워크플로가 같은 한도를 계속 쓰고 있습니다.",
        );
        return 1;
    }
    return 0;
}

function classify(argv) {
    const resourceName = resourceOption(argv);
    // 게이트가 실패한 뒤에 부른다. 남은 quota 가 바닥이면 그 실패는 «가드가 사실을 확인하고
    // 거절함» 이 아니라 한도 소진이다. 로그를 파고들지 않아도 구분되게 표시한다.
    let quota;
    try {
        quota = readRateLimit(resourceName);
    } catch (error) {
        console.log(`::notice::한도 조회에 실패해 원인을 가르지 못했습니다: ${error.message}`);
        return 0;
    }
    const nowSeconds = Math.floor(Date.now() / 1000);
    appendSummary(formatSummary(quota, { nowSeconds, resourceName }));

    if (quota.remaining < DEFAULT_MIN_REMAINING) {
        const message =
            `앞 스텝의 실패는 코드 문제가 아니라 **GitHub API ${resourceName} 한도 소진**입니다 ` +
            `(남은 ${quota.remaining}/${quota.limit}). 재실행은 남은 quota 를 더 태웁니다.`;
        appendSummary(`### ⚠️ 한도 소진으로 인한 실패\n\n${message}\n`);
        console.log(`::error::${message.replaceAll("**", "")}`);
        return 0;
    }
    console.log(
        `::notice::GitHub API ${resourceName} 한도는 충분합니다 (남은 ${quota.remaining}/${quota.limit}) — ` +
            "앞 스텝의 실패는 가드가 실제로 판정한 결과입니다.",
    );
    return 0;
}

function report() {
    // 주기 관측 (#1465). 소진된 뒤에야 «모든 PR 이 같은 자리에서 빨갛다» 로 알아채던 것을,
    // 바닥나기 전에 볼 수 있는 자리로 옮긴다.
    const quotas = parseAllRateLimits(readRateLimitPayload());
    const nowSeconds = Math.floor(Date.now() / 1000);
    appendSummary(formatProbeSummary(quotas, { nowSeconds }));
    for (const quota of quotas) {
        console.log(`${quota.name}: 남은 ${quota.remaining}/${quota.limit}`);
    }

    const verdict = evaluateProbe(quotas);
    const describe = (quota) => `${quota.name} ${quota.remaining}/${quota.limit}`;
    if (verdict.level === "ok") {
        return 0;
    }
    const detail = verdict.offenders.map(describe).join(", ");
    if (verdict.level === "warn") {
        console.log(`::warning::GitHub API 한도가 줄고 있습니다 (${detail}). 게이트가 빨개지기 전에 무엇이 태우는지 확인하세요.`);
        return 0;
    }
    // 여기서 초록으로 두면 관측이 아무것도 바꾸지 않는다. run 을 빨갛게 만들어 목록에서 보이게 한다.
    console.log(
        `::error::GitHub API 한도가 바닥나고 있습니다 (${detail}). ` +
            "이대로면 저장소 전체의 게이트가 코드와 무관하게 실패합니다.",
    );
    return 1;
}

function main(argv) {
    const command = argv.find((token) => !token.startsWith("--")) ?? "ensure";
    switch (command) {
        case "ensure":
            return ensure(argv);
        case "classify":
            return classify(argv);
        case "report":
            return report();
        default:
            console.log(`::error::알 수 없는 명령입니다: ${command}`);
            return 1;
    }
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
    process.exit(main(process.argv.slice(2)));
}
