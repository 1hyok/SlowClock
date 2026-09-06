package com.example.slowclock.core.alarm

import android.app.AlarmManager
import android.content.Context
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduledAlarm
import com.example.slowclock.data.remote.repository.ScheduledAlarmRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일정 알람 예약·취소. ViewModel 이 Context 를 들지 않도록 앱 Context 를 여기서 주입받는다.
 * 실제 AlarmManager 조작은 [ScheduleAlarmHelper] 가 한다.
 *
 * 예약·취소와 같은 자리에서 기기 안 장부([ScheduledAlarmRepository])를 함께 갱신한다. 장부가
 * 없으면 재부팅 뒤에 무엇을 다시 걸어야 하는지 아무도 모른다(#127). 두 일을 한 자리에 묶어 두는
 * 것이 이 클래스의 몫이라, 저장소를 1:1 로 감싸는 프록시가 아니다.
 */
@Singleton
class AlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val scheduledAlarms: ScheduledAlarmRepository,
    ) {
        fun schedule(schedule: Schedule) {
            ScheduleAlarmHelper.scheduleAlarm(context, schedule)
            val record = schedule.toRecord()
            // 걸린 것이 하나도 없으면 기록도 남기지 않는다. 남기면 부팅마다 훑고 버려야 한다.
            if (record.isLive(System.currentTimeMillis())) {
                scheduledAlarms.save(record)
            } else {
                scheduledAlarms.remove(record.id)
            }
        }

        fun cancel(schedule: Schedule) {
            ScheduleAlarmHelper.cancelAlarm(context, schedule)
            scheduledAlarms.remove(schedule.id)
        }

        /**
         * 이 기기에 걸린 알람을 전부 지운다. 계정을 지우면 서버의 일정이 사라지므로 기기에 남은
         * 알람은 근거를 잃는다. 지우지 않으면 계정을 지운 뒤에도 알람이 울리고 재부팅 뒤에도
         * 되살아난다(#127).
         */
        fun cancelAll() {
            scheduledAlarms.all().forEach { ScheduleAlarmHelper.cancelAlarm(context, it.toSchedule()) }
            scheduledAlarms.clear()
        }

        /**
         * 기기 안 장부만으로 알람을 다시 건다. 네트워크와 로그인을 타지 않는다 — 재부팅 직후에는
         * 둘 다 없을 수 있고, 그때 못 걸면 그날 알람이 통째로 사라진다(#127).
         *
         * 이미 지난 알람은 다시 걸지 않고 장부에서 지운다. 세 시간 전 일정이 부팅하자마자 울리는
         * 것은 도움이 아니라 오작동이다.
         */
        fun restoreAll(nowMillis: Long = System.currentTimeMillis()) {
            val (live, expired) = scheduledAlarms.all().partition { it.isLive(nowMillis) }
            expired.forEach { scheduledAlarms.remove(it.id) }
            live.forEach { ScheduleAlarmHelper.scheduleAlarm(context, it.toSchedule()) }
        }

        /**
         * 정시 알람을 걸 수 있는지. Android 12 부터 사용자가 설정에서 따로 허용해야 한다.
         * false 면 화면이 먼저 이유를 설명하고 사용자가 원할 때만 설정으로 보낸다(#83).
         */
        fun canScheduleExactAlarms(): Boolean {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        private fun Schedule.toRecord() =
            ScheduledAlarm(
                id = id,
                title = title,
                description = description,
                startMillis = startTime.toDate().time,
                endMillis = endTime?.toDate()?.time,
            )

        // ScheduleAlarmHelper 는 Schedule 의 id·title·description·startTime·endTime 만 읽는다.
        // 나머지 필드가 기본값이어도 예약과 취소가 같은 자리를 가리킨다.
        private fun ScheduledAlarm.toSchedule() =
            Schedule(
                id = id,
                title = title,
                description = description,
                startTime = Timestamp(Date(startMillis)),
                endTime = endMillis?.let { Timestamp(Date(it)) },
            )
    }
