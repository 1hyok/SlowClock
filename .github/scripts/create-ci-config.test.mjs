import assert from "node:assert/strict";
import { execFile as execFileCallback } from "node:child_process";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import {
    CI_CONFIG_MODE,
    ciEnvironment,
    createCiConfig,
    renderGitHubEnvironment,
    renderGoogleServicesJson,
} from "./create-ci-config.mjs";

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const githubDirectory = resolve(scriptsDirectory, "..");
const execFile = promisify(execFileCallback);

async function createTemporaryWorkspace() {
    const workspace = await mkdtemp(join(tmpdir(), "slowclock-ci-config-"));
    return {
        workspace,
        githubEnv: join(workspace, "github-env"),
    };
}

test("creates the same secretless app configuration in clean workspaces", async () => {
    const first = await createTemporaryWorkspace();
    const second = await createTemporaryWorkspace();

    try {
        await createCiConfig(first);
        await createCiConfig(second);

        const firstGoogleServices = await readFile(
            join(first.workspace, "app/google-services.json"),
            "utf8",
        );
        const secondGoogleServices = await readFile(
            join(second.workspace, "app/google-services.json"),
            "utf8",
        );
        assert.equal(firstGoogleServices, secondGoogleServices);
        assert.equal(firstGoogleServices, renderGoogleServicesJson());
        assert.equal(await readFile(first.githubEnv, "utf8"), renderGitHubEnvironment());
    } finally {
        await Promise.all([
            rm(first.workspace, { recursive: true, force: true }),
            rm(second.workspace, { recursive: true, force: true }),
        ]);
    }
});

test("uses inert placeholders with the production Android package contract", () => {
    const googleServices = JSON.parse(renderGoogleServicesJson());
    const serialized = JSON.stringify(googleServices);

    assert.equal(
        googleServices.client[0].client_info.android_client_info.package_name,
        "com.ilhyok.slowclock",
    );
    assert.equal(ciEnvironment.SLOWCLOCK_CI_CONFIG_MODE, CI_CONFIG_MODE);
    assert.doesNotMatch(serialized, /AIza[0-9A-Za-z_-]{30,}/);
    assert.doesNotMatch(serialized, /apps\.googleusercontent\.com/);
});

test("supports the composite action command-line contract", async () => {
    const temporary = await createTemporaryWorkspace();

    try {
        await execFile(process.execPath, [
            join(scriptsDirectory, "create-ci-config.mjs"),
            "--workspace",
            temporary.workspace,
            "--github-env",
            temporary.githubEnv,
        ]);
        assert.equal(
            await readFile(join(temporary.workspace, "app/google-services.json"), "utf8"),
            renderGoogleServicesJson(),
        );
        assert.equal(await readFile(temporary.githubEnv, "utf8"), renderGitHubEnvironment());
    } finally {
        await rm(temporary.workspace, { recursive: true, force: true });
    }
});

test("keeps pull request validation secretless and release credentials isolated", async () => {
    const workflowsDirectory = join(githubDirectory, "workflows");
    const workflowNames = (await readdir(workflowsDirectory)).filter(
        (name) => name.endsWith(".yml") || name.endsWith(".yaml"),
    );
    const workflows = new Map(
        await Promise.all(
            workflowNames.map(async (name) => [
                name,
                await readFile(join(workflowsDirectory, name), "utf8"),
            ]),
        ),
    );

    const pullRequestTargetWorkflows = [];
    for (const [name, workflow] of workflows) {
        const usesPullRequestTarget = /^\s*pull_request_target\s*:/m.test(workflow);
        if (usesPullRequestTarget) {
            pullRequestTargetWorkflows.push(name);
        }
        if (!/^\s*pull_request\s*:/m.test(workflow) && !usesPullRequestTarget) {
            continue;
        }

        const secretReferences = [
            ...workflow.matchAll(
                /\bsecrets(?:\.([A-Za-z0-9_]+)|\[['"]([^'"]+)['"]\])/g,
            ),
        ].map((match) => match[1] ?? match[2]);
        assert.deepEqual(
            [...new Set(secretReferences)].filter((name_) => name_ !== "GITHUB_TOKEN"),
            [],
            `${name} must not depend on repository or environment secrets`,
        );
    }

    // pull_request_target은 default branch 정의에 쓰기 권한을 줄 수 있어 금지한다. 이 저장소에는
    // PR 코드를 실행하지 않는 좁은 bridge 도 없다 — 하나라도 생기면 여기서 잡는다.
    assert.deepEqual(pullRequestTargetWorkflows, []);

    const actionReference = "uses: ./.github/actions/setup-ci-config";
    for (const name of [
        "lint.yml",
        "unit-test.yml",
        "screenshot.yml",
    ]) {
        assert.match(workflows.get(name), new RegExp(actionReference.replaceAll(".", "\\.")));
    }

    const releaseWorkflow = workflows.get("release-distribution.yml");
    assert.doesNotMatch(releaseWorkflow, /\.\/\.github\/actions\/setup-ci-config/);
    // Firebase 인증은 장기 JSON 키가 아니라 WIF 다 — 릴리스 경로가 Google 자격을
    // 다룬다는 사실은 그대로고, 그 자격이 저장된 비밀이 아니라 단기 토큰으로 바뀌었다.
    for (const secretName of [
        "GOOGLE_SERVICES_JSON_B64",
        "RELEASE_STORE_FILE_B64",
        "GCP_WORKLOAD_IDENTITY_PROVIDER",
        "GCP_FIREBASE_SERVICE_ACCOUNT",
    ]) {
        assert.match(releaseWorkflow, new RegExp(`secrets\\.${secretName}`));
    }
});
