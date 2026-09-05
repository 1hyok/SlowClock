#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

// ktlint-gradle 이 모듈마다 남기는 checkstyle 리포트 위치.
const REPORT_DIRECTORY = path.join("build", "reports", "ktlint");
// 모듈 경로는 feature/<name> 두 단계다. 여유를 두되 build 디렉터리 아래로는 내려가지 않는다.
const MAX_MODULE_DEPTH = 4;
const SKIPPED_DIRECTORIES = new Set(["build", "node_modules", ".git", ".gradle", ".idea"]);
// job summary 는 1MB 상한이라 위반 전량을 붙이지 않는다. 나머지는 Gradle 콘솔 출력에 있다.
const MAX_LISTED_VIOLATIONS = 100;

function decodeXml(value) {
    return String(value)
        .replaceAll("&quot;", '"')
        .replaceAll("&apos;", "'")
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&amp;", "&");
}

function attribute(attributes, name) {
    const match = new RegExp(`\\b${name}="([^"]*)"`).exec(attributes);
    return match ? decodeXml(match[1]) : null;
}

export function parseCheckstyleXml(xml) {
    const violations = [];
    const filePattern = /<file\b([^>]*)>([\s\S]*?)<\/file>/g;
    let fileMatch;
    while ((fileMatch = filePattern.exec(xml)) !== null) {
        const file = attribute(fileMatch[1], "name");
        if (!file) {
            throw new Error("checkstyle file element without a name");
        }
        const errorPattern = /<error\b([^>]*)\/>/g;
        let errorMatch;
        while ((errorMatch = errorPattern.exec(fileMatch[2])) !== null) {
            violations.push({
                file,
                line: Number(attribute(errorMatch[1], "line") ?? 0),
                column: Number(attribute(errorMatch[1], "column") ?? 0),
                severity: attribute(errorMatch[1], "severity") ?? "error",
                message: attribute(errorMatch[1], "message") ?? "",
                rule: attribute(errorMatch[1], "source") ?? "unknown",
            });
        }
    }
    return violations;
}

export async function collectReportFiles(root) {
    const reports = [];

    async function collectFromBuildDirectory(buildDirectory) {
        const reportRoot = path.join(buildDirectory, "reports", "ktlint");
        let entries;
        try {
            entries = await fs.readdir(reportRoot, { withFileTypes: true, recursive: true });
        } catch {
            return;
        }
        for (const entry of entries) {
            if (entry.isFile() && entry.name.endsWith(".xml")) {
                reports.push(path.join(entry.parentPath ?? reportRoot, entry.name));
            }
        }
    }

    async function walk(directory, depth) {
        let entries;
        try {
            entries = await fs.readdir(directory, { withFileTypes: true });
        } catch {
            return;
        }
        for (const entry of entries) {
            if (!entry.isDirectory()) {
                continue;
            }
            if (entry.name === "build") {
                await collectFromBuildDirectory(path.join(directory, entry.name));
                continue;
            }
            if (SKIPPED_DIRECTORIES.has(entry.name) || entry.name.startsWith(".")) {
                continue;
            }
            if (depth > 0) {
                await walk(path.join(directory, entry.name), depth - 1);
            }
        }
    }

    await walk(root, MAX_MODULE_DEPTH);
    return reports.sort();
}

export async function collectViolations(root) {
    const violations = [];
    for (const report of await collectReportFiles(root)) {
        violations.push(...parseCheckstyleXml(await fs.readFile(report, "utf8")));
    }
    return violations;
}

function relativize(root, file) {
    const relative = path.relative(root, file);
    return relative.startsWith("..") ? file : relative;
}

function countByRule(violations) {
    const counts = new Map();
    for (const violation of violations) {
        counts.set(violation.rule, (counts.get(violation.rule) ?? 0) + 1);
    }
    return [...counts.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
}

export function renderSummary(violations, { root = process.cwd(), reportCount = 0 } = {}) {
    if (violations.length === 0) {
        return reportCount === 0
            ? ["## ktlint", "", "리포트가 없다 — ktlintCheck 가 실행되기 전에 중단됐는지 확인할 것."].join("\n")
            : ["## ktlint", "", `✅ 위반 없음 (리포트 ${reportCount}건)`].join("\n");
    }

    const sorted = [...violations].sort(
        (left, right) =>
            left.file.localeCompare(right.file) || left.line - right.line || left.column - right.column,
    );
    const files = new Set(sorted.map((violation) => violation.file));
    const lines = [
        "## ktlint",
        "",
        `❌ **위반 ${sorted.length}건** — 파일 ${files.size}개, 규칙 ${countByRule(sorted).length}종`,
        "",
        "| 규칙 | 건수 |",
        "| --- | ---: |",
    ];
    for (const [rule, count] of countByRule(sorted)) {
        lines.push(`| \`${rule}\` | ${count} |`);
    }
    lines.push("", "<details><summary>위반 위치</summary>", "");
    for (const violation of sorted.slice(0, MAX_LISTED_VIOLATIONS)) {
        lines.push(
            `- \`${relativize(root, violation.file)}:${violation.line}:${violation.column}\` — ${violation.message} (\`${violation.rule}\`)`,
        );
    }
    if (sorted.length > MAX_LISTED_VIOLATIONS) {
        lines.push("", `외 ${sorted.length - MAX_LISTED_VIOLATIONS}건 — 전량은 위 Gradle 스텝 로그에 있다.`);
    }
    lines.push("", "</details>", "", "로컬에서 `./gradlew ktlintFormat` 으로 대부분 자동 교정된다.");
    return lines.join("\n");
}

async function main() {
    const root = process.env.GITHUB_WORKSPACE || process.cwd();
    const reports = await collectReportFiles(root);
    const violations = [];
    for (const report of reports) {
        violations.push(...parseCheckstyleXml(await fs.readFile(report, "utf8")));
    }

    const summary = renderSummary(violations, { root, reportCount: reports.length });
    const target = process.env.GITHUB_STEP_SUMMARY;
    if (target) {
        await fs.appendFile(target, `${summary}\n`);
    } else {
        process.stdout.write(`${summary}\n`);
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    await main();
}
