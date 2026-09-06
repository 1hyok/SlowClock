package com.example.slowclock.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.domain.profile.DeleteAccountResult
import com.example.slowclock.domain.profile.DeleteAccountStep
import com.example.slowclock.domain.profile.DeleteAccountUseCase
import com.example.slowclock.domain.profile.SignOutUseCase
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
        private val scheduleRepository: ScheduleRepository,
        private val deleteAccount: DeleteAccountUseCase,
        private val signOutUseCase: SignOutUseCase,
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
                ProfileIntent.RetryShareCode -> retryShareCode()
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

                ProfileReducerEvent.ShareCodeRetryStarted -> {
                    state.copy(isRetryingShareCode = true)
                }

                is ProfileReducerEvent.ShareCodeRetryFinished -> {
                    state.copy(isRetryingShareCode = false, userMessage = event.message ?: state.userMessage)
                }
            }

        /**
         * 공유 코드를 다시 만들어 본다.
         *
         * 신호가 약한 곳에서 처음 로그인하면 코드가 비어 있는 채로 남고, 그 뒤에 만든 일정은
         * 가족이 어떤 코드로도 읽지 못한다. 비어 있다는 사실만 보여 주면 사용자가 할 수 있는 일이
         * 없으므로 다시 시도할 길을 함께 둔다(#134).
         */
        private fun retryShareCode() {
            if (currentState.isRetryingShareCode) return
            val profile = authRepository.currentProfile ?: return
            dispatch(ProfileReducerEvent.ShareCodeRetryStarted)
            viewModelScope.launch {
                val created =
                    userRepository.ensureShareCode(
                        uid = profile.uid,
                        name = profile.displayName,
                        email = profile.email,
                    )
                if (created) {
                    // 코드만 만들면 그 전에 저장한 일정은 sharedCode 가 빈 채라 가족이 영영
                    // 못 읽는다. 다시 시도 버튼이 「고쳤다」 는 잘못된 안심을 주면 안 된다(#178).
                    scheduleRepository.fillMissingSharedCode(profile.uid)
                    dispatch(ProfileReducerEvent.ShareCodeRetryFinished())
                    loadProfile()
                } else {
                    dispatch(
                        ProfileReducerEvent.ShareCodeRetryFinished(
                            "공유 코드를 만들지 못했습니다. 인터넷 연결을 확인한 뒤 다시 눌러 주세요.",
                        ),
                    )
                }
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
            // 세션만 끊으면 이 기기에 걸린 알람과 등록해 둔 공유 코드가 남는다(#165).
            signOutUseCase()
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
                DeleteAccountStep.SHARE_CODE_WATCHERS -> "공유 설정을 정리하지 못했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.USER_DOCUMENT -> "사용자 정보를 지우지 못했습니다. 잠시 후 다시 시도해 주세요."
                DeleteAccountStep.AUTH_USER -> "계정 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요."
            }
    }
