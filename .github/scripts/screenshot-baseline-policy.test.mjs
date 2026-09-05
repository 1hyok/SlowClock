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

test("the apply lane accepts only the reference roots of modules that declare screenshot tests", async () => {
    // apply 는 PR tree 를 checkout 하지 않고 artifact 의 PNG 만 커밋한다. 허용 경로가 실제
    // screenshotTest 소스셋과 어긋나면 새 모듈의 골든이 조용히 거절되거나, 반대로 아무
    // 경로나 커밋된다. 모듈 목록은 settings.gradle.kts 에서 읽어 손으로 적은 목록과 대조한다.
    const modules = await inspectModules(repoRoot);
    const expected = modules
        .filter(({ screenshot }) => screenshot)
        .map(({ directory }) => `${directory}/src/screenshotTestDebug/reference/`)
        .sort();
    assert.ok(expected.length > 0, "screenshotTest 소스셋을 가진 모듈을 하나도 못 찾았다");

    const rootsBlock = /const roots = \[\n((?:\s+'[^']+',\n)+)\s+\];/.exec(apply)?.[1];
    assert.ok(rootsBlock, "apply 워크플로의 roots 목록을 찾지 못했다");
    const declared = [...rootsBlock.matchAll(/'([^']+)'/g)].map((match) => match[1]).sort();
    assert.deepEqual(declared, expected);
});

test("a run that can generate nothing fails loudly", () => {
    // 영향 범위에 screenshot 모듈이 없으면 골든이 하나도 안 나온다. 초록으로 끝내면 라벨을 단
    // 사람은 생성이 끝난 줄 안다.
    assert.match(generate, /if ! grep -q '\^screenshot_required=true\$' "\$GITHUB_OUTPUT"; then/);
    assert.match(generate, /::error::이 PR의 영향 범위에는 생성할 screenshot baseline이 없습니다/);
});
