package com.example.slowclock.util

import com.example.slowclock.data.model.Schedule
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * 문서를 그날의 회차로 펼치는 규칙.
 *
 * 저장소 안 private 함수로 두었더니 「반복이 아닌 일정의 완료가 되돌아간다」 를 놓쳤다(#157).
 * Firestore 없이 이 규칙만 따로 시험할 수 있어야 그런 회귀가 걸린다.
 */
class ScheduleOccurrenceTest {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                set(year, month - 1, day, hour, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    private fun schedule(
        startMillis: Long,
        recurring: Boolean = false,
        recurringType: String? = null,
        completed: Boolean = false,
        completedDates: List<String> = emptyList(),
        endMillis: Long? = null,
    ) = Schedule(
        id = "doc-1",
        title = "약 먹기",
        startTime = Timestamp(Date(startMillis)),
        endTime = endMillis?.let { Timestamp(Date(it)) },
        completed = completed,
        recurring = recurring,
        recurringType = recurringType,
        completedDates = completedDates,
    )

    @Test
    fun `반복이 아닌 일정은 회차 식별자를 갖지 않는다`() {
        // 채우면 화면이 그 값을 완료 처리에 넘기고 저장소가 completedDates 를 고치는 갈래로 간다.
        // 읽을 때는 completed 를 보므로 완료 표시가 곧바로 되돌아간다(#157).
        val base = at(2026, 9, 6)

        val occurrence = schedule(base).occurrenceOn(at(2026, 9, 6, hour = 20))

        assertEquals("", occurrence?.occurrenceDate)
    }

    @Test
    fun `반복이 아닌 일정은 문서의 완료 여부를 그대로 쓴다`() {
        val base = at(2026, 9, 6)

        val occurrence = schedule(base, completed = true).occurrenceOn(at(2026, 9, 6, hour = 20))

        assertTrue(occurrence!!.completed)
    }

    @Test
    fun `반복이 아닌 일정은 그날에만 나온다`() {
        val base = at(2026, 9, 6)

        assertNull(schedule(base).occurrenceOn(at(2026, 9, 7)))
    }

    @Test
    fun `반복 일정은 회차 식별자를 갖는다`() {
        val base = at(2026, 9, 6)

        val occurrence =
            schedule(base, recurring = true, recurringType = "daily")
                .occurrenceOn(at(2026, 9, 8, hour = 0))

        assertEquals("2026-09-08", occurrence?.occurrenceDate)
        assertEquals(at(2026, 9, 8), occurrence?.startTime?.toDate()?.time)
    }

    @Test
    fun `반복 일정의 완료는 그 회차의 것만 본다`() {
        val base = at(2026, 9, 6)
        val daily = schedule(base, recurring = true, recurringType = "daily", completedDates = listOf("2026-09-06"))

        assertTrue(daily.occurrenceOn(at(2026, 9, 6, hour = 20))!!.completed)
        assertFalse(daily.occurrenceOn(at(2026, 9, 7, hour = 20))!!.completed)
    }

    @Test
    fun `반복 일정의 completed 필드는 회차 판정에 쓰이지 않는다`() {
        // 문서에 하나뿐인 completed 를 반복 일정에 쓰면 한 번 완료한 뒤 영영 완료로 남는다.
        val base = at(2026, 9, 6)
        val daily = schedule(base, recurring = true, recurringType = "daily", completed = true)

        assertFalse(daily.occurrenceOn(at(2026, 9, 7, hour = 20))!!.completed)
    }

    @Test
    fun `종료 시각은 시작에서 떨어진 만큼 함께 옮긴다`() {
        val base = at(2026, 9, 6, hour = 22)
        val end = at(2026, 9, 7, hour = 1) // 자정을 넘긴다
        val daily = schedule(base, recurring = true, recurringType = "daily", endMillis = end)

        val occurrence = daily.occurrenceOn(at(2026, 9, 8, hour = 12))!!

        assertEquals(at(2026, 9, 8, hour = 22), occurrence.startTime.toDate().time)
        assertEquals(at(2026, 9, 9, hour = 1), occurrence.endTime!!.toDate().time)
    }
}
