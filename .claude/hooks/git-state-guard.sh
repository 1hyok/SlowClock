#!/usr/bin/env bash
# PreToolUse Bash hook: block dangerous git state changes without explicit user instruction.
#
# 차단 대상:
#   - git push (모든 형태) — force push 포함
#   - git reset --hard
#   - git rebase
#   - git branch -D
#   - git clean -f
#   - git checkout -- <file>  (작업 파일 폐기)
#   - git restore <file>      (작업 파일 폐기)
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

deny() {
    jq -nc --arg reason "git state 변경은 사용자 명시 지시 필요. '$1' 자율 실행 금지." \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
    exit 0
}

# git push — force push 만 차단. 일반 push 와 --delete 는 통과.
# /create-pr 같은 명시 워크플로우에서 일반 push 는 안전한 작업 (기존 commit 추가만, 손실 없음).
# Force push (-f, --force, --force-with-lease, +refspec) 는 commit 손실 위험 → 차단 유지.
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+push([[:space:]]|$) ]]; then
    if [[ "$cmd" =~ git[[:space:]]+push[[:space:]].*(-f([[:space:]]|$)|--force([[:space:]]|=|$)|--force-with-lease) ]] \
        || [[ "$cmd" =~ git[[:space:]]+push[[:space:]]+[^[:space:]]+[[:space:]]+\+ ]]; then
        deny "git push --force"
    fi
fi

# git reset --hard
if [[ "$cmd" =~ git[[:space:]]+reset[[:space:]].*--hard ]]; then
    deny "git reset --hard"
fi

# git rebase
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+rebase([[:space:]]|$) ]]; then
    deny "git rebase"
fi

# git branch -D (force delete)
# 예외: 단일 feat/<숫자> 브랜치 + 머지된 PR (head 매칭) 1건 이상이면 통과.
# memory feedback_cleanup_merged_branches.md — PR 머지 확인 시점 자동 삭제 영구 허가.
# 모호 케이스 (multi-branch, 옵션 섞임, PR 없음/OPEN/CLOSED, head 매칭 안 됨) 는 deny 유지.
if [[ "$cmd" =~ git[[:space:]]+branch[[:space:]]+-D([[:space:]]|$) ]]; then
    args="${cmd#*git branch -D}"
    read -ra tokens <<< "$args"
    if [[ ${#tokens[@]} -eq 1 && "${tokens[0]}" =~ ^feat/[0-9]+$ ]]; then
        branch="${tokens[0]}"
        if merged=$(gh pr list --head "$branch" --state merged --json number --jq 'length' 2>/dev/null) \
           && [[ "$merged" -gt 0 ]]; then
            exit 0
        fi
    fi
    deny "git branch -D"
fi

# git clean -f / -fd / -ffd 등
if [[ "$cmd" =~ git[[:space:]]+clean[[:space:]].*-[a-zA-Z]*f ]]; then
    deny "git clean -f"
fi

# git checkout -- <file> (작업 파일 폐기)
if [[ "$cmd" =~ git[[:space:]]+checkout[[:space:]]+-- ]]; then
    deny "git checkout --"
fi

# git restore <file> (작업 파일 폐기)
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+restore([[:space:]]|$) ]]; then
    deny "git restore"
fi

exit 0
