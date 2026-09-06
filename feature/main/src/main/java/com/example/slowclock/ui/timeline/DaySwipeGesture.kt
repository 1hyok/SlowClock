package com.example.slowclock.ui.timeline

/** 한 번의 드래그에서 이동량을 누적하고 날짜 이동은 한 번만 낸다. */
internal class DaySwipeGesture(
    private val threshold: Float,
) {
    private var distance = 0f
    private var handled = false

    fun reset() {
        distance = 0f
        handled = false
    }

    fun dragBy(delta: Float): TimelineIntent? {
        if (handled) return null
        distance += delta
        val intent =
            when {
                distance > threshold -> TimelineIntent.PreviousDay
                distance < -threshold -> TimelineIntent.NextDay
                else -> return null
            }
        handled = true
        return intent
    }
}
