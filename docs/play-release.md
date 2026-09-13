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

2026-09-06 실제 Console을 다시 확인했다. 로그인 계정이 접근 가능한 개인 개발자 계정은 있으나, 신원 확인 진행 중이며 소유자 연락처 전화번호 인증이 남아 있다. 앱이 없고 「앱 만들기」가 비활성화되어 있으므로 SlowClock의 앱 생성·등록정보 저장·AAB 업로드·선언 제출은 아직 실행할 수 없다. 접근 가능한 개발자 계정이 SlowClock을 게시할 계정으로 확정된 것은 아니다. 계정 소유자의 확인이 끝난 뒤 사용할 게시 계정에서 이어간다.

등록정보·이미지·앱 콘텐츠 답안은 저장소에서 준비한다. 계정 생성 수수료를 결제하거나 소유자 본인 인증을 대신하지 않는다. 신규 개인 계정의 비공개 테스트 요건과 심사 일정은 실제 계정에 표시되는 안내 및 아래 공식 문서로 확인한다.

공식 근거: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)

## 출시 전 앱 상태 (#34 에서 정리)

- `targetSdk` 는 36, `compileSdk` 는 37 이다(#45, #37). Play 는 2026-08-31 부터 신규 앱과 업데이트에 Android 16(API 36) 타겟을 요구한다. 근거: [Google Play 의 타겟 API 수준 요구사항](https://developer.android.com/google/play/requirements/target-sdk). Android 16 동작 변경 중 이 앱에 닿는 것은 edge-to-edge 강제(이미 `enableEdgeToEdge` 사용)와 predictive back 기본 활성화뿐이다. 후자는 알람 전체화면 Activity 의 `onBackPressed` 재정의를 무력화하므로 #65 에서 `OnBackInvokedDispatcher` 로 옮긴다. compileSdk 37 은 androidx.core 1.19.0 의 요구(AAR 메타데이터 minCompileSdk=37)이며 런타임 동작은 targetSdk 가 정한다.
- `applicationId` 는 `com.ilhyok.slowclock`. Firebase Android 앱과 `google-services.json` 도 이 패키지로 등록돼 있다.
- Google 로그인 스코프는 `userinfo.profile`·`userinfo.email` 뿐이다. 민감 스코프(Calendar)를 빼서 OAuth 검증 심사 대상이 아니다.
- 서비스 계정 키를 APK 에 내장하던 Vertex AI 경로는 제거됐다.
- 개인정보처리방침·이용약관: `https://1hyok.me/SlowClock/privacy.html` · `https://1hyok.me/SlowClock/terms.html` (저장소 `docs/`, GitHub Pages). Play 콘솔 앱 콘텐츠의 개인정보처리방침 URL 에 같은 주소를 쓴다.
- 광고 ID 권한(`AD_ID`·`ACCESS_ADSERVICES_*`)은 manifest 에서 제거했다. 데이터 보안 양식에서 광고 ID 수집은 「아니오」다.
- 권한 선언 양식: `USE_EXACT_ALARM`(알람이 본업인 앱에 자동 부여, #122), `SCHEDULE_EXACT_ALARM`(`maxSdkVersion="32"` — API 32 기기 전용), `USE_FULL_SCREEN_INTENT`(알람 전체 화면), `RECEIVE_BOOT_COMPLETED`(재부팅 뒤 알람 복원, #127), `FOREGROUND_SERVICE_MEDIA_PLAYBACK`(알람 소리 재생). 포그라운드 서비스는 `AlarmTriggerService` 하나이고 유형은 `mediaPlayback` 이다 — `shortService` 는 시간 제한이 있고 배경 오디오의 문턱 아래라 알람 소리를 이어 가지 못해 #122 에서 바꿨다. 이 유형은 콘솔 선언 대상이다(아래 표의 「포그라운드 서비스」 행). 상시 알림을 띄우던 `dataSync` 서비스는 #47 에서 제거했다.
- release 빌드는 R8 과 리소스 축소를 켠다(`isMinifyEnabled = true`, `isShrinkResources = true`, #113). Firestore 가 이름으로 읽는 모델, kotlinx.serialization 이 만드는 serializer, Navigation 3 의 화면 키는 `app/proguard-rules.pro` 가 남긴다. Crashlytics 매핑 업로드도 함께 켜져 있어 난독화된 스택을 되돌릴 수 있다. [preflight 리포트](../.github/scripts/render-release-aab-report.mjs) 가 mapping 유무를 그대로 보고한다.
- 테스터에게 나가는 App Distribution 빌드는 `versionCode` 에 `github.run_number`, `versionName` 에 `1.0-dist.<run>.<sha7>` 을 쓴다(#139). Play 트랙과는 별개의 번호 공간이라 Play 의 단조 증가를 건드리지 않는다. Play 에 올리는 값은 `resolve-play-version-code.mjs` 가 정한다.

## 의존성 경보를 판정하는 법 (#161)

기본 브랜치에 Dependabot 경보가 수십 건 떠 있고 push 할 때마다 그 숫자가 찍힌다. 출시 직전에 보면 멈추게 되는 숫자라, 판정 방법을 여기 적어 둔다.

경보가 보고한 **실제 해결 버전과 실행 경로**를 함께 본다. 앱 `releaseRuntimeClasspath`에 없더라도 Cloud Functions 서버 런타임, 배포 플러그인, 빌드·테스트 도구에 취약 버전이 남을 수 있다. 앱에 포함되지 않는다는 근거로 전체 경보를 기각하지 않는다.

```bash
gh api repos/1hyok/SlowClock/dependabot/alerts --paginate \
  -q '.[] | select(.state=="open") | "\(.security_advisory.severity)\t\(.dependency.package.name)\t\(.security_vulnerability.vulnerable_version_range)"'

JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home \
  ./gradlew :app:buildEnvironment :app:dependencies --console=plain > /tmp/slowclock-app-dependencies.txt
# app:dependencies의 compile/runtime/UTP 구성별 해결 버전과 경보 범위를 대조한다.
# functions와 firestore-tests에서는 각각 npm ci 후 npm audit --json을 확인한다.
```

2026-09-06 확인(의존성 파일 기준 `3eea3ec`): 열린 54건(high 19 · moderate 29 · low 6)의 해당 취약 버전은 Android release runtime에서 확인되지 않았다. 다만 high 19건은 Netty·jose4j·JDOM의 빌드/테스트 경로에 남고, Cloud Functions의 `qs`·`uuid`는 서버 런타임에 남는다. Guava도 runtime `32.1.3-android`와 달리 compile 구성은 `31.1-android`이므로 경로별로 판정해야 한다. 이는 해당 경보 집합에 대한 확인이며 전체 산출물의 안전성을 보증하지 않는다.

PR #199에서 Functions의 `qs`를 `6.16.0`으로 제한 업데이트하고 Node 22의 실제 handler·Express 파싱 검증을 통과했다. 2026-09-06 후속 조회의 열린 경보는 52건(high 19 · moderate 27 · low 6)이었다. 이는 저장소 변경 후 경보 집계이며 운영 Functions 배포 완료를 뜻하지 않는다. `uuid`와 빌드·테스트 도구 경보는 남아 있다. `qs` 수정 근거: [공식 배열 제한 권고](https://github.com/ljharb/qs/security/advisories/GHSA-x5fp-wj9c-mxmx), [공식 isBuffer 권고](https://github.com/ljharb/qs/security/advisories/GHSA-4mjr-xmp4-gh2g).

수정은 상위 라이브러리의 지원 버전을 우선 확인한다. 전이 의존성을 강제로 바꿀 때는 해당 구성의 실제 실행 검증이 필요하다. 최신 상위 버전에도 남는 경보는 상위 수정 추적 대상으로 기록하며 무조건 dismiss하지 않는다.

`dependency-review`는 PR이 바꾸는 의존성만 본다. 기존 경보나 런타임 도달성은 이 검사만으로 판정할 수 없다. 주간 감사의 루트 build/debug runtime 보고만으로 앱 build classpath·compile·UTP·ktlint 구성 전체를 확인했다고 판단하지 않는다.

## 스토어 등록 정보와 앱 콘텐츠 선언 (#47)

스토어 등록 정보 문안과 이미지는 [`docs/play/listing.md`](play/listing.md), [`docs/play/ic_launcher_512.png`](play/ic_launcher_512.png), [`docs/play/feature_graphic_1024x500.png`](play/feature_graphic_1024x500.png) 에 있다. 주요 화면 네 장은 Compose 렌더로 준비돼 있다. 실제 알람·공유 화면의 추가 촬영에는 로그인된 기기가 필요하다.

콘솔 「정책 및 프로그램 → 앱 콘텐츠」 의 답안. 앱이 실제로 하는 일과 [`docs/privacy.html`](privacy.html) 에 맞춰 적었고, 코드가 바뀌면 이 표부터 고친다.

| 항목 | 답 | 근거 |
|---|---|---|
| 개인정보처리방침 URL | `https://1hyok.me/SlowClock/privacy.html` | GitHub Pages. github.io 주소는 이 도메인으로 리다이렉트된다 |
| 광고 | 광고 없음 | 광고 SDK 없음, `AD_ID` 권한 제거 |
| 앱 액세스 | 일부 기능 제한(로그인 필요). 안내문: "Google 계정으로 로그인하면 일정·알람·공유 코드 열람을 사용할 수 있습니다. 별도 가입 승인, 유료 구독이나 초대 제한은 없습니다. 공유를 확인하려면 한 계정의 내 정보에 표시되는 코드를 다른 계정의 공유 화면에 입력하세요." | Firebase UI Google 로그인만 제공. 현재 동작하지 않는 보호자 푸시까지 모든 기능 사용 가능 문구로 약속하지 않음 |
| 콘텐츠 등급 | 앱 유형 「유틸리티·생산성·커뮤니케이션·기타」. 폭력·성적 내용·욕설·약물·도박 전부 「아니오」. 사용자 간 상호작용 「예」(공유 코드를 받은 사람이 그 사람의 일정과 이름을 본다), 개인정보 공유 「예」(이름·일정을 본인이 코드를 알려 준 사람과 공유), 위치 공유 「아니오」, 디지털 구매 「아니오」 | 공유 코드 기능(정보 화면). 가족 그룹을 만드는 화면은 앱에 없다 — `familyGroups` 컬렉션은 계정 삭제 때 정리하는 용도로만 남아 있다 |
| 타겟층 | 18세 이상만 선택. 어린이 대상 아님 | 어르신·보호자용 앱 |
| 뉴스 앱 | 아니오 | 정보 탭은 메디컬타임즈로 이동하는 버튼 하나뿐이고 기사를 앱 안에 표시하지 않는다(#51) |
| 정부 앱 | 아니오 | |
| 금융 기능 | 없음 | |
| 건강 | 제출 답안: Stress Management, Relaxation, Mental Acuity / Mental and Behavioral Health / Medication and Treatment Management / Healthcare Services and Management | 명상·ADHD 감정 일기 및 회피 행동 돌아보기·약 먹기·병원 예약 일정 예시를 공식 범주에 대조한 판단. 의료기기·진단·처방·치료·측정·Health Connect 기능은 제공하지 않음. Console 제출 및 Google 심사 완료를 뜻하지 않음 |
| 데이터 보안: 수집 | 개인 정보(이름, 이메일 주소, 사용자 ID), 앱 활동(앱 상호작용, 기타 사용자 생성 콘텐츠: 일정·메모), 기기 또는 기타 ID(FCM 토큰, Firebase 설치 ID, Analytics 앱 인스턴스 ID, Crashlytics 설치 식별자), 위치(대략적인 위치: IP 기반), 앱 정보 및 성능(비정상 종료 로그, 진단) | Firebase Auth·Firestore·FCM·Analytics·Crashlytics |
| 데이터 보안: 공유 | 제3자 공유 없음. Firebase 는 서비스 제공업체. 공유 코드를 통한 열람은 사용자가 시작한 행동 | privacy.html 4절, [firestore.md](firestore.md) 의 컬렉션 표 |
| 데이터 보안: 처리 | 전송 중 암호화 「예」. 계정 삭제 요청 방법 「예」: 앱 안 내 정보 화면과 `https://1hyok.me/SlowClock/delete-account.html`(#46) | HTTPS, #46 |
| 데이터 보안: 목적 | 계정·일정·FCM 식별자는 「앱 기능」. 앱 상호작용·Analytics 식별자·IP 기반 대략적 위치는 「분석」. 비정상 종료 로그와 진단은 「앱 기능」과 「분석」 | Analytics 는 광고 ID 없이 사용. Crashlytics 는 릴리스 빌드에서만 켠다 |
| 광고 ID | 아니오 | manifest `tools:node="remove"` |
| 권한 선언 | `USE_EXACT_ALARM`·`SCHEDULE_EXACT_ALARM`(API 32 전용): 핵심 기능이 알람인 앱(정해진 시각에 일정 알람). `USE_FULL_SCREEN_INTENT`: 알람 전체 화면. `RECEIVE_BOOT_COMPLETED`: 재부팅 뒤 알람 복원 | manifest |
| 포그라운드 서비스 | `mediaPlayback` 1종(`FOREGROUND_SERVICE_MEDIA_PLAYBACK`). 용도: 「정해 둔 시각에 알람이 울리는 동안 알람 소리를 재생한다. 사용자가 끄거나 5분이 지나면 멈춘다.」 시연 영상에는 알람이 울리고 알림의 「알람 끄기」 로 멈추는 흐름을 담는다 | `app/src/main/AndroidManifest.xml` 의 `android:foregroundServiceType="mediaPlayback"`, `AlarmTriggerService` |
| 사진·동영상 권한 | 해당 없음 | 미디어 권한 없음 |

Analytics는 광고 ID 권한을 제거해도 앱 인스턴스 ID와 IP 기반 대략적 위치를 자동 수집한다. 선언 근거는 [Analytics 공식 안내](https://support.google.com/analytics/answer/11582702?hl=en)와 [Firebase SDK별 안내](https://firebase.google.com/docs/android/play-data-disclosure)다. 현재 앱에는 새 그룹 생성 기능이 없고 과거 그룹 기록은 계정 삭제 때 정리한다.

건강 선언은 데이터 수집 여부와 별개로 실제 제공 기능을 본다. [Play 공식 선언 안내](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en)를 `RecommendationScreen.kt`의 추천 내용에 대조해 위 답안을 정했다. 범주는 명상·인지 건강 안내, 정신 건강 지원, 복약 알림, 의료 예약 일정에 각각 대응한다. 앱은 의료 판단을 하지 않고 사용자가 정한 일정과 알람을 제공한다. 계정 확인이 끝나 Console 입력이 열리면 위 답안을 옮기고 추가 질문을 실제 기능에 맞게 작성한다. [건강 콘텐츠 정책](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en)에 따라 스토어 설명에는 의료기기가 아니며 질환을 진단·치료·치유·예방하지 않는다는 안내와 의료 전문가 상담 안내를 포함한다.

2026-09-06 연결된 `slow-clock-scheduler` Analytics 속성에서 이벤트 보관 2개월, 사용자 데이터 보관 14개월, 새 사용자 활동 시 보관 기간 재설정 켜짐을 확인했다. Google Signals는 꺼져 있고 Google Ads 연결은 0건이다. 광고 개인화는 전체 307개 지역 허용에서 0개 허용으로 변경·저장했으며 이후 수집 데이터에 적용된다. 기존 데이터에 소급되는 삭제 조치는 아니다. [광고 개인화 설정의 적용 범위](https://support.google.com/analytics/answer/9626162?hl=en).

Analytics 계정 데이터 공유는 Google 제품 및 서비스 꺼짐, 참여 모델링 및 비즈니스 통계·기술 지원·비즈니스 추천 켜짐을 확인했다. 계정 전체에 적용되는 값은 변경하지 않았다. 제출할 때 같은 속성과 계정의 최신 값을 다시 대조한다. 계정 삭제와 별도 설치 식별자의 보유 범위는 개인정보처리방침 및 계정 삭제 안내에 함께 설명한다.

### 스크린샷 촬영 절차

휴대전화 스크린샷은 2장 이상 8장 이하, 1080×2400(9:20) PNG 로 낸다. 로그인된 기기가 필요하다.

1. 에뮬레이터 `Pixel_7_Claude_QA`(1080×2400, Play Store 이미지)에 Google 계정으로 로그인한다.
2. `./gradlew :app:installDebug` 로 설치하고 앱을 열어 Google 로그인을 마친다.
3. 첫 실행에 정확한 알람 권한 안내가 한 번 뜬다(#83). 「설정 열기」 로 허용해 두면 다음 실행부터 나오지 않는다.
4. 오늘 날짜로 일정을 서너 개 만든다. 하나는 완료로 표시한다. 더미 데이터는 #59 에서 지웠기 때문에 직접 넣어야 화면이 채워진다.
5. 화면마다 `adb exec-out screencap -p > docs/play/screenshot-<n>.png` 로 찍는다. 순서: 메인(오늘 일정과 현재 할 일), 일정 추가, 타임라인, 완료, 알람 전체 화면.
6. 개인 정보(이메일·이름)가 보이는 화면은 테스트 계정으로 찍는다.

## AAB 빌드와 로컬 검증

`local.properties` 에 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 가 있어야 한다. 로컬 Gradle 은 JDK 21 로 실행한다.

```bash
JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home ./scripts/verify-play-release-bundle.sh
```

스크립트는 다음을 수행한다.

1. `:app:bundleRelease` 를 실행한다.
2. `app-release.aab` 의 필수 bundle 항목과 JAR 서명을 `jarsigner -verify -strict` 로 확인한다. 자가서명 인증서 경고(exit 4)만 허용하고, 같은 exit 4 를 공유하는 인증서 만료·TSA 만료·비활성 알고리즘은 진단 문구로 거부한다. unsigned entry 가 있으면 exit 20 으로 실패한다.
3. 외부 R8 mapping과 AAB 내장 mapping이 모두 있고 바이트가 일치하는지 확인한다. 누락·불일치면 실패한다.
4. AAB 와 서명 인증서의 SHA-256 을 출력한다.

이미 `bundleRelease` 를 실행한 뒤 산출물만 다시 확인하려면 `--skip-build` 를 붙인다.

산출물: `app/build/outputs/bundle/release/app-release.aab`. AAB 는 GitHub Actions artifact·이슈·PR 에 첨부하지 않는다. Play Console 의 비공개 릴리스에 직접 올린다.

PR마다 [`release-aab-preflight.yml`](../.github/workflows/release-aab-preflight.yml)이 임시 서명 키로 같은 검증을 돌린다. `lintRelease`와 release merged manifest의 권한 정책을 확인하고, SDK `dexdump`로 모델 클래스별 getter 정의가 남는지 검사한다. bundletool로 다운로드 크기를 재어 직전 성공 run과 비교한다. R8 mapping만으로 리소스 축소 실행을 입증할 수 없어 해당 항목은 보고서에서 미검증으로 표시한다. 배포 자격은 없다.

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
7. Google 로그인·일정 추가·알람·공유 코드 열람을 확인한 뒤 다음 트랙으로 승격한다.

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
