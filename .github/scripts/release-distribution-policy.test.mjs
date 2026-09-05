import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

// 실제 테스터에게 나가는 배포 경로는 이 워크플로 하나다 (#1029). provenance 는 여기서 깨지면
// 배포된 APK 가 어느 commit·run 에서 나왔는지 사후에 증명할 수단이 없어지므로, 순서와 권한을
// 파일 자체로 고정한다 (#851). canary 쪽 같은 규약은 firebase-wif-canary-policy.test.mjs 가 본다.
const workflow = await readFile(
  new URL("../workflows/release-distribution.yml", import.meta.url),
  "utf8",
);

function indexOf(needle) {
  const index = workflow.indexOf(needle);
  assert.notEqual(index, -1, `release-distribution.yml 에서 찾지 못했습니다: ${needle}`);
  return index;
}

test("배포 APK 는 attest 와 검증을 통과한 뒤에만 업로드된다", () => {
  const build = indexOf("./gradlew assembleRelease");
  const attest = indexOf("actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6");
  const verify = indexOf("gh attestation verify");
  const finalDigestCheck = indexOf('assert "$RELEASE_APK_PATH" "$EXPECTED_SHA256"');
  const upload = indexOf("appDistributionUploadRelease");

  assert.ok(build < attest, "digest 는 signing 이 끝난 APK 에서 떠야 한다");
  assert.ok(attest < verify);
  assert.ok(verify < finalDigestCheck);
  assert.ok(finalDigestCheck < upload, "업로드 직전 digest 재확인이 마지막 fail-closed 지점이다");
});

test("provenance 권한은 워크플로 기본값이 아니라 배포 job 에서만 열린다", () => {
  assert.match(workflow, /^permissions: \{\}$/m);

  const distributeJob = workflow.slice(indexOf("  distribute:"));
  assert.match(distributeJob, /^ {6}attestations: write$/m);
  assert.match(distributeJob, /^ {6}id-token: write$/m);
});

test("attestation 은 이 저장소·이 워크플로·이 commit 으로만 검증된다", () => {
  assert.match(
    workflow,
    /--signer-workflow "\$GITHUB_REPOSITORY\/\.github\/workflows\/release-distribution\.yml"/,
  );
  assert.match(workflow, /--repo "\$GITHUB_REPOSITORY"/);
  assert.match(workflow, /--source-digest "\$GITHUB_SHA"/);
  assert.match(workflow, /--source-ref "\$GITHUB_REF"/);
  assert.match(workflow, /--deny-self-hosted-runners/);
});

test("업로드 대상은 attest 된 그 파일 경로뿐이다", () => {
  // --artifactPath 를 빼면 plugin 이 assemble 을 다시 엮어 검증 대상과 업로드 대상이 갈라진다.
  assert.match(workflow, /--artifactPath="\$RELEASE_APK_PATH"/);
});

test("signed APK 와 R8 mapping 은 Actions artifact 로 게시되지 않는다", () => {
  assert.doesNotMatch(workflow, /actions\/upload-artifact/);
  assert.doesNotMatch(workflow, /actions\/download-artifact/);
});

test("배포 summary 에 source SHA·digest·attestation·run URL 이 남는다", () => {
  const summary = workflow.slice(indexOf("### Release distribution provenance"));
  assert.match(summary, /- source: \\`\$GITHUB_SHA\\`/);
  assert.match(summary, /sha256:\$EXPECTED_SHA256/);
  assert.match(summary, /- attestation: \$ATTESTATION_URL/);
  assert.match(summary, /actions\/runs\/\$GITHUB_RUN_ID/);
});

test("Firebase credential 은 빌드 단계까지 내려오지 않는다", () => {
  const buildStep = workflow.slice(
    indexOf("- name: Build signed release APK"),
    indexOf("- name: Attest the exact signed APK"),
  );
  assert.doesNotMatch(buildStep, /GOOGLE_APPLICATION_CREDENTIALS/);
  assert.match(workflow, /GOOGLE_APPLICATION_CREDENTIALS/);
});
