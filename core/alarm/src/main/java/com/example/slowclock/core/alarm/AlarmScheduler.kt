package com.example.slowclock.core.alarm

import android.app.AlarmManager
import android.content.Context
import com.example.slowclock.data.model.Schedule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일정 알람 예약·취소. ViewModel 이 Context 를 들지 않도록 앱 Context 를 여기서 주입받는다.
 * 실제 AlarmManager 조작은 [ScheduleAlarmHelper] 가 한다.
 */
@Singleton
class AlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule(schedule: Schedule) {
            ScheduleAlarmHelper.scheduleAlarm(context, schedule)
        }

        fun cancel(schedule: Schedule) {
            ScheduleAlarmHelper.cancelAlarm(context, schedule)
        }

        /**
         * 정시 알람을 걸 수 있는지. Android 12 부터 사용자가 설정에서 따로 허용해야 한다.
         * false 면 화면이 먼저 이유를 설명하고 사용자가 원할 때만 설정으로 보낸다(#83).
         */
        fun canScheduleExactAlarms(): Boolean {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        /**
         * 잠금 화면을 덮는 큰 알람 화면을 띄울 수 있는지. false 면 소리와 진동은 그대로 나가지만
         * 그 화면은 뜨지 않는다. 화면이 이유를 설명하고 사용자가 원할 때만 설정으로 보낸다(#128).
         *
         * 화면 모듈이 NotificationManager 를 직접 만지지 않도록 이 클래스를 통로로 쓴다.
         * 정시 알람 안내(#83)가 이미 같은 자리를 쓰고 있어 통로를 둘로 가르지 않는다.
         */
        fun canUseFullScreenAlarm(): Boolean = context.canUseFullScreenAlarm()
    }
