package com.example.slowclock.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알람 자리 번호가 서로 겹치지 않는지 본다.
 *
 * 겹치면 나중에 건 알람이 앞의 것을 덮어쓰고 하나를 취소하면 다른 하나도 사라진다. 화면에는
 * 아무 표시도 나지 않아 "그날 알람이 안 울렸다" 로만 드러난다(#117).
 */
class ScheduleAlarmHelperTest {
    @Test
    fun `같은 일정의 시작과 종료는 다른 자리를 쓴다`() {
        val id = "schedule-1"

        assertNotEquals(
            ScheduleAlarmHelper.generateStartRequestCode(id),
            ScheduleAlarmHelper.generateEndRequestCode(id),
        )
    }

    @Test
    fun `어떤 두 일정이든 시작과 종료가 서로 겹치지 않는다`() {
        // 종전 방식은 종료 번호가 시작 번호에 9999 를 더한 값이라, 해시가 9999 차이 나는 두
        // 일정이 서로의 자리를 침범했다. 홀짝으로 갈라 두면 그 겹침이 구조적으로 사라진다.
        val ids = (1..500).map { "doc-id-$it" }

        for (first in ids) {
            for (second in ids) {
                assertNotEquals(
                    "시작($first)과 종료($second)가 같은 자리를 쓴다",
                    ScheduleAlarmHelper.generateStartRequestCode(first),
                    ScheduleAlarmHelper.generateEndRequestCode(second),
                )
            }
        }
    }

    @Test
    fun `시작은 짝수 종료는 홀수다`() {
        val id = "abc"

        assertEquals(0, ScheduleAlarmHelper.generateStartRequestCode(id) % 2)
        assertTrue(ScheduleAlarmHelper.generateEndRequestCode(id) % 2 != 0)
    }

    @Test
    fun `같은 일정은 매번 같은 자리를 쓴다`() {
        // 자리가 흔들리면 취소가 빗나가 옛 알람이 남는다.
        val id = "stable-id"

        assertEquals(
            ScheduleAlarmHelper.generateStartRequestCode(id),
            ScheduleAlarmHelper.generateStartRequestCode(id),
        )
        assertEquals(
            ScheduleAlarmHelper.generateEndRequestCode(id),
            ScheduleAlarmHelper.generateEndRequestCode(id),
        )
    }

    @Test
    fun `서로 다른 일정은 다른 자리를 쓴다`() {
        val codes = (1..500).map { ScheduleAlarmHelper.generateStartRequestCode("doc-id-$it") }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `다시 알림 action 은 비어 있지 않다`() {
        // 다시 알림은 requestCode 가 아니라 action 으로 자리를 가른다. 일정 알람 번호가 짝수
        // 전체와 홀수 전체를 덮어 「비어 있는 번호 대역」 이 없기 때문이다. action 이 빈 문자열이면
        // filterEquals 가 일정 알람과 같다고 보아 원래 알람을 덮어쓴다(#129).
        assertTrue(ScheduleAlarmHelper.ACTION_SNOOZE_ALARM.isNotBlank())
    }

    @Test
    fun `자리 번호는 일정 id 와 종류로만 정해진다`() {
        // 다시 알림이 원래 알람과 같은 번호를 쓰므로, 그 번호를 내는 규칙이 흔들리면
        // 미뤄 둔 알람을 취소할 때 엉뚱한 자리를 지운다.
        val id = "schedule-1"

        assertEquals(
            ScheduleAlarmHelper.generateStartRequestCode(id),
            ScheduleAlarmHelper.requestCodeOf(id, AlarmKind.START),
        )
        assertEquals(
            ScheduleAlarmHelper.generateEndRequestCode(id),
            ScheduleAlarmHelper.requestCodeOf(id, AlarmKind.END),
        )
    }
}
