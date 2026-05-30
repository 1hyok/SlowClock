// app/src/main/java/com/example/slowclock/util/ScheduleUtils.kt
package com.example.slowclock.util

import com.example.slowclock.data.model.Schedule

/**
 * 일정에 추가 정보가 있는지 확인
 */
fun hasExtraInfo(schedule: Schedule): Boolean {
    return schedule.description.isNotBlank() ||
            schedule.endTime != null ||
            schedule.recurring
}

/**
 * 일정이 현재 진행 중인지 확인
 */
fun isOngoing(schedule: Schedule, currentTime: Long = System.currentTimeMillis()): Boolean {
    val startTime = schedule.startTime.toDate().time
    val endTime = schedule.endTime?.toDate()?.time ?: (startTime + 60 * 60 * 1000)
    return currentTime >= startTime && currentTime <= endTime
}