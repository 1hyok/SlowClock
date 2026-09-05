## 💩 주의사항

- 메인 브랜치에 직접 푸시 금지. PR로 코드 리뷰 받고 머지
- 각자 개발할 기능은 feature branch로 분리해서 작업 (feature/기능명)

## 🚨 개발 환경 설정

### 1. google-services.json (Firebase 기본 설정)

- **용도**: Firebase Auth, Firestore 등 기본 Firebase 서비스용
- **위치**: `app/google-services.json`
- **다운로드
  **: https://console.firebase.google.com/project/slow-clock-scheduler/settings/general/android:com.ilhyok.slowclock

### 2. 서명 키 (release 빌드)

- **위치**: 저장소 밖 (예: `~/slowclock-release.jks`), `local.properties` 의 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD` 로 읽음
- 디버그 빌드에는 필요 없음. Play 업로드 키이므로 분실하지 않도록 백업
- v1 에는 AI 추천(Vertex AI)·Google Calendar 연동이 없어 서비스 계정 키가 필요 없음 (#34)

### 3. 디버그 SHA-1 키 등록

- **용도**: Google 로그인 기능을 위한 앱 인증
- https://console.firebase.google.com/project/slow-clock-scheduler/settings/general/android:com.ilhyok.slowclock
  에서 본인의 디버그용 SHA-1 키 추가

```
  ./gradlew signingReport  # 맥/리눅스
  gradlew signingReport    # 윈도우
```

## 📦 패키지 구조

* `com.example.slowclock`
    * `auth`: Google OAuth 로그인
    * `data`: 데이터 모델 및 Firestore/API 연동
        * `model`: Schedule, User 등 데이터 클래스
        * `remote`: DB 접근 및 API 연동
    * `ui`: Jetpack Compose UI
        * `main`: 메인 화면
        * `addschedule`: 일정 추가/편집
        * `theme`: 접근성 테마
    * `navigation`: 화면 라우팅
    * `notification`: FCM 알림
    * `util`: 공통 유틸리티

## ⚙️ CI/CD

GitHub Actions 파이프라인은 Afternote-FE 의 것을 1인 운영에 맞게 옮긴 것이다(리뷰·머지 순서 정책 제외).

- PR 검증 진입점은 `pr-validation.yml` 하나다. 변경 파일을 분류한 뒤 ktlint·Android Lint·단위 테스트·Compose screenshot 을 영향 모듈에만 돌린다. 시크릿 없이 stub 설정으로 돈다.
- `main` 머지 → Firebase App Distribution 자동 배포: [docs/release/distribution.md](docs/release/distribution.md)
- Google Play 내부 테스트 트랙(수동 실행): [docs/play-release.md](docs/play-release.md)
- screenshot baseline 은 CI 컨테이너에서 만든다(`screenshot-baseline` 라벨): [docs/testing/screenshot.md](docs/testing/screenshot.md)
- 외부 설정(GitHub environment·secret, GCP WIF, Firebase 앱, Pages, 브랜치 보호): [docs/release/ci-setup.md](docs/release/ci-setup.md)
- 워크플로 정책은 `.github/scripts/*.test.mjs` 에 고정돼 있다. 워크플로를 고치면 `node --test .github/scripts/*.test.mjs` 와 `scripts/repository-quality.sh` 를 먼저 돌린다.
