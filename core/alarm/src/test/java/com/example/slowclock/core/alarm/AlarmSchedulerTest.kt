package com.example.slowclock.core.alarm

import android.content.Context
import com.example.slowclock.data.remote.repository.ScheduledAlarmRepository
import com.example.slowclock.data.remote.repository.SnoozedAlarm
import com.example.slowclock.data.remote.repository.SnoozedAlarmRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {
    @Test
    fun `마지막 회차가 끝나 일정 장부가 비어도 미룬 예약을 모두 취소한다`() {
        val context = mockk<Context>()
        val scheduled = mockk<ScheduledAlarmRepository>(relaxed = true)
        val snoozed = mockk<SnoozedAlarmRepository>(relaxed = true)
        val snoozes =
            listOf(42, 43).map { requestCode ->
                SnoozedAlarm(requestCode, "ended", "약", "", true, 1, System.currentTimeMillis() + 300_000)
            }
        // 마지막 알람은 수신할 때 일정 장부에서 빠진다. 그 뒤 사용자가 미루면 이 목록만 남는다.
        every { scheduled.all() } returns emptyList()
        every { snoozed.all() } returns snoozes
        val pending = snoozes.map { it.baseRequestCode }.toMutableSet()
        mockkObject(ScheduleAlarmHelper)
        try {
            every { ScheduleAlarmHelper.cancelSnooze(context, any()) } answers {
                pending.remove(secondArg<Int>())
                Unit
            }

            AlarmScheduler(context, scheduled, snoozed).cancelAll()

            assertTrue("로그아웃 뒤에도 미룸 예약이 남아 있다", pending.isEmpty())
            verify(exactly = 1) { snoozed.clear() }
        } finally {
            unmockkObject(ScheduleAlarmHelper)
        }
    }
}
