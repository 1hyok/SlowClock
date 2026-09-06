package com.example.slowclock.data.remote.repository

import com.example.slowclock.util.Recurrence
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * 걸어 둔 알람 장부에 남는 값. 재부팅 뒤 알람을 되살리는 근거가 이것뿐이라, 여기서 어긋나면
 * 그날 알람이 통째로 사라진다(#127).
 */
class ScheduledAlarmTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `제목에 무엇이 들어 있어도 그대로 되돌아온다`() {
        // 어르신이 제목에 무엇을 적든 장부가 깨지면 안 된다. 직접 만든 구분자로 이었다면
        // 여기서 깨졌을 값들이다.
        val alarm =
            ScheduledAlarm(
                id = "doc-1",
                title = "\"약\" 먹기, 8시\n두 번째 줄 💊",
                description = "설명에 = 과 : 와 | 가 있다",
                startMillis = 1_800_000_000_000,
                endMillis = 1_800_003_600_000,
            )

        val restored = json.decodeFromString<ScheduledAlarm>(json.encodeToString(alarm))

        assertEquals(alarm, restored)
    }

    @Test
    fun `종료 시각이 없는 알람도 왕복한다`() {
        val alarm = ScheduledAlarm("doc-2", "산책", "", 1_800_000_000_000, null)

        assertEquals(alarm, json.decodeFromString<ScheduledAlarm>(json.encodeToString(alarm)))
    }

    @Test
    fun `시작이 아직 안 왔으면 살아 있다`() {
        val alarm = ScheduledAlarm("doc-3", "약", "", startMillis = 200, endMillis = null)

        assertTrue(alarm.isLive(nowMillis = 100))
    }

    @Test
    fun `시작은 지났어도 종료가 남았으면 살아 있다`() {
        val alarm = ScheduledAlarm("doc-4", "물리치료", "", startMillis = 100, endMillis = 300)

        assertTrue(alarm.isLive(nowMillis = 200))
    }

    @Test
    fun `둘 다 지났으면 죽었다`() {
        val alarm = ScheduledAlarm("doc-5", "아침 약", "", startMillis = 100, endMillis = 200)

        assertFalse(alarm.isLive(nowMillis = 300))
    }

    @Test
    fun `경계는 지난 것으로 본다`() {
        // 알람을 거는 쪽이 triggerTime > now 로 판정한다. 기준이 갈리면 장부에는 있는데
        // 걸리지는 않는 유령 기록이 쌓인다.
        val alarm = ScheduledAlarm("doc-6", "정각", "", startMillis = 100, endMillis = null)

        assertFalse(alarm.isLive(nowMillis = 100))
    }

    @Test
    fun `시간대를 바꿔도 판정이 달라지지 않는다`() {
        // 장부에 남는 값이 절대 시각(epoch 밀리초)이라는 사실을 못 박는다. 이래서 복원 수신기가
        // TIMEZONE_CHANGED 를 받지 않는다.
        val alarm = ScheduledAlarm("doc-7", "약", "", startMillis = 1_800_000_000_000, endMillis = null)
        val original = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            val inSeoul = alarm.isLive(nowMillis = 1_700_000_000_000)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val inNewYork = alarm.isLive(nowMillis = 1_700_000_000_000)

            assertEquals(inSeoul, inNewYork)
            assertTrue(inSeoul)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // ── 걸어 둔 회차 (#163) ────────────────────────────────────────────────

    private fun daily(
        start: Long,
        end: Long? = null,
        booked: Long? = null,
    ) = ScheduledAlarm(
        id = "daily-1",
        title = "물리치료",
        description = "",
        startMillis = start,
        endMillis = end,
        recurrence = Recurrence.DAILY.name,
        bookedStartMillis = booked,
    )

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `시작만 울리고 종료가 남았으면 그 회차를 그대로 지킨다`() {
        // 앞당겨 걸면 알람을 다시 거는 일이 그 일정의 자리를 먼저 비우므로, 아직 안 울린
        // 종료 알람이 지워진다. 그러면 반복 일정의 종료는 영영 울리지 않는다(#163).
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val record = daily(start, end, booked = start)

        assertEquals(start, record.occurrenceToBook(nowMillis = start + 10))
    }

    @Test
    fun `종료까지 울린 뒤에야 다음 회차로 넘어간다`() {
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val record = daily(start, end, booked = start)

        assertEquals(start + day, record.occurrenceToBook(nowMillis = end + 10))
    }

    @Test
    fun `종료가 없는 반복 일정은 시작이 울리면 바로 다음 회차로 넘어간다`() {
        val start = 1_800_000_000_000
        val record = daily(start, end = null, booked = start)

        assertEquals(start + day, record.occurrenceToBook(nowMillis = start + 10))
    }

    @Test
    fun `아직 아무것도 안 걸었으면 지금 이후 첫 회차를 낸다`() {
        val start = 1_800_000_000_000
        val record = daily(start, end = null, booked = null)

        assertEquals(start, record.occurrenceToBook(nowMillis = start - 10))
        assertEquals(start + day, record.occurrenceToBook(nowMillis = start + 10))
    }

    @Test
    fun `되풀이하지 않는 일정도 시작이 지났어도 종료가 남았으면 건다`() {
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val once = ScheduledAlarm("once-1", "병원", "", start, end)

        assertEquals(start, once.occurrenceToBook(nowMillis = start + 10))
        assertNull(once.occurrenceToBook(nowMillis = end + 10))
    }

    @Test
    fun `옛 기록은 걸어 둔 회차가 없어도 읽힌다`() {
        // bookedStartMillis 가 없던 판에서 넘어온 JSON 이다. 못 읽으면 그 알람이 통째로 사라진다.
        val stored = """{"id":"old-1","title":"약","description":"","startMillis":1800000000000}"""

        val restored = json.decodeFromString<ScheduledAlarm>(stored)

        assertNull(restored.bookedStartMillis)
        assertEquals(Recurrence.NONE, restored.rule)
    }

    @Test
    fun `시작이 지난 뒤에 저장해도 그날 종료 알람은 건다`() {
        // 09시 30분에 「매일 09~10시」 를 저장하면 오늘 회차가 진행 중이다. 다음 회차부터
        // 세면 오늘 10시 종료 알람을 통째로 놓친다(#163).
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val record = daily(start, end, booked = null)

        assertEquals(start, record.occurrenceToBook(nowMillis = start + 30 * 60 * 1000))
    }

    @Test
    fun `진행 중인 회차가 없으면 다음 회차를 낸다`() {
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val record = daily(start, end, booked = null)

        assertEquals(start + day, record.occurrenceToBook(nowMillis = end + 10))
    }

    @Test
    fun `걸어 둔 회차만 다른 기록은 내용이 같다고 본다`() {
        // 서버 목록으로 알람을 맞출 때 쓴다. 이 판정이 없으면 앱을 열 때마다 전부 다시 걸고,
        // 다시 걸기는 그 자리를 먼저 비우므로 방금 미뤄 둔 알람이 그때마다 사라진다(#176).
        val start = 1_800_000_000_000
        val booked = daily(start, start + 60 * 60 * 1000, booked = start + day)
        val fresh = daily(start, start + 60 * 60 * 1000, booked = null)

        assertTrue(booked.sameContentAs(fresh))
    }

    @Test
    fun `제목이나 시각이 바뀌면 내용이 다르다고 본다`() {
        val start = 1_800_000_000_000
        val end = start + 60 * 60 * 1000
        val booked = daily(start, end, booked = start)

        assertFalse(booked.sameContentAs(daily(start, end, booked = null).copy(title = "다른 제목")))
        assertFalse(booked.sameContentAs(daily(start + 60_000, end, booked = null)))
        assertFalse(booked.sameContentAs(daily(start, end, booked = null).copy(recurrence = Recurrence.WEEKLY.name)))
    }
}
