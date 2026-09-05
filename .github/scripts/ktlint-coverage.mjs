#!/usr/bin/env node

// 어떤 Gradle 모듈이 ktlintCheck 태스크를 갖는지 빌드 스크립트만 읽어 판정한다.
//
// resolve-pr-impact.mjs 는 `.kt`·`.kts` 가 바뀐 모듈마다 `<모듈>:ktlintCheck` 를 고르는데,
// 그 태스크가 실재하는지는 보지 않는다. 없으면 Ktlint job 이 태스크 선택 단계에서 죽는다.
// 모든 모듈은 자기 build.gradle.kts 를 갖고 그 파일 자체가 `.kts` 라, 위험 범위는 「.kt 를 가진
// 모듈」이 아니라 **등록된 모듈 전부**다.
//
// 이 저장소는 루트 build.gradle.kts 의 `subprojects { apply(plugin = "org.jlleitschuh.gradle.ktlint") }`
// 로 모든 모듈에 ktlint 를 붙인다. 그 블록이 있으면 전 모듈이 덮이고, 없으면 모듈이 직접
// plugins 블록에 선언했는지 본다.

import fs from "node:fs/promises";
import path from "node:path";

export const KTLINT_PLUGIN_ID = "org.jlleitschuh.gradle.ktlint";

function moduleDirectory(projectPath) {
    return projectPath.replace(/^:/, "").replaceAll(":", "/");
}

/** 루트 빌드 스크립트가 subprojects 블록에서 ktlint 를 모든 모듈에 적용하는지 판정한다. */
export function rootAppliesKtlintToSubprojects(rootBuildSource) {
    const block = rootBuildSource.match(/subprojects\s*\{([\s\S]*?)\n\}/);
    if (!block) {
        return false;
    }
    return new RegExp(`apply\\(plugin\\s*=\\s*"${KTLINT_PLUGIN_ID.replaceAll(".", "\\.")}"\\)`).test(block[1]);
}

/** 모듈 빌드 스크립트의 plugins 블록에 선언된 id 를 뽑는다. */
function declaredPluginIds(moduleBuildSource) {
    return [...moduleBuildSource.matchAll(/id\("([^"]+)"\)/g)].map((match) => match[1]);
}

/** settings.gradle.kts 에 등록된 모든 모듈의 ktlint 보유 여부를 판정한다. */
export async function inspectKtlintCoverage(root) {
    const settings = await fs.readFile(path.join(root, "settings.gradle.kts"), "utf8");
    const projectPaths = [...settings.matchAll(/include\("(:[^"]+)"\)/g)].map((match) => match[1]);
    const rootBuildSource = await fs.readFile(path.join(root, "build.gradle.kts"), "utf8");
    const coveredByRoot = rootAppliesKtlintToSubprojects(rootBuildSource);

    const modules = [];
    for (const projectPath of projectPaths) {
        const directory = moduleDirectory(projectPath);
        const source = await fs.readFile(path.join(root, directory, "build.gradle.kts"), "utf8");
        const declared = declaredPluginIds(source);
        modules.push({
            projectPath,
            directory,
            hasKtlint: coveredByRoot || declared.includes(KTLINT_PLUGIN_ID),
        });
    }
    return modules;
}
