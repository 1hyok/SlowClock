import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { access, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { MAX_RELEASE_NOTES_LENGTH, renderInternalReleaseNotes } from "./play-internal-track.mjs";

const cli = fileURLToPath(new URL("./play-internal-track.mjs", import.meta.url));

async function notesFixture(t, body, { missingEnvironment = false } = {}) {
    const directory = await mkdtemp(join(tmpdir(), "slowclock-play-notes-test-"));
    t.after(() => rm(directory, { recursive: true, force: true }));
    const bodyPath = join(directory, "PR body $(touch should-not-exist).md");
    const outputPath = join(directory, "notes.txt");
    if (body !== null) await writeFile(bodyPath, body);
    const env = {
        ...process.env,
        SOURCE_REF: "refs/heads/main", SOURCE_SHA: "0123456789abcdef", SLOWCLOCK_VERSION_CODE: "101",
        RELEASE_PR_BODY_FILE: bodyPath,
        // CLI 는 수동 배포 입력으로 PR 템플릿 검증을 우회할 수 없다.
        EVENT_NAME: "workflow_dispatch", ISSUE_NUMBERS: "#999", RELEASE_NOTES: "unrelated input",
    };
    if (missingEnvironment) delete env.RELEASE_PR_BODY_FILE;
    const result = spawnSync(process.execPath, [cli, "notes", outputPath], { cwd: directory, env, encoding: "utf8" });
    if (result.status === 0) {
        result.notes = await readFile(outputPath, "utf8");
    } else {
        await assert.rejects(access(outputPath), { code: "ENOENT" });
    }
    await assert.rejects(access(join(directory, "should-not-exist")), { code: "ENOENT" });
    return result;
}

test("Play notes consume validated Issues and Work Description from the distribution renderer", async (t) => {
    const result = await notesFixture(t, [
        "# Outside #888", "## 📌 Issues", "- closed #190", "- #190", "<!-- #777 -->",
        "````markdown", "## 📌 Issues", "- #666", "```", "````",
        "## 📎 Work Description", "- **일정** 저장 수정 (#555 참고)", "## Test", "- #444",
    ].join("\n"));
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.notes, /포함 이슈: #190\n/);
    assert.match(result.notes, /변경 내용\n- 일정 저장 수정 \(#555 참고\)/);
    assert.doesNotMatch(result.notes, /#888|#777|#666|#444|#999|\*\*/);
});

for (const [label, body, options, error] of [
    ["unset source", "valid", { missingEnvironment: true }, /RELEASE_PR_BODY_FILE/],
    ["missing source file", null, {}, /PR 본문을 찾지 못했습니다/],
    ["empty source file", "", {}, /PR 본문을 찾지 못했습니다/],
    ["issue only outside Issues", "#190\n## Issues\n- none\n## Work Description\n- fixed", {}, /Issues 섹션/],
    ["fenced template only", "```\n## Issues\n- #190\n## Work Description\n- fixed\n```", {}, /Issues 섹션/],
    ["missing Work Description", "## Issues\n- #190\n## Testing\n- done", {}, /Work Description 섹션/],
    ["empty Work Description", "## Issues\n- #190\n## Work Description\n<!-- no change -->", {}, /Work Description 섹션/],
]) {
    test(`Play notes reject ${label} before creating output`, async (t) => {
        const result = await notesFixture(t, body, options);
        assert.notEqual(result.status, 0);
        assert.match(result.stderr, error);
    });
}

test("Play note truncation counts Unicode code points without splitting an emoji", () => {
    const notes = renderInternalReleaseNotes({
        sourceRef: "main", sourceSha: "0123456", versionCode: 101, issues: ["#190"],
        changes: ["- " + "⏰🔔".repeat(300)],
    });
    assert.equal(Array.from(notes).length, MAX_RELEASE_NOTES_LENGTH);
    assert.ok(notes.endsWith("…"));
    assert.equal(Buffer.from(notes, "utf8").toString("utf8"), notes);
});
