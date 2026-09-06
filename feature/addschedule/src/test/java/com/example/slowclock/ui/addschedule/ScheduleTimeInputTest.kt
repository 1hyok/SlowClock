package com.example.slowclock.ui.addschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleTimeInputTest {
    @Test
    fun `하루의 경계 시각과 앞자리 영은 유효하다`() {
        listOf(ScheduleTimeInput("0", "0"), ScheduleTimeInput("23", "59"), ScheduleTimeInput("09", "05")).forEach {
            assertTrue(it.isValid)
        }
    }

    @Test
    fun `빈칸과 범위 밖 값은 날짜로 바꾸지 않는다`() {
        listOf(
            ScheduleTimeInput(),
            ScheduleTimeInput("12", ""),
            ScheduleTimeInput("", "30"),
            ScheduleTimeInput("24", "0"),
            ScheduleTimeInput("12", "60"),
            ScheduleTimeInput("-1", "0"),
            ScheduleTimeInput("abc", "30"),
        ).forEach {
            assertFalse(it.isValid)
            assertNull(it.onDate(Calendar.getInstance()))
        }
    }

    @Test
    fun `시간 변환은 원래 날짜와 시간대를 유지하고 원본을 변경하지 않는다`() {
        val date =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2032, Calendar.FEBRUARY, 29, 8, 15, 37)
                set(Calendar.MILLISECOND, 125)
            }
        val original = date.timeInMillis

        val result = ScheduleTimeInput("23", "59").onDate(date)!!

        assertNotSame(date, result)
        assertEquals(original, date.timeInMillis)
        assertEquals(date.timeZone, result.timeZone)
        assertEquals(2032, result.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(29, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(0, result.get(Calendar.MILLISECOND))
    }
}
