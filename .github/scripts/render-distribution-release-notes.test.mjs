import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(
    new URL("./render-distribution-release-notes.sh", import.meta.url),
);

async function runRenderer(t, env) {
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), "slowclock-release-notes-"));
    t.after(() => fs.rm(directory, { recursive: true, force: true }));
    const outputPath = path.join(directory, "notes.txt");
    const bodyPath = path.join(directory, "pr-body.md");
    if (env.body !== undefined) {
        await fs.writeFile(bodyPath, env.body, "utf8");
    }
    const result = spawnSync("bash", [scriptPath, outputPath], {
        encoding: "utf8",
        env: {
            ...process.env,
            EVENT_NAME: env.eventName,
            ISSUE_NUMBERS: env.issueNumbers ?? "",
            RELEASE_NOTES: env.releaseNotes ?? "",
            RELEASE_PR_BODY_FILE: env.body === undefined ? "" : bodyPath,
            SOURCE_REF: "refs/heads/main",
            SOURCE_SHA: "1234567890abcdef",
        },
    });
    return { result, outputPath };
}

const TEMPLATE_BODY = [
    "## 📌𝘐𝘴𝘴𝘶𝘦𝘴",
    "- closed #34",
    "- #32",
    "",
    "## 📎𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯",
    "- Google 로그인 스코프에서 캘린더를 뺐다",
    "- ",
    "",
    "## 📷𝘚𝘤𝘳𝘦𝘦𝘯𝘴𝘩𝘰𝘵",
    "",
    "## 💬𝘛𝘰 𝘙𝘦𝘷𝘪𝘦𝘸𝘦𝘳𝘴",
    "- 리뷰어용 메모는 릴리스 노트에 들어가지 않는다",
].join("\n");

test("main 머지 PR 본문의 Issues·Work Description 섹션으로 릴리스 노트를 만든다", async (t) => {
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body: TEMPLATE_BODY });

    assert.equal(result.status, 0, result.stderr);
    const notes = await fs.readFile(outputPath, "utf8");
    assert.equal(
        notes,
        [
            "SlowClock 릴리스 후보 배포",
            "기준: main @ 1234567",
            "",
            "포함 이슈",
            "- #34",
            "- #32",
            "",
            "변경 내용",
            "- Google 로그인 스코프에서 캘린더를 뺐다",
            "",
        ].join("\n"),
    );
});

test("ASCII 헤더(## Issues / ## Work Description)도 같은 섹션으로 읽는다", async (t) => {
    const body = "## Issues\n- fixes #7\n\n## Work Description\n- 알람이 잠금 화면에서도 뜬다\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });

    assert.equal(result.status, 0, result.stderr);
    assert.match(await fs.readFile(outputPath, "utf8"), /- #7\n\n변경 내용\n- 알람이 잠금 화면에서도 뜬다/);
});

test("이슈 번호가 없으면 업로드 전에 실패한다", async (t) => {
    const body = "## 📌𝘐𝘴𝘴𝘶𝘦𝘴\n- closed #\n\n## 📎𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯\n- 무언가 바꿨다\n";
    const { result } = await runRenderer(t, { eventName: "push", body });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /이슈 번호가 하나 이상 필요/);
});

test("변경 내용이 비어 있으면 업로드 전에 실패한다", async (t) => {
    const body = "## 📌𝘐𝘴𝘴𝘶𝘦𝘴\n- closed #34\n\n## 📎𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯\n- \n-\n";
    const { result } = await runRenderer(t, { eventName: "push", body });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /변경 내용이 하나 이상 필요/);
});

test("머지 PR 본문을 못 찾으면 템플릿 안내와 함께 실패한다", async (t) => {
    const { result } = await runRenderer(t, { eventName: "push" });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /PULL_REQUEST_TEMPLATE/);
});

test("수동 실행(WIF canary)은 입력값을 ; 로 나눠 릴리스 노트로 쓴다", async (t) => {
    const { result, outputPath } = await runRenderer(t, {
        eventName: "workflow_dispatch",
        issueNumbers: "#35 #35",
        releaseNotes: "WIF 인증 업로드 확인;앱이 로그인 화면까지 뜬다",
    });

    assert.equal(result.status, 0, result.stderr);
    const notes = await fs.readFile(outputPath, "utf8");
    assert.match(notes, /^SlowClock 수동 배포 \(WIF canary\)\n/);
    assert.match(notes, /포함 이슈\n- #35\n\n변경 내용\n- WIF 인증 업로드 확인\n- 앱이 로그인 화면까지 뜬다\n$/);
});

test("이슈 번호로 시작하는 본문 줄이 섹션을 끊지 않는다", async (t) => {
    // 마크다운에서 # 뒤에 공백이 없으면 제목이 아니다. 구분하지 않으면 「#130 에서 …」 처럼
    // 이슈 번호로 문장을 시작한 PR 이 그 줄에서 섹션을 끊어 배포가 막힌다(#159 실측).
    const body =
        "## 📌 Issues\n\nCloses #157\n\n## 📎 Work Description\n\n" +
        "#130 에서 들어간 회귀입니다.\n- 회차 식별자를 되풀이하는 일정에만 채운다\n\n" +
        "## 📷 Screenshot\n\n없음\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /#130 에서 들어간 회귀입니다\./);
    assert.match(notes, /회차 식별자를 되풀이하는 일정에만 채운다/);
});

test("섹션은 다음 제목에서 끊긴다", async (t) => {
    // 위 완화가 섹션 경계까지 풀어 버리면 Screenshot 문단이 변경 내용으로 섞여 들어간다.
    const body =
        "## 📌 Issues\n\nCloses #7\n\n## 📎 Work Description\n\n" +
        "- 알람이 잠금 화면에서도 뜬다\n\n## 📷 Screenshot\n\n스크린샷 없음\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /알람이 잠금 화면에서도 뜬다/);
    assert.doesNotMatch(notes, /스크린샷 없음/);
});

test("굵은 글씨로 시작하는 문단의 별표를 잘라 내지 않는다", async (t) => {
    const body =
        "## 📌 Issues\n\nCloses #8\n\n## 📎 Work Description\n\n" +
        "**회차 식별자는 되풀이하는 일정에만 채웁니다.** 나머지는 그대로다.\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /\*\*회차 식별자는 되풀이하는 일정에만 채웁니다\.\*\*/);
});
