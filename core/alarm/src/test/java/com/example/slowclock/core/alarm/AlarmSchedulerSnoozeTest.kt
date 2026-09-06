package com.example.slowclock.core.alarm

import android.content.Context
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduledAlarm
import com.example.slowclock.data.remote.repository.ScheduledAlarmRepository
import com.example.slowclock.data.remote.repository.SnoozedAlarm
import com.example.slowclock.data.remote.repository.SnoozedAlarmRepository
import com.example.slowclock.util.Recurrence
import com.google.firebase.Timestamp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class AlarmSchedulerSnoozeTest {
    private val now = System.currentTimeMillis()
    private val context = mockk<Context>()
    private val scheduledRepository = mockk<ScheduledAlarmRepository>()
    private val snoozedRepository = mockk<SnoozedAlarmRepository>()
    private val scheduled = mutableMapOf<String, ScheduledAlarm>()
    private val snoozed = mutableMapOf<Int, SnoozedAlarm>()
    private val scheduler = AlarmScheduler(context, scheduledRepository, snoozedRepository)
    private val record =
        ScheduledAlarm(
            id = "s1",
            title = "약",
            description = "",
            startMillis = now - 3_600_000,
            recurrence = Recurrence.DAILY.name,
            bookedStartMillis = now - 3_600_000,
        )
    private val snooze =
        SnoozedAlarm(
            baseRequestCode = ScheduleAlarmHelper.generateStartRequestCode(record.id),
            scheduleId = record.id,
            title = "약 (시작)",
            description = "",
            isFullScreen = true,
            snoozeCount = 1,
            triggerAtMillis = now + 300_000,
        )

    @Before
    fun setUp() {
        scheduled[record.id] = record
        snoozed[snooze.baseRequestCode] = snooze
        every { scheduledRepository.all() } answers { scheduled.values.toList() }
        every { scheduledRepository.save(any()) } answers {
            val value = firstArg<ScheduledAlarm>()
            scheduled[value.id] = value
        }
        every { scheduledRepository.remove(any()) } answers {
            scheduled.remove(firstArg<String>())
            Unit
        }
        every { snoozedRepository.all() } answers { snoozed.values.toList() }
        every { snoozedRepository.remove(any()) } answers {
            snoozed.remove(firstArg<Int>())
            Unit
        }
        mockkObject(ScheduleAlarmHelper, AlarmNotifications)
        every { AlarmNotifications.invalidate(any(), any()) } returns Unit
        every { AlarmNotifications.clear(any()) } returns Unit
        every { AlarmNotifications.retainSchedules(any(), any()) } returns Unit
        every { ScheduleAlarmHelper.scheduleAlarm(context, any(), any()) } returns Unit
        every { ScheduleAlarmHelper.cancelAlarm(context, any()) } returns Unit
        every { ScheduleAlarmHelper.cancelSnooze(context, any()) } returns Unit
        every { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(ScheduleAlarmHelper, AlarmNotifications)
    }

    @Test
    fun `재부팅 복원은 다음 회차를 다시 걸어도 미룸 시각과 횟수를 지킨다`() {
        scheduler.restoreAll()

        assertEquals(snooze, snoozed[snooze.baseRequestCode])
        verify(exactly = 1) {
            ScheduleAlarmHelper.scheduleSnooze(
                context,
                snooze.baseRequestCode,
                snooze.scheduleId,
                snooze.title,
                snooze.description,
                snooze.isFullScreen,
                snooze.snoozeCount,
                snooze.triggerAtMillis,
            )
        }
    }

    @Test
    fun `일정을 수정해 취소한 미룸은 재부팅해도 되살아나지 않는다`() {
        scheduler.schedule(scheduleOf(record).copy(title = "바꾼 약", startTime = Timestamp(Date(now + 3_600_000))))
        assertTrue(snoozed.isEmpty())

        scheduler.restoreAll()

        verify(exactly = 0) { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `일정을 삭제해 취소한 미룸은 재부팅해도 되살아나지 않는다`() {
        scheduler.cancel(scheduleOf(record))
        assertTrue(snoozed.isEmpty())

        scheduler.restoreAll()

        verify(exactly = 0) { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `다음 회차 예약이 취소한 미룸은 재부팅해도 되살아나지 않는다`() {
        scheduler.scheduleNextOccurrence(record.id)
        assertTrue(snoozed.isEmpty())

        scheduler.restoreAll()

        verify(exactly = 0) { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `내용과 회차가 그대로인 서버 동기화는 미룸을 지킨다`() {
        val ongoing = record.copy(endMillis = now + 3_600_000)
        scheduled[record.id] = ongoing

        scheduler.syncWith(listOf(scheduleOf(ongoing)))

        assertEquals(snooze, snoozed[snooze.baseRequestCode])
        verify(exactly = 0) { ScheduleAlarmHelper.scheduleAlarm(context, any(), any()) }
        verify(exactly = 0) { ScheduleAlarmHelper.cancelAlarm(context, any()) }
    }

    @Test
    fun `서버에서 삭제된 일정은 일정 장부 없이 남은 미룸도 취소한다`() {
        scheduled.clear()

        scheduler.syncWith(emptyList())

        assertTrue(snoozed.isEmpty())
        verify(exactly = 1) { ScheduleAlarmHelper.cancelSnooze(context, snooze.baseRequestCode) }
    }

    @Test
    fun `OS 예약 실패는 성공 장부로 남지 않고 다음 앱 복원이 같은 회차를 재시도한다`() {
        val future = record.copy(startMillis = now + 3_600_000, bookedStartMillis = null)
        scheduled.clear()
        every { ScheduleAlarmHelper.scheduleAlarm(context, any(), any()) } throws IllegalStateException("quota")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) { scheduler.schedule(scheduleOf(future)) }
        assertEquals(future, scheduled[future.id])

        every { ScheduleAlarmHelper.scheduleAlarm(context, any(), any()) } returns Unit
        scheduler.restoreOnAppStart()
        assertEquals(future.startMillis, scheduled[future.id]?.bookedStartMillis)
    }

    @Test
    fun `최초 앱 복원은 booked 장부도 다시 걸고 두 번째 진입에서는 미룸을 보존한다`() {
        val future = record.copy(startMillis = now + 3_600_000, bookedStartMillis = now + 3_600_000)
        scheduled[future.id] = future

        scheduler.restoreOnAppStart()
        scheduler.restoreOnAppStart()

        verify(exactly = 1) { ScheduleAlarmHelper.scheduleAlarm(context, any(), any()) }
        assertEquals(snooze, snoozed[snooze.baseRequestCode])
    }

    @Test
    fun `한 일정 복원이 실패해도 나머지 일정과 미룸 복원은 진행한다`() {
        val second = record.copy(id = "s2")
        scheduled[second.id] = second
        every { ScheduleAlarmHelper.scheduleAlarm(context, match { it.id == record.id }, any()) } throws IllegalStateException("quota")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) { scheduler.restoreAll() }

        assertEquals(null, scheduled[record.id]?.bookedStartMillis)
        assertTrue(scheduled[second.id]?.bookedStartMillis != null)
        verify(exactly = 1) { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `미룸 예약 실패는 성공 기록을 남기지 않는다`() {
        snoozed.clear()
        every { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("quota")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            scheduler.snooze(snooze.baseRequestCode, record.id, "약", "", true, 1)
        }

        assertTrue(snoozed.isEmpty())
    }

    @Test
    fun `삭제된 알림의 늦은 미룸은 예약과 장부를 만들지 않는다`() {
        every { AlarmNotifications.isCurrent(context, any(), any()) } returns false

        val accepted = scheduler.snoozeFromNotification(42, "deleted", "약", "", true, 1, 1042, "old")

        org.junit.Assert.assertFalse(accepted)
        verify(exactly = 0) { ScheduleAlarmHelper.scheduleSnooze(context, any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun scheduleOf(record: ScheduledAlarm) =
        Schedule(
            id = record.id,
            title = record.title,
            description = record.description,
            startTime = Timestamp(Date(record.startMillis)),
            endTime = record.endMillis?.let { Timestamp(Date(it)) },
            recurring = true,
            recurringType = Recurrence.DAILY.type,
        )
}
