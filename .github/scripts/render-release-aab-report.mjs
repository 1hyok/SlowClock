import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const REPORT_SCHEMA_VERSION = 1;
export const MEANINGFUL_INCREASE_BYTES = 1024 * 1024;
export const MEANINGFUL_INCREASE_PERCENT = 5;

function parseInteger(name, value) {
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < 0) {
        throw new Error(`${name} must be a non-negative safe integer`);
    }
    return parsed;
}

export function compareSize(currentBytes, baselineBytes) {
    const deltaBytes = currentBytes - baselineBytes;
    const deltaPercent = baselineBytes === 0 ? null : (deltaBytes / baselineBytes) * 100;
    const meaningfulIncrease =
        deltaBytes > 0 &&
        (deltaBytes >= MEANINGFUL_INCREASE_BYTES ||
            (deltaPercent !== null && deltaPercent >= MEANINGFUL_INCREASE_PERCENT));

    return {
        baselineBytes,
        currentBytes,
        deltaBytes,
        deltaPercent,
        meaningfulIncrease,
    };
}

export const NO_MAPPING = "none";

export function buildReport(current, baseline = null, generatedAt = new Date().toISOString()) {
    // v1 은 release 에서 minify 를 끄고 있어 R8 mapping 이 없다. 켜는 순간 preflight 가 digest 를 남긴다.
    const mappingPresent = current.mappingSha256 !== NO_MAPPING;
    const comparison = baseline
        ? {
              aab: compareSize(current.aabSizeBytes, baseline.artifacts.aab.sizeBytes),
              minimumDownload: compareSize(
                  current.minimumDownloadBytes,
                  baseline.sizes.minimumDownloadBytes,
              ),
              maximumDownload: compareSize(
                  current.maximumDownloadBytes,
                  baseline.sizes.maximumDownloadBytes,
              ),
              installableApk: compareSize(
                  current.installableApkBytes,
                  baseline.sizes.installableApkBytes,
              ),
          }
        : null;
    const meaningfulIncrease = comparison
        ? Object.values(comparison).some((metric) => metric.meaningfulIncrease)
        : false;

    return {
        schemaVersion: REPORT_SCHEMA_VERSION,
        generatedAt,
        sourceSha: current.sourceSha,
        validation: {
            ciConfigMode: "stub",
            signingMode: "ephemeral",
            releaseVariant: "release",
            r8Minification: mappingPresent,
            resourceShrinking: mappingPresent,
            requiredEntries: true,
            jarSignature: true,
            mappingPresent,
        },
        artifacts: {
            aab: {
                sha256: current.aabSha256,
                sizeBytes: current.aabSizeBytes,
                signerSha256: current.signerSha256,
            },
            mapping: {
                present: mappingPresent,
                sha256: mappingPresent ? current.mappingSha256 : null,
            },
        },
        sizes: {
            minimumDownloadBytes: current.minimumDownloadBytes,
            maximumDownloadBytes: current.maximumDownloadBytes,
            installableApkBytes: current.installableApkBytes,
        },
        bundletool: {
            version: current.bundletoolVersion,
            sha256: current.bundletoolSha256,
        },
        baseline: baseline
            ? {
                  sourceSha: baseline.sourceSha,
                  generatedAt: baseline.generatedAt,
              }
            : null,
        comparison,
        policy: {
            meaningfulIncreaseBytes: MEANINGFUL_INCREASE_BYTES,
            meaningfulIncreasePercent: MEANINGFUL_INCREASE_PERCENT,
            meaningfulIncrease,
        },
    };
}

function formatBytes(value) {
    return `${value.toLocaleString("en-US")} bytes`;
}

function formatComparison(comparison) {
    if (!comparison) {
        return "baseline unavailable";
    }
    const sign = comparison.deltaBytes > 0 ? "+" : "";
    const percent =
        comparison.deltaPercent === null
            ? "n/a"
            : `${sign}${comparison.deltaPercent.toFixed(2)}%`;
    return `${sign}${formatBytes(comparison.deltaBytes)} (${percent})${
        comparison.meaningfulIncrease ? " ⚠️" : ""
    }`;
}

export function renderMarkdown(report) {
    const comparison = report.comparison;
    return [
        "## Release AAB preflight",
        "",
        `- Source SHA: \`${report.sourceSha}\``,
        `- AAB SHA-256: \`${report.artifacts.aab.sha256}\``,
        `- Signer SHA-256: \`${report.artifacts.aab.signerSha256}\``,
        report.artifacts.mapping.present
            ? `- R8 mapping: present (SHA-256 \`${report.artifacts.mapping.sha256}\`)`
            : "- R8 mapping: absent (isMinifyEnabled=false)",
        `- Baseline: ${report.baseline ? `\`${report.baseline.sourceSha}\`` : "unavailable"}`,
        "",
        "| Metric | Current | Change from baseline |",
        "| --- | ---: | ---: |",
        `| AAB archive | ${formatBytes(report.artifacts.aab.sizeBytes)} | ${formatComparison(
            comparison?.aab,
        )} |`,
        `| Estimated download (min) | ${formatBytes(report.sizes.minimumDownloadBytes)} | ${formatComparison(
            comparison?.minimumDownload,
        )} |`,
        `| Estimated download (max) | ${formatBytes(report.sizes.maximumDownloadBytes)} | ${formatComparison(
            comparison?.maximumDownload,
        )} |`,
        `| Installable universal APK | ${formatBytes(report.sizes.installableApkBytes)} | ${formatComparison(
            comparison?.installableApk,
        )} |`,
        "",
        `Meaningful increase policy: at least ${formatBytes(
            report.policy.meaningfulIncreaseBytes,
        )} or ${report.policy.meaningfulIncreasePercent}%.`,
        "",
        "> The AAB, APK set, universal APK, and R8 mapping are intentionally excluded from public artifacts.",
        "",
    ].join("\n");
}

function parseArguments(arguments_) {
    const options = {};
    for (let index = 0; index < arguments_.length; index += 2) {
        const key = arguments_[index];
        const value = arguments_[index + 1];
        if (!key?.startsWith("--") || value === undefined) {
            throw new Error(`invalid argument near ${key ?? "<end>"}`);
        }
        options[key.slice(2)] = value;
    }
    return options;
}

async function main(arguments_) {
    const options = parseArguments(arguments_);
    const required = [
        "output-json",
        "output-markdown",
        "source-sha",
        "aab-sha256",
        "aab-size-bytes",
        "signer-sha256",
        "mapping-sha256",
        "minimum-download-bytes",
        "maximum-download-bytes",
        "installable-apk-bytes",
        "bundletool-version",
        "bundletool-sha256",
    ];
    for (const name of required) {
        if (!options[name]) {
            throw new Error(`--${name} is required`);
        }
    }

    const baseline = options.baseline
        ? JSON.parse(await readFile(resolve(options.baseline), "utf8"))
        : null;
    if (baseline && baseline.schemaVersion !== REPORT_SCHEMA_VERSION) {
        throw new Error(`unsupported baseline schema: ${baseline.schemaVersion}`);
    }

    const report = buildReport(
        {
            sourceSha: options["source-sha"],
            aabSha256: options["aab-sha256"],
            aabSizeBytes: parseInteger("aab-size-bytes", options["aab-size-bytes"]),
            signerSha256: options["signer-sha256"],
            mappingSha256: options["mapping-sha256"],
            minimumDownloadBytes: parseInteger(
                "minimum-download-bytes",
                options["minimum-download-bytes"],
            ),
            maximumDownloadBytes: parseInteger(
                "maximum-download-bytes",
                options["maximum-download-bytes"],
            ),
            installableApkBytes: parseInteger(
                "installable-apk-bytes",
                options["installable-apk-bytes"],
            ),
            bundletoolVersion: options["bundletool-version"],
            bundletoolSha256: options["bundletool-sha256"],
        },
        baseline,
    );

    const jsonPath = resolve(options["output-json"]);
    const markdownPath = resolve(options["output-markdown"]);
    await Promise.all([
        mkdir(dirname(jsonPath), { recursive: true }),
        mkdir(dirname(markdownPath), { recursive: true }),
    ]);
    await Promise.all([
        writeFile(jsonPath, `${JSON.stringify(report, null, 2)}\n`, "utf8"),
        writeFile(markdownPath, renderMarkdown(report), "utf8"),
    ]);

    if (report.policy.meaningfulIncrease) {
        console.log("::warning::Release size increased beyond the configured threshold.");
    }
    console.log(`Wrote release preflight report to ${jsonPath}`);
}

const isDirectExecution =
    process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirectExecution) {
    try {
        await main(process.argv.slice(2));
    } catch (error) {
        console.error(`::error::${error.message}`);
        process.exitCode = 1;
    }
}
