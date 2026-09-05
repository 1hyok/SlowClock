package com.example.slowclock.ui.main

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * 메인 화면. 오늘 일정은 Firestore 리스너로 받는다. 완료 토글·삭제는 낙관적으로 먼저 반영하고,
 * 실패하면 리스너가 서버 상태로 되돌린다. 공유 일정은 기기에 저장된 공유 코드가 바뀔 때마다
 * 다시 구독한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository,
        private val settingsRepository: SettingsRepository,
    ) : MviViewModel<MainIntent, MainUiState, MainReducerEvent>(MainUiState()) {
        private var scheduleJob: Job? = null

        init {
            dispatch(MainReducerEvent.UserResolved(authRepository.currentUid.orEmpty()))
            observeTodaySchedules()
            observeSharedReminders()
        }

        override fun onIntent(intent: MainIntent) {
            when (intent) {
                MainIntent.Retry -> {
                    dispatch(MainReducerEvent.ErrorConsumed)
                    observeTodaySchedules()
                }

                is MainIntent.ToggleComplete -> {
                    toggleComplete(intent.scheduleId)
                }

                is MainIntent.ShowDetail -> {
                    currentState.todaySchedules
                        .find { it.id == intent.scheduleId }
                        ?.let { dispatch(MainReducerEvent.DetailShown(it)) }
                }

                MainIntent.HideDetail -> {
                    dispatch(MainReducerEvent.DetailHidden)
                }

                is MainIntent.RequestDelete -> {
                    currentState.todaySchedules
                        .find { it.id == intent.scheduleId }
                        ?.let { dispatch(MainReducerEvent.DeleteRequested(it)) }
                }

                MainIntent.DismissDelete -> {
                    dispatch(MainReducerEvent.DeleteDismissed)
                }

                MainIntent.ConfirmDelete -> {
                    deleteSchedule()
                }

                is MainIntent.ToggleSharedReminderComplete -> {
                    toggleSharedReminderComplete(intent.scheduleId)
                }

                MainIntent.ConsumeError -> {
                    dispatch(MainReducerEvent.ErrorConsumed)
                }
            }
        }

        override fun reduce(
            state: MainUiState,
            event: MainReducerEvent,
        ): MainUiState =
            when (event) {
                is MainReducerEvent.UserResolved -> {
                    state.copy(currentUserId = event.userId)
                }

                MainReducerEvent.Loading -> {
                    state.copy(isLoading = true, error = null, canRetry = false)
                }

                is MainReducerEvent.SchedulesLoaded -> {
                    state.withSchedules(event.schedules, event.nowMillis).copy(isLoading = false, error = null)
                }

                is MainReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, error = event.error, canRetry = event.canRetry)
                }

                is MainReducerEvent.CompletionToggled -> {
                    state.withSchedules(
                        state.todaySchedules.map { if (it.id == event.scheduleId) it.copy(completed = !it.completed) else it },
                        event.nowMillis,
                    )
                }

                is MainReducerEvent.DetailShown -> {
                    state.copy(selectedScheduleForDetail = event.schedule)
                }

                MainReducerEvent.DetailHidden -> {
                    state.copy(selectedScheduleForDetail = null)
                }

                is MainReducerEvent.DeleteRequested -> {
                    state.copy(scheduleToDelete = event.schedule)
                }

                MainReducerEvent.DeleteDismissed -> {
                    state.copy(scheduleToDelete = null)
                }

                MainReducerEvent.Deleting -> {
                    state.copy(isLoading = true, error = null, scheduleToDelete = null)
                }

                is MainReducerEvent.Deleted -> {
                    state
                        .withSchedules(state.todaySchedules.filter { it.id != event.scheduleId }, event.nowMillis)
                        .copy(isLoading = false)
                }

                is MainReducerEvent.SharedRemindersLoaded -> {
                    state.copy(sharedReminders = event.reminders)
                }

                is MainReducerEvent.SharedReminderOwnersLoaded -> {
                    state.copy(sharedReminderOwners = event.owners)
                }

                is MainReducerEvent.SharedReminderToggled -> {
                    state.copy(
                        sharedReminders =
                            state.sharedReminders.map {
                                if (it.id == event.scheduleId) it.copy(completed = !it.completed) else it
                            },
                    )
                }

                MainReducerEvent.ErrorConsumed -> {
                    state.copy(error = null, canRetry = false)
                }
            }

        private fun MainUiState.withSchedules(
            schedules: List<Schedule>,
            nowMillis: Long,
        ): MainUiState =
            copy(
                todaySchedules = schedules,
                currentSchedule = selectCurrentSchedule(schedules, nowMillis),
                completedCount = schedules.count { it.completed },
                totalCount = schedules.size,
            )

        private fun observeTodaySchedules() {
            scheduleJob?.cancel()
            scheduleJob =
                viewModelScope.launch {
                    dispatch(MainReducerEvent.Loading)
                    scheduleRepository
                        .observeSchedulesForDate(Calendar.getInstance())
                        .catch { e ->
                            Log.e(TAG, "일정 구독 실패", e)
                            dispatch(MainReducerEvent.LoadFailed(e.toAppError(), canRetry = true))
                        }.collect { schedules ->
                            dispatch(MainReducerEvent.SchedulesLoaded(schedules, System.currentTimeMillis()))
                        }
                }
        }

        private fun observeSharedReminders() {
            viewModelScope.launch {
                settingsRepository
                    .observeShareCode()
                    .flatMapLatest { shareCode ->
                        if (shareCode.isNullOrBlank()) {
                            flowOf(emptyList())
                        } else {
                            scheduleRepository.observeSchedulesBySharedCode(shareCode).catch { e ->
                                Log.e(TAG, "공유 일정 구독 실패", e)
                                emit(emptyList())
                            }
                        }
                    }.collect { reminders ->
                        val today = Calendar.getInstance()
                        val todayReminders = reminders.filter { it.startTime.toDate().isSameDay(today) }
                        dispatch(MainReducerEvent.SharedRemindersLoaded(todayReminders))
                        val ownerIds = todayReminders.map { it.userId }.filter { it.isNotBlank() }.distinct()
                        dispatch(MainReducerEvent.SharedReminderOwnersLoaded(userRepository.getUserNames(ownerIds)))
                    }
            }
        }

        private fun toggleComplete(scheduleId: String) {
            val schedule = currentState.todaySchedules.find { it.id == scheduleId } ?: return
            dispatch(MainReducerEvent.CompletionToggled(scheduleId, System.currentTimeMillis()))
            viewModelScope.launch {
                val result = scheduleRepository.markScheduleAsCompleted(scheduleId, !schedule.completed)
                if (result is ScheduleRepository.ScheduleResult.Error) {
                    Log.e(TAG, "완료 상태 변경 실패: ${result.error.message}")
                    dispatch(MainReducerEvent.LoadFailed(result.error, canRetry = false))
                }
            }
        }

        private fun deleteSchedule() {
            val schedule = currentState.scheduleToDelete ?: return
            dispatch(MainReducerEvent.Deleting)
            viewModelScope.launch {
                when (val result = scheduleRepository.deleteSchedule(schedule.id)) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        dispatch(MainReducerEvent.Deleted(schedule.id, System.currentTimeMillis()))
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        Log.e(TAG, "일정 삭제 실패: ${result.error.message}")
                        dispatch(MainReducerEvent.LoadFailed(result.error, canRetry = true))
                    }
                }
            }
        }

        private fun toggleSharedReminderComplete(scheduleId: String) {
            val reminder = currentState.sharedReminders.find { it.id == scheduleId } ?: return
            dispatch(MainReducerEvent.SharedReminderToggled(scheduleId))
            viewModelScope.launch {
                when (val result = scheduleRepository.markScheduleAsCompleted(scheduleId, !reminder.completed)) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        if (reminder.sharedCode.isNotBlank()) {
                            val nowCompleted = !reminder.completed
                            scheduleRepository.sendNotificationToShareCodeMembers(
                                shareCode = reminder.sharedCode,
                                title = if (nowCompleted) "일정이 완료됨" else "일정이 미완료로 변경됨",
                                message = "${reminder.title} 일정이 ${if (nowCompleted) "완료" else "미완료"} 처리되었습니다.",
                            )
                        }
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        dispatch(MainReducerEvent.LoadFailed(result.error, canRetry = false))
                    }
                }
            }
        }

        private companion object {
            const val TAG = "MainViewModel"
        }
    }

private fun Date.isSameDay(other: Calendar): Boolean {
    val calendar = Calendar.getInstance().apply { time = this@isSameDay }
    return calendar.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}
