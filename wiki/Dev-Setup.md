# 개발 환경 셋업

> ⚠ 아래 키 파일들은 모두 **gitignore** 대상 — 절대 커밋 금지. (secret scanning + push protection이 차단함)

## 1. google-services.json (Firebase 기본)
- **위치:** `app/google-services.json`
- Firebase 콘솔 → `slow-clock-scheduler` → 프로젝트 설정 → Android 앱(`com.example.slowclock`) → `google-services.json` 다운로드

## 2. service_account (Vertex AI 전용)
- **위치:** `core/data/src/main/res/raw/service_account` *(멀티모듈화로 `:app` → `:core:data`로 이동됨)*
- Firebase 콘솔 → 서비스 계정 → 새 비공개 키 생성 → 다운로드 파일명을 `service_account`로

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
./gradlew ktlintCheck            # ktlintFormat 으로 자동수정
./gradlew :app:validateScreenshotTest   # baseline 갱신: updateScreenshotTest
```

## CI 시크릿 (GitHub Actions)
- `GOOGLE_SERVICES_JSON_B64` — lint/unit-test/screenshot/release 공통
- release 배포 시: `RELEASE_STORE_FILE_B64` · `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` · `RELEASE_KEY_PASSWORD` · `FIREBASE_SERVICE_ACCOUNT_JSON`
