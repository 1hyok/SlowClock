#!/usr/bin/env bash

set -uo pipefail

if [ "$#" -gt 2 ]; then
    echo "Usage: $0 [repository-root] [pull-request-files-json]" >&2
    exit 2
fi

if [ "$#" -ge 1 ]; then
    repository_root=$(cd "$1" && pwd -P) || exit 2
else
    repository_root=$(git rev-parse --show-toplevel) || exit 2
fi

changed_files_json=${2:-}

if ! git -C "$repository_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Repository Quality: not a Git worktree: $repository_root" >&2
    exit 2
fi

require_command() {
    local command_name=$1

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Repository Quality: required command is missing: $command_name" >&2
        exit 2
    fi
}

require_command actionlint
require_command bash
require_command git
require_command shellcheck
if [ -n "$changed_files_json" ]; then
    require_command jq
fi

actionlint_binary=$(command -v actionlint)
bash_binary=$(command -v bash)
shellcheck_binary=$(command -v shellcheck)

quality_failed=0
workflow_count=0
shell_script_count=0

cd "$repository_root" || exit 2

workflow_files=()
shell_files=()
scan_files=()
scan_file_count=0
if [ -n "$changed_files_json" ]; then
    if [ ! -s "$changed_files_json" ]; then
        echo "Repository Quality: pull request files JSON is missing: $changed_files_json" >&2
        exit 2
    fi
    while IFS= read -r changed_file; do
        if [ ! -f "$changed_file" ]; then
            continue
        fi
        scan_files+=("$changed_file")
        scan_file_count=$((scan_file_count + 1))
        case "$changed_file" in
            .github/workflows/*.yml|.github/workflows/*.yaml)
                workflow_files+=("$changed_file")
                ;;
        esac
        case "$changed_file" in
            *.sh)
                shell_files+=("$changed_file")
                ;;
        esac
    done < <(
        jq -r '[.. | objects | .filename?, .previous_filename?]
            | map(select(type == "string")) | unique[]' "$changed_files_json"
    )
else
    while IFS= read -r -d '' workflow_file; do
        workflow_files+=("$workflow_file")
    done < <(git ls-files -z -- '.github/workflows/*.yml' '.github/workflows/*.yaml')
    while IFS= read -r -d '' shell_file; do
        shell_files+=("$shell_file")
    done < <(git ls-files -z -- '*.sh')
fi

for workflow_file in "${workflow_files[@]+"${workflow_files[@]}"}"; do
    workflow_count=$((workflow_count + 1))
    workflow_shellcheck_command=$shellcheck_binary


    if ! "$actionlint_binary" \
        -color=false \
        -shellcheck="$workflow_shellcheck_command" \
        "$workflow_file"; then
        echo "Repository Quality: actionlint failed: $workflow_file" >&2
        quality_failed=1
    fi
done

if [ -z "$changed_files_json" ] && [ "$workflow_count" -eq 0 ]; then
    echo "Repository Quality: no tracked workflow YAML files found" >&2
    quality_failed=1
fi

for shell_file in "${shell_files[@]+"${shell_files[@]}"}"; do
    shell_script_count=$((shell_script_count + 1))

    case "$shell_file" in
        build-leaf.sh)
            # File-scoped suppressions for existing intentional behavior:
            # SC2086 splits the accumulated Gradle task list into separate arguments.
            # SC2126 keeps a numeric child-module count used by the following test.
            if ! "$shellcheck_binary" -e SC2086,SC2126 "$shell_file"; then
                echo "Repository Quality: ShellCheck failed: $shell_file" >&2
                quality_failed=1
            fi
            ;;
        *)
            if ! "$shellcheck_binary" "$shell_file"; then
                echo "Repository Quality: ShellCheck failed: $shell_file" >&2
                quality_failed=1
            fi
            ;;
    esac

    if ! "$bash_binary" -n "$shell_file"; then
        echo "Repository Quality: bash syntax failed: $shell_file" >&2
        quality_failed=1
    fi
done

if [ -z "$changed_files_json" ] && [ "$shell_script_count" -eq 0 ]; then
    echo "Repository Quality: no tracked shell scripts found" >&2
    quality_failed=1
fi

# Firebase App Distribution 업로드 경로 2종은 같은 앱에 올린다. concurrency group 이 갈리면
# 두 러너가 동시에 업로드해 어느 빌드가 테스터에게 남을지 완료 순서로 갈린다. 주석만으로는
# 한쪽만 고치는 재발을 막지 못하므로 group 문자열 동일성을 기계로 강제한다 (#995).
upload_workflow_files=()
check_upload_group=false
workflow_file_list=" ${workflow_files[*]-} "

for upload_workflow_candidate in \
    .github/workflows/release-distribution.yml \
    .github/workflows/firebase-wif-canary.yml; do
    if [ -f "$upload_workflow_candidate" ]; then
        upload_workflow_files+=("$upload_workflow_candidate")
    fi
    if [ -z "$changed_files_json" ] || [[ "$workflow_file_list" == *" $upload_workflow_candidate "* ]]; then
        check_upload_group=true
    fi
done

if [ "$check_upload_group" = true ] && [ "${#upload_workflow_files[@]}" -gt 0 ]; then
    upload_group_count=$(
        for upload_workflow_file in "${upload_workflow_files[@]}"; do
            awk '
                /^concurrency:/ { inside = 1; next }
                inside && /^  group:/ { print $2; exit }
                inside && /^[^[:space:]]/ { exit }
            ' "$upload_workflow_file"
        done | sort -u | wc -l
    )

    if [ "$upload_group_count" -ne 1 ]; then
        echo "Repository Quality: Firebase App Distribution upload workflows disagree on concurrency group" >&2
        quality_failed=1
    fi
fi

if [ -n "$changed_files_json" ]; then
    merge_marker_status=1
    merge_marker_output=""
    if [ "$scan_file_count" -gt 0 ]; then
        merge_marker_output=$(git grep -nI -E '^(<<<<<<<|=======|>>>>>>>)( |$)' -- "${scan_files[@]+"${scan_files[@]}"}")
        merge_marker_status=$?
    fi
else
    merge_marker_output=$(git grep -nI -E \
        '^(<<<<<<<|=======|>>>>>>>)( |$)' \
        -- \
        . \
        ':(exclude,glob)**/build/**' \
        ':(exclude,glob)**/.gradle/**' \
        ':(exclude,glob)**/.kotlin/**' \
        ':(exclude,glob)**/.cxx/**' \
        ':(exclude,glob)**/.cache/**' \
        ':(exclude,glob)**/generated/**')
    merge_marker_status=$?
fi

case "$merge_marker_status" in
    0)
        echo "$merge_marker_output" >&2
        echo "Repository Quality: merge conflict marker found" >&2
        quality_failed=1
        ;;
    1)
        ;;
    *)
        echo "Repository Quality: merge conflict marker scan failed" >&2
        quality_failed=1
        ;;
esac

if [ "$quality_failed" -ne 0 ]; then
    exit 1
fi

scope=$([ -n "$changed_files_json" ] && printf 'changed paths' || printf 'full repository')
echo "Repository Quality: passed ($scope; $workflow_count workflows, $shell_script_count shell scripts)"
