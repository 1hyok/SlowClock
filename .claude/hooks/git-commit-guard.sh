#!/usr/bin/env bash
# PreToolUse Bash hook: `git commit` 정책.
#
# 2026-09-05 이전: 사용자가 Android Studio 커밋 탭에서 직접 검토·커밋하는 워크플로우라 Claude 의 `git commit` 을 전부 막았다.
# 2026-09-05 지시("이 프로젝트는 내가 검토할 시간이 없으니 네가 알아서 해라"): 이 저장소에서는 Claude 가 검증을 마친 뒤
# 커밋·push·PR 생성·머지까지 수행한다. 이 훅은 검증을 건너뛰는 `--no-verify` 커밋만 막는다.
# 되돌리려면 이 파일의 이전 판(전부 deny)을 git 이력에서 복원하고 CLAUDE.md 의 규약 문구를 함께 되돌린다.
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+commit([[:space:]]|$) ]] && [[ "$cmd" =~ --no-verify ]]; then
    jq -nc --arg reason "git commit --no-verify 금지. 훅 검증을 건너뛰지 말고 실패 원인을 고친 뒤 커밋하세요." \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
fi

exit 0
