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

test("굵은 글씨로 시작하는 문단의 글자를 잘라 내지 않는다", async (t) => {
    // 목록 표시를 뗄 때 「**굵게**」 의 별표 하나를 표시로 보고 잘라 내던 자리다(#159).
    // 지금은 별표를 표기로 보고 함께 벗기지만, 잘려 나가면 안 되는 것은 글자다.
    const body =
        "## 📌 Issues\n\nCloses #8\n\n## 📎 Work Description\n\n" +
        "**회차 식별자는 되풀이하는 일정에만 채웁니다.** 나머지는 그대로다.\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /- 회차 식별자는 되풀이하는 일정에만 채웁니다\. 나머지는 그대로다\./);
});

test("코드 블록은 통째로 빠진다", async (t) => {
    // 테스터가 앱에서 보는 것은 짧은 목록 하나다. 백틱 줄과 명령어가 항목으로 들어가면
    // 무엇이 바뀌었는지 읽기 어려워진다(#182).
    const body = [
        "## 📌 Issues",
        "Closes #9",
        "## 📎 Work Description",
        "- 알람 소리가 다시 납니다",
        "```bash",
        "./gradlew assembleRelease",
        "```",
        "- 잠금 화면에서도 뜹니다",
        "~~~",
        "adb logcat",
        "~~~",
    ].join("\n");
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /- 알람 소리가 다시 납니다/);
    assert.match(notes, /- 잠금 화면에서도 뜹니다/);
    assert.doesNotMatch(notes, /gradlew/);
    assert.doesNotMatch(notes, /adb logcat/);
    assert.doesNotMatch(notes, /```/);
    assert.doesNotMatch(notes, /~~~/);
});

test("표와 수평선과 HTML 줄은 항목이 되지 않는다", async (t) => {
    const body = [
        "## 📌 Issues",
        "Closes #10",
        "## 📎 Work Description",
        "| 자리 | 전 | 후 |",
        "|---|---|---|",
        "| 공유 읽기 | 누구나 | 가족만 |",
        "---",
        "<details><summary>자세히</summary>",
        "- 가족만 내 일정을 봅니다",
        "</details>",
    ].join("\n");
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.equal(
        notes.split("변경 내용\n")[1],
        "- 가족만 내 일정을 봅니다\n",
    );
});

test("링크는 글자만 남고 인라인 코드 표기는 벗겨진다", async (t) => {
    const body = [
        "## 📌 Issues",
        "Closes #11",
        "## 📎 Work Description",
        "- [개인정보처리방침](https://1hyok.github.io/SlowClock/privacy.html) 을 고쳤습니다",
        "- `sharedCode` 가 빈 일정도 고쳐집니다",
        "- ![화면](https://example.com/shot.png) 알람 화면이 커집니다",
        "> 인용으로 적은 줄도 그대로 읽힙니다",
    ].join("\n");
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    const notes = await fs.readFile(outputPath, "utf8");

    assert.equal(result.status, 0, result.stderr);
    assert.match(notes, /- 개인정보처리방침 을 고쳤습니다/);
    assert.match(notes, /- sharedCode 가 빈 일정도 고쳐집니다/);
    assert.match(notes, /- 알람 화면이 커집니다/);
    assert.match(notes, /- 인용으로 적은 줄도 그대로 읽힙니다/);
    assert.doesNotMatch(notes, /https:/);
});

test("장식만 있는 줄만 남으면 배포를 막는다", async (t) => {
    // 장식을 걷어 낸 뒤 아무 문장도 남지 않으면 테스터는 무엇을 확인할지 모른다.
    // 걷어 내기가 검사를 무력화하면 안 된다(#182).
    const body = [
        "## 📌 Issues",
        "Closes #12",
        "## 📎 Work Description",
        "```",
        "./gradlew test",
        "```",
        "---",
    ].join("\n");
    const { result } = await runRenderer(t, { eventName: "push", body });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /Work Description/);
});


test("PR 검사도 배포와 같은 본문을 받아 같은 노트를 만든다", async (t) => {
    const pr = await runRenderer(t, { eventName: "pull_request", body: TEMPLATE_BODY });
    const push = await runRenderer(t, { eventName: "push", body: TEMPLATE_BODY });
    assert.equal(pr.result.status, 0, pr.result.stderr);
    assert.equal(
        await fs.readFile(pr.outputPath, "utf8"),
        await fs.readFile(push.outputPath, "utf8"),
    );
});

test("실제 PR 검증 스텝이 잘못된 본문을 거부하고 수정된 본문은 통과시킨다", async (t) => {
    const workflow = await fs.readFile(
        new URL("../workflows/repository-quality.yml", import.meta.url), "utf8",
    );
    const step = workflow.split("      - name: Validate distribution release notes\n")[1]
        ?.split("\n      - name:")[0];
    assert.ok(step, "PR 단계에서 배포 노트 검증을 실행해야 합니다");
    assert.match(step, /if: inputs\.pull_request_number > 0/);
    const eventName = step.match(/EVENT_NAME: ([^\n]+)/)?.[1];
    const command = step.split("        run: |\n")[1]
        .split("\n").map((line) => line.replace(/^ {10}/, "")).join("\n");
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), "slowclock-pr-notes-"));
    t.after(() => fs.rm(directory, { recursive: true, force: true }));
    const prJsonFile = path.join(directory, "pr.json");
    const sourceRoot = fileURLToPath(new URL("../../", import.meta.url));
    const cases = [
        ["Closes #182\n배포 오류를 고칩니다", false],
        ["## Issues\nCloses #182\n## Work Description\n- \n", false],
        ["## Issues\nCloses #182\n## Work Description\n```sh\necho test\n```", false],
        [null, false],
        [TEMPLATE_BODY, true],
        [TEMPLATE_BODY + "\n$(touch injected)\n", true],
    ];
    for (const [body, succeeds] of cases) {
        await fs.writeFile(prJsonFile, JSON.stringify({ body }));
        const result = spawnSync("bash", ["-c", command], {
            cwd: sourceRoot,
            encoding: "utf8",
            env: { ...process.env, EVENT_NAME: eventName, PR_JSON_FILE: prJsonFile, RUNNER_TEMP: directory },
        });
        assert.equal(result.status === 0, succeeds, result.stderr);
    }
    await assert.rejects(fs.access(path.join(sourceRoot, "injected")));
});


test("코드 안의 제목과 종류·길이가 다른 fence는 섹션을 끊지 않는다", async (t) => {
    for (const fence of ["````", "~~~~"]) {
        const other = fence.startsWith("`") ? "~~~" : "```";
        const body = [
            "## Issues", "Closes #182", "## Work Description",
            "- 앞의 설명", fence + "text", "# 코드 안 제목",
            other, "- 코드 내부", fence.slice(1), "## 가짜 섹션",
            fence + " text", "- 아직 코드", fence + fence[0],
            "- 뒤의 설명", "## Screenshot", "화면 설명",
        ].join("\n");
        const { result, outputPath } = await runRenderer(t, { eventName: "pull_request", body });
        assert.equal(result.status, 0, result.stderr);
        assert.equal((await fs.readFile(outputPath, "utf8")).split("변경 내용\n")[1],
            "- 앞의 설명\n- 뒤의 설명\n");
    }
});

test("코드와 주석 안의 가짜 섹션과 번호로는 검사를 통과하지 못한다", async (t) => {
    const hidden = "## Issues\nCloses #999\n## Work Description\n- 가짜 설명";
    for (const body of ["```\n" + hidden + "\n```", "<!--\n" + hidden + "\n-->",
        "## Issues\n```\nCloses #999\n```\n## Work Description\n- 설명",
        "## Known issues\nCloses #999\n## Work Description\n- 설명"]) {
        const { result } = await runRenderer(t, { eventName: "pull_request", body });
        assert.equal(result.status, 1, body);
    }
});

test("주석 안의 fence와 제목은 뒤의 설명을 숨기지 않는다", async (t) => {
    const body = "## Issues\nCloses #182\n## Work Description\n" +
        "<!--\n```\n## 가짜 제목\n-->\n- 설명 <!--주석--> 나머지\n" +
        "- *기울임* と _기울임_ と snake_case を표시\n";
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    assert.equal(result.status, 0, result.stderr);
    const notes = await fs.readFile(outputPath, "utf8");
    assert.match(notes, /- 설명  나머지/);
    assert.match(notes, /- 기울임 と 기울임 と snake_case を표시/);
    assert.doesNotMatch(notes, /주석|가짜 제목/);
});

test("긴 리뷰 섹션이 있어도 추출 파이프를 중간에 닫지 않는다", async (t) => {
    const body = "## Issues\nCloses #182\n## Work Description\n- 설명\n" +
        "## To Reviewers\n" + "긴 보충 설명\n".repeat(10000);
    const { result, outputPath } = await runRenderer(t, { eventName: "push", body });
    assert.equal(result.status, 0, result.stderr);
    assert.equal((await fs.readFile(outputPath, "utf8")).split("변경 내용\n")[1], "- 설명\n");
});
