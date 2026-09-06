import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const canaryWorkflow = await readFile(
  new URL("../workflows/firebase-wif-canary.yml", import.meta.url),
  "utf8",
);
const productionWorkflow = await readFile(
  new URL("../workflows/release-distribution.yml", import.meta.url),
  "utf8",
);
const releaseConfigAction = await readFile(
  new URL("../actions/setup-release-config/action.yml", import.meta.url),
  "utf8",
);

test("no workflow carries a long-lived service account JSON", () => {
  // canary 가 성공한 뒤 프로덕션도 WIF 로 전환했다 (#850). 이제 파이프라인 어디에도 장기 키가
  // 없어야 한다 — 되살아나면 이 단언이 그 자리에서 막는다. 롤백이 필요하면 이 테스트를 함께
  // 되돌리는 것이 «의도된 롤백» 의 표시다.
  assert.doesNotMatch(canaryWorkflow, /FIREBASE_SERVICE_ACCOUNT_JSON/);
  assert.doesNotMatch(productionWorkflow, /FIREBASE_SERVICE_ACCOUNT_JSON/);
  // 워크플로가 안 넘겨도 composite action 이 입력을 들고 있으면 경로가 살아 있는 것이다.
  // 2026-08-30 실배포 성공 후 그 롤백 경로까지 걷었다 — 입력·env·ADC 파일 생성·output 전부.
  assert.doesNotMatch(releaseConfigAction, /FIREBASE_SERVICE_ACCOUNT_JSON/);
  assert.doesNotMatch(releaseConfigAction, /firebase-service-account-json/);
  assert.doesNotMatch(releaseConfigAction, /firebase-credentials-path/);
  // 자격 파일 경로를 워크플로가 손으로 지정하면 auth 액션이 export 한 단기 credential 대신
  // 낡은 경로를 물 수 있다. 경로는 액션이 정하게 둔다.
  assert.doesNotMatch(productionWorkflow, /GOOGLE_APPLICATION_CREDENTIALS:/);
});

test("production distribution authenticates with WIF just before upload", () => {
  // 프로덕션도 canary 와 같은 순서를 지켜야 한다 — 빌드·attestation 이 끝난 뒤에야 토큰을
  // 받고, 그 토큰으로 attest 된 바로 그 파일만 올린다.
  const buildIndex = productionWorkflow.indexOf("./gradlew assembleRelease");
  const attestationIndex = productionWorkflow.indexOf(
    "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6",
  );
  const authIndex = productionWorkflow.indexOf(
    "google-github-actions/auth@7c6bc770dae815cd3e89ee6cdf493a5fab2cc093",
  );
  const finalDigestCheckIndex = productionWorkflow.indexOf(
    'assert "$RELEASE_APK_PATH" "$EXPECTED_SHA256"',
  );
  const uploadIndex = productionWorkflow.indexOf("appDistributionUploadRelease");

  assert.notEqual(buildIndex, -1);
  assert.notEqual(attestationIndex, -1);
  assert.notEqual(authIndex, -1);
  assert.notEqual(finalDigestCheckIndex, -1);
  assert.notEqual(uploadIndex, -1);
  assert.ok(buildIndex < attestationIndex);
  assert.ok(attestationIndex < authIndex);
  assert.ok(authIndex < finalDigestCheckIndex);
  assert.ok(finalDigestCheckIndex < uploadIndex);
  assert.match(productionWorkflow, /id-token: write/);
  assert.match(productionWorkflow, /environment: release-distribution/);
});

test("both workflows point at the same WIF secrets", () => {
  // 두 워크플로가 서로 다른 secret 이름을 쓰면 canary 가 검증한 경로와 프로덕션이 실제로 쓰는
  // 경로가 갈린다. canary 의 «먼저 실측한다» 는 보장이 사라진다.
  for (const workflow of [canaryWorkflow, productionWorkflow]) {
    assert.match(workflow, /secrets\.GCP_WORKLOAD_IDENTITY_PROVIDER/);
    assert.match(workflow, /secrets\.GCP_FIREBASE_SERVICE_ACCOUNT/);
  }
});

test("WIF canary is manual, protected, and branch-restricted", () => {
  assert.match(canaryWorkflow, /workflow_dispatch:/);
  assert.match(canaryWorkflow, /environment: release-distribution/);
  assert.match(canaryWorkflow, /refs\/heads\/main/);
  assert.match(canaryWorkflow, /CONFIRM_UPLOAD/);
});

test("WIF canary overrides the normal tester recipients and identifies its build", async () => {
  assert.match(canaryWorkflow, /SLOWCLOCK_DISTRIBUTION_UPLOAD_ONLY: 'true'/);
  assert.doesNotMatch(canaryWorkflow, /--(?:groups|testers)/);
  const appBuild = await readFile(new URL("../../app/build.gradle.kts", import.meta.url), "utf8");
  assert.match(appBuild, /groups = if \(System.getenv\("SLOWCLOCK_DISTRIBUTION_UPLOAD_ONLY"\) == "true"\) "" else "slowclock"/);
  assert.doesNotMatch(appBuild, /(?:testers|testersFile|groupsFile)\s*=/);
  assert.match(canaryWorkflow, /SLOWCLOCK_VERSION_CODE: \$\{\{ github\.run_number \}\}/);
  assert.match(canaryWorkflow, /SLOWCLOCK_VERSION_NAME_SUFFIX="canary\./);
});

test("WIF credentials are issued only after the release APK build", () => {
  const buildIndex = canaryWorkflow.indexOf("./gradlew assembleRelease");
  const attestationIndex = canaryWorkflow.indexOf(
    "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6",
  );
  const authIndex = canaryWorkflow.indexOf(
    "google-github-actions/auth@7c6bc770dae815cd3e89ee6cdf493a5fab2cc093",
  );
  const finalDigestCheckIndex = canaryWorkflow.indexOf(
    'assert "$RELEASE_APK_PATH" "$EXPECTED_SHA256"',
  );
  const uploadIndex = canaryWorkflow.indexOf("appDistributionUploadRelease");

  assert.notEqual(buildIndex, -1);
  assert.notEqual(attestationIndex, -1);
  assert.notEqual(authIndex, -1);
  assert.notEqual(finalDigestCheckIndex, -1);
  assert.notEqual(uploadIndex, -1);
  assert.ok(buildIndex < attestationIndex);
  assert.ok(attestationIndex < authIndex);
  assert.ok(authIndex < finalDigestCheckIndex);
  assert.ok(finalDigestCheckIndex < uploadIndex);
  assert.match(canaryWorkflow, /id-token: write/);
  assert.match(canaryWorkflow, /attestations: write/);
});

test("WIF canary never publishes the signed APK as an Actions artifact", () => {
  assert.doesNotMatch(canaryWorkflow, /actions\/upload-artifact/);
  assert.doesNotMatch(canaryWorkflow, /actions\/download-artifact/);
});
