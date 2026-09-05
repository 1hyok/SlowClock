package com.example.slowclock.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.domain.profile.DeleteAccountResult
import com.example.slowclock.domain.profile.DeleteAccountStep
import com.example.slowclock.domain.profile.DeleteAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository,
        private val deleteAccount: DeleteAccountUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileUiState())
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        init {
            loadProfile()
        }

        private fun loadProfile() {
            viewModelScope.launch {
                val authProfile = authRepository.currentProfile
                if (authProfile == null) {
                    _uiState.update { it.copy(isLoading = false, loadError = "로그인이 필요합니다.") }
                    return@launch
                }
                val user = userRepository.getCurrentUser()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = user?.name?.takeIf { name -> name.isNotBlank() } ?: authProfile.displayName,
                        email = user?.email?.takeIf { email -> email.isNotBlank() } ?: authProfile.email,
                        shareCode = user?.shareCode.orEmpty(),
                        loadError = null,
                    )
                }
            }
        }

        fun signOut() {
            authRepository.signOut()
            _uiState.update { it.copy(shouldLeave = true) }
        }

        fun requestDeleteAccount() {
            _uiState.update { it.copy(isDeleteConfirmVisible = true) }
        }

        fun dismissDeleteConfirm() {
            _uiState.update { it.copy(isDeleteConfirmVisible = false) }
        }

        fun confirmDeleteAccount() {
            if (_uiState.value.isDeleting) return
            _uiState.update { it.copy(isDeleteConfirmVisible = false, isDeleting = true) }
            viewModelScope.launch {
                val result = deleteAccount()
                _uiState.update { state ->
                    when (result) {
                        DeleteAccountResult.Success -> {
                            state.copy(isDeleting = false, shouldLeave = true)
                        }

                        DeleteAccountResult.NotSignedIn -> {
                            state.copy(isDeleting = false, shouldLeave = true)
                        }

                        DeleteAccountResult.RecentLoginRequired -> {
                            state.copy(
                                isDeleting = false,
                                userMessage = "보안을 위해 로그아웃한 뒤 다시 로그인하고 계정 삭제를 다시 눌러 주세요.",
                            )
                        }

                        is DeleteAccountResult.Failed -> {
                            state.copy(
                                isDeleting = false,
                                userMessage = failureMessage(result.step),
                            )
                        }
                    }
                }
            }
        }

        fun onUserMessageShown() {
            _uiState.update { it.copy(userMessage = null) }
        }

        fun onLeaveHandled() {
            _uiState.update { it.copy(shouldLeave = false) }
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
