#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/jarsigner-verification-policy.sh
# repository-quality checks each script independently without -x.
# shellcheck disable=SC1091
source "${script_dir}/jarsigner-verification-policy.sh"

assert_accept() {
    local status=$1
    local output=$2
    if ! verify_jarsigner_result "${status}" "${output}" >/dev/null 2>&1; then
        echo "허용 fixture를 거부했습니다: ${output}" >&2
        exit 1
    fi
}

assert_reject() {
    local status=$1
    local output=$2
    if verify_jarsigner_result "${status}" "${output}" >/dev/null 2>&1; then
        echo "거부 fixture를 허용했습니다: ${output}" >&2
        exit 1
    fi
}

assert_accept 0 $'jar verified.'
assert_accept 4 $'jar verified, with signer errors.\nThis jar contains entries whose certificate chain is invalid.'
assert_accept 4 $'jar verified, with signer errors.\nThis jar contains entries whose signer certificate is self-signed.'

assert_reject 20 $'jar verified, with signer errors.\nThis jar contains unsigned entries.'
assert_reject 4 $'jar verified, with signer errors.\nThe signer certificate has expired.'
assert_reject 4 $'jar verified, with signer errors.\nThe signer certificate is not yet valid.'
assert_reject 4 $'jar verified, with signer errors.\nThe timestamp has expired.'
assert_reject 4 $'jar verified, with signer errors.\nThe SHA1withRSA algorithm is considered a security risk and is disabled.'
assert_reject 4 $'jar verified, with signer errors.\nAn unrecognized future warning reused exit 4.'

echo "jarsigner verification policy fixtures: passed"
