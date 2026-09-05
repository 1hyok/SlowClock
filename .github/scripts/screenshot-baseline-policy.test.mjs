import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { inspectModules } from "./resolve-pr-impact.mjs";

const repoRoot = path.resolve(fileURLToPath(new URL("../..", import.meta.url)));
const verify = await readFile(new URL("../workflows/screenshot.yml", import.meta.url), "utf8");
const generate = await readFile(
    new URL("../workflows/screenshot-baseline-generate.yml", import.meta.url),
    "utf8",
);
const apply = await readFile(
    new URL("../workflows/screenshot-baseline-apply.yml", import.meta.url),
    "utf8",
);

test("both screenshot lanes render inside the same container image", () => {
    // 검증 lane 과 생성 lane 이 다른 이미지를 쓰면 anti-aliasing·폰트 차이가 baseline 에 그대로
    // 박혀 CI rendered PNG 로 baseline 을 교체하는 ping-pong 이 돌아온다.
    for (const source of [verify, generate]) {
        assert.match(source, /file: Dockerfile\.screenshot/);
        assert.match(source, /tags: slowclock-screenshot:latest/);
        assert.match(source, /docker run --rm/);
    }
});

test("the generate lane renders exactly the impact modules and validates them again", () => {
    // impact 가 고른 모듈에서 update 를 돌리고, 같은 모듈을 validate 로 다시 확인해야 «생성은
    // 됐는데 검증은 안 되는» baseline 이 커밋되지 않는다.
    assert.match(generate, /SCREENSHOT_MODULES: \$\{\{ steps\.impact\.outputs\.screenshot_modules \}\}/);
    assert.match(generate, /update_tasks\+=\("\$\{module\}:updateScreenshotTest"\)/);
    assert.match(generate, /validate_tasks\+=\("\$\{module\}:validateScreenshotTest"\)/);
    assert.match(generate, /--rerun/);
});

test("the packaged baseline paths come from the same module list", () => {
    // 렌더한 모듈과 다른 reference 경로를 스테이징하면 골든을 만든 적 없는 자리를 담게 된다.
    assert.match(generate, /module_path="\$\{module#:\}"/);
    assert.match(generate, /src\/screenshotTestDebug\/reference/);
});

test("the apply lane accepts only reference roots that belong to real modules", async () => {
    // apply 는 PR tree 를 checkout 하지 않고 artifact 의 PNG 만 커밋한다. 허용 경로가 실제
    // 모듈과 어긋나면 아무 경로에나 커밋할 수 있게 된다. 모듈 목록은 settings.gradle.kts 에서
    // 읽어 손으로 적은 목록과 대조한다.
    //
    // 정확히 같기를 요구하지는 않는다. 이 lane 은 기본 브랜치의 워크플로를 읽으므로 새 모듈의
    // 경로는 그 모듈에 미리보기를 붙이는 PR 보다 먼저 올라와 있어야 한다. 그래서 아직
    // screenshotTest 소스셋이 없는 모듈의 경로를 미리 적어 두는 것을 허용한다. 생성되는 PNG 가
    // 없으니 허용 범위는 넓어지지 않는다(#111).
    const modules = await inspectModules(repoRoot);
    const withScreenshots = modules
        .filter(({ screenshot }) => screenshot)
        .map(({ directory }) => `${directory}/src/screenshotTestDebug/reference/`)
        .sort();
    assert.ok(withScreenshots.length > 0, "screenshotTest 소스셋을 가진 모듈을 하나도 못 찾았다");

    const allowedByModule = new Set(
        modules.map(({ directory }) => `${directory}/src/screenshotTestDebug/reference/`),
    );

    const rootsBlock = /const roots = \[\n((?:\s+'[^']+',\n)+)\s+\];/.exec(apply)?.[1];
    assert.ok(rootsBlock, "apply 워크플로의 roots 목록을 찾지 못했다");
    const declared = [...rootsBlock.matchAll(/'([^']+)'/g)].map((match) => match[1]).sort();

    for (const root of withScreenshots) {
        assert.ok(declared.includes(root), `미리보기가 있는 모듈의 경로가 빠졌다: ${root}`);
    }
    for (const root of declared) {
        assert.ok(allowedByModule.has(root), `저장소에 없는 모듈의 경로다: ${root}`);
    }
    assert.equal(new Set(declared).size, declared.length, "중복된 경로가 있다");
});

test("a run that can generate nothing fails loudly", () => {
    // 영향 범위에 screenshot 모듈이 없으면 골든이 하나도 안 나온다. 초록으로 끝내면 라벨을 단
    // 사람은 생성이 끝난 줄 안다.
    assert.match(generate, /if ! grep -q '\^screenshot_required=true\$' "\$GITHUB_OUTPUT"; then/);
    assert.match(generate, /::error::이 PR의 영향 범위에는 생성할 screenshot baseline이 없습니다/);
});
