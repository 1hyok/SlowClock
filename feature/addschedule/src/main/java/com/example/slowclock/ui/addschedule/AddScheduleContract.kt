package com.example.slowclock.ui.addschedule

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState
import com.example.slowclock.util.AppError
import java.util.Calendar

sealed interface AddScheduleIntent : MviIntent {
    data class UpdateTitle(
        val value: String,
    ) : AddScheduleIntent

    data class UpdateDescription(
        val value: String,
    ) : AddScheduleIntent

    data class UpdateTime(
        val time: Calendar,
    ) : AddScheduleIntent

    data class UpdateEndTime(
        val time: Calendar?,
    ) : AddScheduleIntent

    data class UpdateRecurring(
        val recurring: Boolean,
    ) : AddScheduleIntent

    data class UpdateRecurringType(
        val type: String,
    ) : AddScheduleIntent

    data class LoadForEdit(
        val scheduleId: String,
    ) : AddScheduleIntent

    data object Save : AddScheduleIntent

    data object Retry : AddScheduleIntent

    data object ConsumeError : AddScheduleIntent

    data object ConsumeSaved : AddScheduleIntent
}

/**
 * 일정 추가·수정 화면의 단일 UI 상태. [isSaved] 는 일회성 신호다.
 * Calendar 는 가변 객체라 상태에 넣은 뒤 바꾸지 않고 새 인스턴스로 교체한다.
 */
data class AddScheduleUiState(
    val title: String = "",
    val description: String = "",
    val selectedTime: Calendar = Calendar.getInstance(),
    val endTime: Calendar? = null,
    val recurring: Boolean = false,
    val recurringType: String = "daily",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: AppError? = null,
    val canRetry: Boolean = false,
    val isEditMode: Boolean = false,
    val editingSchedule: Schedule? = null,
) : UiState {
    val canSave: Boolean get() = title.isNotBlank() && !isLoading
}

sealed interface AddScheduleReducerEvent : ReducerEvent {
    data class TitleChanged(
        val value: String,
    ) : AddScheduleReducerEvent

    data class DescriptionChanged(
        val value: String,
    ) : AddScheduleReducerEvent

    data class TimeChanged(
        val time: Calendar,
    ) : AddScheduleReducerEvent

    data class EndTimeChanged(
        val time: Calendar?,
    ) : AddScheduleReducerEvent

    data class RecurringChanged(
        val recurring: Boolean,
    ) : AddScheduleReducerEvent

    data class RecurringTypeChanged(
        val type: String,
    ) : AddScheduleReducerEvent

    data object EditLoading : AddScheduleReducerEvent

    data class EditLoaded(
        val schedule: Schedule,
        val startTime: Calendar,
        val endTime: Calendar?,
    ) : AddScheduleReducerEvent

    data object Saving : AddScheduleReducerEvent

    data object Saved : AddScheduleReducerEvent

    data class Failed(
        val error: AppError,
        val canRetry: Boolean,
    ) : AddScheduleReducerEvent

    data object ErrorConsumed : AddScheduleReducerEvent

    data object SavedConsumed : AddScheduleReducerEvent
}
