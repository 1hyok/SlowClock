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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private var profileJob: Job? = null
        private var shareCodeJob: Job? = null
        private var profileGeneration = 0L

        init {
            viewModelScope.launch {
                authRepository.observeCurrentUid().distinctUntilChanged().collect { uid ->
                    observeProfile(uid)
                }
            }
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
                ProfileIntent.RetryProfile -> observeProfile(authRepository.currentUid)
            }
        }

        override fun reduce(
            state: ProfileUiState,
            event: ProfileReducerEvent,
        ): ProfileUiState =
            when (event) {
                ProfileReducerEvent.Loading -> {
                    ProfileUiState(leave = state.leave)
                }

                is ProfileReducerEvent.Loaded -> {
                    state.copy(
                        isLoading = false,
                        isSignedOut = false,
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
                    ProfileUiState(isLoading = false, isSignedOut = true, leave = state.leave)
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
            val generation = profileGeneration
            dispatch(ProfileReducerEvent.ShareCodeRetryStarted)
            shareCodeJob =
                viewModelScope.launch {
                    try {
                        val created =
                            userRepository.ensureShareCode(
                                uid = profile.uid,
                                name = profile.displayName,
                                email = profile.email,
                            )
                        if (!isCurrentProfile(profile.uid, generation)) return@launch
                        if (created) {
                            // 코드 생성 전의 일정도 맞춘다. 프로필은 문서 구독으로 갱신된다(#178).
                            scheduleRepository.fillMissingSharedCode(profile.uid)
                            if (isCurrentProfile(profile.uid, generation)) {
                                dispatch(ProfileReducerEvent.ShareCodeRetryFinished())
                            }
                        } else {
                            dispatch(ProfileReducerEvent.ShareCodeRetryFinished(SHARE_CODE_RETRY_ERROR))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (!isCurrentProfile(profile.uid, generation)) return@launch
                        dispatch(
                            ProfileReducerEvent.ShareCodeRetryFinished(SHARE_CODE_RETRY_ERROR),
                        )
                    }
                }
        }

        private fun observeProfile(uid: String?) {
            profileJob?.cancel()
            shareCodeJob?.cancel()
            val generation = ++profileGeneration
            if (uid == null) {
                dispatch(ProfileReducerEvent.SignedOut)
                return
            }
            dispatch(ProfileReducerEvent.Loading)
            profileJob =
                viewModelScope.launch {
                    userRepository
                        .observeUser(uid)
                        .catch {
                            if (isCurrentProfile(uid, generation)) {
                                dispatch(ProfileReducerEvent.LoadFailed("내 정보를 읽지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요."))
                            }
                        }.collect { user ->
                            if (!isCurrentProfile(uid, generation)) return@collect
                            val authProfile = authRepository.currentProfile?.takeIf { it.uid == uid } ?: return@collect
                            dispatch(
                                ProfileReducerEvent.Loaded(
                                    name = user?.name?.takeIf { it.isNotBlank() } ?: authProfile.displayName,
                                    email = user?.email?.takeIf { it.isNotBlank() } ?: authProfile.email,
                                    shareCode = user?.shareCode.orEmpty(),
                                ),
                            )
                        }
                }
        }

        private fun isCurrentProfile(
            uid: String,
            generation: Long,
        ): Boolean = authRepository.currentUid == uid && profileGeneration == generation

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

        private companion object {
            const val SHARE_CODE_RETRY_ERROR = "공유 코드를 만들지 못했습니다. 인터넷 연결을 확인한 뒤 다시 눌러 주세요."
        }
    }
