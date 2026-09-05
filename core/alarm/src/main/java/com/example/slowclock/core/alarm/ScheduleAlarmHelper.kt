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
    private const val END_ALARM_OFFSET = 9999

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

        // 알람 권한 확인 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "정확한 알람 권한이 없습니다. 설정에서 권한을 허용해주세요.")
            return
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
            val intent = createAlarmIntent(context, schedule, "시작", isFullScreen)
            val requestCode = generateStartRequestCode(schedule.id)
            val pendingIntent = createPendingIntent(context, intent, requestCode)

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
            val intent = createAlarmIntent(context, schedule, "종료", isFullScreen)
            val requestCode = generateEndRequestCode(schedule.id)
            val pendingIntent = createPendingIntent(context, intent, requestCode)

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
     * 알람 Intent를 생성합니다.
     */
    private fun createAlarmIntent(
        context: Context,
        schedule: Schedule,
        type: String,
        isFullScreen: Boolean,
    ): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", "${schedule.title} ($type)")
            putExtra("desc", schedule.description)
            putExtra("isFullScreen", isFullScreen)
            putExtra("scheduleId", schedule.id)
            putExtra("alarmType", type)
        }

    /**
     * PendingIntent를 생성합니다.
     */
    private fun createPendingIntent(
        context: Context,
        intent: Intent,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 정확한 알람을 설정합니다.
     */
    private fun setExactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
    ) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
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
            val intent =
                Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("title", "${schedule.title} ($type)")
                    putExtra("desc", schedule.description)
                    putExtra("scheduleId", schedule.id)
                    putExtra("alarmType", type)
                }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // PendingIntent도 함께 취소
            Log.d(TAG, "🛑 $type 알람 취소 성공: ${schedule.title} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "$type 알람 취소 실패: ${e.message}")
        }
    }

    /**
     * RequestCode 생성 함수들
     */
    private fun generateStartRequestCode(scheduleId: String): Int = scheduleId.hashCode()

    private fun generateEndRequestCode(scheduleId: String): Int = scheduleId.hashCode() + END_ALARM_OFFSET
}
