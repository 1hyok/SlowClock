package com.example.slowclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.slowclock.core.alarm.AlarmSchedulerEntryPoint
import java.util.concurrent.Executors

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
 * `TIMEZONE_CHANGED`는 받지 않는다. 반복 계산은 기기 시간대를 사용하지만 저장 모델은
 * epoch뿐이다. 방송을 추가하는 것만으로 원래 선택한 현지 시각을 보존할 수 없으므로
 * 시간대 정책과 모델을 함께 정하기 전에는 기존 동작을 유지한다.
 *
 * 의존성을 `@AndroidEntryPoint` + 필드 주입이 아니라 [AlarmSchedulerEntryPoint] 로 받는다. Kotlin 은
 * `BroadcastReceiver.onReceive` 가 추상이라 Hilt 가 요구하는 `super.onReceive` 를 부를 수 없고,
 * 그 호출을 빼면 주입이 되는지 여부가 부팅 때만 드러난다. 여기서는 받는 자리를 눈으로 볼 수 있게 둔다.
 *
 * 장부 읽기와 OS 예약은 goAsync 작업에서 수행해 메인 스레드를 막지 않는다.
 * 방송 완료 제한은 여전히 적용되며 finally에서 반드시 finish한다.
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
        val pending = goAsync()
        executor.execute {
            try {
                AlarmSchedulerEntryPoint.from(context).restoreAll()
            } catch (error: Exception) {
                Log.e(TAG, "알람 복원 실패", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmRestoreReceiver"
        val executor = Executors.newSingleThreadExecutor()
    }
}
