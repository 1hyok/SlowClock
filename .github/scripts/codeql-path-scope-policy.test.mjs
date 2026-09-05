import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { inspectModules } from "./resolve-pr-impact.mjs";

const workflowUrl = new URL("../workflows/codeql.yml", import.meta.url);
const repoRoot = path.resolve(fileURLToPath(new URL("../..", import.meta.url)));

function jobBlock(source, jobName, nextJobName) {
    const start = source.indexOf(`  ${jobName}:`);
    assert.notEqual(start, -1, `${jobName} job is missing`);
    const end = nextJobName === undefined ? source.length : source.indexOf(`  ${nextJobName}:`, start + 1);
    assert.notEqual(end, -1, `${nextJobName} job is missing`);
    return source.slice(start, end);
}

test("CodeQL keeps its pull request trigger unfiltered so required checks cannot stay pending", async () => {
    const source = await readFile(workflowUrl, "utf8");
    const trigger = /^on:\n\s{2}pull_request:\n([\s\S]*?)^\s{2}push:/m.exec(source)?.[1] ?? "";

    assert.doesNotMatch(trigger, /paths(?:-ignore)?:/);
    assert.match(source, /^\s{2}workflow_dispatch:\n\s{4}inputs:\n\s{6}pull_request_number:/m);
});

test("CodeQL classifies every PR file by language and defaults to full analysis", async () => {
    const source = await readFile(workflowUrl, "utf8");
    const classifier = jobBlock(source, "classify-changes", "analyze-actions");

    assert.match(classifier, /gh api --paginate --slurp/);
    assert.match(classifier, /classify-documentation-changes\.mjs/);
    assert.match(classifier, /docs_only=.*classify-documentation-changes\.mjs/s);
    assert.match(classifier, /resolve-pr-impact\.mjs/);
    assert.match(classifier, /actions_required: \$\{\{ steps\.path-scope\.outputs\.codeql_actions \|\| 'true' \}\}/);
    assert.match(classifier, /javascript_typescript_required: \$\{\{ steps\.path-scope\.outputs\.codeql_javascript_typescript \|\| 'true' \}\}/);
    assert.match(classifier, /java_kotlin_required: \$\{\{ steps\.path-scope\.outputs\.codeql_java_kotlin \|\| 'true' \}\}/);
    assert.match(classifier, /codeql_javascript_typescript=false/);
    assert.match(classifier, /codeql_javascript_typescript=true/);
    assert.match(classifier, /if \[ "\$GITHUB_EVENT_NAME" = "workflow_dispatch" \]; then/);
    assert.match(classifier, /if \[ "\$GITHUB_SHA" != "\$head_sha" \]; then/);
    assert.match(classifier, /permissions:\n\s+contents: read\n\s+pull-requests: read/);
    assert.doesNotMatch(classifier, /security-events: write/);
    assert.match(classifier, /persist-credentials: false/);
    assert.match(classifier, /env -u GH_TOKEN -u GITHUB_TOKEN/);
});

test("CodeQL preserves all required context names and skips only an unaffected language", async () => {
    const source = await readFile(workflowUrl, "utf8");
    const actions = jobBlock(source, "analyze-actions", "analyze-javascript-typescript");
    const javascript = jobBlock(source, "analyze-javascript-typescript", "analyze-java-kotlin");
    const kotlin = jobBlock(source, "analyze-java-kotlin");
    const actionsFailClosed = /if: \$\{\{ !cancelled\(\) && \(needs\.classify-changes\.result != 'success' \|\| needs\.classify-changes\.outputs\.actions_required != 'false'\) \}\}/;
    const javascriptFailClosed = /if: \$\{\{ !cancelled\(\) && \(needs\.classify-changes\.result != 'success' \|\| needs\.classify-changes\.outputs\.javascript_typescript_required != 'false'\) \}\}/;
    const kotlinFailClosed = /if: \$\{\{ !cancelled\(\) && \(needs\.classify-changes\.result != 'success' \|\| needs\.classify-changes\.outputs\.java_kotlin_required != 'false'\) \}\}/;

    assert.match(actions, /^\s{4}name: Analyze \(actions\)$/m);
    assert.match(javascript, /^\s{4}name: Analyze \(javascript-typescript\)$/m);
    assert.match(kotlin, /^\s{4}name: Analyze \(java-kotlin\)$/m);
    assert.match(actions, /^\s{4}needs: classify-changes$/m);
    assert.match(javascript, /^\s{4}needs: classify-changes$/m);
    assert.match(kotlin, /^\s{4}needs: classify-changes$/m);
    assert.match(actions, actionsFailClosed);
    assert.match(javascript, javascriptFailClosed);
    assert.match(kotlin, kotlinFailClosed);
    assert.match(actions, /security-events: write/);
    assert.match(javascript, /security-events: write/);
    assert.match(kotlin, /security-events: write/);
    assert.match(javascript, /languages: javascript-typescript/);
    assert.match(javascript, /build-mode: none/);
    assert.match(javascript, /category: \/language:javascript-typescript/);
    assert.match(javascript, /CODEQL_DATABASE_LANGUAGE: javascript/);
    assert.match(javascript, /CODEQL_REQUIRED_PATH_FRAGMENT: \.github\/scripts\//);
    assert.doesNotMatch(source, /^\s{4}strategy:/m);
});

test("CodeQL compiles every module through compileDebugSources", async () => {
    const source = await readFile(workflowUrl, "utf8");
    const kotlin = jobBlock(source, "analyze-java-kotlin");
    // 이 저장소의 모듈은 전부 Android 라이브러리/애플리케이션이라 compileDebugSources 하나로 모든
    // Kotlin 이 컴파일된다. 순수 JVM 모듈이 생기면 그 모듈은 :classes 로 따로 컴파일해야 추출에
    // 들어온다 — 빌드 스크립트에서 판정해 규약이 바뀌면 여기서 깨지게 한다.
    const modules = await inspectModules(repoRoot);
    assert.ok(modules.length > 0, "모듈을 하나도 못 찾았다 — 판정이 망가졌다");

    assert.match(kotlin, /\.\/gradlew compileDebugSources/);
    // 주석은 왜 그렇게 했는지를 적는 자리라 플래그 이름이 그대로 등장한다 — 실행 줄에만 건다.
    const kotlinCommands = kotlin.split("\n").filter((line) => !/^\s*#/.test(line)).join("\n");
    assert.doesNotMatch(kotlinCommands, /--build-cache/);
    modules.forEach(({ projectPath, android }) => {
        if (!android) {
            assert.ok(kotlin.includes(`${projectPath}:classes`), `${projectPath}:classes is missing from the CodeQL build`);
        }
    });
});
