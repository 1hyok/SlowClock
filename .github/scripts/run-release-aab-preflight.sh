#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
bundle_path="${repo_root}/app/build/outputs/bundle/release/app-release.aab"
mapping_path="${repo_root}/app/build/outputs/mapping/release/mapping.txt"

: "${BUNDLETOOL_JAR:?BUNDLETOOL_JAR is required}"
: "${BUNDLETOOL_VERSION:?BUNDLETOOL_VERSION is required}"
: "${BUNDLETOOL_SHA256:?BUNDLETOOL_SHA256 is required}"
: "${RELEASE_AAB_REPORT_DIR:?RELEASE_AAB_REPORT_DIR is required}"
: "${SLOWCLOCK_CI_RELEASE_KEYSTORE:?SLOWCLOCK_CI_RELEASE_KEYSTORE is required}"
: "${SLOWCLOCK_CI_RELEASE_STORE_PASSWORD_FILE:?SLOWCLOCK_CI_RELEASE_STORE_PASSWORD_FILE is required}"
: "${SLOWCLOCK_CI_RELEASE_KEY_PASSWORD_FILE:?SLOWCLOCK_CI_RELEASE_KEY_PASSWORD_FILE is required}"
: "${SLOWCLOCK_CI_RELEASE_KEY_ALIAS:?SLOWCLOCK_CI_RELEASE_KEY_ALIAS is required}"

[[ "${SLOWCLOCK_CI_CONFIG_MODE:-}" == "stub" ]] || {
    echo "Release preflight requires the secretless CI fixture (setup-ci-config)." >&2
    exit 1
}
[[ "${SLOWCLOCK_CI_RELEASE_SIGNING_MODE:-}" == "ephemeral" ]] || {
    echo "Release preflight requires ephemeral CI-only signing." >&2
    exit 1
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "SHA-256 tool is unavailable." >&2
        return 1
    fi
}

for required_file in \
    "${BUNDLETOOL_JAR}" \
    "${SLOWCLOCK_CI_RELEASE_KEYSTORE}" \
    "${SLOWCLOCK_CI_RELEASE_STORE_PASSWORD_FILE}" \
    "${SLOWCLOCK_CI_RELEASE_KEY_PASSWORD_FILE}"; do
    [[ -s "${required_file}" ]] || {
        echo "Required preflight file is missing or empty: ${required_file}" >&2
        exit 1
    }
done

actual_bundletool_sha256="$(sha256_file "${BUNDLETOOL_JAR}")"
[[ "${actual_bundletool_sha256}" == "${BUNDLETOOL_SHA256}" ]] || {
    echo "bundletool SHA-256 mismatch." >&2
    exit 1
}

mkdir -p "${RELEASE_AAB_REPORT_DIR}"
private_dir="$(mktemp -d "${TMPDIR:-/tmp}/slowclock-release-preflight.XXXXXX")"
trap 'rm -rf "${private_dir}"' EXIT

"${repo_root}/scripts/verify-play-release-bundle.sh"

apks_path="${private_dir}/app-release.apks"
universal_apk_path="${private_dir}/universal.apk"

java -jar "${BUNDLETOOL_JAR}" build-apks \
    --bundle="${bundle_path}" \
    --output="${apks_path}" \
    --mode=universal \
    --overwrite \
    --ks="${SLOWCLOCK_CI_RELEASE_KEYSTORE}" \
    --ks-pass="file:${SLOWCLOCK_CI_RELEASE_STORE_PASSWORD_FILE}" \
    --ks-key-alias="${SLOWCLOCK_CI_RELEASE_KEY_ALIAS}" \
    --key-pass="file:${SLOWCLOCK_CI_RELEASE_KEY_PASSWORD_FILE}"

size_csv="$(java -jar "${BUNDLETOOL_JAR}" get-size total --apks="${apks_path}")"
size_values="$(printf '%s\n' "${size_csv}" | tail -n 1 | tr -d '[:space:]')"
IFS=',' read -r minimum_download_bytes maximum_download_bytes <<< "${size_values}"
for value in "${minimum_download_bytes}" "${maximum_download_bytes}"; do
    [[ "${value}" =~ ^[0-9]+$ ]] || {
        printf 'Unexpected bundletool size output:\n%s\n' "${size_csv}" >&2
        exit 1
    }
done

unzip -p "${apks_path}" universal.apk > "${universal_apk_path}"
[[ -s "${universal_apk_path}" ]] || {
    echo "bundletool did not produce a non-empty universal APK." >&2
    exit 1
}

# R8 이 Firestore 가 이름으로 읽는 자리를 지웠는지 배포될 산출물에서 직접 본다. 이름이
# 난독화되면 예외 없이 문서가 빈 값으로 매핑돼, 빌드도 테스트도 통과하고 기기에서만 깨진다.
# proguard-rules.pro 의 keep 규칙이 지워지거나 좁아지면 여기서 걸린다(#113).
dex_dir="${private_dir}/dex"
rm -rf "${dex_dir}"
mkdir -p "${dex_dir}"
unzip -o -q "${universal_apk_path}" 'classes*.dex' -d "${dex_dir}"
shopt -s nullglob
dex_files=("${dex_dir}"/classes*.dex)
shopt -u nullglob
[[ ${#dex_files[@]} -gt 0 ]] || {
    echo "Universal APK contains no DEX files." >&2
    exit 1
}

required_symbols=(
    'Lcom/example/slowclock/data/model/Schedule;'
    'Lcom/example/slowclock/data/model/User;'
    'Lcom/example/slowclock/data/model/PublicProfile;'
    'getTitle'
    'getStartTime'
    'getShareCode'
    'getFcmToken'
    'Lcom/example/slowclock/navigation/MainKey;'
)
for symbol in "${required_symbols[@]}"; do
    if ! grep -a -l -F -e "${symbol}" "${dex_files[@]}" > /dev/null 2>&1; then
        printf 'R8 stripped a name the app reads by reflection: %s\n' "${symbol}" >&2
        echo 'app/proguard-rules.pro 의 keep 규칙을 확인하라.' >&2
        exit 1
    fi
done

# 기동 스모크는 «배포될 그 산출물» 을 그대로 받아야 한다 — 다시 빌드하면 검증 대상과 배포 대상이
# 갈라진다. 여기서 만든 universal APK 를 요청받은 경로로 넘기고, 지우는 책임은 워크플로에 있다.
if [[ -n "${RELEASE_SMOKE_APK_PATH:-}" ]]; then
    mkdir -p "$(dirname -- "${RELEASE_SMOKE_APK_PATH}")"
    cp "${universal_apk_path}" "${RELEASE_SMOKE_APK_PATH}"
fi

aab_sha256="$(sha256_file "${bundle_path}")"
aab_size_bytes="$(wc -c < "${bundle_path}" | tr -d '[:space:]')"
if [[ -s "${mapping_path}" ]]; then
    mapping_sha256="$(sha256_file "${mapping_path}")"
else
    mapping_sha256="none"
fi
installable_apk_bytes="$(wc -c < "${universal_apk_path}" | tr -d '[:space:]')"
signer_sha256="$(
    keytool -printcert -jarfile "${bundle_path}" |
        awk -F': ' '/SHA256:/{print $2; exit}'
)"
[[ -n "${signer_sha256}" ]] || {
    echo "Unable to read the AAB signer SHA-256." >&2
    exit 1
}

source_sha="${SOURCE_SHA:-$(git -C "${repo_root}" rev-parse HEAD)}"
report_json="${RELEASE_AAB_REPORT_DIR}/release-aab-preflight.json"
report_markdown="${RELEASE_AAB_REPORT_DIR}/release-aab-preflight.md"
report_arguments=(
    --output-json "${report_json}"
    --output-markdown "${report_markdown}"
    --source-sha "${source_sha}"
    --aab-sha256 "${aab_sha256}"
    --aab-size-bytes "${aab_size_bytes}"
    --signer-sha256 "${signer_sha256}"
    --mapping-sha256 "${mapping_sha256}"
    --minimum-download-bytes "${minimum_download_bytes}"
    --maximum-download-bytes "${maximum_download_bytes}"
    --installable-apk-bytes "${installable_apk_bytes}"
    --bundletool-version "${BUNDLETOOL_VERSION}"
    --bundletool-sha256 "${actual_bundletool_sha256}"
)
if [[ -n "${RELEASE_AAB_BASELINE_PATH:-}" ]]; then
    report_arguments+=(--baseline "${RELEASE_AAB_BASELINE_PATH}")
fi

node "${script_dir}/render-release-aab-report.mjs" "${report_arguments[@]}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat "${report_markdown}" >> "${GITHUB_STEP_SUMMARY}"
fi
