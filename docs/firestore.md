# Firestore 데이터 경계

무엇을 어디에 담고 누가 읽을 수 있는지 정하는 문서다. 규칙 파일은 [`firestore.rules`](../firestore.rules) 이고, 그 규칙이 실제로 그렇게 동작하는지는 [`firestore-tests/rules.test.mjs`](../firestore-tests/rules.test.mjs) 가 에뮬레이터로 잠근다.

## 컬렉션

| 경로 | 담는 값 | 읽기 | 쓰기 |
|---|---|---|---|
| `users/{uid}` | 이름, 이메일, 공유 코드, FCM 토큰, 생성·수정 시각 | 본인만 | 본인만 |
| `publicProfiles/{uid}` | id, 이름, 공유 코드 | 로그인한 사용자 전체 | 본인만, 이 세 필드만 |
| `schedules/{id}` | 일정 제목·설명·시각·완료 여부·소유자 uid·공유 코드 | 소유자, 그리고 `sharedCode` 가 있으면 로그인 사용자 | 소유자. 공유 일정은 `completed`·`updatedAt` 만 |
| `notifications/{id}` | 알림 기록(소유자 uid) | 소유자 | 소유자 |
| `shareCodeWatchers/{code}/tokens/{uid}` | 그 공유 코드를 보는 기기의 FCM 토큰 | 본인 문서만 | 본인 문서만 |
| `scheduleRecommendations/{id}` | 유형별 추천 일정 | 로그인한 사용자 전체 | 아무도 못 함(콘솔로만) |

## 새 필드는 어디에 두는가

기준은 하나다. 다른 사용자에게 보여야 하면 `publicProfiles`, 아니면 `users` 다.

Firestore 에는 필드 단위 읽기 제한이 없다. 문서 하나를 읽게 허용하면 그 문서의 모든 필드가 함께 나간다. 그래서 이름 하나를 보여 주려고 `users` 를 열면 이메일과 FCM 토큰도 같이 열린다. 실제로 그런 규칙이 배포돼 있었고 #95 에서 이 구조로 바꿨다.

`publicProfiles` 는 쓰기 규칙이 필드 목록을 `id`·`name`·`shareCode` 로 묶는다. 필드를 늘리려면 규칙과 테스트를 함께 고쳐야 한다. 그 마찰이 의도한 것이다.

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

## Play 데이터 보안 선언과의 관계

[`play-release.md`](play-release.md) 의 데이터 보안 표가 수집·공유 항목의 정본이다. 컬렉션을 늘리거나 담는 값을 바꾸면 그 표와 [`privacy.html`](privacy.html) 을 함께 고친다. 규칙이 선언보다 넓으면 선언이 거짓이 된다.
