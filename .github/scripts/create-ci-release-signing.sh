#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 --workspace PATH --runner-temp PATH --github-env PATH" >&2
    exit 2
}

workspace=""
runner_temp=""
github_env=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --workspace)
            workspace="${2:-}"
            shift 2
            ;;
        --runner-temp)
            runner_temp="${2:-}"
            shift 2
            ;;
        --github-env)
            github_env="${2:-}"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

[[ -n "${workspace}" && -n "${runner_temp}" && -n "${github_env}" ]] || usage
[[ "${SLOWCLOCK_CI_CONFIG_MODE:-}" == "stub" ]] || {
    echo "CI release signing requires SLOWCLOCK_CI_CONFIG_MODE=stub." >&2
    exit 1
}
[[ ! -e "${workspace}/local.properties" ]] || {
    echo "Refusing to overwrite an existing local.properties file." >&2
    exit 1
}

mkdir -p "${runner_temp}"

keystore_path="${runner_temp}/slowclock-ci-release.jks"
store_password_path="${runner_temp}/slowclock-ci-release-store-password"
key_password_path="${runner_temp}/slowclock-ci-release-key-password"
key_alias="slowclock-ci-release"
store_password="slowclock-ci-store-only"
key_password="slowclock-ci-key-only"

printf '%s' "${store_password}" > "${store_password_path}"
printf '%s' "${key_password}" > "${key_password_path}"
chmod 600 "${store_password_path}" "${key_password_path}"

keytool -genkeypair \
    -noprompt \
    -keystore "${keystore_path}" \
    -storetype JKS \
    -storepass "${store_password}" \
    -keypass "${key_password}" \
    -alias "${key_alias}" \
    -dname "CN=SlowClock CI Preflight, O=SlowClock CI, C=KR" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 1 \
    >/dev/null 2>&1
chmod 600 "${keystore_path}"

properties_path="${workspace}/local.properties"
{
    printf 'RELEASE_STORE_FILE=%s\n' "${keystore_path}"
    printf 'RELEASE_STORE_PASSWORD=%s\n' "${store_password}"
    printf 'RELEASE_KEY_ALIAS=%s\n' "${key_alias}"
    printf 'RELEASE_KEY_PASSWORD=%s\n' "${key_password}"
} > "${properties_path}"
chmod 600 "${properties_path}"

{
    printf 'SLOWCLOCK_CI_RELEASE_SIGNING_MODE=ephemeral\n'
    printf 'SLOWCLOCK_CI_RELEASE_KEYSTORE=%s\n' "${keystore_path}"
    printf 'SLOWCLOCK_CI_RELEASE_STORE_PASSWORD_FILE=%s\n' "${store_password_path}"
    printf 'SLOWCLOCK_CI_RELEASE_KEY_PASSWORD_FILE=%s\n' "${key_password_path}"
    printf 'SLOWCLOCK_CI_RELEASE_KEY_ALIAS=%s\n' "${key_alias}"
} >> "${github_env}"

echo "Created an ephemeral CI-only release keystore."
