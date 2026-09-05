# 개발 환경 셋업

> ⚠ 아래 키 파일들은 모두 **gitignore** 대상 — 절대 커밋 금지. (secret scanning + push protection이 차단함)

## 1. google-services.json (Firebase 기본)
- **위치:** `app/google-services.json`
- Firebase 콘솔 → `slow-clock-scheduler` → 프로젝트 설정 → Android 앱(`com.ilhyok.slowclock`) → `google-services.json` 다운로드

## 2. release 서명 키
- **위치:** 저장소 밖 (예: `~/slowclock-release.jks`). `local.properties` 에 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 4키 추가
- 디버그 빌드에는 불필요. v1 에는 Vertex AI·Calendar 연동이 없어 서비스 계정 키도 불필요 (#34)

## 3. 디버그 SHA-1 등록 (Google 로그인)
```bash
./gradlew signingReport   # 디버그 SHA-1 확인
```
출력된 SHA-1을 Firebase 콘솔 Android 앱 설정에 추가.

## 빌드 / 테스트 명령
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew ktlintCheck            # 전 모듈. ktlintFormat 으로 자동수정
./gradlew :app:validateScreenshotTest   # baseline 갱신: updateScreenshotTest
```

## CI 시크릿 (GitHub Actions)
- PR 검증은 시크릿 없이 stub 설정으로 돈다. 릴리스 경로의 시크릿·환경 목록은 `docs/release/distribution.md`·`docs/play-release.md` 참조.
