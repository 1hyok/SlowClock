package com.example.slowclock.ui.main

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.core.alarm.AlarmScheduler
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
import kotlinx.coroutines.flow.combine
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
        private val alarmScheduler: AlarmScheduler,
    ) : MviViewModel<MainIntent, MainUiState, MainReducerEvent>(MainUiState()) {
        private var scheduleJob: Job? = null

        /** 지금 구독이 보고 있는 날. 날이 바뀌면 다시 건다(#171). */
        private var subscribedDay: String = ""

        /** 알람을 이미 맞춘 사용자. 화면이 다시 만들어져도 한 번만 맞춘다(#176). */
        private var alarmSyncedFor: String? = null

        init {
            observeSignedInUser()
            // 정확한 알람 권한이 없으면 첫 진입에 한 번만 이유를 설명한다. 설정으로 보내는 건
            // 사용자가 「설정 열기」 를 눌렀을 때다(#83).
            if (!alarmScheduler.canScheduleExactAlarms() && !settingsRepository.hasSeenExactAlarmNotice()) {
                dispatch(MainReducerEvent.ExactAlarmNoticeShown)
            }
            // 일정 구독은 로그인 상태를 받은 뒤 observeSignedInUser 가 건다.
            observeSharedReminders()
        }

        override fun onIntent(intent: MainIntent) {
            when (intent) {
                MainIntent.ScreenResumed -> {
                    resubscribeIfDayChanged()
                }

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

                MainIntent.OpenExactAlarmSettings -> {
                    settingsRepository.markExactAlarmNoticeSeen()
                    dispatch(MainReducerEvent.ExactAlarmSettingsRequested)
                }

                MainIntent.DismissExactAlarmNotice -> {
                    settingsRepository.markExactAlarmNoticeSeen()
                    dispatch(MainReducerEvent.ExactAlarmNoticeDismissed)
                }

                MainIntent.ConsumeExactAlarmSettingsRequest -> {
                    dispatch(MainReducerEvent.ExactAlarmSettingsRequestConsumed)
                }
            }
        }

        override fun reduce(
            state: MainUiState,
            event: MainReducerEvent,
        ): MainUiState =
            when (event) {
                is MainReducerEvent.UserResolved -> {
                    state.copy(currentUserId = event.userId, isSignedInKnown = true)
                }

                MainReducerEvent.SignedOut -> {
                    // 앞 사용자의 목록·집계·다이얼로그를 전부 비운다. 남겨 두면 다른 계정으로
                    // 로그인한 직후 그 목록이 그대로 먼저 보인다(#137).
                    state.copy(
                        todaySchedules = emptyList(),
                        sharedReminders = emptyList(),
                        sharedReminderOwners = emptyMap(),
                        currentSchedule = null,
                        completedCount = 0,
                        totalCount = 0,
                        isLoading = false,
                        error = null,
                        canRetry = false,
                        selectedScheduleForDetail = null,
                        scheduleToDelete = null,
                    )
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

                MainReducerEvent.ExactAlarmNoticeShown -> {
                    state.copy(showExactAlarmNotice = true)
                }

                MainReducerEvent.ExactAlarmNoticeDismissed -> {
                    state.copy(showExactAlarmNotice = false)
                }

                MainReducerEvent.ExactAlarmSettingsRequested -> {
                    state.copy(showExactAlarmNotice = false, openExactAlarmSettings = Unit)
                }

                MainReducerEvent.ExactAlarmSettingsRequestConsumed -> {
                    state.copy(openExactAlarmSettings = null)
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

        /**
         * 로그인·로그아웃이 화면에 바로 반영되도록 흐름으로 받는다.
         *
         * 로그아웃하면 구독을 끊고 앞 사용자의 목록을 비운다. 끊지 않으면 앞 uid 로 건
         * Firestore 리스너가 계속 살아 있고, 다른 계정으로 로그인해도 첫 응답이 오기 전까지
         * 앞 사람 일정이 그대로 보인다(#137).
         */
        private fun observeSignedInUser() {
            viewModelScope.launch {
                authRepository.observeCurrentUid().collect { uid ->
                    dispatch(MainReducerEvent.UserResolved(uid.orEmpty()))
                    if (uid != null) {
                        observeTodaySchedules()
                        syncAlarms(uid)
                    } else {
                        scheduleJob?.cancel()
                        scheduleJob = null
                        dispatch(MainReducerEvent.SignedOut)
                    }
                }
            }
        }

        /**
         * 서버의 일정으로 이 기기의 알람을 맞춘다.
         *
         * 알람 장부는 기기 안에만 있고 백업·기기 이전에서 뺐다. 폰을 바꾸거나 앱을 다시 깔면
         * 장부가 빈 채로 시작해, 화면에는 일정이 다 보이는데 알람은 하나도 걸려 있지 않다.
         * 사용자가 고칠 방법은 일정을 하나하나 다시 저장하는 것뿐인데 그걸 알 길이 없다(#176).
         *
         * 목록을 못 읽으면 아무것도 하지 않는다. 빈 목록으로 맞추면 걸려 있던 알람을 전부 지운다.
         */
        private fun syncAlarms(uid: String) {
            if (alarmSyncedFor == uid) return
            alarmSyncedFor = uid
            viewModelScope.launch {
                val schedules = scheduleRepository.getSchedulesOf(uid)
                if (schedules == null) {
                    Log.w(TAG, "일정 목록을 못 읽어 알람을 맞추지 못했다")
                    alarmSyncedFor = null
                    return@launch
                }
                runCatching { alarmScheduler.syncWith(schedules) }
                    .onFailure { Log.e(TAG, "알람 맞추기 실패", it) }
            }
        }

        private fun observeTodaySchedules() {
            scheduleJob?.cancel()
            subscribedDay = todayKey()
            scheduleJob =
                viewModelScope.launch {
                    dispatch(MainReducerEvent.Loading)
                    scheduleRepository
                        .observeSchedulesForDate(Calendar.getInstance(), today = true)
                        .catch { e ->
                            Log.e(TAG, "일정 구독 실패", e)
                            dispatch(MainReducerEvent.LoadFailed(e.toAppError(), canRetry = true))
                        }.collect { schedules ->
                            dispatch(MainReducerEvent.SchedulesLoaded(schedules, System.currentTimeMillis()))
                        }
                }
        }

        /**
         * 가족이 공유한 오늘 일정.
         *
         * 로그인 상태와 공유 코드를 함께 키로 삼는다. 공유 코드만 보면 로그아웃했다 다시
         * 로그인해도 구독이 다시 붙지 않는다 — 안쪽 흐름이 한 번 끝나면 flatMapLatest 는
         * 공유 코드 값이 실제로 바뀌기 전까지 새 구독을 걸지 않기 때문이다. 그리고 로그인
         * 전에는 보안 규칙이 읽기를 막으므로 리스너가 그 자리에서 닫힌다(#134 · #137).
         */

        private fun observeSharedReminders() {
            viewModelScope.launch {
                authRepository
                    .observeCurrentUid()
                    .combine(settingsRepository.observeShareCode()) { uid, shareCode ->
                        uid to shareCode
                    }.flatMapLatest { (uid, shareCode) ->
                        if (uid == null || shareCode.isNullOrBlank()) {
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
            val schedule = currentState.todaySchedules.find { it.id == scheduleId } ?: return
            dispatch(MainReducerEvent.CompletionToggled(scheduleId, System.currentTimeMillis()))
            viewModelScope.launch {
                val result =
                    scheduleRepository.markScheduleAsCompleted(
                        scheduleId = scheduleId,
                        completed = !schedule.completed,
                        occurrenceDate = schedule.occurrenceDate,
                    )
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
                        runCatching { alarmScheduler.cancel(schedule) }.onFailure { Log.e(TAG, "알람 취소 실패", it) }
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
                // 공유 일정이 바뀌면 Firestore 트리거(sendFcmToShareCodeWatchers)가 감시자에게 알린다.
                // 클라이언트가 남의 FCM 토큰을 읽어 직접 보내던 경로는 지웠다(#93).
                val result =
                    scheduleRepository.markScheduleAsCompleted(
                        scheduleId = scheduleId,
                        completed = !reminder.completed,
                        occurrenceDate = reminder.occurrenceDate,
                    )
                when (result) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        Unit
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
