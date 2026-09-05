# Compose Preview Screenshot Testing (docker baseline)

`Compose Preview Screenshot Testing` 은 anti-aliasing·font hinting·scale 등 host 환경에 따라 픽셀이 달라져, CI 가 새로 그린 PNG 로 baseline 을 교체하는 ping-pong 이 생기기 쉽다. 이 저장소는 `Dockerfile.screenshot` 을 공통 환경으로 쓰고 역할을 세 워크플로로 나눈다.

- [Compose Preview Screenshot Test](../../.github/workflows/screenshot.yml): PR 의 기존 baseline 검증 (required check)
- [Generate Screenshot Baselines](../../.github/workflows/screenshot-baseline-generate.yml): `screenshot-baseline` 라벨이 붙은 PR 의 정확한 head 에서 baseline 생성·재검증 후 artifact 발행
- [Apply Screenshot Baselines](../../.github/workflows/screenshot-baseline-apply.yml): artifact 와 head 를 다시 검증한 뒤 허용된 PNG 만 PR 브랜치에 커밋

현재 screenshot 테스트가 있는 모듈은 `:app` 하나다 (`app/src/screenshotTest/`). baseline 은 `app/src/screenshotTestDebug/reference/` 에 커밋된다. 다른 모듈에 `src/screenshotTest/` 를 추가하면 [영향 계산기](../../.github/scripts/resolve-pr-impact.mjs) 가 자동으로 잡고, [적용 워크플로의 허용 경로](../../.github/workflows/screenshot-baseline-apply.yml) 와 `screenshot-baseline-policy.test.mjs` 만 함께 늘린다.

## Actions 에서 baseline 갱신 (기본 경로)

1. 갱신할 PR 에 `screenshot-baseline` 라벨을 붙인다.
2. 읽기 전용 생성 워크플로가 PR 의 정확한 head SHA 를 CI 표준 Docker 이미지에서 렌더하고 재검증한다.
3. 생성 워크플로는 `pull_request` 권한 경계에서 실행되므로 PR 코드가 default branch cache 를 오염시키지 않는다.
4. 별도 적용 워크플로가 artifact 에 PNG baseline 이외 변경이 없는지, 생성 대상과 현재 PR head 가 같은지 checkout 없이 재검증한다.
5. 검증된 PNG 만 PR 브랜치에 커밋하고 필수 검사를 다시 요청한다. 성공하면 라벨도 제거된다.

무엇을 캡처할지는 워크플로가 화면을 탐색해서 추측하지 않는다. `app/src/screenshotTest/**/*ScreenshotTest.kt` 가 `@PreviewTest` 로 Preview 함수와 상태를 선언하며, 워크플로는 그 테스트 전체를 실행한다. 새 화면·새 상태를 추가하려면 먼저 screenshot test 를 추가한다. 생성된 이미지는 PR 의 PNG diff 에서 눈으로 최종 확인한다.

## 로컬 baseline 갱신 (Actions 장애 시 fallback)

Docker 호환 runtime(Colima/Docker Desktop) 이 필요하다.

```bash
docker build --platform linux/amd64 -t slowclock-screenshot:latest -f Dockerfile.screenshot .
docker run --rm --platform linux/amd64 -v "$PWD":/workspace -w /workspace slowclock-screenshot:latest \
  ./gradlew :app:updateScreenshotTest --rerun
```

→ 변경된 PNG 가 `app/src/screenshotTestDebug/reference/...` 에 갱신된다. `git add` 후 commit.

## 로컬 baseline 검증 (CI 실패 재현)

```bash
docker run --rm --platform linux/amd64 -v "$PWD":/workspace -w /workspace slowclock-screenshot:latest \
  ./gradlew :app:validateScreenshotTest
```

→ baseline 과 docker 환경에서 새로 그린 PNG 를 비교한다. 실패 시 `app/build/outputs/screenshotTest-results/preview/debug/diffs/` 에서 diff PNG 를 확인한다.

## 호스트 직접 실행은 사용하지 않음

`./gradlew :app:updateScreenshotTest` 를 host 에서 직접 실행하면 macOS / Linux / JDK 마이너 버전 / 폰트 캐시 차이로 CI 와 baseline 이 어긋난다. docker 환경 통일이 root fix 다.
