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
internal enum class AlarmKind(
    val label: String,
) {
    START("시작"),
    END("종료"),
}

/**
 * AlarmManager 를 직접 만지는 유일한 자리. 모듈 밖에서는 [AlarmScheduler] 만 보인다.
 *
 * `internal` 인 이유는 기록 때문이다. 예약·취소는 기기 안 장부 갱신과 짝을 이뤄야 하고
 * 그 짝을 [AlarmScheduler] 가 맞춘다. 여기로 바로 들어오는 문이 열려 있으면 장부가 새고,
 * 그 결과는 「재부팅하니 그 알람만 안 울린다」 로만 드러난다(#127).
 */
internal object ScheduleAlarmHelper {
    private const val TAG = "ScheduleAlarmHelper"

    /**
     * 다시 알림으로 다시 건 알람임을 가리키는 action.
     *
     * PendingIntent 가 같은 자리인지는 requestCode 와 `Intent.filterEquals` 로 정해지고,
     * filterEquals 는 action 과 component 를 보지 extras 는 보지 않는다. 일정 알람의 번호는
     * `hashCode() * 2` 와 `* 2 + 1` 이라 짝수 전체와 홀수 전체, 곧 32비트 정수 전체를 덮으므로
     * 「비어 있는 번호 대역」 이 없다. 그래서 다시 알림은 번호가 아니라 action 으로 가른다(#129).
     *
     * 반대로 일정 알람 쪽에 action 을 새로 붙이면 안 된다. 이미 기기에 걸려 있는 PendingIntent 와
     * filterEquals 가 어긋나 취소가 빗나가고, 지워졌어야 할 알람이 남는다(#117 과 같은 증상).
     */
    internal const val ACTION_SNOOZE_ALARM = "com.example.slowclock.action.SNOOZE_ALARM"

    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_DESC = "desc"
    internal const val EXTRA_FULL_SCREEN = "isFullScreen"
    internal const val EXTRA_SCHEDULE_ID = "scheduleId"
    internal const val EXTRA_ALARM_TYPE = "alarmType"
    internal const val EXTRA_REQUEST_CODE = "requestCode"
    internal const val EXTRA_SNOOZE_COUNT = "snoozeCount"

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
     *
     * 생성자로 대상을 준 뒤 `setClass` 로 한 번 더 지정한다. 중복처럼 보이지만 지우면 안 된다.
     * CodeQL 의 Kotlin 추출기(K2)가 `Intent(Context, Class)` 의 둘째 인자를 데이터베이스에
     * 남기지 않아, 생성자만으로는 대상이 명시적이라는 것을 증명하지 못한다(github/codeql#20153).
     * `setClass` 호출은 인자 타입을 보지 않는 갈래로 판정되어 그 자리를 세운다(#121).
     * 런타임 동작은 같다. 두 방법이 만드는 ComponentName 이 같고, 알람 취소가 대상을 맞추는
     * `filterEquals` 도 그 값만 본다. 그래서 예약과 취소가 계속 같은 자리를 가리킨다.
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
            Intent(context, AlarmReceiver::class.java)
                // 대상을 한 번 더 못박는다. 중복이 아니라 정적 분석을 위한 것이다(#121).
                .setClass(context, AlarmReceiver::class.java)
                .apply {
                    putExtra(EXTRA_TITLE, "${schedule.title} (${kind.label})")
                    putExtra(EXTRA_DESC, schedule.description)
                    putExtra(EXTRA_FULL_SCREEN, isFullScreen)
                    putExtra(EXTRA_SCHEDULE_ID, schedule.id)
                    putExtra(EXTRA_ALARM_TYPE, kind.label)
                    // 이 알람이 어느 자리 것인지 받는 쪽이 알아야 다시 알림을 같은 자리에 건다(#129).
                    putExtra(EXTRA_REQUEST_CODE, requestCode)
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
                // setAlarmClock 은 절전에 가장 강하고, 백그라운드에서 포그라운드 서비스를 시작할 수
                // 있는 예외가 문서에 명시된 몇 안 되는 API 다. 상태 표시줄에 다음 알람 아이콘이
                // 떠서 사용자가 「걸려 있다」 를 눈으로 확인할 수도 있다(#122).
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, showAlarmIntent(context)),
                    pendingIntent,
                )
            } else {
                // 정시 허용이 없을 때의 차선. 몇 분 늦을 수 있지만 절전 상태에서도 울린다.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "${kind.label} 알람 예약: ${schedule.title} at ${Date(triggerTime)} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "${kind.label} 알람 예약 실패: ${e.message}")
        }
    }

    /**
     * 일정의 시작·종료 알람을 지운다. 미뤄 둔 다시 알림도 같은 자리에서 함께 지운다.
     *
     * 다시 알림은 action 으로만 갈린 별개의 PendingIntent 라, 일정 알람만 지우면 일정을 지운
     * 뒤에도 미뤄 둔 알람이 몇 분 뒤 울린다(#129).
     */
    fun cancelAlarm(
        context: Context,
        schedule: Schedule,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmKind.entries.forEach { kind ->
            cancelOne(context, alarmManager, schedule, kind)
            cancelSnooze(context, alarmManager, requestCodeOf(schedule.id, kind))
        }
    }

    /**
     * 5분 뒤 한 번 울릴 알람을 건다. 원래 일정의 알람은 건드리지 않는다.
     *
     * Intent 만들기부터 AlarmManager 호출까지 한 함수에 두는 것, 생성자에 이어 `setClass` 로
     * 대상을 한 번 더 못박는 것은 [scheduleOne] 과 같은 이유다(#117 · #121).
     */
    fun scheduleSnooze(
        context: Context,
        baseRequestCode: Int,
        scheduleId: String,
        title: String,
        desc: String,
        isFullScreen: Boolean,
        snoozeCount: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = SnoozePolicy.nextTriggerAt(System.currentTimeMillis())
        val intent =
            Intent(context, AlarmReceiver::class.java)
                .setClass(context, AlarmReceiver::class.java)
                .setAction(ACTION_SNOOZE_ALARM)
                .apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DESC, desc)
                    putExtra(EXTRA_FULL_SCREEN, isFullScreen)
                    putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(EXTRA_REQUEST_CODE, baseRequestCode)
                    putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                }
        // FLAG_UPDATE_CURRENT 라야 두 번째 다시 알림에서 snoozeCount 가 실제로 올라간다.
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                baseRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        try {
            if (canScheduleExact(alarmManager)) {
                // 일정 알람과 같은 API 를 쓴다. setAlarmClock 은 절전에 가장 강하고, 백그라운드에서
                // 포그라운드 서비스를 시작할 수 있는 문서상의 예외다. 다시 알림도 결국
                // AlarmReceiver 를 거쳐 AlarmTriggerService 로 가므로 그 예외가 그대로 필요하다(#122).
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, showAlarmIntent(context)),
                    pendingIntent,
                )
            } else {
                // API 32 에서 정시 허용이 없을 때만 온다. setAndAllowWhileIdle 은 Doze 에서
                // 앱당 약 9분에 한 번으로 제한되므로 5분보다 늦을 수 있다. 안 울리는 것보다 낫다.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "다시 알림 예약: $title at ${Date(triggerTime)} (자리=$baseRequestCode, 횟수=$snoozeCount)")
        } catch (e: Exception) {
            Log.e(TAG, "다시 알림 예약 실패: ${e.message}")
        }
    }

    /** 미뤄 둔 다시 알림 하나를 지운다. 자리는 일정 알람과 번호가 같고 action 으로 갈린다. */
    private fun cancelSnooze(
        context: Context,
        alarmManager: AlarmManager,
        baseRequestCode: Int,
    ) {
        try {
            val intent =
                Intent(context, AlarmReceiver::class.java)
                    .setClass(context, AlarmReceiver::class.java)
                    .setAction(ACTION_SNOOZE_ALARM)
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    baseRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "다시 알림 취소 실패: ${e.message}")
        }
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
                Intent(context, AlarmReceiver::class.java)
                    // 예약 쪽과 같은 이유로 대상을 다시 지정한다(#121).
                    .setClass(context, AlarmReceiver::class.java)
                    .apply {
                        putExtra(EXTRA_TITLE, "${schedule.title} (${kind.label})")
                        putExtra(EXTRA_DESC, schedule.description)
                        putExtra(EXTRA_SCHEDULE_ID, schedule.id)
                        putExtra(EXTRA_ALARM_TYPE, kind.label)
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

    /** 상태 표시줄의 알람 아이콘을 눌렀을 때 열리는 화면. 앱의 메인으로 보낸다. */
    private fun showAlarmIntent(context: Context): PendingIntent? =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { launch ->
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

    internal fun requestCodeOf(
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
