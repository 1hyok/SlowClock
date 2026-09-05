import assert from "node:assert/strict";
import test from "node:test";

import {
    changedPathsFromGithubFiles,
    githubOutputLines,
    isAndroidModuleBuild,
    resolvePrImpact,
} from "./resolve-pr-impact.mjs";

// settings.gradle.kts 의 실제 모듈 구성을 본뜬 fixture. :app 만 screenshotTest 소스셋을 갖는다.
const modules = [
    module(":app", { screenshot: true }),
    module(":core:model"),
    module(":core:common"),
    module(":core:ui"),
    module(":core:alarm"),
    module(":core:data"),
    module(":feature:main"),
    module(":feature:information"),
];

const dependencies = new Map([
    [":app", new Set([":core:model", ":core:common", ":core:ui", ":core:alarm", ":core:data", ":feature:main", ":feature:information"])],
    [":core:model", new Set()],
    [":core:common", new Set([":core:model"])],
    [":core:ui", new Set([":core:model", ":core:common"])],
    [":core:alarm", new Set([":core:model", ":core:data"])],
    [":core:data", new Set([":core:model", ":core:common"])],
    [":feature:main", new Set([":core:ui", ":core:data", ":core:model", ":core:common", ":core:alarm"])],
    [":feature:information", new Set([":core:ui", ":core:model"])],
]);

function module(projectPath, overrides = {}) {
    return {
        projectPath,
        directory: projectPath.slice(1).replaceAll(":", "/"),
        android: true,
        screenshot: false,
        ...overrides,
    };
}

test("GitHub pagination and rename payloads include both old and new paths", () => {
    assert.deepEqual(
        changedPathsFromGithubFiles([
            [{ filename: "feature/main/New.kt", previous_filename: "feature/main/Old.kt" }],
            [{ filename: "README.md" }],
        ]),
        ["README.md", "feature/main/New.kt", "feature/main/Old.kt"],
    );
});

test("JVM and Android test plugins are excluded from Android lint task selection", () => {
    assert.equal(isAndroidModuleBuild('plugins { id("java-library") }'), false);
    assert.equal(isAndroidModuleBuild('plugins { id("org.jetbrains.kotlin.jvm") }'), false);
    assert.equal(isAndroidModuleBuild('plugins { alias(libs.plugins.kotlin.jvm) }'), false);
    assert.equal(isAndroidModuleBuild('plugins { id("com.android.test") }'), false);
    assert.equal(isAndroidModuleBuild('plugins { alias(libs.plugins.android.test) }'), false);
    assert.equal(isAndroidModuleBuild('plugins { alias(libs.plugins.android.library) }'), true);

    // 순수 JVM 모듈은 lintDebug 가 없고 단위 테스트 태스크 이름도 test 다.
    const mixedModules = [module(":app"), module(":core:model", { android: false })];
    const mixedDependencies = new Map([
        [":app", new Set([":core:model"])],
        [":core:model", new Set()],
    ]);
    const impact = resolvePrImpact(["core/model/src/main/kotlin/Model.kt"], mixedModules, mixedDependencies);

    assert.deepEqual(impact.androidLintTasks, [":app:lintDebug", ":app:processDebugMainManifest"]);
    assert.deepEqual(impact.unitTestTasks, [":app:testDebugUnitTest", ":core:model:test", ":app:compileDebugAndroidTestKotlin"]);
});

test("a production change fans out to every reverse-dependent module", () => {
    const impact = resolvePrImpact(["core/ui/src/main/java/com/example/slowclock/ui/theme/Color.kt"], modules, dependencies);

    // core:ui 를 소비하는 feature 모듈과 app 이 모두 검증 대상이다. core:model 처럼 상류만 있는 모듈은 아니다.
    assert.deepEqual(impact.unitTestModules, [":app", ":core:ui", ":feature:information", ":feature:main"]);
    assert.deepEqual(impact.unitTestTasks, [
        ":app:testDebugUnitTest",
        ":core:ui:testDebugUnitTest",
        ":feature:information:testDebugUnitTest",
        ":feature:main:testDebugUnitTest",
        ":app:compileDebugAndroidTestKotlin",
    ]);
    assert.deepEqual(impact.androidLintTasks, [
        ":app:lintDebug",
        ":core:ui:lintDebug",
        ":feature:information:lintDebug",
        ":feature:main:lintDebug",
        ":app:processDebugMainManifest",
    ]);
    assert.equal(impact.verifyManifest, true);
    assert.deepEqual(impact.ktlintTasks, [":core:ui:ktlintCheck"]);
    // screenshot baseline 은 :app 에만 있고, app 은 core:ui 의 역의존이라 검증 대상이다.
    assert.deepEqual(impact.screenshotModules, [":app"]);
    assert.deepEqual(impact.screenshotTasks, [":app:validateScreenshotTest"]);
    assert.equal(impact.codeqlJavaKotlin, true);
    assert.equal(impact.runNodeTests, false);
    assert.equal(impact.repositoryQualityFull, false);
});

test("a leaf module change stays inside that module", () => {
    const impact = resolvePrImpact(["feature/information/src/main/java/com/example/slowclock/ui/information/InfoViewModel.kt"], modules, dependencies);

    assert.deepEqual(impact.unitTestModules, [":app", ":feature:information"]);
    assert.deepEqual(impact.androidLintTasks, [":app:lintDebug", ":feature:information:lintDebug", ":app:processDebugMainManifest"]);
    assert.deepEqual(impact.screenshotModules, [":app"]);
});

test("a unit test-only change runs that module's tests without lint or screenshots", () => {
    const impact = resolvePrImpact(["core/common/src/test/java/com/example/slowclock/util/FormatterTest.kt"], modules, dependencies);

    assert.deepEqual(impact.unitTestTasks, [":core:common:testDebugUnitTest"]);
    assert.deepEqual(impact.androidLintTasks, []);
    assert.equal(impact.verifyManifest, false);
    assert.deepEqual(impact.screenshotTasks, []);
    assert.deepEqual(impact.ktlintTasks, [":core:common:ktlintCheck"]);
    assert.equal(impact.codeqlJavaKotlin, false);
});

test("an androidTest change compiles the instrumentation source set and lints its module", () => {
    const impact = resolvePrImpact(["app/src/androidTest/java/com/example/slowclock/ExampleInstrumentedTest.kt"], modules, dependencies);

    assert.deepEqual(impact.unitTestTasks, [":app:compileDebugAndroidTestKotlin"]);
    assert.deepEqual(impact.androidLintTasks, [":app:lintDebug", ":app:processDebugMainManifest"]);
    assert.deepEqual(impact.screenshotTasks, []);
});

test("a screenshot test change validates only that module's baselines", () => {
    const impact = resolvePrImpact(["app/src/screenshotTest/java/com/example/slowclock/EmptyStateScreenshotTest.kt"], modules, dependencies);

    assert.deepEqual(impact.screenshotTasks, [":app:validateScreenshotTest"]);
    assert.deepEqual(impact.unitTestTasks, []);
    assert.deepEqual(impact.androidLintTasks, []);
    assert.deepEqual(impact.ktlintTasks, [":app:ktlintCheck"]);
});

test("documentation-only changes select no Gradle work", () => {
    const impact = resolvePrImpact(["README.md", "docs/play-release.md"], modules, dependencies);

    assert.deepEqual(impact.ktlintTasks, []);
    assert.deepEqual(impact.androidLintTasks, []);
    assert.deepEqual(impact.unitTestTasks, []);
    assert.deepEqual(impact.screenshotTasks, []);
    assert.equal(impact.runNodeTests, false);
    assert.equal(impact.repositoryQualityFull, false);
    assert.deepEqual(githubOutputLines(impact), [
        "ktlint_required=false",
        "ktlint_tasks=",
        "android_lint_required=false",
        "android_lint_tasks=",
        "verify_manifest=false",
        "unit_test_required=false",
        "run_node_tests=false",
        "unit_test_tasks=",
        "screenshot_required=false",
        "screenshot_modules=",
        "screenshot_tasks=",
        "codeql_java_kotlin=false",
        "repository_quality_full=false",
        "repository_quality_fixtures=false",
    ]);
});

test("workflow, action and script changes run the policy tests and Actions CodeQL", () => {
    const impact = resolvePrImpact([".github/workflows/lint.yml", ".github/scripts/render-ktlint-summary.mjs"], modules, dependencies);

    assert.equal(impact.runNodeTests, true);
    // lint.yml 은 영향 계산 정책 경로라 모든 lane 을 fail-closed 로 연다.
    assert.equal(impact.repositoryQualityFull, true);
    assert.deepEqual(impact.ktlintTasks, ["ktlintCheck"]);
});

test("repository quality fixtures rerun when the checker itself changes", () => {
    const impact = resolvePrImpact(["scripts/repository-quality.sh"], modules, dependencies);

    assert.equal(impact.repositoryQualityFixtures, true);
    assert.equal(impact.repositoryQualityFull, true);
});

test("global Gradle inputs and the editorconfig widen validation to every module", () => {
    for (const filePath of ["gradle/libs.versions.toml", "settings.gradle.kts", "gradle.properties", "gradlew"]) {
        const impact = resolvePrImpact([filePath], modules, dependencies);
        assert.deepEqual(impact.ktlintTasks, ["ktlintCheck"], filePath);
        assert.deepEqual(impact.unitTestModules, modules.map(({ projectPath }) => projectPath).sort(), filePath);
        assert.deepEqual(impact.screenshotTasks, [":app:validateScreenshotTest"], filePath);
        assert.equal(impact.codeqlJavaKotlin, true, filePath);
        assert.equal(impact.runNodeTests, true, filePath);
    }

    const editorconfig = resolvePrImpact([".editorconfig"], modules, dependencies);
    assert.deepEqual(editorconfig.ktlintTasks, ["ktlintCheck"]);
    assert.deepEqual(editorconfig.unitTestTasks, []);
});

test("screenshot infrastructure changes validate every screenshot module", () => {
    for (const filePath of ["Dockerfile.screenshot", ".dockerignore"]) {
        const impact = resolvePrImpact([filePath], modules, dependencies);
        assert.deepEqual(impact.screenshotModules, [":app"], filePath);
        assert.deepEqual(impact.unitTestTasks, [], filePath);
    }
});

test("Firestore 규칙 파일은 Android 검증을 강제하지 않는다", () => {
    // 규칙은 전용 워크플로가 에뮬레이터로 검증한다. 여기서 전체 lane 을 켜면 규칙 한 줄 고칠 때마다
    // Android 빌드가 통째로 돈다.
    for (const filePath of [
        "firestore.rules",
        "firestore.indexes.json",
        "firebase.json",
        "firestore-tests/rules.test.mjs",
    ]) {
        const impact = resolvePrImpact([filePath], modules, dependencies);

        assert.equal(impact.repositoryQualityFull, false, filePath);
        assert.deepEqual(impact.ktlintTasks, [], filePath);
        assert.deepEqual(impact.screenshotTasks, [], filePath);
        assert.deepEqual(impact.unitTestTasks, [], filePath);
    }
});

test("an unrecognised top-level path fails closed to full validation", () => {
    const impact = resolvePrImpact(["tools/new-thing.sh"], modules, dependencies);

    assert.equal(impact.repositoryQualityFull, true);
    assert.deepEqual(impact.ktlintTasks, ["ktlintCheck"]);
    assert.deepEqual(impact.screenshotTasks, [":app:validateScreenshotTest"]);
});

test("a module build script change compiles for CodeQL and lints the module", () => {
    const impact = resolvePrImpact(["core/data/build.gradle.kts"], modules, dependencies);

    assert.equal(impact.codeqlJavaKotlin, true);
    assert.deepEqual(impact.ktlintTasks, [":core:data:ktlintCheck"]);
    assert.deepEqual(impact.androidLintTasks, [
        ":app:lintDebug",
        ":core:alarm:lintDebug",
        ":core:data:lintDebug",
        ":feature:main:lintDebug",
        ":app:processDebugMainManifest",
    ]);
});
