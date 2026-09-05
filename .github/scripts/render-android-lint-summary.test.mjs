import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
    collectReportFiles,
    collectViolations,
    moduleOf,
    parseLintXml,
    renderSummary,
} from "./render-android-lint-summary.mjs";

// lint 9.2.1 이 실제로 남긴 리포트를 그대로 옮겼다. 여는 태그가 여러 줄이고,
// message 의 닫는 꺾쇠는 이스케이프되지 않는다 (`&lt;꺾쇠> 포함`).
const REPORT = `<?xml version="1.0" encoding="UTF-8"?>
<issues format="6" by="lint 9.2.1">

    <issue
        id="SimpleDateFormat"
        severity="Warning"
        message="To get local formatting use \`getDateInstance()\`, or use \`new SimpleDateFormat(String template, Locale locale)\`."
        category="Correctness"
        priority="6"
        summary="Implied locale in date format"
        explanation="Almost all callers should use \`getDateInstance()\`.&#xA;&#xA;Therefore, you should either use the form of the constructor where you pass in an explicit locale."
        errorLine1="    fun format(date: Date): String = SimpleDateFormat(&quot;yyyy-MM-dd&quot;).format(date)"
        errorLine2="                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~">
        <location
            file="/workspace/core/common/src/main/kotlin/Probe.kt"
            line="7"
            column="38"/>
    </issue>

    <issue
        id="HardcodedText"
        severity="Warning"
        message="Hardcoded string &quot;하드코딩 &lt;꺾쇠> 포함&quot;, should use \`@string\` resource"
        category="Internationalization"
        priority="5"
        summary="Hardcoded text"
        explanation="Hardcoding text attributes directly in layout files is bad."
        errorLine1="        android:text=&quot;하드코딩 &amp;lt;꺾쇠&amp;gt; 포함&quot; />"
        errorLine2="        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~">
        <location
            file="/workspace/core/common/src/main/res/layout/probe.xml"
            line="9"
            column="9"/>
    </issue>

</issues>`;

const EMPTY_REPORT = `<?xml version="1.0" encoding="UTF-8"?>
<issues format="6" by="lint 9.2.1">

</issues>`;

async function writeReport(root, modulePath, xml) {
    const report = path.join(root, modulePath, "build", "reports", "lint-results-debug.xml");
    await fs.mkdir(path.dirname(report), { recursive: true });
    await fs.writeFile(report, xml);
    return report;
}

test("parses a multi-line issue tag, decodes entities, and keeps a raw closing bracket", () => {
    const violations = parseLintXml(REPORT, { module: ":core:common" });

    assert.equal(violations.length, 2);
    assert.deepEqual(violations[0], {
        module: ":core:common",
        id: "SimpleDateFormat",
        severity: "Warning",
        message:
            "To get local formatting use `getDateInstance()`, or use `new SimpleDateFormat(String template, Locale locale)`.",
        file: "/workspace/core/common/src/main/kotlin/Probe.kt",
        line: 7,
        column: 38,
    });
    // `[^>]*` 로 여는 태그를 끊으면 여기서 잘려 두 번째 위반을 통째로 잃는다.
    assert.equal(violations[1].id, "HardcodedText");
    assert.equal(violations[1].message, 'Hardcoded string "하드코딩 <꺾쇠> 포함", should use `@string` resource');
    assert.equal(violations[1].line, 9);
});

test("reports no violations for a clean report", () => {
    assert.deepEqual(parseLintXml(EMPTY_REPORT), []);
});

test("reads what it can from a report lint truncated mid-issue", () => {
    const truncated = REPORT.slice(0, REPORT.indexOf('id="HardcodedText"'));

    const violations = parseLintXml(truncated);

    assert.equal(violations.length, 1);
    assert.equal(violations[0].id, "SimpleDateFormat");
});

test("keeps an issue that carries no location", () => {
    const withoutLocation = `<issues format="6" by="lint 9.2.1">
    <issue id="GradleDependency" severity="Warning" message="A newer version is available" />
</issues>`;

    const violations = parseLintXml(withoutLocation, { module: ":app" });

    assert.equal(violations.length, 1);
    assert.equal(violations[0].file, null);
    assert.equal(violations[0].line, 0);
});

test("drops violations that live in the dependency cache", () => {
    const cached = `<issues format="6" by="lint 9.2.1">
    <issue id="OldTargetApi" severity="Warning" message="External">
        <location file="${path.join(os.homedir(), ".gradle", "caches", "modules-2", "Cached.kt")}" line="3" column="1"/>
    </issue>
</issues>`;

    assert.deepEqual(parseLintXml(cached), []);
});

test("derives the Gradle module path from the report location", () => {
    assert.equal(moduleOf("/workspace", "/workspace/core/common/build/reports/lint-results-debug.xml"), ":core:common");
    assert.equal(
        moduleOf("/workspace", "/workspace/feature/main/build/reports/lint-results-debug.xml"),
        ":feature:main",
    );
});

test("collects one report per module across core and feature trees", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-lint-summary-"));
    try {
        await writeReport(root, "core/common", REPORT);
        await writeReport(root, path.join("feature", "main"), REPORT);
        await writeReport(root, "app", EMPTY_REPORT);
        // lint 의 다른 리포트와 소스 트리의 XML 은 입력이 아니다.
        const noise = path.join(root, "core/ui/build/reports/lint-results-release.xml");
        await fs.mkdir(path.dirname(noise), { recursive: true });
        await fs.writeFile(noise, REPORT);
        const source = path.join(root, "core/ui/src/main/res/layout");
        await fs.mkdir(source, { recursive: true });
        await fs.writeFile(path.join(source, "view.xml"), REPORT);

        const reports = await collectReportFiles(root);
        assert.equal(reports.length, 3);

        const violations = await collectViolations(root);
        assert.equal(violations.length, 4);
        // #279 회귀 가드 — :app 밖의 모듈 위반이 귀속과 함께 살아 있어야 한다.
        assert.deepEqual(
            [...new Set(violations.map((violation) => violation.module))].sort(),
            [":core:common", ":feature:main"],
        );
    } finally {
        await fs.rm(root, { recursive: true, force: true });
    }
});

test("renders a rule tally with severity and repository relative positions", () => {
    const summary = renderSummary(parseLintXml(REPORT, { module: ":core:common" }), {
        root: "/workspace",
        reportCount: 1,
    });

    assert.match(summary, /❌ \*\*위반 2건\*\* — 모듈 1개, 규칙 2종/);
    assert.match(summary, /\| `HardcodedText` \| Warning \| 1 \|/);
    assert.match(summary, /- `:core:common` `core\/common\/src\/main\/kotlin\/Probe\.kt:7:38`/);
    assert.doesNotMatch(summary, /\/workspace\/core/);
});

test("renders a pass line when every module reported without violations", () => {
    assert.match(renderSummary([], { root: "/workspace", reportCount: 26 }), /✅ 위반 없음 \(모듈 26개\)/);
});

test("distinguishes a clean run from a run that produced no report at all", () => {
    assert.match(renderSummary([], { root: "/workspace", reportCount: 0 }), /리포트가 없다/);
});

test("collapses a multi-line message onto one list line", () => {
    const summary = renderSummary(
        [
            {
                module: ":app",
                id: "Recycle",
                severity: "Error",
                message: "첫 줄\n\n둘째 줄",
                file: "/workspace/app/src/main/kotlin/Probe.kt",
                line: 3,
                column: 0,
            },
        ],
        { root: "/workspace", reportCount: 1 },
    );

    assert.match(summary, /- `:app` `app\/src\/main\/kotlin\/Probe\.kt:3` — 첫 줄 둘째 줄 \(`Recycle`\)/);
});

test("truncates the listing but keeps the total and the tally exact", () => {
    const many = Array.from({ length: 150 }, (unused, index) => ({
        module: ":core:common",
        id: "HardcodedText",
        severity: "Warning",
        message: "Hardcoded string",
        file: "/workspace/core/common/src/main/res/layout/probe.xml",
        line: index + 1,
        column: 9,
    }));

    const summary = renderSummary(many, { root: "/workspace", reportCount: 1 });

    assert.match(summary, /❌ \*\*위반 150건\*\*/);
    assert.match(summary, /\| `HardcodedText` \| Warning \| 150 \|/);
    assert.match(summary, /외 50건/);
    assert.equal(summary.split("\n").filter((line) => line.startsWith("- `")).length, 100);
});
