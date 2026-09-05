#!/usr/bin/env node

import { createHash } from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const CORE_ALIASES = new Set([
    "android-gradlePlugin",
    "androidx-biometric",
    "androidx-compose-bom",
    "androidx-credentials",
    "coroutines-core",
    "firebase-bom",
    "hilt-android",
    "kotlin-gradlePlugin",
    "ksp",
    "okhttp",
    "retrofit",
]);

const ACTIONABLE_CONSISTENCY_TYPES = new Set([
    "bom-override-mismatch",
    "declared-resolved-mismatch",
    "missing-version-ref",
    "toolchain-version-mismatch",
]);

const DEFAULT_ISSUE_TYPE_IDS = {
    Bug: "IT_kwDOD_R4ms4B5VUt",
    Task: "IT_kwDOD_R4ms4B5VUs",
};

// 감사가 발행한 이슈에만 붙는 표식. 닫힌 이슈까지 조회 범위를 넓히면서도 저장소 전체 이슈를
// 페이징하지 않기 위한 것이다 (#1191).
const AUDIT_TRACKING_LABEL = "dependency-audit";
const AUDIT_AREA_LABEL = "area:platform";

const COORDINATE_DISPLAY_NAMES = {
    "com.android.tools.build:gradle": "Android Gradle Plugin",
    "org.jetbrains.kotlin:kotlin-gradle-plugin": "Kotlin Gradle Plugin",
};

function cleanMentions(value) {
    return String(value ?? "").replaceAll("@", "@\u200b");
}

function unique(values) {
    return [...new Set(values)];
}

function fingerprint(value) {
    return createHash("sha256").update(JSON.stringify(value)).digest("hex").slice(0, 16);
}

function findingMarkers(finding) {
    return {
        key: `<!-- dependency-audit-key: ${finding.key} -->`,
        fingerprint: `<!-- dependency-audit-fingerprint: ${finding.fingerprint} -->`,
    };
}

function runEvidence(audit) {
    return [
        audit.runUrl ? `- CI 실행: ${audit.runUrl}` : null,
        audit.commitSha ? `- 검사 커밋: \`${audit.commitSha}\`` : null,
        `- 생성 시각: ${audit.generatedAt}`,
    ].filter(Boolean);
}

// 권고가 떴다는 사실만으로는 무엇을 해야 하는지가 정해지지 않는다. 올릴 정식판이 있는지가
// 대응을 가른다 — 있으면 카탈로그 한 줄이고, 없으면 프로덕션 툴체인을 프리릴리스로 올릴지를
// 따로 판단해야 한다. 그 갈림을 본문에 적어 두어야 읽는 사람이 매번 다시 조사하지 않는다.
function patchAvailability(finding, stableFix) {
    if (stableFix) {
        return [
            `정식 패치판 \`${stableFix}\` 이 배포돼 있습니다 — 카탈로그를 이 버전 이상으로 올리면 해소됩니다.`,
        ];
    }
    if (finding.firstPatched.length > 0) {
        return [
            `이 권고를 해소하는 최초 패치 버전은 ${finding.firstPatched.map((version) => `\`${version}\``).join(", ")} 이고, **아직 정식(stable) 릴리스가 없습니다**` +
                `${finding.latestStable ? ` (현재 정식 최신 \`${finding.latestStable}\`)` : ""}.`,
            "",
            "프리릴리스 툴체인 채택의 회귀 위험과 이 권고의 실제 공격면을 견줘 판단해야 합니다. 보류로 판단해 이 이슈를 닫아 두면, 정식 패치판이 배포되는 순간 감사가 자동으로 다시 엽니다.",
        ];
    }
    return ["영향 범위와 수정 버전을 확인하고 안전한 버전으로 올려야 합니다."];
}

function vulnerabilityFindings(audit) {
    const grouped = new Map();
    for (const finding of audit.vulnerabilities ?? []) {
        const current = grouped.get(finding.coordinate) ?? {
            coordinate: finding.coordinate,
            versions: [],
            aliases: [],
            vulnerabilities: [],
            firstPatched: [],
            stableFixVersions: [],
        };
        current.versions.push(finding.version);
        current.aliases.push(...(finding.aliases ?? []));
        current.vulnerabilities.push(...finding.vulnerabilities.map((item) => item.id));
        current.firstPatched.push(...finding.vulnerabilities.map((item) => item.firstPatched).filter(Boolean));
        current.stableFixVersions.push(finding.stableFixVersion ?? null);
        current.latestStable = finding.latestStable ?? current.latestStable ?? null;
        grouped.set(finding.coordinate, current);
    }
    return [...grouped.values()].map((finding) => {
        finding.versions = unique(finding.versions).sort();
        finding.aliases = unique(finding.aliases).sort();
        finding.vulnerabilities = unique(finding.vulnerabilities).sort();
        finding.firstPatched = unique(finding.firstPatched).sort();
        // 해석 버전이 여럿이면 그 전부를 넘기는 버전이라야 «정식으로 고칠 수 있다» 가 된다.
        const stableFix = finding.stableFixVersions.every(Boolean)
            ? unique(finding.stableFixVersions).sort().at(-1)
            : null;
        const displayName = COORDINATE_DISPLAY_NAMES[finding.coordinate] ??
            finding.aliases[0] ??
            finding.coordinate;
        const evidence = [
            `OSV가 현재 해석된 \`${finding.coordinate}\` ${finding.versions.map((version) => `\`${version}\``).join(", ")}에서 보안 권고를 반환했습니다.`,
            "",
            ...finding.vulnerabilities.map(
                (id) => `- [${id}](https://osv.dev/vulnerability/${encodeURIComponent(id)})`,
            ),
            "",
            ...patchAvailability(finding, stableFix),
            "",
            ...runEvidence(audit),
        ].join("\n");
        const result = {
            key: `security:${finding.coordinate}`,
            title: `fix(build): ${displayName} 보안 권고 대응 필요`,
            label: "bug",
            issueType: "Bug",
            overview: evidence,
        };
        // stableFix 는 «있을 때만» 넣는다. 없을 때의 직렬화 결과가 이 필드를 도입하기 전과
        // 같아야, 정식 패치판이 없어 보류해 둔 이슈가 이 변경 자체로 깨어나지 않는다.
        // 반대로 정식판이 나오는 순간 키가 생겨 fingerprint 가 바뀌고, 닫아 둔 이슈가
        // 자동으로 다시 열린다 — #986 을 닫으며 남겨 둔 구멍이 이 한 줄로 닫힌다.
        result.fingerprint = fingerprint({
            key: result.key,
            versions: finding.versions,
            vulnerabilities: finding.vulnerabilities,
            ...(stableFix ? { stableFix } : {}),
        });
        return result;
    });
}

function consistencyIssueFindings(audit) {
    const grouped = new Map();
    for (const finding of audit.consistencyFindings ?? []) {
        if (!ACTIONABLE_CONSISTENCY_TYPES.has(finding.type)) {
            continue;
        }
        const coordinate = finding.coordinate ?? finding.alias;
        const current = grouped.get(coordinate) ?? [];
        current.push(finding);
        grouped.set(coordinate, current);
    }
    return [...grouped.entries()].map(([coordinate, findings]) => {
        const alias = findings[0].alias ?? coordinate;
        const evidence = [
            `CI가 \`${coordinate}\`의 선언·BOM·Gradle 해석 결과 사이 정합성 문제를 감지했습니다.`,
            "",
            ...findings.map((finding) => `- ${cleanMentions(finding.message)}`),
            "",
            "버전 카탈로그와 실제 해석 버전을 하나의 의도로 맞추고 회귀 검증이 필요합니다.",
            "",
            ...runEvidence(audit),
        ].join("\n");
        const result = {
            key: `consistency:${coordinate}`,
            title: `fix(build): ${alias} 선언 버전과 Gradle 해석 버전을 일치시킨다`,
            label: "bug",
            issueType: "Bug",
            overview: evidence,
        };
        result.fingerprint = fingerprint({ key: result.key, findings });
        return result;
    });
}

function majorUpdateFindings(audit) {
    const preferred = new Map();
    for (const entry of audit.entries ?? []) {
        if (!CORE_ALIASES.has(entry.alias) || entry.updateKind !== "major" || !entry.latestStable) {
            continue;
        }
        if (!preferred.has(entry.coordinate) || entry.kind === "library") {
            preferred.set(entry.coordinate, entry);
        }
    }
    return [...preferred.values()].map((entry) => {
        const evidence = [
            `핵심 의존성 \`${entry.coordinate}\`에 major 업데이트가 확인됐습니다.`,
            "",
            `- 현재: \`${entry.currentVersion}\``,
            `- 최신 안정: \`${entry.latestStable}\``,
            entry.latestInChannel ? `- 현재 채널 최신: \`${entry.latestInChannel}\`` : null,
            entry.metadata?.url ? `- Maven metadata: ${entry.metadata.url}` : null,
            "",
            "공식 릴리스 노트의 breaking change와 이 프로젝트의 SDK·JVM 경계를 확인한 뒤 별도 대응해야 합니다.",
            "",
            ...runEvidence(audit),
        ].filter(Boolean).join("\n");
        const result = {
            key: `major:${entry.coordinate}:${String(entry.latestStable).split(".")[0]}`,
            title: `chore(build): ${entry.alias} ${entry.currentVersion}→${entry.latestStable} major 업데이트를 검토한다`,
            label: "enhancement",
            issueType: "Task",
            overview: evidence,
        };
        result.fingerprint = fingerprint({
            key: result.key,
            currentVersion: entry.currentVersion,
            latestStable: entry.latestStable,
        });
        return result;
    });
}

function compatibilityFinding(audit) {
    if (audit.compatibility?.exitCode === undefined || audit.compatibility.exitCode === 0) {
        return [];
    }
    const result = {
        key: "compatibility:dependency-audit",
        title: "fix(build): 주간 Android 의존성 호환성 검증 실패를 복구한다",
        label: "bug",
        issueType: "Bug",
        overview: [
            "기본 브랜치의 주간 의존성 감사에서 현재 SDK·JDK 경계의 Gradle 호환성 검증이 실패했습니다.",
            "",
            `- 종료 코드: \`${audit.compatibility.exitCode}\``,
            "- CI artifact의 `compatibility.log`에서 최초 실패 지점을 확인하세요.",
            "",
            ...runEvidence(audit),
        ].join("\n"),
    };
    result.fingerprint = fingerprint({
        key: result.key,
        commitSha: audit.commitSha,
        exitCode: audit.compatibility.exitCode,
    });
    return [result];
}

export function selectActionableFindings(audit) {
    return [
        ...vulnerabilityFindings(audit),
        ...consistencyIssueFindings(audit),
        ...majorUpdateFindings(audit),
        ...compatibilityFinding(audit),
    ].sort((left, right) => left.key.localeCompare(right.key));
}

export function renderIssueBody(finding) {
    const markers = findingMarkers(finding);
    const typeDescription = finding.label === "bug" ? "버그·오동작" : "신규 기능·기존 기능 개선";
    return [
        "### 작업 유형",
        "",
        `${finding.label} — ${typeDescription}`,
        "",
        "### 주 담당 모듈",
        "",
        "platform — Android 앱·CI·빌드·릴리스·저장소 운영",
        "",
        "### 개요",
        "",
        cleanMentions(finding.overview),
        "",
        markers.key,
        markers.fingerprint,
        "",
        "### 자식 이슈",
        "",
        "_No response_",
        "",
        "### 참고",
        "",
        "dependency-audit 자동 생성 이슈",
    ].join("\n");
}

function updateCommentBody(finding, audit) {
    const markers = findingMarkers(finding);
    return [
        markers.fingerprint,
        "## 주간 의존성 재검사",
        "",
        cleanMentions(finding.overview),
        "",
        audit.runUrl ? `[CI 실행 보기](${audit.runUrl})` : null,
    ].filter(Boolean).join("\n");
}

function apiClient(token) {
    return async (apiPath, options = {}) => {
        const response = await fetch(`https://api.github.com${apiPath}`, {
            ...options,
            headers: {
                Accept: "application/vnd.github+json",
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
                "User-Agent": "SlowClock-dependency-audit",
                "X-GitHub-Api-Version": "2022-11-28",
                ...options.headers,
            },
        });
        const text = await response.text();
        const payload = text ? JSON.parse(text) : null;
        if (!response.ok) {
            throw new Error(`GitHub API ${response.status}: ${payload?.message ?? text}`);
        }
        if (payload?.errors?.length) {
            throw new Error(`GitHub GraphQL: ${payload.errors.map((error) => error.message).join("; ")}`);
        }
        return payload;
    };
}

async function listIssues(api, repository, query) {
    const issues = [];
    for (let page = 1; page <= 10; page += 1) {
        const search = new URLSearchParams({ ...query, per_page: "100", page: String(page) });
        const batch = await api(`/repos/${repository}/issues?${search}`);
        issues.push(...batch.filter((issue) => !issue.pull_request));
        if (batch.length < 100) {
            break;
        }
    }
    return issues;
}

// 열린 이슈는 라벨 없이 전량 조회한다 — 추적 라벨 도입 전에 만들어진 이슈도 계속 찾아야 하고,
// 그 비용은 어차피 지금도 치르고 있다. 닫힌 이슈까지 같은 방식으로 훑으면 저장소 이슈 전체를
// 페이징하게 되므로 그쪽만 라벨로 좁힌다. 라벨이 떨어진 닫힌 이슈는 «못 찾아서 새로 만든다» 는
// 종전과 같은 실패 모드로 떨어질 뿐, 잘못 종료되지는 않는다.
async function listTrackedIssues(api, repository) {
    const open = await listIssues(api, repository, { state: "open" });
    const closed = await listIssues(api, repository, {
        state: "closed",
        labels: AUDIT_TRACKING_LABEL,
    });
    return [...open, ...closed];
}

async function hasFingerprint(api, repository, issue, markers) {
    if (issue.body?.includes(markers.fingerprint)) {
        return true;
    }
    const comments = await listIssueComments(api, repository, issue.number);
    return comments.some((comment) => comment.body?.includes(markers.fingerprint));
}

async function listIssueComments(api, repository, issueNumber) {
    const comments = [];
    for (let page = 1; page <= 10; page += 1) {
        const batch = await api(
            `/repos/${repository}/issues/${issueNumber}/comments?per_page=100&page=${page}`,
        );
        comments.push(...batch);
        if (batch.length < 100) {
            break;
        }
    }
    return comments;
}

async function ensureIssueMetadata(api, issue, finding, repository, assignee, issueTypeIds) {
    const labels = unique([
        ...(issue.labels ?? []).map((label) => typeof label === "string" ? label : label.name),
        finding.label,
        AUDIT_TRACKING_LABEL,
        AUDIT_AREA_LABEL,
    ]).filter(Boolean);
    const assignees = unique([
        ...(issue.assignees ?? []).map((item) => item.login),
        assignee,
    ]).filter(Boolean);
    await api(`/repos/${repository}/issues/${issue.number}`, {
        method: "PATCH",
        body: JSON.stringify({ title: finding.title.slice(0, 256), labels, assignees }),
    });
    await api("/graphql", {
        method: "POST",
        body: JSON.stringify({
            query: "mutation($issueId:ID!,$issueTypeId:ID!){updateIssue(input:{id:$issueId,issueTypeId:$issueTypeId}){issue{number}}}",
            variables: {
                issueId: issue.node_id,
                issueTypeId: issueTypeIds[finding.issueType],
            },
        }),
    });
}

export async function publishFindings({
    audit,
    findings,
    token,
    repository,
    assignee,
    api = apiClient(token),
}) {
    const issueTypeIds = {
        Bug: process.env.BUG_ISSUE_TYPE_ID ?? DEFAULT_ISSUE_TYPE_IDS.Bug,
        Task: process.env.TASK_ISSUE_TYPE_ID ?? DEFAULT_ISSUE_TYPE_IDS.Task,
    };
    const trackedIssues = await listTrackedIssues(api, repository);
    const results = [];
    for (const finding of findings) {
        const markers = findingMarkers(finding);
        const result = (action, issue) => {
            results.push({ action, number: issue.number, url: issue.html_url, key: finding.key });
        };
        let issue = trackedIssues.find((candidate) => candidate.body?.includes(markers.key));
        if (!issue) {
            issue = await api(`/repos/${repository}/issues`, {
                method: "POST",
                body: JSON.stringify({
                    title: finding.title.slice(0, 256),
                    body: renderIssueBody(finding),
                    labels: [finding.label, AUDIT_TRACKING_LABEL, AUDIT_AREA_LABEL],
                    assignees: [assignee],
                }),
            });
            trackedIssues.push(issue);
            await ensureIssueMetadata(api, issue, finding, repository, assignee, issueTypeIds);
            result("created", issue);
            continue;
        }

        // 사람이 닫은 감사 이슈는 «이번 회차는 대응하지 않는다» 는 판정이다. 상황이 그대로인데
        // 되살리면 보류를 표현할 자리가 사라지고, 판정 근거를 담은 코멘트도 새 번호에는 따라오지
        // 않는다 (#1191). 그래서 fingerprint 가 그대로면 손대지 않고, 달라졌을 때만 다시 묻는다.
        if (issue.state === "closed") {
            if (await hasFingerprint(api, repository, issue, markers)) {
                result("suppressed", issue);
                continue;
            }
            await api(`/repos/${repository}/issues/${issue.number}`, {
                method: "PATCH",
                body: JSON.stringify({ state: "open" }),
            });
            issue.state = "open";
            await ensureIssueMetadata(api, issue, finding, repository, assignee, issueTypeIds);
            await api(`/repos/${repository}/issues/${issue.number}/comments`, {
                method: "POST",
                body: JSON.stringify({ body: updateCommentBody(finding, audit) }),
            });
            result("reopened", issue);
            continue;
        }

        await ensureIssueMetadata(api, issue, finding, repository, assignee, issueTypeIds);
        if (await hasFingerprint(api, repository, issue, markers)) {
            result("unchanged", issue);
            continue;
        }
        await api(`/repos/${repository}/issues/${issue.number}/comments`, {
            method: "POST",
            body: JSON.stringify({ body: updateCommentBody(finding, audit) }),
        });
        result("commented", issue);
    }
    return results;
}

async function main() {
    const [auditPath] = process.argv.slice(2);
    if (!auditPath) {
        throw new Error("audit JSON path가 필요합니다.");
    }
    const token = process.env.GITHUB_TOKEN;
    const repository = process.env.GITHUB_REPOSITORY;
    if (!token || !repository) {
        throw new Error("GITHUB_TOKEN과 GITHUB_REPOSITORY가 필요합니다.");
    }
    const audit = JSON.parse(await fs.readFile(auditPath, "utf8"));
    const findings = selectActionableFindings(audit);
    if (findings.length === 0) {
        console.log("이슈 등록 기준에 해당하는 의존성 결과가 없습니다.");
        return;
    }
    const results = await publishFindings({
        audit,
        findings,
        token,
        repository,
        assignee: process.env.DEPENDENCY_AUDIT_ASSIGNEE ?? "1hyok",
    });
    for (const result of results) {
        console.log(`${result.action}: #${result.number} ${result.url}`);
    }
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.stack : error);
        process.exitCode = 1;
    });
}
