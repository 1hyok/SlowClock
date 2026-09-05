package com.example.slowclock.ui.timeline

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState
import com.example.slowclock.util.AppError
import java.util.Calendar

sealed interface TimelineIntent : MviIntent {
    data class SelectDate(
        val year: Int,
        val month: Int,
        val dayOfMonth: Int,
    ) : TimelineIntent

    data object NextDay : TimelineIntent

    data object PreviousDay : TimelineIntent

    data object Retry : TimelineIntent

    data object ConsumeError : TimelineIntent
}

/**
 * [selectedDate] 는 자정으로 맞춘 날짜다. Calendar 는 가변 객체라 상태에 넣은 뒤에는 바꾸지 않고
 * 새 인스턴스로 교체한다.
 */
data class TimelineUiState(
    val selectedDate: Calendar = startOfDay(Calendar.getInstance()),
    val schedules: List<Schedule> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
) : UiState

sealed interface TimelineReducerEvent : ReducerEvent {
    data class DateChanged(
        val date: Calendar,
    ) : TimelineReducerEvent

    data object Loading : TimelineReducerEvent

    data class Loaded(
        val schedules: List<Schedule>,
    ) : TimelineReducerEvent

    data class Failed(
        val error: AppError,
    ) : TimelineReducerEvent

    data object ErrorConsumed : TimelineReducerEvent
}

internal fun startOfDay(source: Calendar): Calendar =
    (source.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

internal fun Calendar.plusDays(days: Int): Calendar = (clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }
