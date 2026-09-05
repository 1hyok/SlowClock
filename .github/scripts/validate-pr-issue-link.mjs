import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

// 같은 저장소 Issue 를 가리키는 모든 참조 — closing 키워드와 비closing(Refs·Part of·Related to) 모두.
const ISSUE_REFERENCE_RE = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|refs?|references?|part\s+of|related\s+to)\s*:?[ \t]+(?:(?:([\w.-]+)\/([\w.-]+))?#(\d+)|https:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/issues\/(\d+))/gi;
// GitHub 가 머지 시 Issue 를 자동으로 닫는 키워드만. 대표 Issue 는 이 형태로만 연결할 수 있다 (#1748).
// 키워드와 번호 사이에 콜론이나 다른 낱말이 끼면 GitHub 도 auto-close 하지 않으므로 여기서도 인정하지 않는다.
// auto-close-issue.yml 이 closing 이슈를 뽑는 패턴과 같은 모양이다 — 두 가드가 같은 Issue 를 봐야 한다.
const CLOSING_REFERENCE_RE = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+(?:(?:([\w.-]+)\/([\w.-]+))?#(\d+)|https:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/issues\/(\d+))/gi;

function visibleMarkdown(text) {
    return String(text ?? "")
        .replace(/<!--[\s\S]*?(?:-->|$)/g, "")
        .replace(/```[\s\S]*?(?:```|$)/g, "")
        .replace(/~~~[\s\S]*?(?:~~~|$)/g, "")
        .replace(/`[^`\n]*`/g, "");
}

function requiredString(value, name) {
    if (typeof value !== "string" || value.trim() === "") {
        throw new Error(`${name} 값이 없습니다.`);
    }
    return value.trim();
}

function extractIssueNumbers(text, repository, pattern) {
    const [expectedOwner, expectedName] = requiredString(repository, "repository").split("/");
    if (!expectedOwner || !expectedName) {
        throw new Error(`repository 형식이 owner/name 이 아닙니다: ${repository}`);
    }

    const issueNumbers = new Set();
    pattern.lastIndex = 0;
    for (const match of visibleMarkdown(text).matchAll(pattern)) {
        const owner = match[1] ?? match[4] ?? expectedOwner;
        const name = match[2] ?? match[5] ?? expectedName;
        const issueNumber = Number(match[3] ?? match[6]);
        if (
            owner.toLowerCase() === expectedOwner.toLowerCase()
            && name.toLowerCase() === expectedName.toLowerCase()
        ) {
            issueNumbers.add(issueNumber);
        }
    }
    return [...issueNumbers];
}

export function extractSameRepositoryIssueNumbers(text, repository) {
    return extractIssueNumbers(text, repository, ISSUE_REFERENCE_RE);
}

export function extractClosingIssueNumbers(text, repository) {
    return extractIssueNumbers(text, repository, CLOSING_REFERENCE_RE);
}

function issueAssigneeLogins(issue) {
    return (issue?.assignees ?? [])
        .map((assignee) => typeof assignee === "string" ? assignee : assignee?.login)
        .filter(Boolean);
}

// 봇은 Issue 담당자로 지정할 수 없으므로 담당자 대조에서 면제한다.
function isBotAuthor(user, login) {
    return user?.type === "Bot" || login.endsWith("[bot]");
}

const ISSUE_ASSIGNEE_EXEMPT_LABEL = "issue-assignee-exempt";

// 어사인 이관을 기다릴 수 없는 긴급 PR 은 라벨 하나로 담당자 대조만 면제한다.
// Issue 연결 요건 자체는 면제하지 않는다.
export function hasIssueAssigneeExemptLabel(pullRequest) {
    return (pullRequest?.labels ?? [])
        .map((label) => typeof label === "string" ? label : label?.name)
        .includes(ISSUE_ASSIGNEE_EXEMPT_LABEL);
}

export async function validatePullRequestIssueLink({ pullRequest, repository, loadIssue }) {
    if (typeof loadIssue !== "function") {
        throw new Error("loadIssue 함수가 필요합니다.");
    }
    const pullRequestNumber = pullRequest?.number;
    if (!Number.isInteger(pullRequestNumber)) {
        throw new Error("pull_request.number 값이 없습니다.");
    }
    const author = requiredString(pullRequest?.user?.login, "pull_request.user.login");

    // 대표 Issue 는 이 PR 이 끝내는 Issue 다 — 본문의 첫 closing 참조(closed/closes/fixes/resolves #N).
    // 이 저장소의 PR 제목에는 이슈 번호를 넣지 않는다(PR 번호와 혼동) — 제목 규칙은 두지 않는다.
    // Refs 만 있고 closing 참조가 없으면 Issue 의 일부만 하고 남는 몫이 본문 산문에만 남는 도피로가
    // 열리므로, 일부만 한다면 그 몫을 새 Issue 로 분리해 대표 Issue 로 삼는다. 봇 PR 은 사람이
    // 나중에 링크를 붙이는 구조라 Refs 를 허용한다.
    const references = extractSameRepositoryIssueNumbers(pullRequest.body, repository);
    if (references.length === 0) {
        throw new Error(
            `PR #${pullRequestNumber}에 같은 저장소의 Issue 참조가 없습니다. 관련 기존 Issue를 재사용하고 본문 Issues 섹션에 closed #N을 추가하세요.`,
        );
    }

    const botAuthor = isBotAuthor(pullRequest.user, author);
    const closingReferences = extractClosingIssueNumbers(pullRequest.body, repository);
    if (!botAuthor && closingReferences.length === 0) {
        throw new Error(
            `PR #${pullRequestNumber}의 대표 Issue를 본문에서 closed #N(Closes/Fixes/Resolves 도 됩니다)으로 연결하세요. Refs 로는 대표 Issue를 걸 수 없습니다 — 이 PR이 Issue의 일부만 하면 그 몫을 새 Issue로 분리해 대표 Issue로 삼고, 함께 건드리지만 닫지 않는 Issue는 Refs #M 으로 덧붙입니다.`,
        );
    }
    const titleIssueNumber = closingReferences[0] ?? references[0];

    const issues = [];
    const rejected = [];
    const issuesByNumber = new Map();
    for (const issueNumber of references) {
        let issue;
        try {
            issue = await loadIssue(issueNumber);
        } catch (error) {
            rejected.push(`#${issueNumber}: 조회 실패 (${error.message})`);
            continue;
        }
        if (issue?.pull_request) {
            rejected.push(`#${issueNumber}: Issue가 아니라 PR`);
            continue;
        }
        issuesByNumber.set(issueNumber, issue);
        issues.push(issueNumber);
    }

    if (issues.length === 0) {
        throw new Error(
            `PR #${pullRequestNumber}에 연결된 실제 Issue가 없습니다. ${rejected.join(", ")}`,
        );
    }
    if (!issues.includes(titleIssueNumber)) {
        const titleRejection = rejected.find((message) => message.startsWith(`#${titleIssueNumber}:`));
        throw new Error(
            `PR #${pullRequestNumber}의 대표 Issue #${titleIssueNumber}가 실제 Issue로 확인되지 않았습니다. ${titleRejection ?? "Issue 조회 결과를 확인하세요."}`,
        );
    }

    if (!botAuthor && !hasIssueAssigneeExemptLabel(pullRequest)) {
        const assignees = issueAssigneeLogins(issuesByNumber.get(titleIssueNumber));
        if (assignees.length === 0) {
            throw new Error(
                `PR #${pullRequestNumber}의 대표 Issue #${titleIssueNumber}에 담당자가 없습니다. Issue에 @${author}를 어사인한 뒤 다시 실행하세요.`,
            );
        }
        if (!assignees.some((login) => login.toLowerCase() === author.toLowerCase())) {
            throw new Error(
                `PR #${pullRequestNumber} 작성자 @${author}는 대표 Issue #${titleIssueNumber}의 담당자(${assignees.map((login) => `@${login}`).join(", ")})가 아닙니다. 본인이 담당하는 Issue로만 PR을 열 수 있습니다. 담당자 이관을 기다릴 수 없는 긴급 수선이면 \`${ISSUE_ASSIGNEE_EXEMPT_LABEL}\` 라벨을 붙인 뒤 재검증(본문 수정 또는 push)을 트리거하세요.`,
            );
        }
    }
    return { issues, rejected };
}

async function requestIssue(apiUrl, repository, token, issueNumber) {
    const response = await fetch(`${apiUrl}/repos/${repository}/issues/${issueNumber}`, {
        headers: {
            Accept: "application/vnd.github+json",
            Authorization: `Bearer ${token}`,
            "X-GitHub-Api-Version": "2022-11-28",
        },
    });
    if (!response.ok) {
        const detail = (await response.text()).slice(0, 500);
        throw new Error(`HTTP ${response.status}: ${detail}`);
    }
    return response.json();
}

async function main() {
    const eventPath = requiredString(process.argv[2] ?? process.env.GITHUB_EVENT_PATH, "pull request JSON path");
    const repository = requiredString(process.env.GITHUB_REPOSITORY, "GITHUB_REPOSITORY");
    const token = requiredString(process.env.GH_TOKEN ?? process.env.GITHUB_TOKEN, "GH_TOKEN");
    const apiUrl = requiredString(process.env.GITHUB_API_URL ?? "https://api.github.com", "GITHUB_API_URL");
    const event = JSON.parse(await readFile(eventPath, "utf8"));
    const pullRequest = event.pull_request ?? event;
    if (!Number.isInteger(pullRequest?.number)) throw new Error("pull request JSON이 아닙니다.");

    const result = await validatePullRequestIssueLink({
        pullRequest,
        repository,
        loadIssue: (issueNumber) => requestIssue(apiUrl, repository, token, issueNumber),
    });
    if (hasIssueAssigneeExemptLabel(pullRequest)) {
        console.log(`${ISSUE_ASSIGNEE_EXEMPT_LABEL} 라벨 — 담당자 대조 건너뜀`);
    }
    for (const warning of result.rejected) {
        console.log(`::warning::무효 Issue 참조: ${warning}`);
    }
    console.log(`PR #${pullRequest.number} linked issues: ${result.issues.map((number) => `#${number}`).join(", ")}`);
}

const isDirectExecution = process.argv[1]
    && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirectExecution) {
    try {
        await main();
    } catch (error) {
        console.error(`::error::${error.message}`);
        process.exitCode = 1;
    }
}
