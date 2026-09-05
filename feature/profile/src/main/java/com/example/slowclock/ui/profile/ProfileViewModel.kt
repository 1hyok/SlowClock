package com.example.slowclock.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.domain.profile.DeleteAccountResult
import com.example.slowclock.domain.profile.DeleteAccountStep
import com.example.slowclock.domain.profile.DeleteAccountUseCase
import com.example.slowclock.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository,
        private val deleteAccount: DeleteAccountUseCase,
    ) : MviViewModel<ProfileIntent, ProfileUiState, ProfileReducerEvent>(ProfileUiState()) {
        init {
            loadProfile()
        }

        override fun onIntent(intent: ProfileIntent) {
            when (intent) {
                ProfileIntent.SignOut -> signOut()
                ProfileIntent.RequestDeleteAccount -> dispatch(ProfileReducerEvent.DeleteConfirmShown)
                ProfileIntent.DismissDeleteConfirm -> dispatch(ProfileReducerEvent.DeleteConfirmHidden)
                ProfileIntent.ConfirmDeleteAccount -> confirmDeleteAccount()
                ProfileIntent.ConsumeUserMessage -> dispatch(ProfileReducerEvent.UserMessageConsumed)
                ProfileIntent.ConsumeLeave -> dispatch(ProfileReducerEvent.LeaveConsumed)
            }
        }

        override fun reduce(
            state: ProfileUiState,
            event: ProfileReducerEvent,
        ): ProfileUiState =
            when (event) {
                is ProfileReducerEvent.Loaded -> {
                    state.copy(
                        isLoading = false,
                        name = event.name,
                        email = event.email,
                        shareCode = event.shareCode,
                        loadError = null,
                    )
                }

                is ProfileReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, loadError = event.message)
                }

                ProfileReducerEvent.SignedOut -> {
                    state.copy(isLoading = false, isSignedOut = true, loadError = null)
                }

                ProfileReducerEvent.DeleteConfirmShown -> {
                    state.copy(isDeleteConfirmVisible = true)
                }

                ProfileReducerEvent.DeleteConfirmHidden -> {
                    state.copy(isDeleteConfirmVisible = false)
                }

                ProfileReducerEvent.DeleteStarted -> {
                    state.copy(isDeleteConfirmVisible = false, isDeleting = true)
                }

                is ProfileReducerEvent.DeleteFailed -> {
                    state.copy(isDeleting = false, userMessage = event.message)
                }

                is ProfileReducerEvent.Left -> {
                    state.copy(isDeleting = false, leave = event.reason)
                }

                ProfileReducerEvent.UserMessageConsumed -> {
                    state.copy(userMessage = null)
                }

                ProfileReducerEvent.LeaveConsumed -> {
                    state.copy(leave = null)
                }
            }

        private fun loadProfile() {
            viewModelScope.launch {
                val authProfile = authRepository.currentProfile
                if (authProfile == null) {
                    dispatch(ProfileReducerEvent.SignedOut)
                    return@launch
                }
                val user = userRepository.getCurrentUser()
                dispatch(
                    ProfileReducerEvent.Loaded(
                        name = user?.name?.takeIf { it.isNotBlank() } ?: authProfile.displayName,
                        email = user?.email?.takeIf { it.isNotBlank() } ?: authProfile.email,
                        shareCode = user?.shareCode.orEmpty(),
                    ),
                )
            }
        }

        private fun signOut() {
            authRepository.signOut()
            dispatch(ProfileReducerEvent.Left(ProfileLeaveReason.SIGNED_OUT))
        }

        private fun confirmDeleteAccount() {
            if (currentState.isDeleting) return
            dispatch(ProfileReducerEvent.DeleteStarted)
            viewModelScope.launch {
                when (val result = deleteAccount()) {
                    DeleteAccountResult.Success,
                    DeleteAccountResult.NotSignedIn,
                    -> {
                        dispatch(ProfileReducerEvent.Left(ProfileLeaveReason.ACCOUNT_DELETED))
                    }

                    DeleteAccountResult.RecentLoginRequired -> {
                        dispatch(
                            ProfileReducerEvent.DeleteFailed(
                                "보안을 위해 로그아웃한 뒤 다시 로그인하고 계정 삭제를 다시 눌러 주세요.",
                            ),
                        )
                    }

                    is DeleteAccountResult.Failed -> {
                        dispatch(ProfileReducerEvent.DeleteFailed(failureMessage(result.step)))
                    }
                }
            }
        }

        private fun failureMessage(step: DeleteAccountStep): String =
            when (step) {
                DeleteAccountStep.SCHEDULES -> "일정을 지우지 못했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.FAMILY_GROUPS -> "가족 그룹 정리에 실패했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.NOTIFICATIONS -> "알림 기록을 지우지 못했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.USER_DOCUMENT -> "사용자 정보를 지우지 못했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.AUTH_USER -> "계정 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요."
            }
    }
