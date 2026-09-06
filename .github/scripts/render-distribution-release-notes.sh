#!/usr/bin/env bash

# Firebase App Distribution 릴리스 노트를 만든다.
#   push / pull_request : PR 본문의 「📌 Issues」·「📎 Work Description」 섹션에서 뽑는다.
#   workflow_dispatch    : ISSUE_NUMBERS · RELEASE_NOTES 입력에서 뽑는다 (WIF canary).
# 이슈 번호와 변경 내용이 하나도 없으면 업로드 전에 실패한다 — 테스터가 무엇을 확인할지 모르는
# 배포는 내보내지 않는다.

set -euo pipefail

output_path="${1:?release notes output path is required}"
event_name="${EVENT_NAME:-}"
issue_numbers="${ISSUE_NUMBERS:-}"
release_notes="${RELEASE_NOTES:-}"

# 섹션 경계를 찾기 전에 코드·주석을 걷어 낸다. 코드 안의 제목은 PR 제목이 아니다.
# 닫는 fence 는 여는 fence 와 같은 문자로, 길이가 같거나 길어야 한다.
strip_blocks() {
    awk '
        {
            line = $0
            sub(/\r$/, "", line)
            fence = line
            sub(/^[[:space:]]*/, "", fence)
            if (fence_char != "") {
                if (substr(fence, 1, 1) == fence_char) {
                    run = fence
                    sub(fence_char == "`" ? "[^`].*$" : "[^~].*$", "", run)
                    tail = substr(fence, length(run) + 1)
                    if (length(run) >= fence_length && tail ~ /^[[:space:]]*$/) {
                        fence_char = ""
                    }
                }
                next
            }
            # 주석 안의 fence 는 코드 블록을 열지 않는다. 같은 줄의 보이는 글은 보존한다.
            visible = ""
            while (length(line)) {
                if (in_comment) {
                    finish = index(line, "-->")
                    if (!finish) { line = ""; break }
                    line = substr(line, finish + 3)
                    in_comment = 0
                } else {
                    begin = index(line, "<!--")
                    if (!begin) { visible = visible line; break }
                    visible = visible substr(line, 1, begin - 1)
                    line = substr(line, begin + 4)
                    in_comment = 1
                }
            }
            fence = visible
            sub(/^[[:space:]]*/, "", fence)
            if (fence ~ /^(```|~~~)/) {
                fence_char = substr(fence, 1, 1)
                run = fence
                sub(fence_char == "`" ? "[^`].*$" : "[^~].*$", "", run)
                fence_length = length(run)
                next
            }
            print visible
        }
    '
}

extract_section() {
    local marker="$1"
    local ascii_marker="$2"
    local styled_marker="$3"
    local body_file="$4"

    strip_blocks < "$body_file" |
        awk -v marker="$marker" -v ascii_marker="$ascii_marker" -v styled_marker="$styled_marker" '
            /^[[:space:]]*#{1,6}[[:space:]]/ {
                heading = $0
                if (capturing) { capturing = 0; finished = 1 }
                if (finished) next
                sub(/^[[:space:]]*#+[[:space:]]+/, "", heading)
                sub(/[[:space:]]+#+[[:space:]]*$/, "", heading)
                sub("^" marker "[[:space:]]*", "", heading)
                sub(/[[:space:]]+$/, "", heading)
                if (tolower(heading) == ascii_marker || heading == styled_marker) {
                    capturing = 1
                    next
                }
            }
            capturing { print }
        '
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
    push|pull_request)
        distribution_title="SlowClock 릴리스 후보 배포"
        release_pr_body_file="${RELEASE_PR_BODY_FILE:-}"
        if [[ ! -s "$release_pr_body_file" ]]; then
            printf '::error::릴리스 노트를 만들 PR 본문을 찾지 못했습니다.\n' >&2
            print_pr_format >&2
            exit 1
        fi

        issue_numbers="$(extract_section '📌' 'issues' '𝘐𝘴𝘴𝘶𝘦𝘴' "$release_pr_body_file")"
        release_notes="$(extract_section '📎' 'work description' '𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯' "$release_pr_body_file")"
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

# 테스터가 앱에서 보는 것은 짧은 목록 하나다. 마크다운 장식이 그대로 실리면 표 구분선과
# 백틱이 항목으로 들어와 무엇이 바뀌었는지 읽기 어려워진다. 사람이 읽을 문장만 남긴다(#182).
normalized_notes="$(
    printf '%s\n' "$release_notes" |
        strip_blocks |
        awk '
            # 표의 줄과 구분선. 짧은 목록에 표가 들어갈 자리가 없다.
            /^[[:space:]]*\|/ {
                next
            }

            # 수평선(--- · *** · ___). 목록 표시를 떼기 전에 걸러야 빈 항목으로 남지 않는다.
            /^[[:space:]]*([-*_][[:space:]]*){3,}$/ {
                next
            }

            # HTML 로 시작하는 줄. <details> 같은 접기 표시가 그대로 나가면 뜻이 없다.
            /^[[:space:]]*<[a-zA-Z\/!]/ {
                next
            }

            {
                point = $0
                sub(/^[[:space:]]*>[[:space:]]*/, "", point)
                # 목록 표시는 「- 」 나 「* 」 처럼 뒤에 공백이 온다. 공백을 요구하지 않으면
                # 「**굵게** 로 시작하는 문단」 의 별표 하나를 목록 표시로 보고 잘라 낸다(#159).
                sub(/^[[:space:]]*[-*][[:space:]]+/, "", point)
                # 내용 없이 표시만 있는 줄. 이 줄은 비운 것으로 봐야 아래 검사가 걸러 낸다.
                sub(/^[[:space:]]*[-*][[:space:]]*$/, "", point)
                sub(/^[[:space:]]*[0-9]+\.[[:space:]]*/, "", point)
                # 체크 상자는 표시만 떼고 글자는 남긴다.
                sub(/^\[[ xX]\][[:space:]]*/, "", point)

                # 그림은 통째로 뺀다. 대체 글자만 남으면 무엇을 가리키는지 알 수 없다.
                gsub(/!\[[^]]*\]\([^)]*\)/, "", point)
                # 링크는 글자만 남긴다. 주소는 테스터가 열 수 없는 자리가 많다.
                while (match(point, /\[[^]]*\]\([^)]*\)/)) {
                    chunk = substr(point, RSTART, RLENGTH)
                    label = substr(chunk, 2, index(chunk, "](") - 2)
                    point = substr(point, 1, RSTART - 1) label substr(point, RSTART + RLENGTH)
                }
                # 인라인 코드와 굵게·기울임 표기. 글자만 남기고 기호는 뺀다.
                gsub(/`/, "", point)
                gsub(/\*\*/, "", point)
                gsub(/__/, "", point)
                # 기울임은 짝이 있는 표시만 벗긴다. snake_case 같은 식별자는 보존한다.
                while (match(point, /\*[^*]+\*/)) {
                    point = substr(point, 1, RSTART - 1) substr(point, RSTART + 1, RLENGTH - 2) substr(point, RSTART + RLENGTH)
                }
                while (match(point, /(^|[[:space:]])_[^_]+_([[:space:][:punct:]]|$)/)) {
                    chunk = substr(point, RSTART, RLENGTH)
                    sub(/_/, "", chunk)
                    sub(/_/, "", chunk)
                    point = substr(point, 1, RSTART - 1) chunk substr(point, RSTART + RLENGTH)
                }

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
    if [[ "$event_name" == "push" || "$event_name" == "pull_request" ]]; then
        print_pr_format >&2
    fi
    exit 1
fi

if [[ -z "$normalized_notes" ]]; then
    printf '::error::Work Description 섹션에는 변경 내용이 하나 이상 필요합니다.\n' >&2
    if [[ "$event_name" == "push" || "$event_name" == "pull_request" ]]; then
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
