package com.example.slowclock.data.remote.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
