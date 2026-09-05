import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

// CI artifact 보관 기간의 정본 (#1400).
// 리포트·디버깅용은 전부 표준값 하나를 쓰고, 그 밖의 값은 아래 예외 표에 이유와 함께 등재된
// 자리에서만 허용한다. 등재 없이 새 값이 들어오면 이 테스트가 그 자리에서 깨진다.
const STANDARD_RETENTION_DAYS = 7;

// 키는 `<워크플로 파일>#<보관 일수>`, 값은 그 값을 쓰는 이유.
// 같은 파일이 같은 예외 값을 여러 스텝에서 써도 한 항목으로 덮는다.
const ALLOWED_EXCEPTIONS = new Map([
    ["release-aab-preflight.yml#90", "릴리스 산출물 — 배포 후 추적 근거"],
    ["dependency-audit.yml#14", "주간 감사 증거 — 스케줄 주기(7일)보다 길어야 회차 간 비교가 끊기지 않는다"],
    ["screenshot-baseline-generate.yml#1", "job 간 전송용 — 골든 blob"],
    ["dependency-submission.yml#1", "job 간 전송용 — 의존성 그래프"],
]);

const workflowDirectory = new URL("../workflows/", import.meta.url);

const workflowFiles = (await readdir(workflowDirectory))
    .filter((name) => name.endsWith(".yml"))
    .sort();

const workflows = await Promise.all(
    workflowFiles.map(async (name) => ({
        name,
        lines: (await readFile(new URL(name, workflowDirectory), "utf8")).split("\n"),
    })),
);

// `retention-days:` 와 gradle action 의 `artifact-retention-days:` 를 한 축으로 본다.
const RETENTION_KEY = /^\s*(?:artifact-)?retention-days:\s*(\d+)\s*$/;

function precedingCommentBlock(lines, declarationIndex) {
    const block = [];
    for (let cursor = declarationIndex - 1; cursor >= 0; cursor -= 1) {
        const trimmed = (lines[cursor] ?? "").trim();
        if (!trimmed.startsWith("#")) {
            break;
        }
        block.unshift(trimmed);
    }
    return block;
}

function collectRetentionDeclarations() {
    const declarations = [];
    for (const { name, lines } of workflows) {
        lines.forEach((line, index) => {
            const matched = RETENTION_KEY.exec(line);
            if (matched === null) {
                return;
            }
            declarations.push({
                workflow: name,
                lineNumber: index + 1,
                days: Number(matched[1]),
                // 이유 주석은 여러 줄일 수 있다. 선언 바로 위의 연속 주석 블록을 통째로 본다.
                precedingComments: precedingCommentBlock(lines, index),
            });
        });
    }
    return declarations;
}

const retentionDeclarations = collectRetentionDeclarations();

test("every retention declaration is either the standard value or a registered exception", () => {
    assert.notEqual(retentionDeclarations.length, 0, "보관 기간 선언을 하나도 찾지 못했다 — 선언 문법이 바뀌었는지 확인할 것");

    const unregistered = retentionDeclarations
        .filter(({ days }) => days !== STANDARD_RETENTION_DAYS)
        .filter(({ workflow, days }) => !ALLOWED_EXCEPTIONS.has(`${workflow}#${days}`))
        .map(({ workflow, lineNumber, days }) => `${workflow}:${lineNumber} → ${days}일`);

    assert.deepEqual(
        unregistered,
        [],
        "표준(7일)도 등재된 예외도 아닌 보관 기간이다. 값을 표준으로 되돌리거나 ALLOWED_EXCEPTIONS 에 이유와 함께 등재할 것",
    );
});

test("non-standard retention values carry the reason as a comment at the declaration", () => {
    // 예외 표는 이 테스트 안에만 있어 워크플로만 읽는 사람에겐 안 보인다.
    // 「왜 이 자리만 90일인가」를 그 자리에서 읽을 수 있어야 한다.
    const missingComment = retentionDeclarations
        .filter(({ days }) => days !== STANDARD_RETENTION_DAYS)
        .filter(({ precedingComments }) => !precedingComments.some((comment) => comment.startsWith("# 예외:")))
        .map(({ workflow, lineNumber }) => `${workflow}:${lineNumber}`);

    assert.deepEqual(missingComment, [], "표준이 아닌 보관 기간 바로 위에 `# 예외:` 로 시작하는 이유 주석이 필요하다");
});

test("standard retention values are marked as the shared default, not a local choice", () => {
    const missingComment = retentionDeclarations
        .filter(({ days }) => days === STANDARD_RETENTION_DAYS)
        .filter(({ precedingComments }) => !precedingComments.some((comment) => comment.startsWith("# 표준 보관 기간")))
        .map(({ workflow, lineNumber }) => `${workflow}:${lineNumber}`);

    assert.deepEqual(missingComment, [], "표준 보관 기간 자리에는 `# 표준 보관 기간` 주석을 달아 개별 판단이 아님을 남긴다");
});

test("registered exceptions are all still in use", () => {
    // 예외를 쓰던 스텝이 사라져도 표가 남으면, 다음 사람이 그 값을 「허용된 관례」로 오해한다.
    const inUse = new Set(retentionDeclarations.map(({ workflow, days }) => `${workflow}#${days}`));
    const stale = [...ALLOWED_EXCEPTIONS.keys()].filter((key) => !inUse.has(key));

    assert.deepEqual(stale, [], "쓰이지 않는 예외가 표에 남아 있다 — 해당 항목을 지울 것");
});

test("every artifact upload declares a retention period", () => {
    // 미지정은 GitHub 기본값(90일)로 조용히 떨어진다. 표준을 정해 둔 의미가 없어진다.
    const uploadsWithoutRetention = [];
    for (const { name, lines } of workflows) {
        lines.forEach((line, index) => {
            if (!/^\s*uses:\s*actions\/upload-artifact@/.test(line)) {
                return;
            }
            // `with:` 블록은 uses 바로 아래에 온다. 다음 스텝(`- name:`)까지를 한 스텝으로 본다.
            const stepBody = [];
            for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
                if (/^\s*- name:/.test(lines[cursor])) {
                    break;
                }
                stepBody.push(lines[cursor]);
            }
            if (!stepBody.some((bodyLine) => RETENTION_KEY.test(bodyLine))) {
                uploadsWithoutRetention.push(`${name}:${index + 1}`);
            }
        });
    }

    assert.deepEqual(uploadsWithoutRetention, [], "upload-artifact 스텝에 retention-days 가 없다 — 미지정은 기본 90일이 된다");
});
