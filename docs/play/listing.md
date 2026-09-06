# Google Play 스토어 등록 정보 (기본 언어 한국어)

## 앱 이름 (30자 이내)

느린 시계

## 짧은 설명 (80자 이내)

큰 글씨와 정시 알람으로 오늘 할 일을 놓치지 않게 돕는 어르신용 일정 앱

## 자세한 설명 (4000자 이내)

느린 시계는 어르신과 보호자를 위한 일정·알람 앱입니다. 작은 글씨와 복잡한 메뉴 대신, 지금 해야 할 일 하나를 큰 글씨로 보여 주고 정한 시각에 알람을 울립니다.

주요 기능

- 오늘의 일정을 시간순으로 큰 글씨로 보여 줍니다. 지금 해야 할 일이 화면 맨 위에 옵니다.
- 정한 시각에 정확히 알람이 울립니다. 알람을 화면 가득 띄우도록 허용해 두면 잠금 화면에서도 큰 글씨로 뜹니다.
- 일정을 끝내면 한 번 눌러 완료로 표시합니다. 완료한 일은 따로 모아 볼 수 있습니다.
- 매일·매주 반복되는 일정을 한 번만 등록하면 됩니다.
- 공유 코드 여섯 자리를 가족에게 알려 주면, 가족이 내 오늘 일정을 자기 앱에서 함께 볼 수 있습니다.
- 어르신·학생·집중이 어려운 분을 위한 일정 추천을 참고할 수 있습니다.

이런 분께 권합니다

- 약 먹는 시간, 병원 가는 날처럼 놓치면 안 되는 일정을 크게 보고 싶은 분
- 부모님의 하루 일정을 함께 확인하고 싶은 보호자
- 할 일을 한 번에 하나씩만 보고 싶은 분

계정과 데이터

- Google 계정으로 로그인합니다. 일정과 계정 정보는 Firebase 에 안전하게 저장되며 광고나 위치 정보 수집은 없습니다.
- 계정 삭제는 앱의 내 정보 화면에서 할 수 있습니다.

개인정보처리방침: https://1hyok.me/SlowClock/privacy.html
이용약관: https://1hyok.me/SlowClock/terms.html
문의: dnfjddk2@gmail.com

## 분류

- 앱 유형: 앱 (게임 아님)
- 카테고리: 생산성
- 태그: 일정, 알람, 접근성

## 스토어 등록 정보 자료

| 자료 | 파일 | 규격 |
|---|---|---|
| 앱 아이콘 | `docs/play/ic_launcher_512.png` | 512×512 PNG, 투명 없음 |
| 그래픽 이미지 | `docs/play/feature_graphic_1024x500.png` | 1024×500 PNG |
| 휴대전화 스크린샷 | `docs/play/screenshots/` 4장 | 2~8장, 1080×2400 PNG |

올릴 순서와 파일이다.

| 순서 | 파일 | 화면 |
|---|---|---|
| 1 | `docs/play/screenshots/01-main.png` | 메인. 지금 할 일과 오늘 진행 상황 |
| 2 | `docs/play/screenshots/03-timeline.png` | 시간표. 하루를 시간 순서로 |
| 3 | `docs/play/screenshots/02-done.png` | 완료한 일 |
| 4 | `docs/play/screenshots/04-dark.png` | 어두운 모드 |

네 장은 앱의 화면 코드를 그대로 그려 만들었다. 만드는 자리는 `feature/main/src/screenshotTest/.../StoreScreenshotTest.kt` 이고, 렌더 결과는 그 모듈의 baseline 으로 먼저 들어간다. 여기 있는 넉 장은 그 baseline 을 옮겨 온 사본이다.

baseline 이 바뀌면 이 사본도 함께 옮겨야 한다. 화면을 고쳤는데 옮기지 않으면 스토어 그림만 옛 화면으로 남는다(#155). 옮기는 자리는 이 넷이다.

| baseline | 여기 |
|---|---|
| `StoreMainScreenshot_스토어 메인_768e228a_0.png` | `01-main.png` |
| `StoreDoneScreenshot_스토어 완료_9c836483_0.png` | `02-done.png` |
| `StoreTimelineScreenshot_스토어 시간표_1683b525_0.png` | `03-timeline.png` |
| `StoreDarkScreenshot_스토어 어두운 모드_dde69d6e_0.png` | `04-dark.png` |

baseline 은 macOS 렌더와 CI(Linux) 렌더가 달라 로컬에서 만들지 않는다. PR 에 `screenshot-baseline` 라벨을 붙여 CI 컨테이너가 갱신하게 한 뒤, 그 결과를 받아 위 표대로 옮긴다.

알람이 울리는 전체 화면과 공유 코드 화면은 남았다. 실제 동작과 계정이 있어야 자연스러워서 기기에서 찍는다. 에뮬레이터 `Pixel_7_Claude_QA`(1080×2400) 에 Google 계정으로 로그인한 뒤 `adb exec-out screencap -p > 파일.png` 로 찍는다. Play 는 두 장부터 받으므로 이 두 장이 없어도 등록은 막히지 않는다.

## 연락처

- 이메일: dnfjddk2@gmail.com
- 웹사이트: https://1hyok.me/SlowClock/
