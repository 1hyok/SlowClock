package com.example.slowclock.ui.profile

import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState

/** 내 정보 화면에서 사용자가 하려는 것. */
sealed interface ProfileIntent : MviIntent {
    data object SignOut : ProfileIntent

    data object RequestDeleteAccount : ProfileIntent

    data object DismissDeleteConfirm : ProfileIntent

    data object ConfirmDeleteAccount : ProfileIntent

    data object ConsumeUserMessage : ProfileIntent

    data object ConsumeLeave : ProfileIntent

    /** 공유 코드를 못 만든 상태에서 다시 시도한다. */
    data object RetryShareCode : ProfileIntent

    data object RetryProfile : ProfileIntent
}

/** 화면을 떠나야 하는 이유. 로그아웃과 계정 삭제 모두 같은 경로(뒤로 가기)로 나간다. */
enum class ProfileLeaveReason {
    SIGNED_OUT,
    ACCOUNT_DELETED,
}

/**
 * 내 정보 화면의 단일 UI 상태.
 *
 * [userMessage] 와 [leave] 는 일회성 신호다. 화면이 [ObserveSignal][com.example.slowclock.ui.mvi.ObserveSignal] 로
 * 소비하고 `ConsumeXxx` Intent 로 되돌린다.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val email: String = "",
    val shareCode: String = "",
    val loadError: String? = null,
    /** 로그인하지 않아 정보를 보여 줄 수 없다. */
    val isSignedOut: Boolean = false,
    val isDeleteConfirmVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val userMessage: String? = null,
    val leave: ProfileLeaveReason? = null,
    /** 공유 코드를 다시 만드는 중이다. 그동안 다시 시도 버튼을 막는다. */
    val isRetryingShareCode: Boolean = false,
) : UiState

/** 내 정보 화면의 상태가 겪은 것. 화면은 만들지 않고 ViewModel 만 dispatch 한다. */
sealed interface ProfileReducerEvent : ReducerEvent {
    data object Loading : ProfileReducerEvent

    /** 로그인하지 않았다. 오류가 아니라 로그인 안내를 띄우는 상태다. */
    data object SignedOut : ProfileReducerEvent

    data class Loaded(
        val name: String,
        val email: String,
        val shareCode: String,
    ) : ProfileReducerEvent

    data object ShareCodeRetryStarted : ProfileReducerEvent

    /** [message] 가 있으면 다시 만들지 못했다는 뜻이다. 화면이 한 번 보여 주고 소비한다. */
    data class ShareCodeRetryFinished(
        val message: String? = null,
    ) : ProfileReducerEvent

    data class LoadFailed(
        val message: String,
    ) : ProfileReducerEvent

    data object DeleteConfirmShown : ProfileReducerEvent

    data object DeleteConfirmHidden : ProfileReducerEvent

    data object DeleteStarted : ProfileReducerEvent

    data class DeleteFailed(
        val message: String,
    ) : ProfileReducerEvent

    data class Left(
        val reason: ProfileLeaveReason,
    ) : ProfileReducerEvent

    data object UserMessageConsumed : ProfileReducerEvent

    data object LeaveConsumed : ProfileReducerEvent
}
