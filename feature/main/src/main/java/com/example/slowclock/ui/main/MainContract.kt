package com.example.slowclock.ui.main

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState
import com.example.slowclock.util.AppError

/** 메인 화면에서 사용자가 하려는 것. */
sealed interface MainIntent : MviIntent {
    data object Retry : MainIntent

    data class ToggleComplete(
        val scheduleId: String,
    ) : MainIntent

    data class ShowDetail(
        val scheduleId: String,
    ) : MainIntent

    data object HideDetail : MainIntent

    data class RequestDelete(
        val scheduleId: String,
    ) : MainIntent

    data object DismissDelete : MainIntent

    data object ConfirmDelete : MainIntent

    data class ToggleSharedReminderComplete(
        val scheduleId: String,
    ) : MainIntent

    data object ConsumeError : MainIntent

    /** 정확한 알람 안내에서 「설정 열기」 를 눌렀다. */
    data object OpenExactAlarmSettings : MainIntent

    /** 정확한 알람 안내를 닫았다. 다시 띄우지 않는다. */
    data object DismissExactAlarmNotice : MainIntent

    data object ConsumeExactAlarmSettingsRequest : MainIntent
}

/** 메인 화면의 단일 UI 상태. 오늘 일정과 공유 일정, 다이얼로그 상태를 담는다. */
data class MainUiState(
    val todaySchedules: List<Schedule> = emptyList(),
    val sharedReminders: List<Schedule> = emptyList(),
    val sharedReminderOwners: Map<String, String> = emptyMap(),
    val currentSchedule: Schedule? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val canRetry: Boolean = false,
    val selectedScheduleForDetail: Schedule? = null,
    /** null 이 아니면 삭제 확인 다이얼로그가 떠 있다. */
    val scheduleToDelete: Schedule? = null,
    val currentUserId: String = "",
    /** true 면 정확한 알람 권한을 설명하는 다이얼로그가 떠 있다. */
    val showExactAlarmNotice: Boolean = false,
    /** null 이 아니면 화면이 시스템 설정을 한 번 연다. */
    val openExactAlarmSettings: Unit? = null,
) : UiState

/** 메인 화면의 상태가 겪은 것. 화면은 만들지 않고 ViewModel 만 dispatch 한다. */
sealed interface MainReducerEvent : ReducerEvent {
    data class UserResolved(
        val userId: String,
    ) : MainReducerEvent

    data object Loading : MainReducerEvent

    /** [nowMillis] 는 「지금 할 일」 계산에만 쓴다. reduce 가 시계를 읽지 않도록 밖에서 넣는다. */
    data class SchedulesLoaded(
        val schedules: List<Schedule>,
        val nowMillis: Long,
    ) : MainReducerEvent

    data class LoadFailed(
        val error: AppError,
        val canRetry: Boolean,
    ) : MainReducerEvent

    data class CompletionToggled(
        val scheduleId: String,
        val nowMillis: Long,
    ) : MainReducerEvent

    data class DetailShown(
        val schedule: Schedule,
    ) : MainReducerEvent

    data object DetailHidden : MainReducerEvent

    data class DeleteRequested(
        val schedule: Schedule,
    ) : MainReducerEvent

    data object DeleteDismissed : MainReducerEvent

    data object Deleting : MainReducerEvent

    data class Deleted(
        val scheduleId: String,
        val nowMillis: Long,
    ) : MainReducerEvent

    data class SharedRemindersLoaded(
        val reminders: List<Schedule>,
    ) : MainReducerEvent

    data class SharedReminderOwnersLoaded(
        val owners: Map<String, String>,
    ) : MainReducerEvent

    data class SharedReminderToggled(
        val scheduleId: String,
    ) : MainReducerEvent

    data object ErrorConsumed : MainReducerEvent

    data object ExactAlarmNoticeShown : MainReducerEvent

    data object ExactAlarmNoticeDismissed : MainReducerEvent

    data object ExactAlarmSettingsRequested : MainReducerEvent

    data object ExactAlarmSettingsRequestConsumed : MainReducerEvent
}
