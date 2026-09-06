package com.example.slowclock.ui.main

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState
import com.example.slowclock.util.AppError

/** 메인 화면에서 사용자가 하려는 것. */
sealed interface MainIntent : MviIntent {
    data object Retry : MainIntent

    /** 화면이 다시 보인다. 날이 바뀌었으면 오늘 회차로 다시 구독한다(#171). */
    data object ScreenResumed : MainIntent

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

    data object OpenNotificationSettings : MainIntent

    data object ConsumeNotificationSettingsRequest : MainIntent

    data object ExactAlarmSettingsUnavailable : MainIntent

    data object ConsumeUserMessage : MainIntent
}

/** 동일한 일정을 다시 삭제하더라도 늦은 이전 응답과 구분한다. */
data class DeleteOperation(
    val schedule: Schedule,
    val userId: String,
    val requestId: String,
)

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
    val pendingDelete: DeleteOperation? = null,
    /** 목록 스냅샷이 와도 사용자가 재시도하거나 닫을 때까지 실패 작업을 보존한다. */
    val failedDelete: DeleteOperation? = null,
    val currentUserId: String = "",
    /** 로그인 여부를 아직 모르는 동안에는 로그인 안내를 띄우지 않는다. */
    val isSignedInKnown: Boolean = false,
    /** true 면 정확한 알람 권한을 설명하는 다이얼로그가 떠 있다. */
    val showExactAlarmNotice: Boolean = false,
    /** null 이 아니면 화면이 시스템 설정을 한 번 연다. */
    val openExactAlarmSettings: Unit? = null,
    val alarmControlsAvailable: Boolean = true,
    val openNotificationSettings: Unit? = null,
    val userMessage: String? = null,
) : UiState

/** 메인 화면의 상태가 겪은 것. 화면은 만들지 않고 ViewModel 만 dispatch 한다. */
sealed interface MainReducerEvent : ReducerEvent {
    data class UserResolved(
        val userId: String,
    ) : MainReducerEvent

    /**
     * 로그아웃했다. 앞 사용자의 일정과 집계를 비운다.
     *
     * [UserResolved] 만 내면 화면은 로그인 안내로 바뀌지만 상태에는 앞 사람 목록이 남아,
     * 다른 계정으로 로그인한 직후 Firestore 첫 응답이 오기 전까지 그 목록이 그대로 보인다(#137).
     */
    data object SignedOut : MainReducerEvent

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

    data class CompletionRestored(
        val schedule: Schedule,
        val shared: Boolean,
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

    data class Deleting(
        val operation: DeleteOperation,
    ) : MainReducerEvent

    data class DeleteFailed(
        val operation: DeleteOperation,
        val error: AppError,
    ) : MainReducerEvent

    data class Deleted(
        val operation: DeleteOperation,
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

    data class AlarmControlsChecked(
        val available: Boolean,
    ) : MainReducerEvent

    data object NotificationSettingsRequested : MainReducerEvent

    data object NotificationSettingsRequestConsumed : MainReducerEvent

    data object ExactAlarmSettingsFailed : MainReducerEvent

    data object UserMessageConsumed : MainReducerEvent
}
