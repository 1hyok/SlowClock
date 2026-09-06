package com.example.slowclock.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaySwipeGestureTest {
    @Test
    fun `작은 이동을 누적해 양쪽 날짜로 한 번만 이동한다`() {
        for ((delta, expected) in listOf(20f to TimelineIntent.PreviousDay, -20f to TimelineIntent.NextDay)) {
            val gesture = DaySwipeGesture(100f)
            repeat(5) { assertNull(gesture.dragBy(delta)) }
            assertEquals(expected, gesture.dragBy(delta))
            assertNull(gesture.dragBy(delta * 20))
            assertNull(gesture.dragBy(-delta * 20))
        }
    }

    @Test
    fun `왕복 이동은 순이동으로 판단하고 새 제스처는 초기화한다`() {
        val gesture = DaySwipeGesture(100f)
        assertNull(gesture.dragBy(80f))
        assertNull(gesture.dragBy(-80f))
        assertNull(gesture.dragBy(50f))
        gesture.reset()
        assertNull(gesture.dragBy(60f))
        assertEquals(TimelineIntent.PreviousDay, gesture.dragBy(60f))
        gesture.reset()
        assertEquals(TimelineIntent.NextDay, gesture.dragBy(-120f))
    }
}
