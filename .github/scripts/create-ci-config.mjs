import { appendFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const CI_CONFIG_MODE = "stub";

export const ciEnvironment = Object.freeze({
    SLOWCLOCK_CI_CONFIG_MODE: CI_CONFIG_MODE,
});

export const googleServicesFixture = Object.freeze({
    project_info: {
        project_number: "000000000000",
        project_id: "slowclock-ci-stub",
        storage_bucket: "slowclock-ci-stub.invalid",
    },
    client: [
        {
            client_info: {
                mobilesdk_app_id: "1:000000000000:android:0000000000000000000000",
                android_client_info: {
                    package_name: "com.ilhyok.slowclock",
                },
            },
            oauth_client: [],
            api_key: [
                {
                    current_key: "ci_placeholder_not_a_real_google_api_key",
                },
            ],
            services: {
                appinvite_service: {
                    other_platform_oauth_client: [],
                },
            },
        },
    ],
    configuration_version: "1",
});

export function renderGoogleServicesJson() {
    return `${JSON.stringify(googleServicesFixture, null, 2)}\n`;
}

export function renderGitHubEnvironment() {
    return `${Object.entries(ciEnvironment)
        .map(([name, value]) => `${name}=${value}`)
        .join("\n")}\n`;
}

export async function createCiConfig({ workspace, githubEnv }) {
    if (!workspace) {
        throw new Error("workspace is required");
    }
    if (!githubEnv) {
        throw new Error("githubEnv is required");
    }

    const googleServicesPath = resolve(workspace, "app/google-services.json");
    await mkdir(dirname(googleServicesPath), { recursive: true });
    await writeFile(googleServicesPath, renderGoogleServicesJson(), {
        encoding: "utf8",
        mode: 0o600,
    });
    await appendFile(githubEnv, renderGitHubEnvironment(), "utf8");

    return { googleServicesPath };
}

function parseArguments(arguments_) {
    const options = {};
    for (let index = 0; index < arguments_.length; index += 1) {
        const argument = arguments_[index];
        const value = arguments_[index + 1];
        if (argument === "--workspace") {
            options.workspace = value;
            index += 1;
        } else if (argument === "--github-env") {
            options.githubEnv = value;
            index += 1;
        } else {
            throw new Error(`unknown argument: ${argument}`);
        }
    }
    return options;
}

const isDirectExecution =
    process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirectExecution) {
    try {
        const arguments_ = parseArguments(process.argv.slice(2));
        const { googleServicesPath } = await createCiConfig({
            workspace: arguments_.workspace ?? process.env.GITHUB_WORKSPACE,
            githubEnv: arguments_.githubEnv ?? process.env.GITHUB_ENV,
        });
        console.log(`Created CI-only app configuration at ${googleServicesPath}`);
    } catch (error) {
        console.error(`::error::${error.message}`);
        process.exitCode = 1;
    }
}
