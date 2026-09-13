# 알람·데이터 후속 검증 범위

2026-09-06, #218. 운영 Firebase 호출·배포 없이 소스와 호스트 JVM에서 검증했다. 기기 성능 실측은 별도 진행한다.

## 취소 전파 (R13)

UserRepository의 사용자 읽기·삭제·공유 코드 보장·공개 프로필 쓰기·소유자 이름 조회와 NotificationRepository/FamilyGroupRepository의 계정 정리가 `CancellationException`을 다시 던진다. 일반 오류에 대한 기존 null/false/빈 목록 반환은 유지한다. 특히 공개 프로필 쓰기 대기가 취소됐는데 `ensureShareCode`가 성공으로 반환하는 경계를 닫는다.

대기 중인 실제 `TaskCompletionSource.task` 12개 경로에서 코루틴을 취소한다. Job의 취소 플래그만 확인하지 않고 호출 뒤의 동기 코드가 실행되지 않는지 검사한다. 삭제 읽기·코드 등록·그룹 삭제 취소 뒤 다음 쓰기가 시작되지 않는지도 확인한다. 원격에 이미 제출한 쓰기 자체를 되돌리는 기능은 아니다. 동기 문서 파싱과 snapshot callback의 오류 처리는 그대로 둔다.

근거: [Android 코루틴 취소 예외 전파](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#watch-out-for-exceptions), [Task.await의 취소 범위](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/kotlinx.coroutines.tasks/await.html).

## 시간대와 MVI 테스트 (R07, R61)

시간대 동작은 [기존 epoch 호환 정책](recurrence-time-policy.md)에 명시하고 6개 회귀 테스트로 검증한다. 생성 지역 시각 또는 여행지 벽시계 시각으로 데이터를 임의 이행하지 않는다.

`MviViewModelTest`의 같은 파일 안 fake 산술을 반복하는 단언을 제거하고, 실제 StateFlow collector가 오류→소비→같은 오류를 관측하는지 검증한다. StateFlow의 모든 중간값 전달이나 화면 lifecycle의 전체 동작을 보장한다고 해석하지 않는다. 제품별 검증은 Main/Profile/AddSchedule 등의 ViewModel 회귀 테스트를 근거로 삼는다.

## 성능 확인과 측정 경계 (R14, R30, R31)

R14: 날짜 조회에서 사용하지 않던 Calendar 복제 2개를 제거했다. 전체 사용자 문서 조회는 유지한다. 과거에 시작한 반복 문서를 날짜 범위로 제외하면 오늘 회차가 사라진다. 캐시가 있어도 최초 query·서버 동기화·장기 재연결의 읽기가 모두 없어지는 것은 아니다. [Firestore 최초 snapshot](https://firebase.google.com/docs/firestore/query-data/listen), [리스너 재접속 과금](https://firebase.google.com/docs/firestore/pricing#listening_to_query_results).

실제 `RecurrenceRule.occurrenceOn` bytecode에 합성 anchor를 넣고, 반환 epoch를 모아 정렬하는 호스트 계산을 측정했다. JDK 21.0.11/aarch64/14 logical CPU, 서울 시간대, 각 규모 20회 준비·100회 측정이다. 기준은 2026-01-01 08:00에서 `i % 31`일 뒤, 반복 종류 4개를 균등 분배하고 대상 날짜는 2026-09-06이다.

| 입력 anchor 수 | 선택된 회차 수 | p50 (ms) | p95 (ms) | 최대 (ms) |
| --- | --- | --- | --- | --- |
| 100 | 29 | 0.144 | 0.222 | 1.217 |
| 1,000 | 290 | 0.686 | 0.874 | 1.061 |
| 10,000 | 2,903 | 3.666 | 4.411 | 5.105 |

이 값에는 Firestore 문서 파싱·네트워크·디스크·Binder·Compose·기기 프레임이 포함되지 않는다. 실제 사용자의 데이터 분포도 아니다. 조회/파싱 전체 지연, 출시 성능 또는 비용을 보장하는 근거로 사용하지 않는다. 현재 근거로 스키마·인덱스·구독 구조를 바꾸지 않는다.

R30: MainViewModel 생성 시 설정 읽기와 알람 권한/알림 상태 확인은 여전히 동기 호출이다. 설정 캐시가 준비되지 않은 경우의 대기와 Binder 응답 비용은 호스트 mock으로 판정할 수 없다. 초기 알람 복원은 별도 IO 경로이며, 이 사실을 모든 시작 경로가 비동기라는 뜻으로 쓰지 않는다.

R31: `AlarmScheduler.cancelAll`은 예약 일정과 미룸 장부를 순회한다. 로그아웃에서는 취소 완료 뒤 공유 상태를 비우고 인증 세션을 끊는 순서를 유지한다(`SignOutUseCaseTest`). 기존 새 설치 경로도 동기 순서를 유지한다. 비동기로 분리하면 계정 전환 뒤 늦은 이전 계정 취소가 새 알람에 영향을 줄 수 있어, 실측 없이 실행 순서를 바꾸지 않는다.

다음 기기 검증에서는 동일한 합성 규모로 cold/warm 시작의 SharedPreferences·권한 Binder 대기, 로그아웃/새 설치의 전체 취소 소요 시간과 프레임 trace, 계정 전환 중 취소 완료 순서를 함께 기록한다. 실제 데이터 규모의 Firestore 비용은 별도 비용 검토 범위다. 이번 호스트 결과만으로 저사양 기기의 ANR·프레임 지연이 없다고 결론 내리지 않는다.
