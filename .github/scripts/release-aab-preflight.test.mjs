import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
    MEANINGFUL_INCREASE_BYTES,
    NO_MAPPING,
    buildReport,
    compareSize,
    renderMarkdown,
} from "./render-release-aab-report.mjs";

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptsDirectory, "../..");

function currentMetrics(overrides = {}) {
    return {
        sourceSha: "current-sha",
        aabSha256: "a".repeat(64),
        aabSizeBytes: 16_000_000,
        signerSha256: "AA:BB",
        mappingSha256: "b".repeat(64),
        minimumDownloadBytes: 10_000_000,
        maximumDownloadBytes: 12_000_000,
        installableApkBytes: 13_000_000,
        bundletoolVersion: "1.18.3",
        bundletoolSha256: "c".repeat(64),
        ...overrides,
    };
}

test("reports a first-run baseline gap without fabricating a comparison", () => {
    const report = buildReport(currentMetrics(), null, "2026-08-22T00:00:00.000Z");

    assert.equal(report.baseline, null);
    assert.equal(report.comparison, null);
    assert.equal(report.policy.meaningfulIncrease, false);
    assert.match(renderMarkdown(report), /Baseline: unavailable/);
});

test("marks either a five-percent or one-MiB increase as meaningful", () => {
    assert.equal(compareSize(105, 100).meaningfulIncrease, true);
    assert.equal(
        compareSize(10_000_000 + MEANINGFUL_INCREASE_BYTES, 10_000_000)
            .meaningfulIncrease,
        true,
    );
    assert.equal(compareSize(10_100_000, 10_000_000).meaningfulIncrease, false);
});

test("compares only public numeric report fields from the previous baseline", () => {
    const baseline = buildReport(
        currentMetrics({
            sourceSha: "baseline-sha",
            aabSizeBytes: 15_000_000,
            maximumDownloadBytes: 11_000_000,
            installableApkBytes: 12_000_000,
        }),
        null,
        "2026-08-21T00:00:00.000Z",
    );
    const report = buildReport(currentMetrics(), baseline, "2026-08-22T00:00:00.000Z");

    assert.equal(report.baseline.sourceSha, "baseline-sha");
    assert.equal(report.comparison.aab.deltaBytes, 1_000_000);
    assert.equal(report.comparison.minimumDownload.deltaBytes, 0);
    assert.equal(report.comparison.maximumDownload.deltaBytes, 1_000_000);
    assert.equal(report.comparison.installableApk.deltaBytes, 1_000_000);
});

test("release workflow is secretless, non-deploying, and uploads reports only", async () => {
    const workflow = await readFile(
        join(repositoryRoot, ".github/workflows/release-aab-preflight.yml"),
        "utf8",
    );
    const appBuild = await readFile(join(repositoryRoot, "app/build.gradle.kts"), "utf8");
    const verifier = await readFile(
        join(repositoryRoot, "scripts/verify-play-release-bundle.sh"),
        "utf8",
    );
    const uploadBlock = workflow.slice(
        workflow.indexOf("- name: Upload digest and size reports only"),
    );

    assert.match(workflow, /^\s*pull_request:\s*\n\s+branches: \[main\]/m);
    assert.match(workflow, /^\s*schedule:\s*\n\s+# .*\n\s+- cron: '37 18 \* \* 1,4'/m);
    assert.match(workflow, /^\s*workflow_dispatch:\s*$/m);
    assert.match(workflow, /github\.event\.pull_request\.head\.repo\.full_name == github\.repository/);
    assert.match(workflow, /uses: \.\/\.github\/actions\/setup-ci-config/);
    assert.match(workflow, /uses: \.\/\.github\/actions\/setup-ci-release-signing/);
    assert.match(workflow, /a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29/);
    assert.doesNotMatch(workflow, /\bsecrets\./);
    assert.doesNotMatch(
        workflow,
        /appDistributionUpload|firebaseAppDistribution|publishBundle|upload.*Play/i,
    );
    assert.doesNotMatch(workflow, /^\s*environment:/m);
    assert.doesNotMatch(uploadBlock, /\.aab|mapping\.txt|\.apk(?:s)?/);
    assert.match(workflow, /release-aab-preflight\.json/);
    assert.match(workflow, /release-aab-preflight\.md/);
    assert.match(verifier, /:app:bundleRelease/);
    for (const entry of [
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
        "base/resources.pb",
        "base/dex/classes.dex",
    ]) {
        assert.match(verifier, new RegExp(entry.replaceAll(".", "\\.")));
    }
    // release 는 R8 로 코드와 리소스를 줄인다. 끄면 배포 산출물이 갑자기 여덟 배로 불고
    // 난독화 전제로 쓴 preflight 의 이름 확인이 무의미해진다(#113).
    assert.match(appBuild, /isMinifyEnabled = true/);
    assert.match(appBuild, /isShrinkResources = true/);
    assert.doesNotMatch(appBuild, /isMinifyEnabled = false/);

    // R8 이 이름을 지우면 Firestore 매핑이 조용히 빈다. 배포 전 검사가 산출물에서 직접 본다.
    const preflight = await readFile(
        join(repositoryRoot, ".github/scripts/run-release-aab-preflight.sh"),
        "utf8",
    );
    for (const symbol of [
        "Lcom/example/slowclock/data/model/Schedule;",
        "Lcom/example/slowclock/data/model/User;",
        "getTitle",
        "getStartTime",
    ]) {
        assert.ok(
            preflight.includes(symbol),
            `배포 전 검사가 ${symbol} 를 확인하지 않는다`,
        );
    }
});

test("reports an absent R8 mapping honestly instead of fabricating a digest", () => {
    const report = buildReport(currentMetrics({ mappingSha256: NO_MAPPING }), null, "2026-09-05T00:00:00.000Z");

    assert.equal(report.validation.mappingPresent, false);
    assert.equal(report.validation.r8Minification, false);
    assert.equal(report.artifacts.mapping.present, false);
    assert.equal(report.artifacts.mapping.sha256, null);
    assert.match(renderMarkdown(report), /R8 mapping: absent \(isMinifyEnabled=false\)/);

    const withMapping = buildReport(currentMetrics(), null, "2026-09-05T00:00:00.000Z");
    assert.equal(withMapping.artifacts.mapping.present, true);
    assert.match(renderMarkdown(withMapping), /R8 mapping: present/);
});

