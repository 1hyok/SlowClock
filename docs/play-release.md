# Google Play release runbook

Firebase App Distribution 과 Google Play 의 목적을 분리하고, 첫 Play 출시에서 되돌릴 수 없는 서명 결정을 내리기 전에 확인할 기준을 정한다.

## 배포 채널

| 채널 | 목적 | 산출물 | 서명·보호 상태 |
|---|---|---|---|
| Firebase App Distribution | 출시 전 내부 확인 | release APK | upload key 로 직접 서명 |
| Google Play | 내부 테스트를 거쳐 production 배포 | release AAB | Play App Signing 등록 전 |

- Firebase APK 배포는 Google Play 출시 뒤에도 내부 QA 용도로만 사용한다 ([distribution.md](release/distribution.md)).
- AAB 는 기기에 직접 설치하는 파일이 아니다. Play 내부 테스트 트랙 또는 bundletool 로 생성한 APK 를 통해 검증한다.
- Play Console 등록·키 업로드·production 승격은 이 문서의 로컬 검증과 별개의 외부 상태다.

### Play Console 확인 상태

2026-09-05 기준 Play 개발자 계정이 없다. 2023-11-13 이후 만든 개인 계정은 테스터 12명이 14일 연속 옵트인한 비공개 테스트를 거쳐야 production 신청이 가능하고, 심사는 최대 7일이다. 계정 생성과 테스터 모집이 일정의 하한이므로 앱 작업과 무관하게 먼저 시작한다.

공식 근거: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)

## 출시 전 앱 상태 (#34 에서 정리)

- · 는 36 이다(#45). Play 는 2026-08-31 부터 신규 앱과 업데이트에 Android 16(API 36) 타겟을 요구한다. 근거: [Google Play 의 타겟 API 수준 요구사항](https://developer.android.com/google/play/requirements/target-sdk). Android 16 동작 변경 중 이 앱에 닿는 것은 edge-to-edge 강제(이미  사용)와 predictive back 기본 활성화(Navigation Compose 사용,  미사용)뿐이다.
- `applicationId` 는 `com.ilhyok.slowclock`. Firebase Android 앱과 `google-services.json` 도 이 패키지로 등록돼 있다.
- Google 로그인 스코프는 `userinfo.profile`·`userinfo.email` 뿐이다. 민감 스코프(Calendar)를 빼서 OAuth 검증 심사 대상이 아니다.
- 서비스 계정 키를 APK 에 내장하던 Vertex AI 경로는 제거됐다.
- 개인정보처리방침·이용약관: `https://1hyok.github.io/SlowClock/privacy.html` · `https://1hyok.github.io/SlowClock/terms.html` (저장소 `docs/`, GitHub Pages). Play 콘솔 앱 콘텐츠의 개인정보처리방침 URL 에 같은 주소를 쓴다.
- 광고 ID 권한(`AD_ID`·`ACCESS_ADSERVICES_*`)은 manifest 에서 제거했다. 데이터 보안 양식에서 광고 ID 수집은 「아니오」다.
- 권한 선언 양식: `SCHEDULE_EXACT_ALARM`(알람 앱 핵심 기능), `USE_FULL_SCREEN_INTENT`(알람 전체 화면), 포그라운드 서비스 유형 `dataSync`·`shortService`.
- release 빌드는 R8 을 켜지 않는다(`isMinifyEnabled = false`). Firestore 리플렉션 매핑에 keep 규칙이 없고 난독화 산출물을 기기에서 검증하지 않았기 때문이다. 켜는 작업은 별도 이슈로 다루고, 그때 [preflight 리포트](../.github/scripts/render-release-aab-report.mjs) 의 mapping 단언을 되돌린다.

## AAB 빌드와 로컬 검증

`local.properties` 에 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 가 있어야 한다. 로컬 Gradle 은 JDK 21 로 실행한다.

```bash
JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home ./scripts/verify-play-release-bundle.sh
```

스크립트는 다음을 수행한다.

1. `:app:bundleRelease` 를 실행한다.
2. `app-release.aab` 의 필수 bundle 항목과 JAR 서명을 `jarsigner -verify -strict` 로 확인한다. 자가서명 인증서 경고(exit 4)만 허용하고, 같은 exit 4 를 공유하는 인증서 만료·TSA 만료·비활성 알고리즘은 진단 문구로 거부한다. unsigned entry 가 있으면 exit 20 으로 실패한다.
3. R8 mapping 이 있으면 경로를, 없으면 「없음 (isMinifyEnabled=false)」를 보고한다.
4. AAB 와 서명 인증서의 SHA-256 을 출력한다.

이미 `bundleRelease` 를 실행한 뒤 산출물만 다시 확인하려면 `--skip-build` 를 붙인다.

산출물: `app/build/outputs/bundle/release/app-release.aab`. AAB 는 GitHub Actions artifact·이슈·PR 에 첨부하지 않는다. Play Console 의 비공개 릴리스에 직접 올린다.

PR 마다 [`release-aab-preflight.yml`](../.github/workflows/release-aab-preflight.yml) 이 임시 서명 키로 같은 검증을 돌리고 bundletool 로 다운로드 크기를 재어 직전 성공 run 과 비교한다. 배포 자격은 없다.

공식 근거: [명령줄에서 App Bundle 빌드](https://developer.android.com/build/building-cmdline#build_bundle)

## Play App Signing 키 결정

Play App Signing 은 설치되는 APK 에 사용하는 app signing key 와 Play 에 제출하는 AAB 에 사용하는 upload key 를 분리한다.

| 키 | 보관 주체 | 용도 | 분실·노출 시 처리 |
|---|---|---|---|
| app signing key | Google Play | 사용자 기기에 배포할 APK 서명 | Play 의 key upgrade 절차 사용 |
| upload key | 개발자·CI (`~/slowclock-release.jks`, alias `slowclock-upload`) | Play Console 에 올릴 AAB 서명 | Play Console 에서 reset 요청 가능 |

첫 Play 등록 화면에서 Google 이 app signing key 를 생성하는 기본안을 쓴다. 현재 keystore 는 upload key 가 된다. 등록 뒤에는 app signing key 사본을 다시 내려받을 수 없다.

- Play 가 발급한 app signing certificate 의 SHA-1 을 Firebase Android 앱(`com.ilhyok.slowclock`)에 추가해야 Play 설치본에서 Google 로그인이 된다. `firebase apps:android:sha:create 1:887622067286:android:9a909124166f54123e87f7 <SHA1>` 뒤 `google-services.json` 을 다시 받아 environment secret 을 갱신한다.
- Firebase APK(upload key 서명)와 Play APK(app signing key 서명)는 인증서가 달라 서로 위에 업데이트되지 않는다. 테스터가 채널을 옮길 때는 삭제 후 재설치한다.

공식 근거: [Play App Signing](https://developer.android.com/studio/publish/app-signing#app-signing-google-play)

## 첫 내부 테스트 릴리스

1. `versionCode` 가 Play 에 올린 모든 이전 산출물보다 큰지 확인한다. 첫 수동 업로드는 `SLOWCLOCK_VERSION_CODE` 없이 빌드해 `1` 을 쓴다. 워크플로가 만드는 첫 값은 `101` 이라 단조 증가를 자동으로 만족한다.
2. AAB 검증 스크립트의 AAB SHA-256·서명 인증서 SHA-256 을 릴리스 기록에 남긴다.
3. Play Console 에서 내부 테스트 트랙을 만들고 Play App Signing 방식을 확정한다.
4. AAB 를 업로드한다.
5. Play 의 app signing certificate SHA-1 을 Firebase Android 앱에 등록한다(위 절).
6. Play 링크로 신규 설치와 업데이트를 확인한다.
7. Google 로그인·일정 추가·알람·가족 그룹 공유를 확인한 뒤 다음 트랙으로 승격한다.

서명 key·keystore·비밀번호·서비스 계정 JSON 은 저장소나 문서에 넣지 않는다.

## 자동화: Play 내부 테스트 트랙 배포

[`release-play-internal.yml`](../.github/workflows/release-play-internal.yml) 은 `main` 에서 수동 실행하고 `play-internal` environment 승인을 받아야 움직인다. production·open·closed track 승격 step 은 없다. 승격은 Play Console 에서 사람이 한다.

### 사전 준비

#### 1. Play Console 앱 등록 (`play.google.com/console`)

1. 개발자 계정 생성(1회 등록비).
2. 모든 앱 → 앱 만들기: 앱 이름 `느린 시계`, 기본 언어 한국어, 유형 `앱`, 무료.
3. 정책 및 프로그램 → 앱 콘텐츠의 필수 선언(개인정보처리방침 URL, 광고, 콘텐츠 등급, 타겟층, 데이터 보안, 권한 선언)을 모두 채운다.
4. 테스트 및 출시 → 테스트 → 내부 테스트 → 테스터에서 테스터 이메일 목록을 만든다.

#### 2. Play Console 첫 AAB 수동 업로드

Android Publisher API 는 Console 에서 최소 한 번 수동 업로드된 앱에만 업로드를 허용한다. 첫 AAB 는 `./scripts/verify-play-release-bundle.sh` 로 만든 산출물을 직접 올린다(위 「첫 내부 테스트 릴리스」).

#### 3. Google Cloud 서비스 계정

`play-internal-publisher@slow-clock-scheduler.iam.gserviceaccount.com` 과 WIF binding 은 [ci-setup.md](release/ci-setup.md) 의 절차로 만든다. 프로젝트 IAM 역할은 주지 않는다. `androidpublisher.googleapis.com` 은 활성화돼 있다.

#### 4. Play Console 서비스 계정에 최소 권한 부여

설정 → API 액세스에서 Google Cloud 프로젝트 `slow-clock-scheduler` 를 연결한 뒤, 사용자 및 권한 → 사용자 초대로 서비스 계정 이메일을 추가한다.

- 앱 범위: `느린 시계` 하나만 선택한다.
- 체크할 권한: 앱 정보 보기, 테스트 트랙에 출시.
- 해제할 권한: 프로덕션 트랙에 출시, 재무·주문 관리, 사용자 관리.

#### 5. GitHub environment 와 자격

`play-internal` environment 의 secret·variable 은 [ci-setup.md](release/ci-setup.md) 3절에 있다. Deployment branches 는 `main` 만 허용한다.

### 실행

Actions → Release Play Internal Track → Run workflow 에서 브랜치 `main` 을 선택해 실행한다.

1. `main` 이 아니면 거부하고, 필요한 secret·variable 이 비면 이름을 출력하고 멈춘다.
2. WIF 로 조회용 단기 토큰을 받아 Play 의 현재 최대 versionCode 를 읽는다(edit 는 commit 없이 되돌린다).
3. 단조 증가 versionCode 를 확정한다(`run_number × 100 + run_attempt`). 중복이면 여기서 끝난다. AAB 를 만들지 않는다.
4. 확정된 versionCode 로 signed AAB 를 빌드하고 서명·필수 항목을 검증한다.
5. AAB 에 SLSA provenance 를 붙이고 이 워크플로·이 commit 으로 검증한다.
6. 업로드 직전에 토큰을 새로 받고 digest 를 다시 확인한 뒤, edit 생성 → bundle 업로드 → internal 트랙 갱신 → commit 순으로 게시한다.
7. run summary 에 track·versionCode·source SHA·AAB digest·attestation·Play edit id 를 남긴다.

### 실패 모드

| 상황 | 어디서 멈추는가 |
|---|---|
| Play Console·서비스 계정 준비 전 | 첫 스텝. 누락된 secret·variable 이름을 출력한다 |
| `main` 이외의 ref | keystore 를 풀기 전 |
| versionCode 중복·역행 | 빌드 전 |
| 잘못 서명된 AAB | `scripts/verify-play-release-bundle.sh` |
| 권한 부족·API commit 실패 | 업로드 스텝. 미완료 edit 를 삭제한 뒤 원인을 그대로 올린다 |

어느 단계에서 실패하든 열린 edit 는 정리되고, AAB·keystore 는 러너에서 삭제된다. Actions artifact 로는 게시되지 않는다.

### 롤백

Play 는 이미 게시된 versionCode 를 되돌리지 않는다.

1. Play Console 내부 테스트 → 출시 관리에서 문제 릴리스를 중단(halt)한다.
2. 수정본은 더 큰 versionCode 로 다시 배포한다. 워크플로를 다시 실행하면 값이 자동으로 올라간다.
3. Firebase App Distribution 은 별개 채널이라 영향을 받지 않는다.

## Play Integrity API 경계

Automatic integrity protection 은 Play App Signing 이 선행 조건이고 일부 파트너에게만 제공된다. Play Integrity API 클라이언트 의존성이나 토큰 요청 코드는 추가하지 않는다. 서버 검증 계약이 생기면 별도 이슈에서 다룬다.

공식 근거: [Play Integrity API 개요](https://developer.android.com/google/play/integrity/overview)
