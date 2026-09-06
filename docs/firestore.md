# Firestore 데이터 경계

무엇을 어디에 담고 누가 읽을 수 있는지 정하는 문서다. 규칙 파일은 [`firestore.rules`](../firestore.rules) 이고, 그 규칙이 실제로 그렇게 동작하는지는 [`firestore-tests/rules.test.mjs`](../firestore-tests/rules.test.mjs) 가 에뮬레이터로 잠근다.

## 컬렉션

| 경로 | 담는 값 | 읽기 | 쓰기 |
|---|---|---|---|
| `users/{uid}` | 이름, 이메일, 공유 코드, FCM 토큰, 생성·수정 시각 | 본인만 | 본인만 |
| `publicProfiles/{uid}` | id, 이름 | 로그인한 사용자가 문서 하나씩(`get`). 목록 조회(`list`)는 아무도 못 함 | 본인만, 이 두 필드만 |
| `shareCodes/{code}` | 그 코드의 임자 uid | 아무도 못 함 | 본인 소유로 만들기·동일값 확인, 임자의 삭제 및 없는 등록부 삭제 재시도 |
| `schedules/{id}` | 일정 제목·설명·시각·완료 여부·소유자 uid·공유 코드 | 소유자, 그리고 그 `sharedCode` 의 감시자로 등록한 사람 | 소유자. 공유 일정은 감시자가 `completed`·`completedDates`·`updatedAt` 만 |
| `notifications/{id}` | 알림 기록(소유자 uid) | 소유자 | 소유자 |
| `shareCodeWatchers/{code}/tokens/{uid}` | 그 공유 코드를 보는 기기의 FCM 토큰 | 본인 문서만 | 본인 문서만 |
| `familyGroups/{id}` | 과거 그룹의 소유자 uid·구성원 uid 목록·이름 등. 현재 앱은 생성하지 않고 탈퇴 때 정리 | 소유자 또는 구성원 | 기존 문서는 소유자 또는 구성원. 신규 생성은 허용하지 않음 |
| `scheduleRecommendations/{id}` | 유형별 추천 일정 | 로그인한 사용자 전체 | 아무도 못 함(콘솔로만) |

## 새 필드는 어디에 두는가

기준은 하나다. 다른 사용자에게 보여야 하면 `publicProfiles`, 아니면 `users` 다.

Firestore 에는 필드 단위 읽기 제한이 없다. 문서 하나를 읽게 허용하면 그 문서의 모든 필드가 함께 나간다. 그래서 이름 하나를 보여 주려고 `users` 를 열면 이메일과 FCM 토큰도 같이 열린다. 실제로 그런 규칙이 배포돼 있었고 #95 에서 이 구조로 바꿨다.

`publicProfiles` 는 쓰기 규칙이 필드 목록을 `id`·`name` 으로 묶는다. 필드를 늘리려면 규칙과 테스트를 함께 고쳐야 한다. 그 마찰이 의도한 것이다.

## 공유 범위를 무엇으로 정하는가

「로그인했는가」 는 범위가 아니다. Google 계정은 누구나 몇 초 만에 새로 만들 수 있어, 로그인만 조건으로 두면 그 컬렉션은 사실상 공개다. 그리고 읽기 권한을 문서 하나 단위로만 생각하면 부족하다. `get` 을 열면 「문서 이름을 아는 사람」 이 읽지만, `list` 까지 열면 「아무것도 모르는 사람」 이 컬렉션을 통째로 가져간다.

그래서 이 저장소는 세 가지를 지킨다(#174).

1. 사람을 찾는 열쇠(공유 코드)는 훑을 수 있는 자리에 두지 않는다. `shareCodes` 는 코드가 문서 이름이고 읽기를 아무에게도 열지 않는다. 중복 확인은 만들기 한 번으로 끝나므로, 확인과 저장 사이에 같은 코드를 두 사람이 가져가는 틈도 없다.
2. 공유 일정은 「공유로 표시됐는가」 가 아니라 「그 코드의 감시자로 등록한 사람인가」 로 연다. 등록 문서(`shareCodeWatchers/{code}/tokens/{uid}`)가 그 관계의 증거다. 이 문서는 읽기 권한의 근거이기도 해서, FCM 토큰을 못 받아도 등록 자체는 한다.
3. 읽기보다 쓰기를 더 좁게 본다. 남이 완료 표시를 뒤집을 수 있으면 어르신이 약을 이미 먹은 줄로 안다. 이 앱에서는 그쪽이 더 아픈 피해다.

## 일정 쓰기와 재시도

일정 생성·편집·삭제·완료는 온라인 transaction으로 처리한다. 서버가 결과를 확인한 뒤에 저장 성공을 표시하고 알람 후처리를 한다. 연결이 끊기면 연결 필요 안내와 재시도를 제공하며, 입력은 그대로 유지한다. 조회용 오프라인 캐시는 유지하지만 이 네 가지 쓰기를 오프라인 대기열에 넣지는 않는다. transaction callback에는 UI나 알람 처리를 넣지 않는다.

새 일정 ID는 제출 전에 만들어 `SavedStateHandle`에 입력과 함께 보존한다. 같은 ID의 문서가 이미 있으면 소유자와 편집 필드를 비교하며, 같은 요청만 서버의 최신 문서를 반환한다. 다른 내용은 덮어쓰거나 성공으로 처리하지 않고 목록에서 확인하도록 안내한다. 코루틴 취소가 이미 전송한 commit을 철회하지는 않으므로, 재시도에서도 같은 ID를 쓰는 것이 필요하다.

`SavedStateHandle`은 시스템이 같은 task를 복원하는 범위다. 강제 종료·최근 앱 목록에서 제거·재부팅으로 task가 사라진 경우의 입력 복원까지 보장하지 않는다. 백그라운드 상태에서 저장되지 않은 최신 값에도 한계가 있다. 이 범위를 넘어서는 내구성 있는 작성 중 보관함은 별도다.

공유 등록부 복구와 일정 쓰기는 같은 transaction에서 수행하고 규칙은 `getAfter`로 최종 소유 관계를 확인한다. 첫 제출의 고정 ID가 비어 있는지 확인할 수 있도록 로그인한 요청의 미존재 일정 `get`만 허용한다. 기존 타인 일정과 목록 및 등록부 읽기는 계속 제한한다. 완료 실패는 화면의 임시 표시를 되돌리며, 그 사이 도착한 새 서버 목록은 덮지 않는다. 완료·시간표 화면은 로그아웃 시 이전 목록을 비우고 로그인 필요 안내를 표시한다. 재로그인하면 선택한 날짜의 구독을 다시 시작하며, 이전 로그인 세대의 완료 응답은 새 화면을 변경하지 않는다.

근거: [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [SavedStateHandle 복원 범위](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate), [Task.await 취소](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/kotlinx.coroutines.tasks/await.html).

## 서버가 관리자 권한으로 읽는 곳

Cloud Functions 는 Admin SDK 로 동작해 규칙을 거치지 않는다. 규칙만 읽으면 이 경로를 놓친다.

- `sendFcmToShareCodeWatchers`(`functions/index.js`): `schedules` 문서가 바뀌면 `shareCodeWatchers/{code}/tokens` 를 읽어 감시자에게 알림을 보낸다. 클라이언트는 남의 토큰을 읽지 않는다.

## 규칙 테스트

```bash
cd firestore-tests
npm ci
npm test
```

`firebase emulators:exec` 가 Firestore 에뮬레이터에 `firestore.rules` 를 올리고 테스트를 돌린다. JDK 가 필요하다. CI 에서는 [`firestore-rules.yml`](../.github/workflows/firestore-rules.yml) 이 규칙·테스트가 바뀐 PR 과 main push 에서 같은 명령을 돌린다.

테스트는 두 가지를 잠근다. 앱이 실제로 하는 접근이 통과하는지와 남의 데이터 접근이 막히는지다. 규칙을 좁힐 때 기능이 깨지는 것을 여기서 먼저 잡는다.

## 배포

```bash
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

규칙은 배포해야 적용된다. 저장소 파일을 고치는 것만으로는 운영 데이터베이스가 바뀌지 않는다. 인덱스는 [`firestore.indexes.json`](../firestore.indexes.json) 이고 지금은 `schedules`(userId + startTime) 하나다.

배포 전에 `npm test` 로 규칙 테스트를 돌린다. 규칙을 좁히는 배포는 앱의 읽기 경로를 끊을 수 있어, 배포 뒤 기기에서 로그인·일정 조회·공유 코드 입력까지 확인한다.

공유 읽기 규칙은 문서마다 감시자 등록을 확인한다. 규칙의 문서 접근 횟수에는 한도가 있으므로, 공유 일정이 늘어도 목록 조회가 막히지 않는지 규칙 테스트가 실제 건수로 재 둔다.

## Play 데이터 보안 선언과의 관계

[`play-release.md`](play-release.md) 의 데이터 보안 표가 수집·공유 항목의 정본이다. 컬렉션을 늘리거나 담는 값을 바꾸면 그 표와 [`privacy.html`](privacy.html) 을 함께 고친다. 규칙이 선언보다 넓으면 선언이 거짓이 된다.
