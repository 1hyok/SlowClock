package com.example.slowclock.ui.done

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
        private var scheduleRevision = 0L
        private val pendingCompletions = mutableSetOf<String>()

        /** 지금 구독이 보고 있는 날. 날이 바뀌면 다시 건다(#171). */
        private var subscribedDay: String = ""

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

                DoneIntent.ScreenResumed -> {
                    resubscribeIfDayChanged()
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

                is DoneReducerEvent.Restored -> {
                    state.copy(
                        schedules =
                            state.schedules.map {
                                if (it.id == event.schedule.id && it.occurrenceDate == event.schedule.occurrenceDate) {
                                    it.copy(completed = event.schedule.completed)
                                } else {
                                    it
                                }
                            },
                    )
                }

                DoneReducerEvent.ErrorConsumed -> {
                    state.copy(error = null)
                }
            }

        private fun observeTodaySchedules() {
            scheduleJob?.cancel()
            scheduleRevision++
            subscribedDay = todayKey()
            scheduleJob =
                viewModelScope.launch {
                    dispatch(DoneReducerEvent.Loading)
                    scheduleRepository
                        .observeSchedulesForDate(Calendar.getInstance(), today = true)
                        .catch { e ->
                            Log.e(TAG, "일정 구독 실패", e)
                            dispatch(DoneReducerEvent.Failed(e.toAppError()))
                        }.collect {
                            scheduleRevision++
                            dispatch(DoneReducerEvent.Loaded(it))
                        }
                }
        }

        /**
         * 날이 바뀌었으면 오늘 회차로 다시 구독한다.
         *
         * 앱을 켜 둔 채 자정을 넘기면 구독은 어제 회차를 보고 있다. 그 회차 식별자가 완료 기록의
         * 열쇠라, 그대로 두면 어제 날짜가 서버에 남는다(#171).
         */
        private fun resubscribeIfDayChanged() {
            if (subscribedDay.isNotEmpty() && subscribedDay != todayKey()) {
                observeTodaySchedules()
            }
        }

        private fun todayKey(): String =
            Calendar.getInstance().let {
                "%04d-%03d".format(it.get(Calendar.YEAR), it.get(Calendar.DAY_OF_YEAR))
            }

        private fun toggleComplete(scheduleId: String) {
            val schedule = currentState.schedules.find { it.id == scheduleId } ?: return
            if (!pendingCompletions.add(scheduleId)) return
            val revision = scheduleRevision
            dispatch(DoneReducerEvent.Toggled(scheduleId))

            fun restoreIfCurrent() {
                if (revision == scheduleRevision) dispatch(DoneReducerEvent.Restored(schedule))
            }
            viewModelScope.launch {
                try {
                    val result = scheduleRepository.markScheduleAsCompleted(scheduleId, !schedule.completed, schedule.occurrenceDate)
                    if (result is ScheduleRepository.ScheduleResult.Error) {
                        restoreIfCurrent()
                        dispatch(DoneReducerEvent.Failed(result.error))
                    }
                } catch (e: CancellationException) {
                    restoreIfCurrent()
                    throw e
                } finally {
                    pendingCompletions.remove(scheduleId)
                }
            }
        }

        private companion object {
            const val TAG = "DoneViewModel"
        }
    }
