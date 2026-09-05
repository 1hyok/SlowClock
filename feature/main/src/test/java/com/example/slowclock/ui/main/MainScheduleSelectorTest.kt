package com.example.slowclock.ui.main

import com.example.slowclock.data.model.Schedule
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class MainScheduleSelectorTest {
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun schedule(
        id: String,
        startOffset: Long,
        endOffset: Long? = null,
        completed: Boolean = false,
    ) = Schedule(
        id = id,
        title = id,
        startTime = Timestamp(Date(now + startOffset)),
        endTime = endOffset?.let { Timestamp(Date(now + it)) },
        completed = completed,
    )

    @Test
    fun `진행 중인 일정이 있으면 끝나는 시각이 가장 빠른 것을 고른다`() {
        val schedules =
            listOf(
                schedule("late-end", startOffset = -hour, endOffset = 3 * hour),
                schedule("early-end", startOffset = -2 * hour, endOffset = hour),
                schedule("future", startOffset = hour),
            )

        assertEquals("early-end", selectCurrentSchedule(schedules, now)?.id)
    }

    @Test
    fun `진행 중인 일정이 없으면 다음에 시작할 일정을 고른다`() {
        val schedules =
            listOf(
                schedule("later", startOffset = 3 * hour),
                schedule("sooner", startOffset = hour),
                schedule("past", startOffset = -5 * hour, endOffset = -4 * hour),
            )

        assertEquals("sooner", selectCurrentSchedule(schedules, now)?.id)
    }

    @Test
    fun `종료 시각이 없는 일정은 시작 뒤 한 시간까지 진행 중으로 본다`() {
        val schedules = listOf(schedule("open-ended", startOffset = -30 * 60 * 1000L))

        assertEquals("open-ended", selectCurrentSchedule(schedules, now)?.id)
        assertNull(selectCurrentSchedule(schedules, now + 2 * hour))
    }

    @Test
    fun `완료한 일정은 후보에서 뺀다`() {
        val schedules =
            listOf(
                schedule("done", startOffset = -hour, endOffset = hour, completed = true),
                schedule("next", startOffset = 2 * hour),
            )

        assertEquals("next", selectCurrentSchedule(schedules, now)?.id)
    }
}
