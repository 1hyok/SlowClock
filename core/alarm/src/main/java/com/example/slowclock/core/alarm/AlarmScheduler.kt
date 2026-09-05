package com.example.slowclock.core.alarm

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
    }
