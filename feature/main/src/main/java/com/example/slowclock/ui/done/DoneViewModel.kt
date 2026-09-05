package com.example.slowclock.ui.done

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

/** 완료 화면. 오늘 일정을 Firestore 리스너로 받아 완료·남은 일정으로 나눈다. */
@HiltViewModel
class DoneViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
    ) : MviViewModel<DoneIntent, DoneUiState, DoneReducerEvent>(DoneUiState()) {
        private var scheduleJob: Job? = null

        init {
            observeTodaySchedules()
        }

        override fun onIntent(intent: DoneIntent) {
            when (intent) {
                is DoneIntent.ToggleComplete -> {
                    toggleComplete(intent.scheduleId)
                }

                DoneIntent.Retry -> {
                    dispatch(DoneReducerEvent.ErrorConsumed)
                    observeTodaySchedules()
                }

                DoneIntent.ConsumeError -> {
                    dispatch(DoneReducerEvent.ErrorConsumed)
                }
            }
        }

        override fun reduce(
            state: DoneUiState,
            event: DoneReducerEvent,
        ): DoneUiState =
            when (event) {
                DoneReducerEvent.Loading -> {
                    state.copy(isLoading = true, error = null)
                }

                is DoneReducerEvent.Loaded -> {
                    state.copy(schedules = event.schedules, isLoading = false, error = null)
                }

                is DoneReducerEvent.Failed -> {
                    state.copy(isLoading = false, error = event.error)
                }

                is DoneReducerEvent.Toggled -> {
                    state.copy(
                        schedules = state.schedules.map { if (it.id == event.scheduleId) it.copy(completed = !it.completed) else it },
                    )
                }

                DoneReducerEvent.ErrorConsumed -> {
                    state.copy(error = null)
                }
            }

        private fun observeTodaySchedules() {
            scheduleJob?.cancel()
            scheduleJob =
                viewModelScope.launch {
                    dispatch(DoneReducerEvent.Loading)
                    scheduleRepository
                        .observeSchedulesForDate(Calendar.getInstance())
                        .catch { e ->
                            Log.e(TAG, "일정 구독 실패", e)
                            dispatch(DoneReducerEvent.Failed(e.toAppError()))
                        }.collect { dispatch(DoneReducerEvent.Loaded(it)) }
                }
        }

        private fun toggleComplete(scheduleId: String) {
            val schedule = currentState.schedules.find { it.id == scheduleId } ?: return
            dispatch(DoneReducerEvent.Toggled(scheduleId))
            viewModelScope.launch {
                val result = scheduleRepository.markScheduleAsCompleted(scheduleId, !schedule.completed)
                if (result is ScheduleRepository.ScheduleResult.Error) {
                    Log.e(TAG, "완료 상태 변경 실패: ${result.error.message}")
                    dispatch(DoneReducerEvent.Failed(result.error))
                }
            }
        }

        private companion object {
            const val TAG = "DoneViewModel"
        }
    }
