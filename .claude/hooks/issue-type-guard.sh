#!/usr/bin/env bash
# PostToolUse Bash hook: `gh issue create` 직후 새 이슈의 "type 라벨" 검증.
#
# GitHub 네이티브 Issue Type 은 organization 전용이라 개인 레포(1hyok/SlowClock)에는 없음 →
# 종류 분류를 Task / Bug / Feature "라벨" 로 운영한다. 이 hook 은 새 이슈에 type 라벨이
# 붙었는지, title prefix 와 맞는지 검증한다.
#   - type 라벨 없음 → 보정 안내 (gh issue edit --add-label)
#   - title prefix 와 mismatch → 안내
#     - fix(...)   → Bug
#     - feat(...)  → Feature
#     - chore/refactor/test/ci/build/docs(...) → Task
#
# Bypass: 의도적으로 prefix 와 다른 라벨을 쓰려면 issue 본문에
# `<!-- type-override: <Task|Bug|Feature> -->` marker 를 박는다.
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
output="$(echo "$input" | jq -r '.tool_response.output // empty')"
[ -z "$cmd" ] && exit 0

# `gh issue create` 호출만 대상
if ! [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)gh[[:space:]]+issue[[:space:]]+create ]]; then
    exit 0
fi

# 출력에서 새 이슈 URL 추출 (`https://github.com/<owner>/<repo>/issues/<N>`)
url="$(echo "$output" | grep -oE 'https://github\.com/[^/]+/[^/]+/issues/[0-9]+' | head -1)"
[ -z "$url" ] && exit 0
issue_num="$(echo "$url" | sed -E 's|.*/issues/([0-9]+)|\1|')"

# 새 이슈의 라벨 + title + body 조회
data="$(gh issue view "$issue_num" --json labels,title,body 2>/dev/null)" || exit 0
title="$(echo "$data" | jq -r '.title // ""')"
body="$(echo "$data" | jq -r '.body // ""')"
# 현재 붙은 type 라벨 (Task/Bug/Feature 중 첫번째)
type_label="$(echo "$data" | jq -r '[.labels[].name] | map(select(. == "Task" or . == "Bug" or . == "Feature")) | .[0] // "null"')"

# override marker (Task|Bug|Feature)
override="$(echo "$body" | grep -oE '<!-- *type-override: *(Task|Bug|Feature) *-->' | grep -oE '(Task|Bug|Feature)' | head -1)"

# type 라벨 누락
if [ "$type_label" = "null" ]; then
    jq -nc --arg url "$url" --arg n "$issue_num" \
      '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: ("Issue \($url) 에 type 라벨(Task/Bug/Feature) 누락. title prefix 에 맞는 라벨을 즉시 부여: gh issue edit \($n) --add-label <Task|Bug|Feature>. (fix→Bug, feat→Feature, chore/refactor/test/ci/build/docs→Task)")}}'
    exit 0
fi

# override 있으면 통과
[ -n "$override" ] && exit 0

# title prefix → expected Type 추론
expected=""
case "$title" in
    fix\(*|fix:*) expected="Bug" ;;
    feat\(*|feat:*) expected="Feature" ;;
    chore\(*|chore:*|refactor\(*|refactor:*|test\(*|test:*|ci\(*|ci:*|build\(*|build:*|docs\(*|docs:*) expected="Task" ;;
esac

if [ -n "$expected" ] && [ "$type_label" != "$expected" ]; then
    jq -nc --arg url "$url" --arg actual "$type_label" --arg expected "$expected" --arg title "$title" --arg n "$issue_num" \
      '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: ("Issue \($url) 의 type 라벨=\($actual) 가 title prefix 와 mismatch. Title: \"\($title)\" → expected=\($expected). 보정: gh issue edit \($n) --remove-label \($actual) --add-label \($expected). 의도적이면 issue 본문에 `<!-- type-override: \($actual) -->` 추가.")}}'
fi

exit 0
