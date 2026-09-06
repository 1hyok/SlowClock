package com.example.slowclock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 반복 일정의 회차 계산.
 *
 * 이 계산이 없던 동안 반복 일정은 첫날 한 번만 울리고 다음 날 목록에서도 사라졌다. 화면에는
 * 「매일」 이라고 적혀 있어 사용자는 걸려 있다고 믿는다(#130).
 */
class RecurrenceRuleTest {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 0,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                set(year, month - 1, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    // 2026-09-06 은 일요일이다.
    private val base = at(2026, 9, 6, hour = 9)

    @Test
    fun `반복이 꺼져 있으면 종류가 무엇이든 되풀이하지 않는다`() {
        assertEquals(Recurrence.NONE, Recurrence.of(recurring = false, recurringType = "daily"))
    }

    @Test
    fun `모르는 종류는 매일로 본다`() {
        // 화면의 기본값이 「매일」 이라 사용자가 그렇게 믿고 저장했을 값이다. 되풀이를 통째로
        // 버리면 그 일정은 조용히 한 번만 울린다.
        assertEquals(Recurrence.DAILY, Recurrence.of(recurring = true, recurringType = "yearly"))
        assertEquals(Recurrence.DAILY, Recurrence.of(recurring = true, recurringType = null))
    }

    @Test
    fun `매일 반복은 다음 날에도 같은 시각에 있다`() {
        val tomorrow = RecurrenceRule.occurrenceOn(base, Recurrence.DAILY, at(2026, 9, 7, hour = 0))

        assertEquals(at(2026, 9, 7, hour = 9), tomorrow)
    }

    @Test
    fun `첫 회차보다 앞선 날에는 없다`() {
        assertNull(RecurrenceRule.occurrenceOn(base, Recurrence.DAILY, at(2026, 9, 5, hour = 0)))
    }

    @Test
    fun `매주 반복은 같은 요일에만 있다`() {
        // 2026-09-13 은 다음 일요일, 09-12 는 토요일이다.
        assertEquals(at(2026, 9, 13, hour = 9), RecurrenceRule.occurrenceOn(base, Recurrence.WEEKLY, at(2026, 9, 13)))
        assertNull(RecurrenceRule.occurrenceOn(base, Recurrence.WEEKLY, at(2026, 9, 12)))
    }

    @Test
    fun `매월 반복은 같은 날짜에만 있다`() {
        assertEquals(at(2026, 10, 6, hour = 9), RecurrenceRule.occurrenceOn(base, Recurrence.MONTHLY, at(2026, 10, 6)))
        assertNull(RecurrenceRule.occurrenceOn(base, Recurrence.MONTHLY, at(2026, 10, 7)))
    }

    @Test
    fun `매월 31일은 그 날짜가 없는 달에 마지막 날로 당긴다`() {
        // 건너뛰면 두 달에 한 번씩 약이 조용히 빠진다. 놓치면 안 되는 일을 챙기는 앱이라
        // 거르는 쪽보다 당기는 쪽을 고른다.
        val monthEnd = at(2026, 1, 31, hour = 8)

        assertEquals(at(2026, 2, 28, hour = 8), RecurrenceRule.occurrenceOn(monthEnd, Recurrence.MONTHLY, at(2026, 2, 28)))
        assertNull(RecurrenceRule.occurrenceOn(monthEnd, Recurrence.MONTHLY, at(2026, 2, 27)))
    }

    @Test
    fun `되풀이하지 않는 일정은 그날에만 있다`() {
        assertEquals(base, RecurrenceRule.occurrenceOn(base, Recurrence.NONE, at(2026, 9, 6, hour = 20)))
        assertNull(RecurrenceRule.occurrenceOn(base, Recurrence.NONE, at(2026, 9, 7)))
    }

    @Test
    fun `다음 회차는 물어본 시각보다 뒤에 있다`() {
        val next = RecurrenceRule.nextOccurrenceAfter(base, Recurrence.DAILY, at(2026, 9, 6, hour = 10))

        assertEquals(at(2026, 9, 7, hour = 9), next)
    }

    @Test
    fun `아직 첫 회차가 안 왔으면 그것이 다음 회차다`() {
        val next = RecurrenceRule.nextOccurrenceAfter(base, Recurrence.DAILY, at(2026, 9, 6, hour = 8))

        assertEquals(base, next)
    }

    @Test
    fun `되풀이하지 않는 일정은 지나면 다음이 없다`() {
        assertNull(RecurrenceRule.nextOccurrenceAfter(base, Recurrence.NONE, at(2026, 9, 6, hour = 10)))
        assertEquals(base, RecurrenceRule.nextOccurrenceAfter(base, Recurrence.NONE, at(2026, 9, 6, hour = 8)))
    }

    @Test
    fun `되풀이하는 일정은 아무리 나중에 물어도 다음이 있다`() {
        // 재부팅으로 걸어 둔 알람이 사라져도, 지금 시각에서 다시 세면 그만이다.
        listOf(Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.MONTHLY).forEach { recurrence ->
            assertNotNull(
                "$recurrence 가 다음 회차를 내지 못했다",
                RecurrenceRule.nextOccurrenceAfter(base, recurrence, at(2030, 3, 15, hour = 23)),
            )
        }
    }

    @Test
    fun `매월 31일도 두 달 안에 다음 회차가 나온다`() {
        val monthEnd = at(2026, 1, 31, hour = 8)

        assertEquals(
            at(2026, 2, 28, hour = 8),
            RecurrenceRule.nextOccurrenceAfter(monthEnd, Recurrence.MONTHLY, at(2026, 2, 1)),
        )
    }

    @Test
    fun `회차 열쇠는 날짜만 담는다`() {
        assertEquals("2026-09-06", RecurrenceRule.occurrenceKey(at(2026, 9, 6, hour = 23, minute = 59)))
        assertEquals("2026-01-01", RecurrenceRule.occurrenceKey(at(2026, 1, 1, hour = 0)))
    }
}
