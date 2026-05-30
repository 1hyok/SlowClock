#!/usr/bin/env bash
# PreToolUse Bash hook: enforce feat/<issue-number> branch naming + issue existence on GitHub.
#
# Triggers on any of:
#   git checkout -b|-B <name>
#   git switch -c <name>
#   git branch -m|-M <new>            (rename current)
#   git branch -m|-M <old> <new>      (rename specific)
#
# On violation, denies via PreToolUse JSON output. Otherwise exits 0 (allow).
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

new_branch=""
if [[ "$cmd" =~ git[[:space:]]+checkout[[:space:]]+-[bB][[:space:]]+([^[:space:]]+) ]]; then
    new_branch="${BASH_REMATCH[1]}"
elif [[ "$cmd" =~ git[[:space:]]+switch[[:space:]]+-c[[:space:]]+([^[:space:]]+) ]]; then
    new_branch="${BASH_REMATCH[1]}"
elif [[ "$cmd" =~ git[[:space:]]+branch[[:space:]]+-[mM][[:space:]]+([^[:space:]]+)[[:space:]]+([^[:space:]]+) ]]; then
    new_branch="${BASH_REMATCH[2]}"
elif [[ "$cmd" =~ git[[:space:]]+branch[[:space:]]+-[mM][[:space:]]+([^[:space:]]+) ]]; then
    new_branch="${BASH_REMATCH[1]}"
fi

[ -z "$new_branch" ] && exit 0

# Strip surrounding quotes
new_branch="${new_branch#\"}"; new_branch="${new_branch%\"}"
new_branch="${new_branch#\'}"; new_branch="${new_branch%\'}"

deny() {
    jq -nc --arg reason "$1" \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
    exit 0
}

if [[ ! "$new_branch" =~ ^feat/([0-9]+)$ ]]; then
    deny "브랜치 이름 '$new_branch' 이 규약(feat/<이슈번호>)을 어깁니다. .github/ISSUE_TEMPLATE/custom.md 양식으로 GitHub 이슈를 먼저 작성한 뒤 부여된 번호로 분기하세요."
fi

issue_num="${BASH_REMATCH[1]}"
if ! gh issue view "$issue_num" >/dev/null 2>&1; then
    deny "이슈 #$issue_num 이 GitHub(1hyok/SlowClock)에 존재하지 않습니다. .github/ISSUE_TEMPLATE/custom.md 양식으로 이슈를 먼저 작성하고 부여된 번호로 분기하세요."
fi

exit 0
