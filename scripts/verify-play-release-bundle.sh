#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
# shellcheck source=scripts/jarsigner-verification-policy.sh
# repository-quality checks each script independently without -x.
# shellcheck disable=SC1091
source "${script_dir}/jarsigner-verification-policy.sh"
bundle_path="${repo_root}/app/build/outputs/bundle/release/app-release.aab"
mapping_path="${repo_root}/app/build/outputs/mapping/release/mapping.txt"

case "${1:-}" in
    "")
        "${repo_root}/gradlew" :app:bundleRelease --no-daemon --console=plain
        ;;
    --skip-build)
        ;;
    *)
        echo "Usage: $0 [--skip-build]" >&2
        exit 2
        ;;
esac

if [[ ! -s "${bundle_path}" ]]; then
    echo "AAB를 찾을 수 없거나 비어 있습니다: ${bundle_path}" >&2
    exit 1
fi

# release 는 R8 을 사용한다. 이전 빌드의 mapping 을 현재 AAB 의 것으로 보고하지 않는다.
if [[ ! -s "${mapping_path}" ]]; then
    echo "R8 mapping을 찾을 수 없거나 비어 있습니다: ${mapping_path}" >&2
    exit 1
fi

bundle_entries="$(unzip -Z1 "${bundle_path}")"
required_entries=(
    "BundleConfig.pb"
    "base/manifest/AndroidManifest.xml"
    "base/resources.pb"
    "base/dex/classes.dex"
    "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
)

for required_entry in "${required_entries[@]}"; do
    if ! grep -Fqx "${required_entry}" <<<"${bundle_entries}"; then
        echo "AAB 필수 항목이 없습니다: ${required_entry}" >&2
        exit 1
    fi
done

private_dir="$(mktemp -d "${TMPDIR:-/tmp}/slowclock-bundle-mapping.XXXXXX")"
trap 'rm -rf "${private_dir}"' EXIT
unzip -p "${bundle_path}" \
    'BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map' > "${private_dir}/proguard.map"
if ! cmp -s "${mapping_path}" "${private_dir}/proguard.map"; then
    echo "외부 R8 mapping이 AAB에 포함된 mapping과 일치하지 않습니다." >&2
    exit 1
fi

export LC_ALL=C
# jarsigner는 unsigned entry가 섞여 있어도 "jar verified."와 exit 0을 내므로 -strict로 경고를 exit code에 승격시킨다.
# -strict exit code는 경고 비트의 합이다. 자가서명 upload key는 인증서 체인 그룹(4)을 항상 세우므로 0·4만 허용하고,
# unsigned entry(16)·서명 검증 실패(1) 등 나머지 비트는 실패로 처리한다.
strict_status=0
verification_output="$(jarsigner -verify -strict "${bundle_path}" 2>&1)" || strict_status=$?
verify_jarsigner_result "${strict_status}" "${verification_output}"

if command -v shasum >/dev/null 2>&1; then
    bundle_sha256="$(shasum -a 256 "${bundle_path}" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
    bundle_sha256="$(sha256sum "${bundle_path}" | awk '{print $1}')"
else
    echo "SHA-256 계산 도구(shasum 또는 sha256sum)가 없습니다." >&2
    exit 1
fi

signer_sha256="$(
    keytool -printcert -jarfile "${bundle_path}" |
        awk -F': ' '/SHA256:/{print $2; exit}'
)"
if [[ -z "${signer_sha256}" ]]; then
    echo "AAB 서명 인증서 SHA-256을 읽지 못했습니다." >&2
    exit 1
fi

bundle_size="$(wc -c < "${bundle_path}" | tr -d '[:space:]')"

printf 'AAB: %s\n' "${bundle_path}"
printf '크기(bytes): %s\n' "${bundle_size}"
printf 'AAB SHA-256: %s\n' "${bundle_sha256}"
printf '서명 인증서 SHA-256: %s\n' "${signer_sha256}"
printf 'R8 mapping (AAB 내 파일과 일치): %s\n' "${mapping_path}"
