package com.example.slowclock.ui.addschedule

import androidx.lifecycle.SavedStateHandle
import java.util.Calendar

/** task가 시스템에 의해 복원될 때의 가벼운 입력값만 보존한다. 원격 저장 완료를 의미하지 않는다. */
internal fun SavedStateHandle.restoreDraft(): AddScheduleUiState {
    val start = Calendar.getInstance().apply { get<Long>("draft_start")?.let { timeInMillis = it } }
    val end = get<Long>("draft_end")?.let { millis -> Calendar.getInstance().apply { timeInMillis = millis } }
    return AddScheduleUiState(
        title = get<String>("draft_title").orEmpty(),
        description = get<String>("draft_description").orEmpty(),
        selectedTime = start,
        endTime = end,
        startTimeInput =
            ScheduleTimeInput(
                get<String>("draft_start_hour") ?: start.get(Calendar.HOUR_OF_DAY).toString(),
                get<String>("draft_start_minute") ?: start.get(Calendar.MINUTE).toString(),
            ),
        endTimeInput =
            ScheduleTimeInput(
                get<String>("draft_end_hour") ?: end?.get(Calendar.HOUR_OF_DAY)?.toString().orEmpty(),
                get<String>("draft_end_minute") ?: end?.get(Calendar.MINUTE)?.toString().orEmpty(),
            ),
        recurring = get<Boolean>("draft_recurring") ?: false,
        recurringType = get<String>("draft_recurring_type") ?: "daily",
    )
}

internal fun SavedStateHandle.saveDraft(state: AddScheduleUiState) {
    this["draft_title"] = state.title
    this["draft_description"] = state.description
    this["draft_start"] = state.selectedTime.timeInMillis
    this["draft_end"] = state.endTime?.timeInMillis
    this["draft_start_hour"] = state.startTimeInput.hour
    this["draft_start_minute"] = state.startTimeInput.minute
    this["draft_end_hour"] = state.endTimeInput.hour
    this["draft_end_minute"] = state.endTimeInput.minute
    this["draft_recurring"] = state.recurring
    this["draft_recurring_type"] = state.recurringType
}
