import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);

async function workflows() {
    const names = (await readdir(workflowDirectory)).filter((name) => name.endsWith(".yml"));
    return Promise.all(names.map(async (name) => [name, await readFile(new URL(name, workflowDirectory), "utf8")]));
}

// 주석은 왜 그렇게 했는지를 적어 두는 자리라 액션 이름·플래그가 그대로 등장한다.
// 정책은 실제로 실행되는 줄에만 걸어야 한다.
function withoutComments(source) {
    return source
        .split("\n")
        .filter((line) => !/^\s*#/.test(line))
        .join("\n");
}

// Actions 캐시 엔트리는 (키, ref) 로 격리되고, 읽기는 «현재 ref → base → default branch»
// 순으로 매칭된다. 이 저장소는 main 단일 흐름이라 경쟁이 일어나는 범위는 main 스코프 하나다 —
// main push 에서 도는 release 워크플로도 같은 키를 쓰므로 여기서 함께 센다.
function runsOnMainPush(source) {
    return /^\s{2}push:\n\s{4}branches:\s*\[[^\]]*\bmain\b/m.test(source);
}

function runsOnPullRequests(source) {
    return /^on:\n(?:[^\n]*\n)*?\s{2}pull_request:/m.test(source);
}

// 캐시를 만드는 액션은 setup-gradle 하나가 아니다. dependency-submission 도 같은 Gradle User
// Home 엔트리를 같은 키로 저장한다 — 이쪽을 세지 않으면 main writer 가 둘이 된 채로 정책이
// 통과한 적이 있다 (#1480).
function gradleCacheSteps(source) {
    return (withoutComments(source).match(/uses:\s*gradle\/actions\/(?:setup-gradle|dependency-submission)@/g) ?? [])
        .length;
}

// 생략은 read-only 선언이 아니라 액션 기본값에 맡기는 것이고, dependency-submission 은 그
// 기본값으로 main 에서도 저장한다. 그래서 «false 명시» 를 세면 위반이 그대로 새어 나간다 —
// 판정은 «step 마다 true 를 명시했는가» 여야 한다.
function readOnlyDeclarations(source) {
    return (withoutComments(source).match(/cache-read-only:\s*true/g) ?? []).length;
}

test("exactly one workflow may write the shared Gradle User Home cache", async () => {
    // Actions 캐시는 같은 키를 덮어쓰지 못한다. writer 가 둘이면 먼저 끝난 쪽이 키를
    // 차지하고, build cache 를 채운 쪽의 저장이 조용히 버려진다 (#996).
    const writers = (await workflows())
        .filter(([, source]) => runsOnMainPush(source) || runsOnPullRequests(source))
        .filter(([, source]) => gradleCacheSteps(source) > readOnlyDeclarations(source))
        .map(([name]) => name);

    assert.deepEqual(writers, ["build-cache-warm.yml"]);
});

test("every other Gradle workflow reads the cache without writing to it", async () => {
    for (const [name, source] of await workflows()) {
        if (name === "build-cache-warm.yml" || gradleCacheSteps(source) === 0) {
            continue;
        }
        if (!runsOnPullRequests(source) && !runsOnMainPush(source)) {
            continue;
        }
        assert.equal(
            readOnlyDeclarations(source),
            gradleCacheSteps(source),
            `${name} must declare cache-read-only: true on every Gradle cache step`,
        );
        // 조건식으로 쓰면 pull_request 가 아닌 trigger 에서 writer 로 돌변한다.
        assert.doesNotMatch(
            source,
            /cache-read-only:\s*\$\{\{/,
            `${name} must not decide cache writability from the event`,
        );
    }
});

test("the warming workflow runs on main pushes and is never cancelled mid-run", async () => {
    const source = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");

    assert.match(source, /push:\n\s+branches:\s*\[main\]/);
    // 캐시는 job 의 post 스텝에서 저장된다. 중도 취소되면 그 스텝에 닿지 못한다.
    assert.match(source, /cancel-in-progress:\s*false/);
    assert.match(source, /--build-cache/);
});

test("the warming workflow covers the tasks pull request jobs actually run", async () => {
    const warm = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");
    const lint = await readFile(new URL("lint.yml", workflowDirectory), "utf8");
    const unitTest = await readFile(new URL("unit-test.yml", workflowDirectory), "utf8");

    for (const task of ["ktlintCheck", "lintDebug"]) {
        assert.ok(lint.includes(task), `lint.yml no longer exposes ${task} as its full-scope default`);
        assert.match(warm, new RegExp(`\\b${task}\\b`), `warming misses ${task}`);
    }
    for (const task of ["testDebugUnitTest", ":app:compileDebugAndroidTestKotlin"]) {
        assert.ok(unitTest.includes(task), `unit-test.yml no longer runs ${task}`);
        assert.ok(warm.includes(task), `warming misses ${task}`);
    }
});


test("CodeQL stays out of the build cache in both directions", async () => {
    const source = await readFile(new URL("codeql.yml", workflowDirectory), "utf8");

    // 추출기가 컴파일러 호출을 관찰해야 하므로 FROM-CACHE 로 넘어가면 안 된다 (#1014).
    assert.doesNotMatch(withoutComments(source), /--build-cache/);
    assert.match(source, /cache-read-only:\s*true/);
});

test("the local Gradle default stays uncached", async () => {
    // --build-cache 는 CI 한정이다. 로컬은 손상된 cache 엔트리 사례 때문에 꺼 둔다.
    const properties = await readFile(new URL("../../gradle.properties", import.meta.url), "utf8");

    assert.match(properties, /^org\.gradle\.caching=false$/m);
});

// setup-gradle 이 basic 캐시 키를 만들 때 해시하는 glob 정본 — 이 워크플로가 고정한 v6.3.0 기준.
// https://github.com/gradle/actions/blob/v6.3.0/sources/src/cache-service-basic.ts
//
// 키 프리픽스가 `setup-java` 라 setup-java 의 목록(gradle.properties 포함)으로 착각하기 쉽다.
// 둘은 다르다. 이 워크플로에서 캐시를 만드는 쪽은 setup-gradle 이므로 정본은 이쪽이다.
const CACHE_KEY_INPUTS = [
    "**/*.gradle*",
    "**/gradle-wrapper.properties",
    "buildSrc/**/Versions.kt",
    "buildSrc/**/Dependencies.kt",
    "gradle/*.versions.toml",
    "**/versions.properties",
];

test("the warming workflow only runs when the cache key can actually change", async () => {
    // 키가 그대로면 setup-gradle 이 exact match 로 복원하고 저장을 건너뛴다. 그런 run 은
    // 저장이 원천적으로 불가능한데 11분을 쓴다 (#1047). 트리거를 키 입력과 일치시킨다 —
    // 좁으면 캐시가 채워지지 않고, 넓으면 저장 못 하는 run 이 다시 생긴다.
    const source = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");
    const pathsBlock = /^\s{4}paths:\n((?:\s{6}- .*\n)+)/m.exec(source)?.[1];

    assert.ok(pathsBlock, "the warming workflow must filter its push trigger by path");
    const declared = [...pathsBlock.matchAll(/^\s{6}- '(.+)'$/gm)].map((match) => match[1]);
    assert.deepEqual(declared, CACHE_KEY_INPUTS);
});

test("manual warming stays available for a cache that needs rebuilding out of band", async () => {
    const source = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");

    assert.match(source, /^\s{2}workflow_dispatch:$/m);
});
