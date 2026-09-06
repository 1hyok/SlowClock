package com.example.slowclock.ui.done

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState
import com.example.slowclock.util.AppError

sealed interface DoneIntent : MviIntent {
    data class ToggleComplete(
        val scheduleId: String,
    ) : DoneIntent

    data object Retry : DoneIntent

    /** 화면이 다시 보인다. 날이 바뀌었으면 오늘 회차로 다시 구독한다(#171). */
    data object ScreenResumed : DoneIntent

    data object ConsumeError : DoneIntent
}

data class DoneUiState(
    val schedules: List<Schedule> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val completed: List<Schedule> get() = schedules.filter { it.completed }
    val remaining: List<Schedule> get() = schedules.filter { !it.completed }
}

sealed interface DoneReducerEvent : ReducerEvent {
    data object Loading : DoneReducerEvent

    data class Loaded(
        val schedules: List<Schedule>,
    ) : DoneReducerEvent

    data class Failed(
        val error: AppError,
    ) : DoneReducerEvent

    data class Toggled(
        val scheduleId: String,
    ) : DoneReducerEvent

    data object ErrorConsumed : DoneReducerEvent
}
