package com.example.slowclock.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다시 알림 규칙. 값을 여기에 못 박아 두어, 바꾸는 사람이 테스트를 함께 고치며 이 결정을 보게 한다.
 *
 * 무한히 미룰 수 있으면 고령자가 계속 미루다 일정을 놓친다. 반대로 아예 못 미루면 지금 손이
 * 비지 않는 사용자에게 남는 선택지가 「완전히 끄기」 하나뿐이다(#129).
 */
class SnoozePolicyTest {
    @Test
    fun `한 번 미루는 시간은 5분이다`() {
        assertEquals(5, SnoozePolicy.MINUTES)
    }

    @Test
    fun `미룰 수 있는 횟수는 두 번이다`() {
        assertEquals(2, SnoozePolicy.MAX_COUNT)
    }

    @Test
    fun `아직 안 미뤘거나 한 번 미뤘으면 더 미룰 수 있다`() {
        assertTrue(SnoozePolicy.canSnooze(0))
        assertTrue(SnoozePolicy.canSnooze(1))
    }

    @Test
    fun `두 번 미뤘으면 더 미룰 수 없다`() {
        assertFalse(SnoozePolicy.canSnooze(2))
        assertFalse(SnoozePolicy.canSnooze(3))
    }

    @Test
    fun `다시 울릴 시각은 지금부터 정확히 5분 뒤다`() {
        val now = 1_700_000_000_000L

        assertEquals(now + 5 * 60 * 1000L, SnoozePolicy.nextTriggerAt(now))
    }
}
