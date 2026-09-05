# CI/CD 설정 runbook: GitHub · Google Cloud · Firebase

워크플로가 참조하는 외부 설정의 정본. 워크플로 파일만으로는 만들 수 없는 것(environment, secret, WIF, Firebase 앱 등록, 브랜치 보호)을 여기서 관리한다. 값은 저장소에 넣지 않는다. 이름과 만드는 방법만 적는다.

## 상태 (2026-09-05, PR #39 머지 뒤 갱신)

| 항목 | 상태 |
|---|---|
| Firebase Android 앱 `com.ilhyok.slowclock` 등록 + debug/upload SHA-1 | 완료 (App ID `1:887622067286:android:9a909124166f54123e87f7`) |
| Firebase App Distribution 그룹 `slowclock` + 테스터 | 완료 |
| Google Cloud API 활성화 (`sts`, `androidpublisher`, `iamcredentials`, `firebaseappdistribution`) | 완료 |
| GitHub environment `release-distribution`, `play-internal` (deployment branch `main`) | 완료 |
| WIF pool `github-actions` / provider `github` | 완료 (`…/workloadIdentityPools/github-actions/providers/github`, ACTIVE) |
| 서비스 계정 2종 + IAM binding | 완료 (`github-firebase-distribution`: firebaseappdistro.admin, `play-internal-publisher`: 역할 없음) |
| environment secret·variable 등록 | 완료 (두 environment 에 7개 secret, `play-internal` 에 `PLAY_PACKAGE_NAME`) |
| GitHub Pages(`docs/`) 활성화 | 완료. 사용자 사이트에 커스텀 도메인이 있어 `https://1hyok.me/SlowClock/` 로 서빙되고 `1hyok.github.io/SlowClock/` 은 그리로 리다이렉트된다 |
| main 브랜치 보호 required checks 교체 | 완료 (5개) |
| Play Console 개발자 계정·앱 등록·서비스 계정 초대 | 미완, [play-release.md](../play-release.md) 참조. 이것만 사람이 해야 한다 |

## 1. Workload Identity Federation

```bash
P=slow-clock-scheduler
gcloud iam workload-identity-pools create github-actions --location=global --display-name="GitHub Actions" --project "$P"
gcloud iam workload-identity-pools providers create-oidc github \
  --location=global --workload-identity-pool=github-actions --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref,attribute.environment=assertion.environment" \
  --attribute-condition="assertion.repository == '1hyok/SlowClock' && assertion.repository_owner == '1hyok'" \
  --project "$P"
gcloud iam workload-identity-pools providers describe github --location=global --workload-identity-pool=github-actions --project "$P" --format="value(name)"
```

마지막 명령의 출력(`projects/887622067286/locations/global/workloadIdentityPools/github-actions/providers/github`)이 secret `GCP_WORKLOAD_IDENTITY_PROVIDER` 값이다.

## 2. 서비스 계정과 IAM

```bash
P=slow-clock-scheduler; PN=887622067286
MEMBER="principalSet://iam.googleapis.com/projects/$PN/locations/global/workloadIdentityPools/github-actions/attribute.repository/1hyok/SlowClock"

gcloud iam service-accounts create github-firebase-distribution --display-name="GitHub Actions Firebase App Distribution" --project "$P"
gcloud iam service-accounts create play-internal-publisher --display-name="GitHub Actions Play internal publisher" --project "$P"

gcloud iam service-accounts add-iam-policy-binding "github-firebase-distribution@$P.iam.gserviceaccount.com" --project "$P" --role=roles/iam.workloadIdentityUser --member="$MEMBER"
gcloud iam service-accounts add-iam-policy-binding "play-internal-publisher@$P.iam.gserviceaccount.com" --project "$P" --role=roles/iam.workloadIdentityUser --member="$MEMBER"

# Firebase 배포 계정만 프로젝트 역할을 받는다. Play 계정의 권한은 Play Console 에서 준다.
gcloud projects add-iam-policy-binding "$P" --member="serviceAccount:github-firebase-distribution@$P.iam.gserviceaccount.com" --role=roles/firebaseappdistro.admin --condition=None
```

## 3. GitHub environment secret · variable

`release-distribution` 과 `play-internal` 두 environment 에 같은 서명·Firebase 설정을 넣고, 서비스 계정만 갈라 넣는다.

```bash
cd /path/to/SlowClock
PW='<keystore 비밀번호 — local.properties 의 RELEASE_STORE_PASSWORD>'
for e in release-distribution play-internal; do
  base64 -i app/google-services.json | tr -d '\n' | gh secret set GOOGLE_SERVICES_JSON_B64 --env "$e"
  base64 -i ~/slowclock-release.jks   | tr -d '\n' | gh secret set RELEASE_STORE_FILE_B64  --env "$e"
  printf '%s' "$PW"              | gh secret set RELEASE_STORE_PASSWORD --env "$e"
  printf '%s' slowclock-upload   | gh secret set RELEASE_KEY_ALIAS      --env "$e"
  printf '%s' "$PW"              | gh secret set RELEASE_KEY_PASSWORD   --env "$e"
  printf '%s' projects/887622067286/locations/global/workloadIdentityPools/github-actions/providers/github | gh secret set GCP_WORKLOAD_IDENTITY_PROVIDER --env "$e"
done
printf '%s' github-firebase-distribution@slow-clock-scheduler.iam.gserviceaccount.com | gh secret set GCP_FIREBASE_SERVICE_ACCOUNT --env release-distribution
printf '%s' play-internal-publisher@slow-clock-scheduler.iam.gserviceaccount.com       | gh secret set GCP_PLAY_SERVICE_ACCOUNT --env play-internal
gh variable set PLAY_PACKAGE_NAME --env play-internal --body com.ilhyok.slowclock
```

- `app/google-services.json` 은 Firebase 콘솔 → 프로젝트 설정 → Android 앱 `com.ilhyok.slowclock` 에서 받은 실파일이어야 한다 (`firebase apps:sdkconfig ANDROID 1:887622067286:android:9a909124166f54123e87f7 --project slow-clock-scheduler -o app/google-services.json`).
- 저장소 수준 secret `GOOGLE_SERVICES_JSON_B64` 는 예전 워크플로의 잔재라 삭제했다. 새 워크플로는 PR 검증에 시크릿을 쓰지 않는다.
- keystore 와 비밀번호는 1Password 에 백업한다. upload key 를 잃으면 Play Console 에서 reset 을 요청해야 한다.

## 4. GitHub Pages (약관·개인정보처리방침)

`docs/` 의 `privacy.html`·`terms.html` 을 `https://1hyok.github.io/SlowClock/` 로 게시한다. 앱(`AuthManager`)과 Play 콘솔의 개인정보처리방침 URL 이 이 주소를 쓴다.

```bash
gh api -X POST repos/1hyok/SlowClock/pages -f build_type=legacy -f 'source[branch]=main' -f 'source[path]=/docs'
```

`docs/.nojekyll` 이 있어 Markdown 문서는 변환되지 않고 HTML 만 그대로 서빙된다.

## 5. main 브랜치 보호: required status checks

새 워크플로의 check 이름은 `<caller job name> / <reusable job name>` 이다. 기존 이름 3개를 아래 5개로 교체한다 (`pr-gate-policy.test.mjs` 의 `REQUIRED_VALIDATION_CONTEXTS` 와 같아야 한다).

```bash
gh api -X PATCH repos/1hyok/SlowClock/branches/main/protection/required_status_checks \
  -F strict=false \
  -f 'contexts[]=Repository Quality / Repository Quality' \
  -f 'contexts[]=Static Analysis / Check Code Quality (Ktlint)' \
  -f 'contexts[]=Static Analysis / Check Project Issues (Android Lint)' \
  -f 'contexts[]=Unit Test / Run Unit Tests' \
  -f 'contexts[]=Screenshot / Validate Compose Preview Screenshots'
```

CodeQL(`Analyze (java-kotlin)` 등)은 required 로 두지 않는다. 분석 시간이 길고 결과는 Security 탭에서 본다. 저장소의 CodeQL default setup 은 꺼 두었다. 켜면 워크플로(advanced) 의 SARIF 업로드를 거부한다.

## 6. 라벨

- `screenshot-baseline`: baseline 생성 요청 ([screenshot.md](../testing/screenshot.md)). `gh label create screenshot-baseline --color 1D76DB --description "CI 컨테이너에서 screenshot baseline 생성"`
- `issue-assignee-exempt`: PR 작성자가 대표 이슈 담당자가 아닐 때 담당자 대조 면제. 1인 운영에서는 쓸 일이 없다.
- Dependabot 은 `Task` 라벨을 붙인다.
