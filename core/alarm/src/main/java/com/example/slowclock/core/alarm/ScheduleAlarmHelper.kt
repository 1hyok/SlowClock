package com.example.slowclock.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.receiver.AlarmReceiver
import java.util.*

object ScheduleAlarmHelper {
    private const val TAG = "ScheduleAlarmHelper"

    /**
     * 스케줄에 대한 알람을 예약합니다.
     * @param context Context
     * @param schedule 스케줄 정보
     * @param isFullScreen 풀스크린 알람 여부 (기본값: false)
     */
    fun scheduleAlarm(
        context: Context,
        schedule: Schedule,
        isFullScreen: Boolean = true,
    ) {
        // 기존 알람 취소 후 새로 예약
        cancelAlarm(context, schedule)

        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Android 12 부터 정시 알람은 따로 허용받아야 한다. 허용이 없다고 알람을 아예 걸지 않으면
        // 그 일정은 소리 없이 지나간다. 앱이 사용자에게 「몇 분 늦게 울릴 수 있다」 고 안내하므로
        // 늦더라도 울리게 부정확 알람으로 건다(#117).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "정시 알람 권한이 없어 부정확 알람으로 겁니다.")
        }

        // 시작 시간 알람 예약
        scheduleStartAlarm(context, schedule, now, alarmManager, isFullScreen)

        // 종료 시간 알람 예약
        scheduleEndAlarm(context, schedule, now, alarmManager, isFullScreen)
    }

    /**
     * 시작 시간 알람을 예약합니다.
     */
    private fun scheduleStartAlarm(
        context: Context,
        schedule: Schedule,
        now: Long,
        alarmManager: AlarmManager,
        isFullScreen: Boolean,
    ) {
        schedule.startTime.toDate().time.takeIf { it > now }?.let { triggerTime ->
            val requestCode = generateStartRequestCode(schedule.id)
            val pendingIntent = createAlarmPendingIntent(context, schedule, "시작", isFullScreen, requestCode)

            try {
                setExactAlarm(alarmManager, triggerTime, pendingIntent)
                Log.d(TAG, "⏰ 시작 알람 예약 성공: ${schedule.title} at ${Date(triggerTime)} (requestCode=$requestCode, fullScreen=$isFullScreen)")
            } catch (e: Exception) {
                Log.e(TAG, "시작 알람 예약 실패: ${e.message}")
            }
        } ?: run {
            Log.d(TAG, "시작 시간이 없거나 이미 지난 시간입니다: ${schedule.title}")
        }
    }

    /**
     * 종료 시간 알람을 예약합니다.
     */
    private fun scheduleEndAlarm(
        context: Context,
        schedule: Schedule,
        now: Long,
        alarmManager: AlarmManager,
        isFullScreen: Boolean,
    ) {
        schedule.endTime?.toDate()?.time?.takeIf { it > now }?.let { triggerTime ->
            val requestCode = generateEndRequestCode(schedule.id)
            val pendingIntent = createAlarmPendingIntent(context, schedule, "종료", isFullScreen, requestCode)

            try {
                setExactAlarm(alarmManager, triggerTime, pendingIntent)
                Log.d(TAG, "⏰ 종료 알람 예약 성공: ${schedule.title} at ${Date(triggerTime)} (requestCode=$requestCode, fullScreen=$isFullScreen)")
            } catch (e: Exception) {
                Log.e(TAG, "종료 알람 예약 실패: ${e.message}")
            }
        } ?: run {
            Log.d(TAG, "종료 시간이 없거나 이미 지난 시간입니다: ${schedule.title}")
        }
    }

    /**
     * 알람이 걸릴 자리를 가리키는 PendingIntent.
     *
     * Intent 를 만드는 자리와 PendingIntent 를 만드는 자리를 붙여 둔다. 둘을 다른 함수로 나눠
     * 두면 정적 분석이 대상 컴포넌트를 따라가지 못해 «암시적 PendingIntent» 로 본다. 실제로는
     * [AlarmReceiver] 로 못박혀 있고 FLAG_IMMUTABLE 이라 받는 쪽이 고칠 수도 없다(#117).
     */
    private fun createAlarmPendingIntent(
        context: Context,
        schedule: Schedule,
        type: String,
        isFullScreen: Boolean,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra("title", "${schedule.title} ($type)")
                putExtra("desc", schedule.description)
                putExtra("isFullScreen", isFullScreen)
                putExtra("scheduleId", schedule.id)
                putExtra("alarmType", type)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 정확한 알람을 설정합니다.
     */
    private fun setExactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
    ) {
        val canBeExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            // 정시 허용이 없을 때의 차선. 몇 분 늦을 수 있지만 절전 상태에서도 울린다.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * 스케줄의 모든 알람을 취소합니다.
     */
    fun cancelAlarm(
        context: Context,
        schedule: Schedule,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 시작 알람 취소
        cancelSpecificAlarm(context, alarmManager, schedule, generateStartRequestCode(schedule.id), "시작")

        // 종료 알람 취소
        cancelSpecificAlarm(context, alarmManager, schedule, generateEndRequestCode(schedule.id), "종료")
    }

    /**
     * 특정 알람을 취소합니다.
     */
    private fun cancelSpecificAlarm(
        context: Context,
        alarmManager: AlarmManager,
        schedule: Schedule,
        requestCode: Int,
        type: String,
    ) {
        try {
            // 예약할 때와 같은 자리 번호·같은 대상이면 취소가 맞는다. extras 는 대상 판정에
            // 들어가지 않으므로 예약과 같은 함수를 그대로 쓴다.
            val pendingIntent =
                createAlarmPendingIntent(
                    context = context,
                    schedule = schedule,
                    type = type,
                    isFullScreen = true,
                    requestCode = requestCode,
                )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // PendingIntent도 함께 취소
            Log.d(TAG, "🛑 $type 알람 취소 성공: ${schedule.title} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "$type 알람 취소 실패: ${e.message}")
        }
    }

    /**
     * 알람 자리를 가리키는 번호. 시작은 짝수, 종료는 홀수로 만든다.
     *
     * 종전에는 종료 번호가 시작 번호에 9999 를 더한 값이었다. 어떤 일정의 해시가 다른 일정의
     * 해시보다 정확히 9999 크면 두 알람이 같은 자리를 써서, 나중에 건 쪽이 앞의 것을 덮어쓰고
     * 하나를 취소하면 다른 하나도 사라졌다. 사용자에게는 알람이 안 울린 것으로만 보인다(#117).
     *
     * 홀짝으로 갈라 두면 시작과 종료가 서로 겹치는 일은 구조적으로 사라진다. 남는 것은 서로 다른
     * 일정 id 의 해시가 같은 경우인데, 이는 32비트 번호를 쓰는 한 피할 수 없다.
     */
    internal fun generateStartRequestCode(scheduleId: String): Int = scheduleId.hashCode() * 2

    internal fun generateEndRequestCode(scheduleId: String): Int = scheduleId.hashCode() * 2 + 1
}
