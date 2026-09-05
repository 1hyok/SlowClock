# SlowClock (느린 시계)

고령자 접근성을 우선한 일정·알람 Android 앱. 큰 글자와 큰 터치 영역, 보호자 공유 코드로 가족이 일정을 함께 본다.

## 🧱 스택

- Kotlin · Jetpack Compose · Material3
- Firebase: Firestore(주 저장소) · Auth · FCM · Analytics · Cloud Functions(`functions/`, Node 22)
- Hilt DI · Coroutines/Flow · Navigation 3
- Gradle 버전 카탈로그(`gradle/libs.versions.toml`)와 `build-logic` 컨벤션 플러그인. 숫자와 좌표는 그 두 곳이 정본이다.

## 📦 모듈 구조

```
:app                      진입점(Application·MainActivity)·navigation·auth
:core:model               엔티티(Schedule · User · FamilyGroup · Recommendation · Notification)
:core:common              util · constants · 오류 매핑
:core:ui                  테마 토큰 · 공용 컴포넌트 · MVI 베이스(Compose 를 api 로 노출)
:core:data                Repository(Firestore 진입점) · Hilt Firebase 모듈
:core:alarm               AlarmManager 예약 · 알림 · 알람 전체화면
:feature:main             메인 · 완료 · 타임라인 · 설정
:feature:addschedule      일정 추가·수정
:feature:recommendation   유형별 추천 일정
:feature:profile          프로필 · 계정 삭제
```

의존은 `:app` → `:feature` → `:core` 한 방향이다. 화면은 Repository 를 거쳐서만 데이터에 닿고, 프레젠테이션은 MVI 로 쓴다. 규약은 [docs/architecture.md](docs/architecture.md).

## 🚨 개발 환경 설정

### 1. google-services.json (Firebase 기본 설정)

- 용도: Firebase Auth, Firestore 등 기본 Firebase 서비스
- 위치: `app/google-services.json` (gitignore)
- 다운로드: https://console.firebase.google.com/project/slow-clock-scheduler/settings/general/android:com.ilhyok.slowclock

### 2. 서명 키 (release 빌드)

- 위치: 저장소 밖(예: `~/slowclock-release.jks`). `local.properties` 의 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 로 읽는다.
- 디버그 빌드에는 필요 없다. Play 업로드 키이므로 분실하지 않게 백업한다.
- v1 에는 외부 AI 추천과 캘린더 연동이 없어 서비스 계정 키도 필요 없다(#34).

### 3. 디버그 SHA-1 키 등록

Google 로그인에 쓰는 앱 인증이다. 아래 명령으로 SHA-1 을 뽑아 [Firebase 콘솔](https://console.firebase.google.com/project/slow-clock-scheduler/settings/general/android:com.ilhyok.slowclock)에 추가한다.

```bash
./gradlew signingReport
```

### 4. 빌드 · 검증

```bash
./gradlew ktlintCheck                    # 전 모듈 (자동 수정은 ktlintFormat)
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:validateScreenshotTest    # baseline 은 CI 컨테이너에서 만든다
```

Gradle 은 JDK 21 로 돈다(`gradle/gradle-daemon-jvm.properties`).

## 🌿 작업 규약

- main 에 직접 푸시하지 않는다. 이슈를 먼저 만들고 `feat/<이슈번호>` 브랜치에서 작업한 뒤 PR 로 머지한다.
- PR 본문은 저장소 템플릿(📌 Issues / 📎 Work Description / 📷 Screenshot / 💬 To Reviewers)을 따른다.

## ⚙️ CI/CD

GitHub Actions 파이프라인은 Afternote-FE 의 것을 1인 운영에 맞게 옮긴 것이다(리뷰·머지 순서 정책 제외).

- PR 검증 진입점은 `pr-validation.yml` 하나다. 변경 파일을 분류한 뒤 ktlint·Android Lint·단위 테스트·Compose screenshot 을 영향 모듈에만 돌린다. 시크릿 없이 stub 설정으로 돈다.
- `main` 머지 → Firebase App Distribution 자동 배포: [docs/release/distribution.md](docs/release/distribution.md)
- Google Play 내부 테스트 트랙(수동 실행): [docs/play-release.md](docs/play-release.md)
- screenshot baseline 은 CI 컨테이너에서 만든다(`screenshot-baseline` 라벨): [docs/testing/screenshot.md](docs/testing/screenshot.md)
- 외부 설정(GitHub environment·secret, GCP WIF, Firebase 앱, Pages, 브랜치 보호): [docs/release/ci-setup.md](docs/release/ci-setup.md)
- Firestore 데이터 경계와 보안 규칙 운용: [docs/firestore.md](docs/firestore.md)
- 워크플로 정책은 `.github/scripts/*.test.mjs` 에 고정돼 있다. 워크플로를 고치면 `node --test .github/scripts/*.test.mjs` 와 `scripts/repository-quality.sh` 를 먼저 돌린다.
