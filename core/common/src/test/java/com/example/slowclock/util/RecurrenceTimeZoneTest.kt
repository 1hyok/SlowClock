package com.example.slowclock.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.util.TimeZone

/** 기존 epoch 모델의 경계. 실행기 전체 기본값을 바꾸므로 매 테스트 뒤 원래 시간대로 돌린다. */
class RecurrenceTimeZoneTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun saveTimeZone() {
        originalZone = TimeZone.getDefault()
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `서울에서 만든 epoch는 LA 계산 시 LA 시각과 요일로 해석한다`() {
        val base = instant("2026-01-06T08:00:00+09:00")
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

        assertEquals(
            instant("2026-01-08T15:00:00-08:00"),
            RecurrenceRule.occurrenceOn(base, Recurrence.DAILY, instant("2026-01-08T00:00:00-08:00")),
        )
        assertEquals(
            instant("2026-01-12T15:00:00-08:00"),
            RecurrenceRule.occurrenceOn(base, Recurrence.WEEKLY, instant("2026-01-12T00:00:00-08:00")),
        )
        assertNull(RecurrenceRule.occurrenceOn(base, Recurrence.WEEKLY, instant("2026-01-13T00:00:00-08:00")))
    }

    @Test
    fun `단발 일정의 epoch는 시간대 변경 뒤에도 그대로다`() {
        val base = instant("2026-01-06T08:00:00+09:00")
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

        assertEquals(base, RecurrenceRule.occurrenceOn(base, Recurrence.NONE, instant("2026-01-05T00:00:00-08:00")))
        assertEquals(base, RecurrenceRule.nextOccurrenceAfter(base, Recurrence.NONE, base - 1))
        assertNull(RecurrenceRule.nextOccurrenceAfter(base, Recurrence.NONE, base))
    }

    @Test
    fun `동일 시간대의 매일 아침 회차는 DST 시작 때 23시간 뒤다`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val base = instant("2026-02-01T08:00:00-08:00")
        val before = instant("2026-03-07T08:00:00-08:00")
        val after = RecurrenceRule.nextOccurrenceAfter(base, Recurrence.DAILY, before)

        assertEquals(instant("2026-03-08T08:00:00-07:00"), after)
        assertEquals(23 * 60 * 60 * 1_000L, after!! - before)
    }

    @Test
    fun `호스트 Calendar의 DST 공백은 존재하는 이후 시각으로 보정한다`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val base = instant("2026-02-01T02:30:00-08:00")

        assertEquals(
            instant("2026-03-08T03:30:00-07:00"),
            RecurrenceRule.occurrenceOn(base, Recurrence.DAILY, instant("2026-03-08T00:00:00-08:00")),
        )
    }

    @Test
    fun `호스트 Calendar의 DST 중복은 늦은 offset의 회차 하나를 선택한다`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val base = instant("2026-10-01T01:30:00-07:00")
        val occurrence = RecurrenceRule.occurrenceOn(base, Recurrence.DAILY, instant("2026-11-01T00:00:00-07:00"))

        assertEquals(instant("2026-11-01T01:30:00-08:00"), occurrence)
        assertEquals(
            instant("2026-11-02T01:30:00-08:00"),
            RecurrenceRule.nextOccurrenceAfter(base, Recurrence.DAILY, occurrence!!),
        )
    }

    @Test
    fun `회차 키는 계산 당시 시간대의 날짜며 기존 키를 이행하지 않는다`() {
        val base = instant("2026-01-06T08:00:00+09:00")
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        assertEquals("2026-01-06", RecurrenceRule.occurrenceKey(base))
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("2026-01-05", RecurrenceRule.occurrenceKey(base))
    }

    private fun instant(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
