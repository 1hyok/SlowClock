# 개발 환경 셋업

> 아래 키 파일은 모두 gitignore 대상이다. 커밋하지 않는다(secret scanning + push protection 이 차단한다).

## 1. google-services.json (Firebase 기본)
- 위치: `app/google-services.json`
- Firebase 콘솔 → `slow-clock-scheduler` → 프로젝트 설정 → Android 앱(`com.ilhyok.slowclock`) → `google-services.json` 다운로드

## 2. release 서명 키
- 위치: 저장소 밖(예: `~/slowclock-release.jks`). `local.properties` 에 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 네 키를 추가한다.
- 디버그 빌드에는 필요 없다. v1 에는 외부 AI 추천과 캘린더 연동이 없어 서비스 계정 키도 필요 없다(#34).

## 3. 디버그 SHA-1 등록 (Google 로그인)
```bash
./gradlew signingReport
```
출력된 SHA-1 을 Firebase 콘솔 Android 앱 설정에 추가한다.

## 4. JDK
Gradle 데몬은 JDK 21 로 고정돼 있다(`gradle/gradle-daemon-jvm.properties`). 로컬 기본 JDK 가 더 높아도 Gradle 이 21 을 내려받아 쓴다.

## 빌드 / 테스트 명령
```bash
./gradlew ktlintCheck                    # 전 모듈. ktlintFormat 으로 자동 수정
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest              # 전 모듈
./gradlew :app:lintDebug
./gradlew :app:validateScreenshotTest    # baseline 은 CI 컨테이너에서 만든다
```

스크린샷 baseline 은 로컬 macOS 렌더와 CI 리눅스 렌더가 달라 로컬에서 갱신하지 않는다. 의도한 시각 변경이면 PR 에 `screenshot-baseline` 라벨을 붙여 CI 가 갱신하게 한다(`docs/testing/screenshot.md`).

## 모듈 추가
모듈 공통 설정은 `build-logic` 컨벤션 플러그인이 준다. 새 모듈은 `settings.gradle.kts` 에 등록하고 빌드 파일에는 플러그인 alias(`slowclock.android.library` 계열)와 네임스페이스, 모듈 고유 의존만 적는다.

## CI 시크릿 (GitHub Actions)
- PR 검증은 시크릿 없이 stub 설정으로 돈다. 릴리스 경로의 시크릿·환경 목록은 `docs/release/distribution.md`·`docs/play-release.md` 를 본다.
