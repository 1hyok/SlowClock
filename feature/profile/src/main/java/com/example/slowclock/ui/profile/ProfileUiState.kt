package com.example.slowclock.ui.profile

/**
 * 내 정보 화면의 단일 UI 상태.
 *
 * 일회성 신호(안내 문구, 화면 이탈)도 여기에 담고, 화면이 소비한 뒤 ViewModel 의 onXxxHandled 로 되돌린다.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val email: String = "",
    val shareCode: String = "",
    val loadError: String? = null,
    val isDeleteConfirmVisible: Boolean = false,
    val isDeleting: Boolean = false,
    /** 스낵바로 한 번 보여 줄 안내. 화면이 보여 준 뒤 [ProfileViewModel.onUserMessageShown] 으로 비운다. */
    val userMessage: String? = null,
    /** 로그아웃이나 계정 삭제가 끝나 화면을 떠나야 한다. 화면이 처리한 뒤 [ProfileViewModel.onLeaveHandled] 로 비운다. */
    val shouldLeave: Boolean = false,
)
