package com.example.slowclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.slowclock.core.alarm.AlarmSchedulerEntryPoint

/**
 * 걸어 둔 알람을 다시 거는 자리.
 *
 * 세 방송이 하는 일은 같다 — 장부를 읽어 다시 건다. 다시 걸기는 멱등이라(먼저 취소하고 건다)
 * 나눌 이유가 없다.
 *
 * - `BOOT_COMPLETED`: 기기를 끄면 걸린 알람이 전부 취소된다. Android 15 부터는 강제 종료로
 *   중지 상태에 들어갔던 앱을 사용자가 다시 열 때도 이 방송이 와서, 같은 코드가 그 복구까지
 *   덮는다. https://developer.android.com/about/versions/15/behavior-changes-all
 * - `MY_PACKAGE_REPLACED`: 앱을 교체한 뒤 예약이 남지 않는 기기가 있다. 다만 이 방송은 암시적
 *   방송 예외 목록에 없으므로 유일한 복구 경로로 삼지 않는다.
 * - `TIME_SET`(상수 이름은 `ACTION_TIME_CHANGED`): 부팅 직후 시계가 아직 네트워크 시각과 맞지
 *   않을 수 있다. 그 상태에서 「지났다」 로 걸러 버린 알람을, 시각이 교정된 뒤 되살린다.
 *
 * `TIMEZONE_CHANGED` 는 받지 않는다. `Schedule.startTime` 은 Firebase `Timestamp`, 곧 절대
 * 시각이고 알람 경로는 그 epoch 밀리초를 그대로 AlarmManager 에 넘긴다. 시간대를 읽는 코드가
 * 한 줄도 없어 다시 계산할 것이 없다. 등록해 두면 뒤에 읽는 사람이 「시각이 현지시각이구나」 로
 * 오해한다.
 *
 * 의존성을 `@AndroidEntryPoint` + 필드 주입이 아니라 [AlarmSchedulerEntryPoint] 로 받는다. Kotlin 은
 * `BroadcastReceiver.onReceive` 가 추상이라 Hilt 가 요구하는 `super.onReceive` 를 부를 수 없고,
 * 그 호출을 빼면 주입이 되는지 여부가 부팅 때만 드러난다. 여기서는 받는 자리를 눈으로 볼 수 있게 둔다.
 *
 * `goAsync` 를 쓰지 않는다. 이 경로에는 네트워크도 Firestore 도 없어 동기로 끝난다. `goAsync`
 * 를 써도 10초 제한은 그대로이고 `finish()` 를 빠뜨릴 자리만 는다. 그래서 이 수신기에 suspend
 * 호출을 들이지 않는 것이 이 파일의 계약이다.
 */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            -> Unit

            else -> return
        }

        Log.d(TAG, "알람 복원: ${intent.action}")
        runCatching { AlarmSchedulerEntryPoint.from(context).restoreAll() }.onFailure {
            // 복원에 실패해도 부팅 방송을 물고 죽지 않는다. 앱을 열면 그때 다시 걸린다.
            Log.e(TAG, "알람 복원 실패", it)
        }
    }

    private companion object {
        const val TAG = "AlarmRestoreReceiver"
    }
}
