#!/usr/bin/env bash

# jarsigner -strict의 exit 4는 서로 다른 severe warning을 공유한다. 이 함수는 자가서명 또는
# 신뢰 체인 미검증만 허용하고, 같은 비트의 인증서 기간·TSA 만료·비활성 알고리즘은 fail-closed한다.
verify_jarsigner_result() {
    local strict_status=$1
    local verification_output=$2

    if [[ "${strict_status}" -ne 0 && "${strict_status}" -ne 4 ]]; then
        printf '%s\n' "${verification_output}" >&2
        echo "AAB JAR 서명 검증이 실패했습니다(jarsigner -strict exit ${strict_status})." >&2
        return 1
    fi
    if [[ "${verification_output}" != *"jar verified"* ]]; then
        printf '%s\n' "${verification_output}" >&2
        echo "AAB JAR 서명을 확인하지 못했습니다." >&2
        return 1
    fi

    case "${verification_output}" in
        *"signer certificate has expired"* | \
            *"signer certificate is not yet valid"* | \
            *"timestamp has expired"* | \
            *"TSA certificate has expired"* | \
            *"is considered a security risk and is disabled"*)
            printf '%s\n' "${verification_output}" >&2
            echo "AAB 서명에서 허용할 수 없는 exit 4 원인(인증서 기간·TSA 만료·비활성 알고리즘)을 발견했습니다." >&2
            return 1
            ;;
    esac

    # Oracle 문서상 exit 4의 나머지 허용 가능한 원인은 chainNotValidated와 signerSelfSigned뿐이다.
    # exit 4인데 둘 중 어느 진단도 없으면 새 JDK가 같은 비트에 원인을 추가했을 수 있으므로 막는다.
    if [[ "${strict_status}" -eq 4 &&
        "${verification_output}" != *"certificate chain is invalid"* &&
        "${verification_output}" != *"signer certificate is self-signed"* ]]; then
        printf '%s\n' "${verification_output}" >&2
        echo "AAB JAR 서명의 exit 4 원인을 안전한 체인 경고로 확인하지 못했습니다." >&2
        return 1
    fi
}
