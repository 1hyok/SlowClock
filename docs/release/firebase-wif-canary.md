# Firebase App Distribution WIF (Workload Identity Federation)

배포 워크플로는 장기 서비스 계정 JSON 키 대신 GitHub OIDC → Google Cloud Workload Identity Federation 으로 업로드 직전에 단기 자격을 받는다. 이 문서는 그 설정의 정본과, 설정을 바꾼 뒤 production 배포(main 머지) 없이 경로를 검증하는 canary 절차다.

## 설정 정본 (Google Cloud 프로젝트 `slow-clock-scheduler`, 번호 `887622067286`)

| 항목 | 값 |
|---|---|
| Workload Identity Pool | `github-actions` (location `global`) |
| Provider | `github`, issuer `https://token.actions.githubusercontent.com` |
| attribute mapping | `google.subject=assertion.sub`, `attribute.repository=assertion.repository`, `attribute.repository_owner=assertion.repository_owner`, `attribute.ref=assertion.ref`, `attribute.workflow_ref=assertion.workflow_ref`, `attribute.environment=assertion.environment` |
| attribute condition | `assertion.repository == '1hyok/SlowClock' && assertion.repository_owner == '1hyok'` |
| Firebase 배포 서비스 계정 | `github-firebase-distribution@slow-clock-scheduler.iam.gserviceaccount.com`, 프로젝트 역할 `roles/firebaseappdistro.admin` |
| Play 배포 서비스 계정 | `play-internal-publisher@slow-clock-scheduler.iam.gserviceaccount.com`, 프로젝트 역할 없음(Play Console 에서 초대) |
| 서비스 계정 impersonation | 두 계정 모두 `roles/iam.workloadIdentityUser` 를 `principalSet://iam.googleapis.com/projects/887622067286/locations/global/workloadIdentityPools/github-actions/attribute.repository/1hyok/SlowClock` 에 부여 |
| 필요한 API | `sts.googleapis.com`, `iamcredentials.googleapis.com`, `firebaseappdistribution.googleapis.com`, `androidpublisher.googleapis.com` |

생성 명령은 [CI 설정 runbook](ci-setup.md) 에 있다. GitHub 쪽 값은 `release-distribution`·`play-internal` environment 의 secret `GCP_WORKLOAD_IDENTITY_PROVIDER`(`projects/887622067286/locations/global/workloadIdentityPools/github-actions/providers/github`) 와 `GCP_FIREBASE_SERVICE_ACCOUNT` / `GCP_PLAY_SERVICE_ACCOUNT` 다.

## 안전 경계

- WIF 인증은 signed APK 를 빌드하고 attestation 을 검증한 뒤, 업로드 직전에만 수행한다. 빌드 단계에는 Google 자격이 없다.
- provider·service account 값, token, 생성된 ADC 파일 내용은 workflow summary 나 artifact 에 남기지 않는다.
- signed APK 는 public Actions artifact 로 게시하지 않는다. 같은 러너에서 바로 업로드한다.
- attribute condition 이 저장소를 고정하므로 fork·다른 저장소의 워크플로는 토큰을 받지 못한다. environment 의 deployment branch 정책(`main`)이 브랜치를 고정한다.

공식 근거:

- [Google Cloud deployment pipeline WIF](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [GitHub Actions OIDC for Google Cloud](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-google-cloud-platform)
- [Firebase App Distribution CI/CD](https://firebase.google.com/docs/app-distribution/best-practices-distributing-android-apps-to-qa-testers-with-ci-cd)
- [google-github-actions/auth](https://github.com/google-github-actions/auth)

## canary 실행

Actions → Firebase WIF Canary → Run workflow 에서 브랜치 `main`, `issue_numbers`(예: `#35`), `release_notes`(`;` 로 구분), `confirm_upload=true` 를 넣고 실행한다. `release-distribution` environment 의 보호 규칙이 있으면 승인 뒤 job 이 시작된다.

성공 판정:

- run summary 에 `Firebase WIF canary. result: PASS`, source SHA, artifact digest, attestation URL 이 남는다.
- Firebase Console → App Distribution 에 같은 SHA 의 릴리스가 생긴다. 업로드 step의 `SLOWCLOCK_DISTRIBUTION_UPLOAD_ONLY=true`로 Gradle의 기본 테스터 그룹을 비워 업로드까지만 검증한다. Gradle CLI는 빈 `--groups` 인자를 거절하므로 빈 CLI 인자를 쓰지 않는다. 테스트 설치나 테스터 알림에는 사용하지 않는다.
- 버전 이름은 `1.0-canary.<run>.<sha7>`이며 코드도 canary workflow의 run 번호다. 일반 배포와 번호 공간이 다르므로 canary를 일반 테스터에게 수동 배포하지 않는다. 일반 배포본은 `1.0-dist.<run>.<sha7>`로 구분한다.

실패하면 어느 단계에서 멈췄는지로 원인을 가른다.

| 단계 | 원인 |
|---|---|
| Verify required secrets | environment secret 누락, [ci-setup.md](ci-setup.md) 참조 |
| Authenticate to Google Cloud with WIF | pool/provider/서비스 계정 binding 누락, attribute condition 불일치 |
| Upload the already-built APK | 서비스 계정에 `roles/firebaseappdistro.admin` 없음, Firebase 앱 `com.ilhyok.slowclock` 미등록 |

canary 가 실패해도 production 워크플로(`release-distribution.yml`)는 같은 경로를 쓰므로 함께 실패한다. 별도 fallback 자격은 두지 않는다. 급하면 [로컬 fallback](distribution.md#로컬-fallback-ci-장애-시) 으로 배포한다.
