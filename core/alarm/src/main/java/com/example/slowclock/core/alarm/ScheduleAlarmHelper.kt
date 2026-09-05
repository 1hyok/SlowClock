package com.example.slowclock.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.receiver.AlarmReceiver
import java.util.Date

/** 일정 하나가 만드는 알람의 종류. 시작과 종료가 각각 한 자리를 쓴다. */
private enum class AlarmKind(
    val label: String,
) {
    START("시작"),
    END("종료"),
}

object ScheduleAlarmHelper {
    private const val TAG = "ScheduleAlarmHelper"

    /**
     * 일정의 시작·종료 알람을 건다. 이미 걸려 있던 같은 일정의 알람은 먼저 지운다.
     *
     * Android 12 부터 정시 알람은 따로 허용받아야 한다. 허용이 없다고 알람을 아예 걸지 않으면
     * 그 일정은 소리 없이 지나간다. 앱이 사용자에게 「몇 분 늦게 울릴 수 있다」 고 안내하므로
     * 늦더라도 울리게 부정확 알람으로 건다(#117).
     */
    fun scheduleAlarm(
        context: Context,
        schedule: Schedule,
        isFullScreen: Boolean = true,
    ) {
        cancelAlarm(context, schedule)

        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(alarmManager)) {
            Log.w(TAG, "정시 알람 권한이 없어 부정확 알람으로 겁니다.")
        }

        scheduleOne(context, alarmManager, schedule, AlarmKind.START, schedule.startTime.toDate().time, now, isFullScreen)
        schedule.endTime?.let { end ->
            scheduleOne(context, alarmManager, schedule, AlarmKind.END, end.toDate().time, now, isFullScreen)
        }
    }

    /**
     * 알람 하나를 건다.
     *
     * Intent 만들기부터 AlarmManager 호출까지 한 함수 안에 둔다. 나눠 두면 정적 분석이 대상
     * 컴포넌트를 따라가지 못해 「암시적 PendingIntent」 로 본다. 실제로는 [AlarmReceiver] 로
     * 못박혀 있고 FLAG_IMMUTABLE 이라 받는 쪽이 고칠 수도 없다(#117).
     */
    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        schedule: Schedule,
        kind: AlarmKind,
        triggerTime: Long,
        now: Long,
        isFullScreen: Boolean,
    ) {
        if (triggerTime <= now) {
            Log.d(TAG, "${kind.label} 시각이 이미 지났습니다: ${schedule.title}")
            return
        }

        val requestCode = requestCodeOf(schedule.id, kind)
        val intent =
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra("title", "${schedule.title} (${kind.label})")
                putExtra("desc", schedule.description)
                putExtra("isFullScreen", isFullScreen)
                putExtra("scheduleId", schedule.id)
                putExtra("alarmType", kind.label)
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        try {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                // 정시 허용이 없을 때의 차선. 몇 분 늦을 수 있지만 절전 상태에서도 울린다.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "${kind.label} 알람 예약: ${schedule.title} at ${Date(triggerTime)} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "${kind.label} 알람 예약 실패: ${e.message}")
        }
    }

    /** 일정의 시작·종료 알람을 지운다. */
    fun cancelAlarm(
        context: Context,
        schedule: Schedule,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmKind.entries.forEach { kind -> cancelOne(context, alarmManager, schedule, kind) }
    }

    /**
     * 알람 하나를 지운다.
     *
     * 대상이 맞는지는 자리 번호와 컴포넌트로 정해진다. extras 는 보지 않으므로 걸 때와 같은
     * 모양으로 다시 만들어 넘기면 된다. 여기서도 Intent 와 PendingIntent 를 한 자리에 둔다.
     */
    private fun cancelOne(
        context: Context,
        alarmManager: AlarmManager,
        schedule: Schedule,
        kind: AlarmKind,
    ) {
        val requestCode = requestCodeOf(schedule.id, kind)
        try {
            val intent =
                Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("title", "${schedule.title} (${kind.label})")
                    putExtra("desc", schedule.description)
                    putExtra("scheduleId", schedule.id)
                    putExtra("alarmType", kind.label)
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "${kind.label} 알람 취소: ${schedule.title} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "${kind.label} 알람 취소 실패: ${e.message}")
        }
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun requestCodeOf(
        scheduleId: String,
        kind: AlarmKind,
    ): Int =
        when (kind) {
            AlarmKind.START -> generateStartRequestCode(scheduleId)
            AlarmKind.END -> generateEndRequestCode(scheduleId)
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
