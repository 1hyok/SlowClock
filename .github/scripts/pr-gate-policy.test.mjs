import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);
const ENTRY_WORKFLOW = "pr-validation.yml";
const VALIDATION_WORKFLOWS = ["lint.yml", "unit-test.yml", "screenshot.yml", "repository-quality.yml"];
const HEAVY_VALIDATION_WORKFLOWS = ["lint.yml", "unit-test.yml", "screenshot.yml"];
// main 브랜치 보호 규칙의 required status checks 와 함께 바꿔야 하는 외부 계약이다.
const REQUIRED_VALIDATION_CONTEXTS = [
    "Repository Quality / Repository Quality",
    "Screenshot / Validate Compose Preview Screenshots",
    "Static Analysis / Check Code Quality (Ktlint)",
    "Static Analysis / Check Project Issues (Android Lint)",
    "Unit Test / Run Unit Tests",
];

async function workflows() {
    const names = (await readdir(workflowDirectory)).filter((name) => name.endsWith(".yml"));
    return Promise.all(names.map(async (name) => [name, await readFile(new URL(name, workflowDirectory), "utf8")]));
}

function readWorkflow(name) {
    return readFile(new URL(name, workflowDirectory), "utf8");
}

function jobNames(source) {
    const jobsSection = source.slice(source.indexOf("\njobs:\n"));
    return [...jobsSection.matchAll(/^ {2}([A-Za-z][\w-]*):$/gm)].map((match) => match[1]);
}

function displayNameOf(source, job) {
    const pattern = new RegExp(`^ {2}${job}:$[\\s\\S]*?^ {4}name:\\s*(.+)$`, "m");
    return pattern.exec(source)?.[1]?.trim();
}

function calledWorkflowOf(source, job) {
    const pattern = new RegExp(`^ {2}${job}:$[\\s\\S]*?^ {4}uses:\\s*\\./\\.github/workflows/(.+)$`, "m");
    return pattern.exec(source)?.[1]?.trim();
}

test("pull request validation has exactly one entry point", async () => {
    // 검증 워크플로가 스스로 pull_request 를 듣고 있으면 같은 이름의 check 가 두 벌 돈다.
    const entryPoints = (await workflows())
        .filter(([name, source]) => /^on:\n(?:[^\n]*\n)*?\s{2}pull_request:/m.test(source) && VALIDATION_WORKFLOWS.includes(name))
        .map(([name]) => name);

    assert.deepEqual(entryPoints, []);
    assert.match(
        await readWorkflow(ENTRY_WORKFLOW),
        /^\s{2}pull_request:\n\s{4}types: \[opened, synchronize, reopened, edited\]$/m,
    );
});

test("every validation workflow is reachable only as a reusable call", async () => {
    for (const name of VALIDATION_WORKFLOWS) {
        assert.match(await readWorkflow(name), /^\s{2}workflow_call:$/m, `${name} must be callable`);
    }
});

test("the entry workflow calls every validation workflow without an aggregate runner job", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const jobs = jobNames(entry);

    assert.equal(jobs.length, VALIDATION_WORKFLOWS.length);
    assert.doesNotMatch(entry, /^ {2}ci-gate:$/m);
    for (const workflow of VALIDATION_WORKFLOWS) {
        assert.ok(entry.includes(`uses: ./.github/workflows/${workflow}`), `${workflow} is not called`);
    }
});

test("required validation context names stay aligned with the repository ruleset", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const contexts = [];

    for (const job of jobNames(entry)) {
        const callerName = displayNameOf(entry, job);
        const workflow = calledWorkflowOf(entry, job);
        assert.ok(callerName, `${job} has no display name`);
        assert.ok(workflow, `${job} is not a reusable workflow call`);

        const reusable = await readWorkflow(workflow);
        for (const reusableJob of jobNames(reusable)) {
            const reusableName = displayNameOf(reusable, reusableJob);
            assert.ok(reusableName, `${workflow}:${reusableJob} has no display name`);
            contexts.push(`${callerName} / ${reusableName}`);
        }
    }

    assert.deepEqual(contexts.sort(), [...REQUIRED_VALIDATION_CONTEXTS].sort());
});


test("stale runs are cancelled per pull request", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.match(
        entry,
        /^concurrency:\n\s{2}group: pr-validation-\$\{\{ github\.event\.pull_request\.number \|\| inputs\.pull_request_number \|\| github\.ref \}\}\n\s{2}cancel-in-progress: true$/m,
    );
});

test("token-authored commits preserve the pull request context on manual dispatch", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.match(entry, /^\s{2}workflow_dispatch:\n\s{4}inputs:\n\s{6}pull_request_number:/m);
    assert.equal(
        (entry.match(/pull_request_number: \$\{\{ inputs\.pull_request_number \|\| github\.event\.pull_request\.number \|\| 0 \}\}/g) ?? []).length,
        VALIDATION_WORKFLOWS.length,
    );
});


test("validation without a pull request number falls back to the full suite", async () => {
    // main push 와 잘못된 dispatch 에는 PR 이 없다. 번호가 빈 문자열이면 number 입력 자체가 깨지고,
    // 0 으로 닫으면 main push 와 같은 전량 검증 경로를 그대로 탄다.
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const repositoryQuality = await readWorkflow("repository-quality.yml");

    assert.equal(
        (entry.match(/\|\| github\.event\.pull_request\.number \|\| 0 \}\}/g) ?? []).length,
        VALIDATION_WORKFLOWS.length,
    );
    for (const gate of ["Require linked Issue"]) {
        assert.match(
            repositoryQuality,
            new RegExp(`- name: ${gate}\\n\\s+if: inputs\\.pull_request_number > 0`),
            `${gate} must stay inert without a pull request`,
        );
    }
});


test("the entry point keeps no pull_request branch or path filter", async () => {
    // base 변경은 기본 activity type 에 없어 재트리거되지 않는다. 필터가 있으면 base 를 바꾼
    // PR 이 검증을 한 번도 거치지 않고 머지된다.
    // required workflow 자체의 paths 필터는 제외된 PR 에 check 를 만들지 않아 ruleset 을
    // 영구 pending 으로 남길 수 있으므로 변경 파일 분류는 job 안에서 한다.
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const trigger = /^on:\n\s{2}pull_request:\n([\s\S]*?)^\S/m.exec(entry)?.[1] ?? "";

    assert.doesNotMatch(trigger, /branches/);
    assert.doesNotMatch(trigger, /^\s+paths(?:-ignore)?:/m);
});

test("impact outputs scope each heavy lane and classification failure runs full validation", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.equal((entry.match(/^ {4}needs: repository-quality$/gm) ?? []).length, HEAVY_VALIDATION_WORKFLOWS.length);
    assert.equal(
        (entry.match(/^ {4}if: \$\{\{ !cancelled\(\) \}\}$/gm) ?? []).length,
        HEAVY_VALIDATION_WORKFLOWS.length,
        "quality failures must fan out to full validation without reviving a cancelled stale run",
    );
    for (const output of ["ktlint_required", "android_lint_required", "unit_test_required", "screenshot_required"]) {
        assert.match(
            entry,
            new RegExp(`needs\\.repository-quality\\.result != 'success' \\|\\| needs\\.repository-quality\\.outputs\\.${output} != 'false'`),
        );
    }
    assert.match(entry, /outputs\.ktlint_tasks \|\| 'ktlintCheck'/);
    assert.match(entry, /outputs\.screenshot_tasks \|\| ':app:validateScreenshotTest'/);
});

test("heavy reusable workflows default to full validation and preserve every required job context", async () => {
    for (const name of ["unit-test.yml", "screenshot.yml"]) {
        const source = await readWorkflow(name);
        const jobs = jobNames(source);

        assert.match(
            source,
            /^ {6}run_validation:\n(?: {8}.+\n)*? {8}default: true\n {8}type: boolean$/m,
            `${name} must default callers such as main validation to the full suite`,
        );
        assert.equal(
            (source.match(/^ {4}if: inputs\.run_validation$/gm) ?? []).length,
            jobs.length,
            `${name} must skip work at the existing required job boundary`,
        );
    }
    const lint = await readWorkflow("lint.yml");
    for (const input of ["run_ktlint", "run_android_lint"]) {
        assert.match(
            lint,
            new RegExp(`^ {6}${input}:\\n(?: {8}.+\\n)*? {8}default: true\\n {8}type: boolean$`, "m"),
        );
    }
    assert.match(lint, /^ {4}if: inputs\.run_ktlint$/m);
    assert.match(lint, /^ {4}if: inputs\.run_android_lint$/m);
});

test("repository quality owns fail-closed paginated impact classification and PR gates", async () => {
    const repositoryQuality = await readWorkflow("repository-quality.yml");
    const unitTest = await readWorkflow("unit-test.yml");

    assert.match(repositoryQuality, /^ {4}outputs:\n {6}docs_only:\n(?: {8}.+\n)*? {8}value: \$\{\{ jobs\.repository-quality\.outputs\.docs_only \}\}$/m);
    assert.match(repositoryQuality, /^ {4}outputs:\n {6}docs_only: \$\{\{ steps\.classify-documentation-changes\.outputs\.docs_only \}\}$/m);
    assert.match(repositoryQuality, /gh api --paginate --slurp/);
    assert.match(repositoryQuality, /classify-documentation-changes\.mjs \\\n\s+"\$CHANGED_FILES"/);
    assert.match(repositoryQuality, /resolve-pr-impact\.mjs "\$files_json"/);
    assert.match(repositoryQuality, /if \[ "\$GITHUB_EVENT_NAME" = "workflow_dispatch" \]; then/);
    assert.match(repositoryQuality, /if \[ "\$GITHUB_SHA" != "\$head_sha" \]; then/);
    assert.match(repositoryQuality, /persist-credentials: false/);
    assert.match(repositoryQuality, /env -u GH_TOKEN -u GITHUB_TOKEN/);
    assert.match(
        repositoryQuality,
        /- name: Require linked Issue\n\s+if: inputs\.pull_request_number > 0/,
    );
    assert.match(repositoryQuality, /pull_request_json=%s\\n' "\$pull_request_file"/);
    assert.doesNotMatch(unitTest, /Require linked Issue/);
});

test("editing the pull request body retriggers every required validation context", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.match(entry, /types: \[opened, synchronize, reopened, edited\]/);
});

test("repository quality still runs on main pushes", async () => {
    const source = await readWorkflow("repository-quality.yml");

    assert.match(source, /^\s{2}push:\n\s{4}branches:\s*\[main\]$/m);
});
