package com.example.slowclock.core.alarm

import android.app.AlarmManager
import android.content.Context
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduledAlarm
import com.example.slowclock.data.remote.repository.ScheduledAlarmRepository
import com.example.slowclock.data.remote.repository.SnoozedAlarm
import com.example.slowclock.data.remote.repository.SnoozedAlarmRepository
import com.example.slowclock.util.Recurrence
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
        private val snoozedAlarms: SnoozedAlarmRepository,
    ) {
        /**
         * 일정의 다음 회차 알람을 건다.
         *
         * 되풀이하는 일정도 한 번에 하나만 건다. 며칠치를 미리 걸어 두지 않는 이유는, 걸어 둔
         * 것이 사라져도(재부팅·강제 종료) 지금 시각에서 다시 세면 그만이기 때문이다. 다음 회차는
         * 알람이 울릴 때 [AlarmReceiver] 가 이어서 건다(#130).
         */
        fun schedule(schedule: Schedule) {
            book(schedule.toRecord(), System.currentTimeMillis())
        }

        fun cancel(schedule: Schedule) {
            ScheduleAlarmHelper.cancelAlarm(context, schedule)
            scheduledAlarms.remove(schedule.id)
            forgetSnoozesOf(schedule.id)
        }

        /**
         * 서버에 있는 일정으로 이 기기의 알람을 맞춘다.
         *
         * 장부는 기기 안에만 있고 백업·기기 이전에서 일부러 뺐다. 그래서 폰을 바꾸거나 앱을 다시
         * 깔면 장부가 빈 채로 시작하고, 서버에 일정이 그대로 있어도 알람은 하나도 걸리지 않는다.
         * 사용자에게는 「일정은 보이는데 안 울린다」 로만 보인다(#176).
         *
         * 내용이 그대로인 일정은 걸린 회차를 지킨다. 앱을 열 때마다 다시 걸면 방금 미뤄 둔
         * 알람이 그때마다 사라진다.
         */
        fun syncWith(schedules: List<Schedule>) {
            val nowMillis = System.currentTimeMillis()
            val incoming = schedules.associateBy { it.id }
            val booked = scheduledAlarms.all()
            // 서버에서 사라진 일정의 알람은 지운다. 다른 기기에서 지운 일정이 이 기기에서만
            // 계속 울리는 것도 이 자리에서 함께 막힌다.
            booked.filterNot { incoming.containsKey(it.id) }.forEach { stale ->
                ScheduleAlarmHelper.cancelAlarm(context, stale.toSchedule())
                scheduledAlarms.remove(stale.id)
                forgetSnoozesOf(stale.id)
            }
            val bookedById = booked.associateBy { it.id }
            incoming.values.forEach { schedule ->
                val fresh = schedule.toRecord()
                val existing = bookedById[schedule.id]
                // 장부에도 없고 걸 것도 남지 않은 일정은 손댈 것이 없다. 지난 일정을 앱을 열
                // 때마다 훑어 취소하지 않는다.
                if (existing == null && !fresh.isLive(nowMillis)) return@forEach
                book(if (existing != null && existing.sameContentAs(fresh)) existing else fresh, nowMillis)
            }
        }

        /**
         * 알람 하나를 미룬다. 걸어 둔 사실을 기기에 남긴다.
         *
         * 다시 알림은 AlarmManager 에만 걸려 있어 재부팅과 앱 교체로 사라진다. 미룬 사람은 5분
         * 뒤 울릴 것이라고 믿고 있고, 그 믿음이 이 기능의 전부다(#177).
         */
        fun snooze(
            baseRequestCode: Int,
            scheduleId: String,
            title: String,
            desc: String,
            isFullScreen: Boolean,
            snoozeCount: Int,
        ) {
            val triggerAt = SnoozePolicy.nextTriggerAt(System.currentTimeMillis())
            ScheduleAlarmHelper.scheduleSnooze(
                context = context,
                baseRequestCode = baseRequestCode,
                scheduleId = scheduleId,
                title = title,
                desc = desc,
                isFullScreen = isFullScreen,
                snoozeCount = snoozeCount,
                triggerTime = triggerAt,
            )
            snoozedAlarms.save(
                SnoozedAlarm(
                    baseRequestCode = baseRequestCode,
                    scheduleId = scheduleId,
                    title = title,
                    description = desc,
                    isFullScreen = isFullScreen,
                    snoozeCount = snoozeCount,
                    triggerAtMillis = triggerAt,
                ),
            )
        }

        /** 미뤄 둔 알람이 울렸다. 기록을 지운다. 남기면 다음 부팅에 한 번 더 울린다. */
        fun snoozeFired(baseRequestCode: Int) {
            snoozedAlarms.remove(baseRequestCode)
        }

        private fun forgetSnoozesOf(scheduleId: String) {
            snoozedAlarms.all().filter { it.scheduleId == scheduleId }.forEach { snoozedAlarms.remove(it.baseRequestCode) }
        }

        /**
         * 방금 울린 일정의 다음 회차를 건다. 걸 것이 더 없으면 장부에서 지운다.
         *
         * 걸어 둔 회차에 아직 울릴 것이 남아 있으면(시작만 울리고 종료가 남았을 때) 아무것도
         * 하지 않는다. 알람을 다시 거는 일은 그 일정의 자리를 먼저 비우므로, 앞당겨 걸면 아직
         * 안 울린 종료 알람이 지워진다(#163).
         */
        fun scheduleNextOccurrence(scheduleId: String) {
            val record = scheduledAlarms.all().firstOrNull { it.id == scheduleId } ?: return
            book(record, System.currentTimeMillis())
        }

        /**
         * 장부의 기록 하나를 지금 걸어야 할 회차로 맞춘다.
         *
         * 이미 그 회차가 걸려 있으면 손대지 않는다. 예약·복원·다음 회차가 모두 이 자리를 지나므로
         * 「어느 회차가 걸려 있나」 를 아는 곳이 하나뿐이다.
         */
        private fun book(
            record: ScheduledAlarm,
            nowMillis: Long,
        ) {
            val occurrence = record.occurrenceToBook(nowMillis)
            if (occurrence == null) {
                // 걸 것이 없으면 자리도 장부도 비운다. 남기면 부팅마다 훑고 버려야 한다.
                ScheduleAlarmHelper.cancelAlarm(context, record.toSchedule())
                scheduledAlarms.remove(record.id)
                return
            }
            if (occurrence == record.bookedStartMillis) return

            ScheduleAlarmHelper.scheduleAlarm(context, record.toSchedule(occurrence))
            scheduledAlarms.save(record.copy(bookedStartMillis = occurrence))
        }

        /**
         * 이 기기에 걸린 알람을 전부 지운다. 계정을 지우면 서버의 일정이 사라지므로 기기에 남은
         * 알람은 근거를 잃는다. 지우지 않으면 계정을 지운 뒤에도 알람이 울리고 재부팅 뒤에도
         * 되살아난다(#127).
         */
        fun cancelAll() {
            scheduledAlarms.all().forEach { ScheduleAlarmHelper.cancelAlarm(context, it.toSchedule()) }
            scheduledAlarms.clear()
            // 미뤄 둔 알람은 일정 알람과 자리 번호가 같아 위 취소로 함께 지워진다. 장부만 남으면
            // 다음 부팅에 근거 없는 알람이 되살아나므로 여기서 함께 비운다(#177).
            snoozedAlarms.clear()
        }

        /** 알람을 거는 쪽이 보는 값. 장부의 기록을 [occurrenceStartMillis] 회차로 옮겨 놓은 일정이다. */
        private fun ScheduledAlarm.toSchedule(occurrenceStartMillis: Long): Schedule {
            // 종료는 시작에서 떨어진 만큼 함께 옮긴다. 자정을 넘는 일정도 길이가 유지된다.
            val shifted = occurrenceStartMillis - startMillis
            return toSchedule().copy(
                startTime = Timestamp(Date(startMillis + shifted)),
                endTime = endMillis?.let { Timestamp(Date(it + shifted)) },
            )
        }

        /**
         * 기기 안 장부만으로 알람을 다시 건다. 네트워크와 로그인을 타지 않는다 — 재부팅 직후에는
         * 둘 다 없을 수 있고, 그때 못 걸면 그날 알람이 통째로 사라진다(#127).
         *
         * 이미 지난 알람은 다시 걸지 않고 장부에서 지운다. 세 시간 전 일정이 부팅하자마자 울리는
         * 것은 도움이 아니라 오작동이다.
         */
        fun restoreAll() {
            val nowMillis = System.currentTimeMillis()
            scheduledAlarms.all().forEach { record ->
                // 재부팅으로 걸린 것이 전부 사라졌으므로, 지키던 회차라도 다시 걸어야 한다.
                book(record.copy(bookedStartMillis = null), nowMillis)
            }
            restoreSnoozes(nowMillis)
        }

        /**
         * 미뤄 둔 알람을 다시 건다. 이미 지난 것은 버린다.
         *
         * 일정 알람보다 뒤에 건다. 자리 번호가 같아 순서가 바뀌면 방금 되살린 다시 알림을
         * 일정 알람 쪽 취소가 지운다(#177).
         */
        private fun restoreSnoozes(nowMillis: Long) {
            snoozedAlarms.all().forEach { snoozed ->
                if (snoozed.triggerAtMillis <= nowMillis) {
                    // 부팅하자마자 지난 알람이 울리는 것은 도움이 아니라 오작동이다.
                    snoozedAlarms.remove(snoozed.baseRequestCode)
                    return@forEach
                }
                ScheduleAlarmHelper.scheduleSnooze(
                    context = context,
                    baseRequestCode = snoozed.baseRequestCode,
                    scheduleId = snoozed.scheduleId,
                    title = snoozed.title,
                    desc = snoozed.description,
                    isFullScreen = snoozed.isFullScreen,
                    snoozeCount = snoozed.snoozeCount,
                    triggerTime = snoozed.triggerAtMillis,
                )
            }
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

        private fun Schedule.toRecord() =
            ScheduledAlarm(
                id = id,
                title = title,
                description = description,
                startMillis = startTime.toDate().time,
                endMillis = endTime?.toDate()?.time,
                recurrence = Recurrence.of(recurring, recurringType).name,
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
