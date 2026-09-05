import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const lintWorkflow = await readFile(new URL("../workflows/lint.yml", import.meta.url), "utf8");

test("ktlint runs once with the planner-selected Gradle task array", () => {
    // ktlint 를 실제로 돌리는 자리는 Gradle 태스크 하나뿐이다. 검사를 한 번 더 하는 액션이
    // 끼면(과거 ScaCap/action-ktlint) 같은 규칙을 두 번 돌리며 job 의 30% 를 쓴다 (#1012).
    assert.match(lintWorkflow, /run_ktlint:/);
    assert.match(lintWorkflow, /GRADLE_TASKS: \$\{\{ inputs\.ktlint_tasks \}\}/);
    assert.match(lintWorkflow, /\.\/gradlew "\$\{tasks\[@\]\}" --continue --parallel --build-cache/);
    // 주석은 이력이라 남기고, 실제로 액션을 부르는 uses 줄만 금지한다.
    assert.doesNotMatch(lintWorkflow, /^\s*uses:\s*ScaCap\/action-ktlint/m);
});

test("the ktlint binary version has a single source of truth", () => {
    // 워크플로가 버전을 손으로 적으면 libs.versions.toml 과 표류해 두 검사 결과가 갈린다.
    assert.doesNotMatch(lintWorkflow, /ktlint_version/);
});

test("ktlint scope comes only from canonical Gradle tasks, not raw diff-line filters", () => {
    assert.match(lintWorkflow, /ktlint_tasks:/);
    assert.match(lintWorkflow, /default: "ktlintCheck"/);
    assert.doesNotMatch(lintWorkflow, /filter_mode/);
});

test("ktlint violations are rendered into the job summary even when the check fails", () => {
    assert.match(lintWorkflow, /render-ktlint-summary\.mjs/);
    const summaryStep = /- name: Summarize ktlint violations\n\s+if: always\(\)/;
    assert.match(lintWorkflow, summaryStep);
});

test("Android Lint runs once with reverse-dependent module tasks", () => {
    assert.match(lintWorkflow, /run_android_lint:/);
    assert.match(lintWorkflow, /GRADLE_TASKS: \$\{\{ inputs\.android_lint_tasks \}\}/);
    assert.match(lintWorkflow, /if: inputs\.verify_manifest/);
});

test("Android Lint reports through the job summary, not a Docker reviewdog action", () => {
    // #279 가 푼 문제(모듈별 위반이 수만 줄 로그에 묻힘)를 되돌리지 않는다 — 자리만 PR
    // 인라인 코멘트에서 job summary 로 옮겼다. 그 도커 액션은 매 run 마다 reviewdog 을
    // 네트워크에서 받아 설치했고, 설치가 조용히 실패하면 lint 위반이 0건이어도 required
    // check 가 red 가 됐다. 이미지는 job 당 한 번만 빌드돼 재시도로도 풀리지 않는다 (#1093).
    assert.match(lintWorkflow, /render-android-lint-summary\.mjs/);
    assert.match(lintWorkflow, /- name: Summarize Android Lint violations\n\s+if: always\(\)/);
    // 주석은 이력이라 남기고, 실제로 액션을 부르는 uses 줄만 금지한다.
    assert.doesNotMatch(lintWorkflow, /^\s*uses:\s*dvdandroid\/action-android-lint/m);
});
