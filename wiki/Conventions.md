# 작업 규약

## 이슈 우선 (issue-first)
1. GitHub 이슈 작성 (`.github/ISSUE_TEMPLATE/custom.md` 양식)
2. **Type 라벨**(Task/Bug/Feature) + Assignee 지정
3. `feat/<이슈번호>` 브랜치 생성 (예: `feat/21`)
4. 이슈에 브랜치 링크

> `git checkout -b`로 임의 이름 브랜치 직행은 `git-branch-guard` hook이 **차단** — 반드시 `feat/<번호>` + 해당 이슈가 실제 존재해야 함.

## 브랜치 / 커밋 / PR
- **자율 커밋 금지** — Android Studio 커밋 탭에서 직접 검토·커밋 (`git-commit-guard` hook)
- `git push --force` · `reset --hard` · `rebase` 등 상태 변경은 명시 지시 시에만 (`git-state-guard`)
- PR 제목 prefix → 라벨: `feat:`→Feature · `fix:`→Bug · `chore:`/`refactor:`→Task
- PR 본문에 `closed #N` → 머지 시 이슈 자동 close
- 머지는 리뷰 승인 후 (self-merge 지양)

## Type 라벨
`Task`(작업/잡무) · `Bug`(fix) · `Feature`(feat) — 개인 레포라 GitHub 네이티브 Issue Type 대신 라벨로 운영.

## 자동화 (.claude/ · .github/)
- **hook:** git-branch/commit/state-guard · lsp-enforce · issue-type-guard · pending-work-audit · restore-claude-md
- **CI workflows:** lint · unit-test · screenshot · PRassign · auto-close-issue · mock-cleanup-check · release-distribution
- **보안:** Secret scanning + Push protection · Dependabot(alerts + security updates) · CodeQL

## 아키텍처 규칙 (요약)
- UI 상태 = 화면당 단일 객체 + `StateFlow` + `collectAsStateWithLifecycle()`
- Repository 우회해 DataSource 직접 의존 금지
- 색상 하드코딩 금지 → `ui/theme` 토큰
- 자세한 건 `CLAUDE.md`(개인 컨벤션, gitignore) 참고
