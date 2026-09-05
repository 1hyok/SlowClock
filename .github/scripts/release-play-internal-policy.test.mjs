import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

// Play 로 나가는 경로는 이 워크플로 하나다 (#852). Firebase App Distribution 경로의 규약은
// release-distribution-policy.test.mjs 가 따로 본다 — 두 채널은 목적도 산출물도 다르므로
// 정책도 섞지 않는다.
const workflow = await readFile(
    new URL("../workflows/release-play-internal.yml", import.meta.url),
    "utf8",
);
const publisher = await readFile(new URL("./play-internal-track.mjs", import.meta.url), "utf8");

// 주석은 «왜 그렇게 했는가» 를 적는 자리라 금지 대상의 이름이 그대로 등장한다. 정책은 실제로
// 실행되는 줄에만 걸어야 한다.
function withoutComments(source) {
    return source
        .split("\n")
        .filter((line) => !/^\s*(#|\/\/|\*|\/\*)/.test(line))
        .join("\n");
}

function indexOf(needle) {
    const index = workflow.indexOf(needle);
    assert.notEqual(index, -1, `release-play-internal.yml 에서 찾지 못했습니다: ${needle}`);
    return index;
}

test("Play 배포는 수동 실행과 environment 승인 뒤에만 시작된다", () => {
    // push·pull_request·schedule 이 붙는 순간 승인 없는 자동 게시가 생긴다.
    assert.match(workflow, /^on:\n {2}workflow_dispatch:\n$/m);
    assert.doesNotMatch(workflow, /^ {2}push:$/m);
    assert.doesNotMatch(workflow, /^ {2}pull_request:$/m);
    assert.doesNotMatch(workflow, /^ {2}schedule:$/m);
    assert.match(workflow, /^ {4}environment: play-internal$/m);
});

test("Play 자격은 Firebase environment 와 분리된 이름으로만 참조된다", () => {
    // 같은 service account 를 두 채널이 공유하면 한쪽 권한 사고가 다른 채널로 번진다.
    assert.match(workflow, /secrets\.GCP_PLAY_SERVICE_ACCOUNT/);
    assert.doesNotMatch(workflow, /GCP_FIREBASE_SERVICE_ACCOUNT/);
    assert.doesNotMatch(workflow, /environment: release-distribution/);
});

test("설정이 아직 없으면 이름을 알려주고 빌드 전에 멈춘다", () => {
    // Play Console·service account 준비 전에 눌러도 40분짜리 빌드를 돌린 뒤 죽지 않아야 한다.
    const guard = indexOf("- name: Verify Play publishing configuration is set");
    const build = indexOf("./gradlew :app:bundleRelease");
    assert.ok(guard < build, "설정 검사는 빌드보다 먼저다");
    assert.match(workflow, /missing="\$missing GCP_WORKLOAD_IDENTITY_PROVIDER\(secret\)"/);
    assert.match(workflow, /missing="\$missing GCP_PLAY_SERVICE_ACCOUNT\(secret\)"/);
    assert.match(workflow, /missing="\$missing PLAY_PACKAGE_NAME\(variable\)"/);
    assert.match(workflow, /::error::Play 배포 설정이 아직 없다/);
});

test("upload key 는 main 이 아닌 ref 에서 만져지지 않는다", () => {
    const guard = indexOf("- name: Refuse to publish from anything but main");
    const releaseConfig = indexOf("- name: Set up release build configuration");
    assert.ok(guard < releaseConfig, "ref 검사는 keystore 를 풀기 전이다");
    assert.match(workflow, /if \[ "\$GITHUB_REF" != "refs\/heads\/main" \]; then/);
});

test("versionCode 는 빌드 전에 Play 현재값과 대조해 확정된다", () => {
    // 중복 code 를 AAB 를 만든 뒤에 발견하면 그 산출물은 통째로 버려진다.
    const probe = indexOf("play-internal-track.mjs latest");
    const resolve = indexOf("resolve-play-version-code.mjs");
    const build = indexOf("./gradlew :app:bundleRelease");
    const publish = indexOf("play-internal-track.mjs \\\n            publish");

    assert.ok(probe < resolve);
    assert.ok(resolve < build, "단조 증가 판정은 빌드보다 먼저다");
    assert.ok(build < publish);
    assert.match(
        workflow,
        /PLAY_LATEST_VERSION_CODE: \$\{\{ steps\.play_latest\.outputs\.latest_version_code \}\}/,
    );
    // 빌드가 그 값을 실제로 받아야 versionCode 1 고정이 풀린다.
    const buildStep = workflow.slice(indexOf("- name: Build the signed release AAB"), build);
    assert.match(
        buildStep,
        /SLOWCLOCK_VERSION_CODE: \$\{\{ steps\.version_code\.outputs\.version_code \}\}/,
    );
});

test("업로드되는 AAB 는 attest 와 digest 재확인을 통과한 그 파일뿐이다", () => {
    const build = indexOf("./gradlew :app:bundleRelease");
    const attest = indexOf("actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6");
    const verify = indexOf("gh attestation verify");
    const finalDigestCheck = indexOf('assert "$RELEASE_AAB_PATH" "$EXPECTED_SHA256"');
    const publish = indexOf("play-internal-track.mjs \\\n            publish");

    assert.ok(build < attest, "digest 는 signing 이 끝난 AAB 에서 떠야 한다");
    assert.ok(attest < verify);
    assert.ok(verify < finalDigestCheck);
    assert.ok(finalDigestCheck < publish, "업로드 직전 digest 재확인이 마지막 fail-closed 지점이다");
    assert.match(
        workflow,
        /--signer-workflow "\$GITHUB_REPOSITORY\/\.github\/workflows\/release-play-internal\.yml"/,
    );
});

test("Play 토큰은 빌드 단계로 내려오지 않고 업로드 직전에 다시 발급된다", () => {
    const buildStep = workflow.slice(
        indexOf("- name: Build the signed release AAB"),
        indexOf("- name: Verify the AAB signature and contents"),
    );
    assert.doesNotMatch(buildStep, /PLAY_ACCESS_TOKEN/);
    assert.doesNotMatch(buildStep, /GOOGLE_APPLICATION_CREDENTIALS/);

    // 조회용·업로드용 토큰 둘 다 파일로 떨구지 않는다 — 러너에 남는 자격을 만들지 않는다.
    assert.equal((workflow.match(/create_credentials_file: false/g) ?? []).length, 2);
    assert.equal((workflow.match(/export_environment_variables: false/g) ?? []).length, 2);
    assert.equal(
        (workflow.match(/access_token_scopes: https:\/\/www\.googleapis\.com\/auth\/androidpublisher/g) ?? [])
            .length,
        2,
    );

    const build = indexOf("./gradlew :app:bundleRelease");
    const uploadAuth = indexOf("- name: Authenticate to Google Cloud for the Play upload");
    assert.ok(build < uploadAuth, "업로드 토큰은 빌드가 끝난 뒤에 받는다");
});

test("internal 이외의 track 으로 나가는 경로가 없다", () => {
    // 자동 승격은 이 이슈의 비범위다. «지금은 안 쓴다» 가 아니라 코드가 존재하지 않아야 한다.
    for (const source of [withoutComments(workflow), withoutComments(publisher)]) {
        assert.doesNotMatch(source, /\b(production|alpha|beta)\b/i);
        assert.doesNotMatch(source, /userFraction|inAppUpdatePriority/);
    }
    assert.match(publisher, /^export const INTERNAL_TRACK = "internal";$/m);
});

test("AAB 와 R8 mapping 은 Actions artifact 로 게시되지 않고 러너에서도 지워진다", () => {
    assert.doesNotMatch(workflow, /actions\/upload-artifact/);
    assert.doesNotMatch(workflow, /actions\/download-artifact/);

    const cleanup = workflow.slice(indexOf("- name: Remove private release outputs"));
    assert.match(cleanup, /app\/build\/outputs\/bundle\/release\/app-release\.aab/);
    assert.match(cleanup, /app\/build\/outputs\/mapping\/release\/mapping\.txt/);
    assert.equal((workflow.match(/if: always\(\)/g) ?? []).length, 2);
    assert.match(workflow, /uses: \.\/\.github\/actions\/cleanup-release-config/);
});

test("같은 앱에 두 run 이 겹치지 않고 중도 취소되지도 않는다", () => {
    // 겹친 run 은 Play 에 미완료 edit 를 남기고, 중도 취소는 그 edit 를 정리할 기회까지 없앤다.
    assert.match(workflow, /^concurrency:\n {2}group: play-internal-track-upload$/m);
    assert.doesNotMatch(workflow, /cancel-in-progress:\s*true/);
});

test("배포 기록에 track·versionCode·source SHA·digest·run 이 남는다", () => {
    const summary = workflow.slice(indexOf("### Play internal track publish"));
    assert.match(summary, /- track: internal/);
    assert.match(summary, /- versionCode: \\`\$VERSION_CODE\\`/);
    assert.match(summary, /- source: \\`\$GITHUB_SHA\\`/);
    assert.match(summary, /sha256:\$EXPECTED_SHA256/);
    assert.match(summary, /- attestation: \$ATTESTATION_URL/);
    assert.match(summary, /actions\/runs\/\$GITHUB_RUN_ID/);
});

test("권한은 워크플로 기본값이 아니라 배포 job 에서만 열린다", () => {
    assert.match(workflow, /^permissions: \{\}$/m);

    const publishJob = workflow.slice(indexOf("  publish:"));
    assert.match(publishJob, /^ {6}attestations: write$/m);
    assert.match(publishJob, /^ {6}id-token: write$/m);
    assert.match(publishJob, /^ {6}contents: read$/m);
    assert.doesNotMatch(publishJob, /^ {6}contents: write$/m);
});
