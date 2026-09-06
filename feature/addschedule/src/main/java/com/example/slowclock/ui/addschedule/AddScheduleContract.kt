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

    data class UpdateTimeInput(
        val value: ScheduleTimeInput,
        val isEnd: Boolean = false,
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
    val startTimeInput: ScheduleTimeInput = ScheduleTimeInput.from(selectedTime),
    val endTimeInput: ScheduleTimeInput = ScheduleTimeInput.from(endTime),
    val recurring: Boolean = false,
    val recurringType: String = "daily",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: AppError? = null,
    val canRetry: Boolean = false,
    val isEditMode: Boolean = false,
    val editingSchedule: Schedule? = null,
    val editScheduleId: String? = null,
    /** 서버 저장은 완료됐다. 재시도는 이 ID의 로컬 알람만 예약한다. */
    val pendingAlarmSchedule: Schedule? = null,
) : UiState {
    val hasValidTimeInput: Boolean get() = startTimeInput.isValid && (endTimeInput.isEmpty || endTimeInput.isValid)
    val canSave: Boolean get() =
        title.isNotBlank() && !isLoading && !isSaved && pendingAlarmSchedule == null && hasValidTimeInput &&
            (!isEditMode || editingSchedule != null)

    fun canApplyRecommendedTitle(scheduleId: String?): Boolean =
        !isLoading && !isSaved && (scheduleId.isNullOrBlank() || editingSchedule?.id == scheduleId)
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

    data class TimeInputChanged(
        val value: ScheduleTimeInput,
        val isEnd: Boolean,
    ) : AddScheduleReducerEvent

    data class EditLoading(
        val scheduleId: String,
    ) : AddScheduleReducerEvent

    data class EditLoaded(
        val schedule: Schedule,
        val startTime: Calendar,
        val endTime: Calendar?,
    ) : AddScheduleReducerEvent

    data object Saving : AddScheduleReducerEvent

    data object Saved : AddScheduleReducerEvent

    data class AlarmFailed(
        val schedule: Schedule,
    ) : AddScheduleReducerEvent

    data class Failed(
        val error: AppError,
        val canRetry: Boolean,
    ) : AddScheduleReducerEvent

    data object ErrorConsumed : AddScheduleReducerEvent

    data object SavedConsumed : AddScheduleReducerEvent
}

/** 입력 중인 빈칸도 보존한다. 저장 가능 여부는 화면과 저장 동작이 같은 값으로 판단한다. */
data class ScheduleTimeInput(
    val hour: String = "",
    val minute: String = "",
) {
    val isEmpty: Boolean get() = hour.isEmpty() && minute.isEmpty()
    val isValid: Boolean get() = hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59

    fun onDate(date: Calendar): Calendar? =
        if (isValid) {
            (date.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } else {
            null
        }

    companion object {
        fun from(time: Calendar?): ScheduleTimeInput {
            if (time == null) return ScheduleTimeInput()
            return ScheduleTimeInput(
                hour = time.get(Calendar.HOUR_OF_DAY).toString(),
                minute = time.get(Calendar.MINUTE).toString(),
            )
        }
    }
}
