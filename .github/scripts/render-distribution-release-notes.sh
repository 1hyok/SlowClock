#!/usr/bin/env bash

# Firebase App Distribution 릴리스 노트를 만든다.
#   push(main 머지)      : 머지된 PR 본문의 「📌 Issues」·「📎 Work Description」 섹션에서 뽑는다.
#   workflow_dispatch    : ISSUE_NUMBERS · RELEASE_NOTES 입력에서 뽑는다 (WIF canary).
# 이슈 번호와 변경 내용이 하나도 없으면 업로드 전에 실패한다 — 테스터가 무엇을 확인할지 모르는
# 배포는 내보내지 않는다.

set -euo pipefail

output_path="${1:?release notes output path is required}"
event_name="${EVENT_NAME:-}"
issue_numbers="${ISSUE_NUMBERS:-}"
release_notes="${RELEASE_NOTES:-}"

# PR 템플릿의 헤더는 이모지 + 수학 이탤릭 유니코드(📌𝘐𝘴𝘴𝘶𝘦𝘴 · 📎𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯) 라 이모지와
# 일반 ASCII 표기 둘 다 받는다.
extract_section() {
    local marker="$1"
    local ascii_marker="$2"
    local body_file="$3"

    awk -v marker="$marker" -v ascii_marker="$ascii_marker" '
        /^#[#]*[[:space:]]*/ {
            heading = $0
            if (capturing) {
                exit
            }
            if (index(heading, marker) > 0 || tolower(heading) ~ tolower(ascii_marker)) {
                capturing = 1
                next
            }
        }

        capturing {
            print
        }
    ' "$body_file"
}

print_pr_format() {
    printf '%s\n' \
        'main 에 머지되는 PR 본문에 다음 섹션을 채워 주세요 (.github/PULL_REQUEST_TEMPLATE.md).' \
        '## 📌 Issues' \
        '- closed #123' \
        '## 📎 Work Description' \
        '- 바뀐 동작과 테스터가 확인할 결과'
}

case "$event_name" in
    workflow_dispatch)
        distribution_title="SlowClock 수동 배포 (WIF canary)"
        release_notes="$(printf '%s\n' "$release_notes" | tr ';' '\n')"
        ;;
    push)
        distribution_title="SlowClock 릴리스 후보 배포"
        release_pr_body_file="${RELEASE_PR_BODY_FILE:-}"
        if [[ ! -s "$release_pr_body_file" ]]; then
            printf '::error::main push와 연결된 머지 PR 본문을 찾지 못했습니다.\n' >&2
            print_pr_format >&2
            exit 1
        fi

        issue_numbers="$(extract_section '📌' 'issues' "$release_pr_body_file")"
        release_notes="$(extract_section '📎' 'work description' "$release_pr_body_file")"
        ;;
    *)
        printf '::error::지원하지 않는 배포 이벤트입니다: %s\n' "${event_name:-<empty>}" >&2
        exit 1
        ;;
esac

normalized_issues="$(
    printf '%s\n' "$issue_numbers" |
        awk '
            {
                remaining = $0
                while (match(remaining, /#[0-9]+/)) {
                    issue = substr(remaining, RSTART, RLENGTH)
                    if (!seen[issue]++) {
                        print issue
                    }
                    remaining = substr(remaining, RSTART + RLENGTH)
                }
            }
        '
)"

normalized_notes="$(
    printf '%s\n' "$release_notes" |
        awk '
            /^[[:space:]]*<!--/ {
                in_comment = 1
            }

            in_comment {
                if ($0 ~ /-->/) {
                    in_comment = 0
                }
                next
            }

            {
                point = $0
                sub(/^[[:space:]]*[-*][[:space:]]*/, "", point)
                sub(/^[[:space:]]*[0-9]+\.[[:space:]]*/, "", point)
                sub(/^[[:space:]]+/, "", point)
                sub(/[[:space:]]+$/, "", point)

                if (point != "" && tolower(point) != "no response" && !seen[point]++) {
                    print point
                }
            }
        '
)"

if [[ -z "$normalized_issues" ]]; then
    printf '::error::Issues 섹션에는 #123 형식의 이슈 번호가 하나 이상 필요합니다.\n' >&2
    if [[ "$event_name" == "push" ]]; then
        print_pr_format >&2
    fi
    exit 1
fi

if [[ -z "$normalized_notes" ]]; then
    printf '::error::Work Description 섹션에는 변경 내용이 하나 이상 필요합니다.\n' >&2
    if [[ "$event_name" == "push" ]]; then
        print_pr_format >&2
    fi
    exit 1
fi

source_ref="${SOURCE_REF:-unknown}"
source_ref="${source_ref#refs/heads/}"
source_sha="${SOURCE_SHA:-unknown}"
short_sha="${source_sha:0:7}"

mkdir -p "$(dirname "$output_path")"
{
    printf '%s\n' "$distribution_title"
    printf '기준: %s @ %s\n\n' "$source_ref" "$short_sha"
    printf '포함 이슈\n'
    while IFS= read -r issue; do
        printf -- '- %s\n' "$issue"
    done <<< "$normalized_issues"
    printf '\n변경 내용\n'
    while IFS= read -r point; do
        printf -- '- %s\n' "$point"
    done <<< "$normalized_notes"
} > "$output_path"
