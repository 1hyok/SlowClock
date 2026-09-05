import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";

import { inspectKtlintCoverage, rootAppliesKtlintToSubprojects } from "./ktlint-coverage.mjs";

const root = path.resolve(fileURLToPath(new URL("../..", import.meta.url)));

test("every registered module has a ktlintCheck task", async () => {
    // resolve-pr-impact.mjs 는 `.kt`·`.kts` 가 바뀐 모듈마다 `<모듈>:ktlintCheck` 를 고르고,
    // 그 태스크가 실재하는지는 확인하지 않는다. 모든 모듈이 자기 build.gradle.kts 를 갖고 그
    // 파일이 `.kts` 라, 태스크가 없는 모듈은 자기 빌드 스크립트만 고쳐도 Ktlint job 을 태스크
    // 선택 단계에서 죽인다. 새 모듈이 추가되는 시점에 여기서 잡는다.
    const modules = await inspectKtlintCoverage(root);
    const uncovered = modules.filter(({ hasKtlint }) => !hasKtlint).map(({ projectPath }) => projectPath);
    assert.deepEqual(
        uncovered,
        [],
        `ktlintCheck 가 없는 모듈: ${uncovered.join(", ")} — 루트 subprojects 블록을 유지하거나 ` +
            `id("org.jlleitschuh.gradle.ktlint") 를 직접 적용해야 한다`,
    );
});

test("the root build script applies ktlint to every subproject", async () => {
    // 루트 subprojects 블록이 정본이다 — 모듈마다 plugins 블록에 다시 선언하지 않는다.
    const rootBuildSource = await fs.readFile(path.join(root, "build.gradle.kts"), "utf8");
    assert.ok(rootAppliesKtlintToSubprojects(rootBuildSource), "루트 build.gradle.kts 의 subprojects 블록이 ktlint 를 apply 하지 않는다");
    assert.ok(!rootAppliesKtlintToSubprojects("subprojects {\n    apply(plugin = \"com.android.library\")\n}\n"));
    assert.ok(!rootAppliesKtlintToSubprojects("plugins {\n    alias(libs.plugins.ktlint.gradle) apply false\n}\n"));
});
