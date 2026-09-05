package com.example.slowclock.ui.timeline

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** 타임라인 화면. 고른 날짜의 일정을 Firestore 리스너로 받는다. 날짜가 바뀌면 다시 구독한다. */
@HiltViewModel
class TimelineViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
    ) : MviViewModel<TimelineIntent, TimelineUiState, TimelineReducerEvent>(TimelineUiState()) {
        private var scheduleJob: Job? = null

        init {
            observeSchedules(currentState.selectedDate)
        }

        override fun onIntent(intent: TimelineIntent) {
            when (intent) {
                is TimelineIntent.SelectDate -> {
                    changeDate(
                        startOfDay(
                            Calendar.getInstance().apply { set(intent.year, intent.month, intent.dayOfMonth) },
                        ),
                    )
                }

                TimelineIntent.NextDay -> {
                    changeDate(currentState.selectedDate.plusDays(1))
                }

                TimelineIntent.PreviousDay -> {
                    changeDate(currentState.selectedDate.plusDays(-1))
                }

                TimelineIntent.Retry -> {
                    dispatch(TimelineReducerEvent.ErrorConsumed)
                    observeSchedules(currentState.selectedDate)
                }

                TimelineIntent.ConsumeError -> {
                    dispatch(TimelineReducerEvent.ErrorConsumed)
                }
            }
        }

        override fun reduce(
            state: TimelineUiState,
            event: TimelineReducerEvent,
        ): TimelineUiState =
            when (event) {
                is TimelineReducerEvent.DateChanged -> state.copy(selectedDate = event.date, schedules = emptyList())
                TimelineReducerEvent.Loading -> state.copy(isLoading = true, error = null)
                is TimelineReducerEvent.Loaded -> state.copy(schedules = event.schedules, isLoading = false, error = null)
                is TimelineReducerEvent.Failed -> state.copy(isLoading = false, error = event.error)
                TimelineReducerEvent.ErrorConsumed -> state.copy(error = null)
            }

        private fun changeDate(date: Calendar) {
            dispatch(TimelineReducerEvent.DateChanged(date))
            observeSchedules(date)
        }

        private fun observeSchedules(date: Calendar) {
            scheduleJob?.cancel()
            scheduleJob =
                viewModelScope.launch {
                    dispatch(TimelineReducerEvent.Loading)
                    scheduleRepository
                        .observeSchedulesForDate(date)
                        .catch { e ->
                            Log.e(TAG, "일정 구독 실패", e)
                            dispatch(TimelineReducerEvent.Failed(e.toAppError()))
                        }.collect { dispatch(TimelineReducerEvent.Loaded(it)) }
                }
        }

        private companion object {
            const val TAG = "TimelineViewModel"
        }
    }
