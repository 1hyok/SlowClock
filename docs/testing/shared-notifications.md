# 공유 일정 푸시의 세션 확인

#206부터 sender와 Android 앱이 함께 `shared_schedule` v1 계약을 사용한다. 로그아웃하거나 다른 공유 코드를 저장한 기기는 이전 계정·코드의 새 푸시를 표시하지 않는다. FCM 전송/수신은 정시 알람과 별개다.

## 데이터 계약

Cloud Function `sendFcmToShareCodeWatchers`는 `notification` 없이 다음 문자열 data 필드를 보낸다.

| 필드 | 값/근거 |
|---|---|
| type / schemaVersion | `shared_schedule` / `1` |
| recipientUid | `shareCodeWatchers/{code}/tokens/{uid}`의 문서 ID. 문서 데이터의 `userId`를 신뢰하지 않는다. |
| shareCode | 변경된 일정의 공유 코드. 삭제에는 before 값을 사용한다. |
| scheduleId | Firestore trigger 경로의 일정 ID |
| title / body | 변경 종류 안내 / 일정 제목. body는 빈 문자열일 수 있다. |

수신자는 모든 필수 필드와 현재 Auth UID·로컬 공유 코드를 확인한다. 필드 누락, 알 수 없는 버전, legacy notification payload는 기본 알림으로 대체하지 않는다. 수신 hot path에는 원격 조회나 suspend 작업이 없다. 앱 알림 권한 또는 공유 알림 채널이 차단된 경우 표시하지 않는다.

서버는 `(recipientUid, token)`을 중복 제거하고 `sendEach`에 최대 500개씩 넘긴다. 한 토큰이 이전 UID와 현재 UID에 모두 남아 있어도 현재 UID 메시지를 보존한다. 로그에는 전송 성공/실패 수만 남긴다. Android FCM 수신 데이터·토큰은 로그에 쓰지 않는다.

## 세션과 이미 표시된 알림

`SharedScheduleNotifier`의 단일 잠금에서 현재 세션 확인부터 `notify`까지 실행한다. 로그아웃의 로컬 코드 삭제·Auth signOut·공유 알림 정리, 코드 변경·해제도 같은 잠금을 사용한다. 알림 표시가 먼저 시작되면 세션 변경이 그 알림을 지운 뒤 반환한다. 변경이 먼저 완료되면 이전 메시지는 표시하지 않는다. 실패한 Auth signOut에도 로컬 코드와 기존 공유 알림은 지워진다.

공유 코드 저장은 시작 시 UID/코드/세션 revision을 기억한다. 등록/해제의 서버 응답을 기다리는 동안 로그아웃·재로그인 또는 코드 변경이 발생하면 늦은 성공이 로컬 설정을 되살리거나 새 설정을 지우지 않는다. 서버 응답은 잠금 밖에서 기다린다.

알림 식별자는 `shared_schedule:` 접두어와 UID·코드·일정 ID로 만든 문자열 tag를 사용한다. 같은 일정의 갱신은 같은 알림을 교체하고 서로 다른 일정은 정수 hash 충돌로 덮어쓰지 않는다. 세션 변경은 다음 알림만 취소한다.

- 새 공유 tag 접두어의 알림.
- 기존 앱의 무tag ID 0 + `schedule_channel` 알림.
- 기존 Firebase SDK의 `FCM-Notification:` tag + ID 0 + `fcm_fallback_notification_channel` 알림. 현재 앱 manifest와 구 sender는 기본 채널/tag를 별도로 지정하지 않았다.

정시 알람·놓친 알람을 이 관리자에서 `cancelAll`로 지우지 않는다. 로그아웃 자체의 기존 AlarmScheduler 예약 취소 정책은 유지된다.

## 토큰 갱신과 여러 기기

`FCMService.onNewToken`은 잠금 안에서 현재 UID/코드를 얻어 `UserRepository.updateFcmRegistration`으로 사용자 토큰과 현재 watcher 토큰을 갱신한다. 서버 응답을 기다리지는 않는다. 실패 시 다음 로그인/목록 구독의 기존 등록 경로가 다시 시도한다. 토큰 조회를 await한 기존 등록 경로도 UID가 바뀌었는지 다시 확인한다.

로그아웃에서 원격 UID watcher를 삭제하거나 FCM token을 폐기하지 않는다. UID별 한 문서는 다른 기기의 마지막 토큰을 가질 수 있기 때문이다. 현재의 마지막 등록 기기 우선 구조는 유지하며 설치별/FID 등록과 모든 기기 동시 수신 보장은 #84 후속이다. 잔류 토큰에 메시지가 도착할 수 있으나 새 앱의 현재 UID/코드가 맞지 않으면 표시하지 않는다.

## 출시 전 전환 순서와 한계

1. 서버와 앱 변경을 함께 검증한다. 아래 자동 검증은 실제 운영 토큰에 알림을 보내지 않는다.
2. [Firestore 전환 절차](../firestore.md)에 따라 기존 데이터·권한 inventory를 확인하고 새 rules를 배포한다. 새 rules와 data-only Function 배포를 모두 완료하는 것이 새 앱 출시의 선행 조건이다. #102의 Firebase 운영 배포 선행조건은 별개이며 이 PR이 배포나 과금 설정을 수행하지 않는다.
3. 선행 배포를 확인한 뒤 새 앱을 출시하고, 테스터의 업데이트 여부와 background 수신·코드 변경·로그아웃 후 미표시를 검증한다. 새 sender + 새 receiver 조합에서만 보장한다.
4. 전환 전 큐에 남은 legacy notification 메시지는 OS가 자동 표시할 수 있다. 서버 전환으로 그 큐가 소급 삭제되지 않는다. 이 경계와 구버전 테스터 잔존 여부를 출시 판정에 기록한다.

새 앱 + 기존 sender는 background OS 표시를 막을 수 없고, 기존 앱 + 새 sender도 기존 앱의 무조건 기본 알림 경로 때문에 로그아웃 미표시를 보장하지 않는다. 구버전 호환이 필요하면 별도 capability/전환 정책이 필요하다. 현재 릴리스 전환은 구앱까지의 보장을 주장하지 않는다. 이미 표시된 legacy 알림은 다음 세션 변경 때 좁게 정리하지만 그 뒤 도착한 legacy 큐까지 차단하는 것은 아니다.

## 검증

- `node --test .github/scripts/functions-notification.test.mjs`: 실제 index.js를 fake Admin SDK에 로드하여 모든 이벤트, 삭제 before, UID 위조 필드, 동일 token/다른 UID, 1001개 분할, 부분/전체 실패, 민감 내용 로그 부재를 검사한다.
- `:core:alarm:testDebugUnitTest`: 실제 세션 잠금에서 latch를 사용한 notify/로그아웃·코드 교체 경합, 세션 revision, 메시지 거절, 권한/채널 차단, tag 충돌, 좁은 취소 대상, 실제 FCMService callback 분기를 검사한다.
- `:core:data:testDebugUnitTest`: 사용자/현재 watcher 토큰 동시 갱신, 빈 토큰/로그아웃 거절, token await 도중 계정 변경을 검사한다.
- `:feature:main:testDebugUnitTest`, `:feature:profile:testDebugUnitTest`: 늦은 등록/해제 응답과 세션 정리 호출을 검사한다.

공식 근거: [Android 수신 경로](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [Admin SDK sendEach](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk), [메시지 우선순위](https://firebase.google.com/docs/cloud-messaging/android-message-priority), [토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens), [Firebase SDK 기본 tag/채널](https://github.com/firebase/firebase-android-sdk/blob/master/firebase-messaging/src/main/java/com/google/firebase/messaging/CommonNotificationBuilder.java).


### Android 실제 알림 검증

`SharedScheduleNotificationTest`는 별도 `demo-slowclock-fcm` FirebaseApp과 테스트 설정 파일만 사용한다. `sharedAuthEmulatorPort` 인자가 없으면 명시적으로 skip하므로 일반 기기 실행을 통과 증거로 세지 않는다. 운영 계정이 없는 폐기 가능한 Android emulator에서 실행한다. 테스트는 현재 앱의 공유 알림을 지우므로 개인 기기에서 실행하지 않는다.

1. 별도 폴더의 `firebase.json`에 `{"emulators":{"auth":{"port":9096},"ui":{"enabled":false}}}`를 저장하고 `firebase emulators:start --only auth --project demo-slowclock-fcm --config firebase.json`을 실행한다.
2. `:app:assembleDebug :app:assembleDebugAndroidTest`로 만든 두 APK를 전용 emulator에 `adb -s <serial> install -r -g`로 설치한다.
3. 아래 세 메서드를 `adb -s <serial> shell am instrument -w -r -e sharedAuthEmulatorPort 9096 -e class <class>#<method> com.ilhyok.slowclock.test/androidx.test.runner.AndroidJUnitRunner`로 실행한다. class는 `com.example.slowclock.notification.SharedScheduleNotificationTest`다. 앱 알림을 허용한 상태에서 실행한다.
   - `currentSessionOnlyAndDistinctScheduleIds`
   - `logoutClearsNewAndLegacySharedNotificationsButKeepsAlarm`
   - `codeReplacementAndUnregisterRemovePreviousNotifications`
4. `adb -s <serial> shell pm revoke com.ilhyok.slowclock android.permission.POST_NOTIFICATIONS` 후 같은 runner에 `-e sharedPermissionDenied true`를 추가해 `permissionDeniedDoesNotPost`를 실행한다. 끝나면 `pm grant`로 권한을 복구하고 전용 emulator/Auth emulator를 종료한다.

Auth emulator용 HTTP 허용은 debug variant의 10.0.2.2/127.0.0.1/localhost에만 한정한다. release에는 해당 manifest/config가 포함되지 않는다. [Auth emulator 연결](https://firebase.google.com/docs/emulator-suite/connect_auth)과 [domain별 network security config](https://developer.android.com/privacy-and-security/security-config)를 따른다. 실제 OS NotificationManager, 알림 권한, UID/설정 검증을 사용하며 FCM 네트워크 발송 성공이나 운영 배포 검증을 대신하지 않는다.


### 계정 삭제 중 세션 전환

수신 경로 점검 중 계정 삭제가 비동기 단계 이후 현재 코드/UID를 다시 골라 새 계정의 watcher 또는 Auth 사용자를 지울 수 있는 경로도 확인했다. 삭제는 시작 시 UID·코드·revision을 고정하고 각 await 뒤 `ensureActive`와 동일 세션을 확인한다. 로컬 알람 취소도 동일 잠금에서 현재 세션일 때만 실행한다. watcher 해제와 Auth 삭제 Repository는 명시한 expected UID가 현재 사용자와 일치해야 시작하고, Auth는 확인한 FirebaseUser 인스턴스를 사용한다.

6개 삭제 단계 각각에서 계정 전환 또는 취소가 일어나도 다음 파괴 단계로 진행하지 않는 테스트를 둔다. 이미 제출된 Firebase 작업은 coroutine 대기 취소만으로 철회됐다고 주장하지 않는다. Auth 단계에 도달했다면 원격 데이터와 watcher를 이미 제거했으므로 Auth 실패/취소 때도 그 세션의 로컬 코드·공유 알림을 finally에서 정리한다. 새 세션이 시작된 경우에는 revision/UID 검사로 이 정리가 새 설정을 건드리지 않는다. [협력적 취소 확인](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html)을 따른다.
