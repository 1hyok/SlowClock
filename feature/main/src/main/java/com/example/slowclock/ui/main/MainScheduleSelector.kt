package com.example.slowclock.ui.main

import com.example.slowclock.data.model.Schedule

private const val DEFAULT_DURATION_MILLIS = 60 * 60 * 1000L

private fun Schedule.endMillis(): Long = endTime?.toDate()?.time ?: (startTime.toDate().time + DEFAULT_DURATION_MILLIS)

/**
 * 「지금 할 일」 하나를 고른다. 순수 함수라 리듀서 안에서 부른다.
 *
 * 1. 진행 중인 일정이 있으면 그중 끝나는 시각이 가장 빠른 것
 * 2. 없으면 아직 시작하지 않은 일정 중 시작 시각이 가장 빠른 것(같으면 끝나는 시각이 빠른 것)
 * 완료한 일정은 후보에서 뺀다.
 */
internal fun selectCurrentSchedule(
    schedules: List<Schedule>,
    nowMillis: Long,
): Schedule? {
    val incomplete = schedules.filter { !it.completed }
    val ongoing =
        incomplete.filter { schedule ->
            val start = schedule.startTime.toDate().time
            nowMillis >= start && nowMillis <= schedule.endMillis()
        }
    if (ongoing.isNotEmpty()) {
        return ongoing.minByOrNull { it.endMillis() }
    }
    return incomplete
        .filter { it.startTime.toDate().time > nowMillis }
        .sortedWith(compareBy<Schedule> { it.startTime.toDate().time }.thenBy { it.endMillis() })
        .firstOrNull()
}
