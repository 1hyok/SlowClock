package com.example.slowclock.ui.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 공유 코드 입력 화면. 감시자로 등록한 뒤 코드를 기기에 저장한다. */
@HiltViewModel
class ShareCodeViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val userRepository: UserRepository,
    ) : MviViewModel<ShareCodeIntent, ShareCodeUiState, ShareCodeReducerEvent>(ShareCodeUiState()) {
        init {
            dispatch(ShareCodeReducerEvent.InputChanged(settingsRepository.getShareCode().orEmpty()))
        }

        override fun onIntent(intent: ShareCodeIntent) {
            when (intent) {
                is ShareCodeIntent.UpdateInput -> dispatch(ShareCodeReducerEvent.InputChanged(intent.value))
                ShareCodeIntent.Save -> save()
                ShareCodeIntent.ConsumeSaved -> dispatch(ShareCodeReducerEvent.SavedConsumed)
            }
        }

        override fun reduce(
            state: ShareCodeUiState,
            event: ShareCodeReducerEvent,
        ): ShareCodeUiState =
            when (event) {
                is ShareCodeReducerEvent.InputChanged -> {
                    state.copy(input = event.value, saveError = null)
                }

                ShareCodeReducerEvent.Saving -> {
                    state.copy(isSaving = true, saveError = null)
                }

                ShareCodeReducerEvent.Saved -> {
                    state.copy(isSaving = false, isSaved = true)
                }

                ShareCodeReducerEvent.SavedConsumed -> {
                    state.copy(isSaved = false)
                }

                ShareCodeReducerEvent.SaveFailed -> {
                    state.copy(
                        isSaving = false,
                        saveError = "코드를 등록하지 못했습니다. 인터넷에 연결한 뒤 다시 눌러 주세요.",
                    )
                }
            }

        private fun save() {
            if (!currentState.canSave) return
            val shareCode = currentState.input.trim()
            // 바꾸기 전 코드. 등록을 지우지 않으면 그 사람에게 내 토큰이 계속 남는다(#124).
            val previousShareCode = settingsRepository.getShareCode()
            dispatch(ShareCodeReducerEvent.Saving)
            viewModelScope.launch {
                // 감시자 등록이 곧 공유 일정을 읽을 권한이다. 등록에 실패한 코드를 기기에 저장하면
                // 가족 일정이 빈 채로 남고 사용자는 이유를 알 길이 없다. 그래서 등록이 된 뒤에
                // 저장한다. 실패하면 앞 코드도 그대로 둔다 — 새 코드가 안 됐는데 앞의 것까지
                // 잃으면 되돌아갈 자리가 없다(#174).
                if (!userRepository.registerShareCodeWatcher(shareCode)) {
                    Log.w("ShareCodeWatcher", "watcher register failed shareCode=$shareCode")
                    dispatch(ShareCodeReducerEvent.SaveFailed)
                    return@launch
                }
                if (!previousShareCode.isNullOrBlank() && previousShareCode != shareCode) {
                    val unregistered = userRepository.unregisterShareCodeWatcher(previousShareCode)
                    Log.d("ShareCodeWatcher", "watcher unregistered=$unregistered shareCode=$previousShareCode")
                }
                settingsRepository.setShareCode(shareCode)
                dispatch(ShareCodeReducerEvent.Saved)
            }
        }
    }
