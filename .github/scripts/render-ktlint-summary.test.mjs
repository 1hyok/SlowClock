import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { collectReportFiles, collectViolations, parseCheckstyleXml, renderSummary } from "./render-ktlint-summary.mjs";

const REPORT = `<?xml version="1.0" encoding="utf-8"?>
<checkstyle version="8.0">
    <file name="/workspace/core/common/src/main/java/Probe.kt">
        <error line="4" column="15" severity="error" message="Missing newline after &apos;{&apos;" source="standard:statement-wrapping" />
        <error line="4" column="20" severity="error" message="Missing spacing around &quot;=&quot;" source="standard:op-spacing" />
    </file>
</checkstyle>`;

const EMPTY_REPORT = `<?xml version="1.0" encoding="utf-8"?>
<checkstyle version="8.0">
</checkstyle>`;

test("parses file, position, rule, and decoded message from a checkstyle report", () => {
    const violations = parseCheckstyleXml(REPORT);

    assert.equal(violations.length, 2);
    assert.deepEqual(violations[0], {
        file: "/workspace/core/common/src/main/java/Probe.kt",
        line: 4,
        column: 15,
        severity: "error",
        message: "Missing newline after '{'",
        rule: "standard:statement-wrapping",
    });
    assert.equal(violations[1].message, 'Missing spacing around "="');
});

test("reports no violations for an empty checkstyle report", () => {
    assert.deepEqual(parseCheckstyleXml(EMPTY_REPORT), []);
});

test("collects reports from every module build directory but not from the sources", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "ktlint-summary-"));
    try {
        const moduleReport = path.join(
            root,
            "feature/main/build/reports/ktlint/ktlintMainSourceSetCheck",
        );
        await fs.mkdir(moduleReport, { recursive: true });
        await fs.writeFile(path.join(moduleReport, "ktlintMainSourceSetCheck.xml"), REPORT);
        await fs.writeFile(path.join(moduleReport, "ktlintMainSourceSetCheck.txt"), "plain output");

        const unrelated = path.join(root, "core/ui/build/reports/lint-results-debug.xml");
        await fs.mkdir(path.dirname(unrelated), { recursive: true });
        await fs.writeFile(unrelated, "<issues />");

        const sourceDirectory = path.join(root, "core/ui/src/main/java");
        await fs.mkdir(sourceDirectory, { recursive: true });
        await fs.writeFile(path.join(sourceDirectory, "Sample.xml"), REPORT);

        const reports = await collectReportFiles(root);
        assert.equal(reports.length, 1);
        assert.match(reports[0], /ktlintMainSourceSetCheck\.xml$/);

        const violations = await collectViolations(root);
        assert.equal(violations.length, 2);
    } finally {
        await fs.rm(root, { recursive: true, force: true });
    }
});

test("renders a rule tally and repository relative positions when violations exist", () => {
    const summary = renderSummary(parseCheckstyleXml(REPORT), { root: "/workspace", reportCount: 1 });

    assert.match(summary, /❌ \*\*위반 2건\*\* — 파일 1개, 규칙 2종/);
    assert.match(summary, /\| `standard:op-spacing` \| 1 \|/);
    assert.match(summary, /- `core\/common\/src\/main\/java\/Probe\.kt:4:15`/);
    assert.doesNotMatch(summary, /\/workspace\/core/);
    assert.match(summary, /ktlintFormat/);
});

test("renders a pass line when reports exist without violations", () => {
    const summary = renderSummary([], { root: "/workspace", reportCount: 3 });

    assert.match(summary, /✅ 위반 없음 \(리포트 3건\)/);
});

test("distinguishes a clean run from a run that produced no report at all", () => {
    const summary = renderSummary([], { root: "/workspace", reportCount: 0 });

    assert.match(summary, /리포트가 없다/);
});

test("truncates the listing but keeps the total and the tally exact", () => {
    const many = Array.from({ length: 150 }, (unused, index) => ({
        file: "/workspace/core/common/src/main/java/Probe.kt",
        line: index + 1,
        column: 1,
        severity: "error",
        message: "Missing newline",
        rule: "standard:statement-wrapping",
    }));

    const summary = renderSummary(many, { root: "/workspace", reportCount: 1 });

    assert.match(summary, /❌ \*\*위반 150건\*\*/);
    assert.match(summary, /\| `standard:statement-wrapping` \| 150 \|/);
    assert.match(summary, /외 50건/);
    assert.equal(summary.split("\n").filter((line) => line.startsWith("- `")).length, 100);
});
