# 작업 규약

## 이슈 우선 (issue-first)
1. GitHub 이슈 작성 (`.github/ISSUE_TEMPLATE/custom.md` 양식)
2. Type 라벨(Task/Bug/Feature) + Assignee 지정
3. `feat/<이슈번호>` 브랜치 생성 (예: `feat/21`)
4. 이슈에 브랜치 링크

> `git checkout -b` 로 임의 이름 브랜치 직행은 `git-branch-guard` hook 이 차단한다. `feat/<번호>` 이고 그 이슈가 실제로 있어야 한다.

## 브랜치 / 커밋 / PR
- 1인 운영이라 리뷰 승인을 요구하지 않는다. 빌드·lint·테스트 검증을 통과하면 커밋 → PR → CI 초록 → squash 머지 → 브랜치 삭제까지 이어서 한다(2026-09-05 개정).
- `--no-verify` 커밋은 hook 이 막는다. `push --force` · `reset --hard` · `rebase` 같은 파괴적 상태 변경도 막는다(`git-state-guard`).
- PR 제목 prefix → 라벨: `feat:`→Feature · `fix:`→Bug · `chore:`/`refactor:`→Task
- PR 본문에 `Closes #N` → 머지 시 이슈 자동 close
- PR 본문은 저장소 템플릿(📌 Issues / 📎 Work Description / 📷 Screenshot / 💬 To Reviewers)을 따른다.

## Type 라벨
`Task`(작업/잡무) · `Bug`(fix) · `Feature`(feat). 개인 레포라 GitHub 네이티브 Issue Type 대신 라벨로 운영.

## 자동화 (.claude/ · .github/)
- hook: git-branch/commit/state-guard · lsp-enforce · issue-type-guard · pending-work-audit · restore-claude-md
- CI: PR 검증 진입점 `pr-validation.yml`(ktlint · Android Lint · 단위 테스트 · screenshot) · CodeQL · Dependency Review · Repository Quality · Release AAB Preflight · App Distribution · Play 내부 트랙
- 보안: Secret scanning + Push protection · Dependabot(alerts + security updates) · CodeQL

## 아키텍처 규칙 (요약)
- 프레젠테이션은 MVI. Intent 로 들어가고 ReducerEvent 로 상태가 바뀐다. 계약은 [docs/architecture.md](../docs/architecture.md).
- UI 상태는 화면당 단일 객체 + `StateFlow` + `collectAsStateWithLifecycle()`. 일회성 신호도 UiState 의 nullable 필드로 흡수한다.
- Repository 를 우회해 DataSource(Firestore) 에 직접 의존하지 않는다.
- 색상·타이포 하드코딩 금지. `core/ui` 의 테마 토큰을 쓴다.
