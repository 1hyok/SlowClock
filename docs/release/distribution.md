# 테스터 APK 배포 (Firebase App Distribution)

`main` 에 머지된 빌드를 Firebase App Distribution 으로 테스터에게 자동 전달하는 흐름. Firebase 프로젝트 `slow-clock-scheduler`, 테스터 그룹 `slowclock`.

이 배포는 Google Play 출시가 아니다. Play 내부 테스트 트랙 배포는 [Play release runbook](../play-release.md) 을 따른다.

## 배포 경로

| 채널 | 트리거 | 워크플로 | 산출물 | 자격 |
|---|---|---|---|---|
| Firebase App Distribution | `main` push | [`release-distribution.yml`](../../.github/workflows/release-distribution.yml) | release APK | `release-distribution` environment |
| WIF canary | 수동 실행 + environment 승인 | [`firebase-wif-canary.yml`](../../.github/workflows/firebase-wif-canary.yml) | release APK | `release-distribution` environment |

`feat/<N>` → `main` 단일 흐름이라 main 에 머지된 PR 하나가 곧 릴리스 후보다. 별도 릴리스 브랜치·릴리스 PR 은 없다.

## 릴리스 노트: 머지 PR 본문에서 만든다

워크플로는 main push 에 연결된 머지 PR 의 본문에서 두 섹션을 읽는다 (`.github/PULL_REQUEST_TEMPLATE.md`).

```markdown
## 📌 Issues
- closed #34

## 📎 Work Description
- Google 로그인 스코프에서 캘린더를 뺐다
- 알람이 잠금 화면에서도 뜬다
```

- `📌 Issues` 에서 `#N` 형식의 번호를, `📎 Work Description` 에서 글머리표 줄을 모은다.
- 둘 중 하나라도 비어 있으면 [`render-distribution-release-notes.sh`](../../.github/scripts/render-distribution-release-notes.sh) 가 Firebase 업로드 전에 실패한다. 테스터가 무엇을 확인할지 모르는 배포는 내보내지 않는다.
- `💬 To Reviewers` 는 릴리스 노트에 들어가지 않는다.

## 자격과 시크릿

PR 검증(lint·unit-test·screenshot)은 시크릿 없이 [`setup-ci-config`](../../.github/actions/setup-ci-config/action.yml) 가 만드는 stub 설정으로 돈다. 실서비스 값은 보호된 environment 의 secret 으로만 존재한다.

| 이름 | 위치 | 값 |
|---|---|---|
| `GOOGLE_SERVICES_JSON_B64` | `release-distribution` → Environment secrets | Firebase 콘솔의 `google-services.json`(`com.ilhyok.slowclock`) base64 |
| `RELEASE_STORE_FILE_B64` | `release-distribution` → Environment secrets | upload keystore(`~/slowclock-release.jks`) base64 |
| `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` · `RELEASE_KEY_PASSWORD` | `release-distribution` → Environment secrets | keystore 비밀번호 · alias(`slowclock-upload`) · key 비밀번호 |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `release-distribution` → Environment secrets | `projects/887622067286/locations/global/workloadIdentityPools/github-actions/providers/github` |
| `GCP_FIREBASE_SERVICE_ACCOUNT` | `release-distribution` → Environment secrets | `github-firebase-distribution@slow-clock-scheduler.iam.gserviceaccount.com` |

- Deployment branches 는 `main` 만 허용한다.
- 장기 서비스 계정 JSON 키는 어디에도 두지 않는다. 업로드 직전에 Workload Identity Federation 으로 단기 자격을 받는다 ([WIF runbook](firebase-wif-canary.md)).
- base64 인코딩: `base64 -i ~/slowclock-release.jks | tr -d '\n' | pbcopy` (macOS)
- 시크릿 등록 절차 전체는 [CI 설정 runbook](ci-setup.md).

## 배포 provenance: 이 APK 가 어느 commit·run 에서 나왔는지

배포 워크플로는 signing이 끝난 APK를 SDK `apksigner verify --verbose --print-certs`로 검증한다. 이 검증을 통과한 APK 하나를 subject로 [GitHub artifact attestation](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) 을 발급하고, 업로드 전에 스스로 검증한다. 서명·저장소·signer workflow·source commit·GitHub-hosted 러너 중 하나라도 어긋나거나 attestation subject digest 가 빌드 직후 digest 와 다르면 Firebase 업로드까지 가지 않는다.

성공한 run 의 summary 에 남는 값은 넷이다: source commit SHA, `sha256:` artifact digest, attestation URL, run URL. APK·AAB 자체는 public Actions artifact 로 게시하지 않는다.

받은 APK 가 정말 그 배포 경로에서 나왔는지는 손에 든 파일로 직접 확인할 수 있다.

```bash
gh attestation verify ~/Downloads/app-release.apk --repo 1hyok/SlowClock --signer-workflow 1hyok/SlowClock/.github/workflows/release-distribution.yml --source-ref refs/heads/main --deny-self-hosted-runners
```

> 아래 로컬 fallback 으로 올린 빌드에는 attestation 이 없다. 그 경로로 배포한 APK 는 위 명령이 실패하는 게 정상이며, 그래서 CI 장애 때만 쓴다.

## 로컬 fallback (CI 장애 시)

1. `local.properties` 에 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 4키가 있어야 한다. 새 keystore 를 임의로 만들지 않는다. 다른 키로 서명하면 기존 설치 위에 업데이트되지 않는다.
2. `app/google-services.json` 은 Firebase 콘솔 → 프로젝트 설정 → Android 앱 `com.ilhyok.slowclock` 에서 받은 파일이어야 한다.
3. `firebase login` 이 돼 있어야 한다.

```bash
git fetch origin main
git switch --detach origin/main
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)"
test -z "$(git status --porcelain)"

EVENT_NAME=workflow_dispatch \
ISSUE_NUMBERS="#34" \
RELEASE_NOTES="캘린더 스코프 제거 확인;잠금 화면 알람 확인" \
SOURCE_REF=main \
SOURCE_SHA="$(git rev-parse origin/main)" \
bash .github/scripts/render-distribution-release-notes.sh /tmp/slowclock-release-notes.txt

JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home \
./gradlew assembleRelease appDistributionUploadRelease \
  --releaseNotesFile=/tmp/slowclock-release-notes.txt \
  --no-daemon
```

> 로컬·Firebase 빌드는 `versionCode` 1 을 유지한다. Firebase App Distribution 은 versionCode 로 빌드를 구분하지 않는다. Play 용 값은 `SLOWCLOCK_VERSION_CODE` 환경변수로만 주입되며 [`release-play-internal.yml`](../../.github/workflows/release-play-internal.yml) 이 run 마다 단조 증가하는 값을 만든다.

## Play 내부 테스트 트랙과의 경계

| | Firebase App Distribution | Play 내부 테스트 트랙 |
|---|---|---|
| 목적 | 출시 전 내부 확인 | Play 설치 경로 검증 → production 승격 |
| 산출물 | release APK | release AAB |
| 트리거 | `main` push 자동 | `main` 에서 수동 실행 + environment 승인 |
| versionCode | `1` 고정 | run 마다 단조 증가 |
| 자격 | `release-distribution` environment | `play-internal` environment (별도 서비스 계정) |
| 롤백 | 이전 빌드를 다시 배포 | 릴리스 중단 후 더 큰 versionCode 로 재배포 |

두 채널의 설치 인증서가 다르면(Play App Signing 이 앱 서명 키를 새로 만들 때) 앱이 서로 위에 업데이트되지 않는다. 테스터가 채널을 옮길 때는 삭제 후 재설치가 필요하다.

## 테스터 관리

- 추가/제거: Firebase Console → App Distribution → 테스터 및 그룹 → `slowclock` 그룹 편집. CLI: `firebase appdistribution:testers:add <email> --group-alias slowclock --project slow-clock-scheduler`
- 신규 테스터는 첫 초대 이메일에서 App Tester 앱 설치 안내를 받는다 → 이후 빌드는 자동 알림
