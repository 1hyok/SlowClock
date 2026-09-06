#!/usr/bin/env bash

set -euo pipefail

apk_path="${1:?release APK path is required}"
[[ -s "${apk_path}" ]] || {
    echo "Release APK is missing or empty: ${apk_path}" >&2
    exit 1
}

apksigner_path="${APKSIGNER:-}"
if [[ -z "${apksigner_path}" ]]; then
    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    [[ -d "${sdk_root}/build-tools" ]] || {
        echo "Android SDK build-tools are required to verify the APK signature." >&2
        exit 1
    }
    apksigner_path="$(find "${sdk_root}/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)"
fi
[[ -x "${apksigner_path}" ]] || {
    echo "SDK apksigner is unavailable." >&2
    exit 1
}

"${apksigner_path}" verify --verbose --print-certs "${apk_path}"
