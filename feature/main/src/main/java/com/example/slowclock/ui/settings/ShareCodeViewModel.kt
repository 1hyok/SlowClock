package com.example.slowclock.ui.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 공유 코드 입력 화면. 코드를 기기에 저장하고 감시자 토큰을 등록한다. */
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
                is ShareCodeReducerEvent.InputChanged -> state.copy(input = event.value)
                ShareCodeReducerEvent.Saving -> state.copy(isSaving = true)
                ShareCodeReducerEvent.Saved -> state.copy(isSaving = false, isSaved = true)
                ShareCodeReducerEvent.SavedConsumed -> state.copy(isSaved = false)
            }

        private fun save() {
            if (!currentState.canSave) return
            val shareCode = currentState.input.trim()
            // 바꾸기 전 코드. 등록을 지우지 않으면 그 사람에게 내 토큰이 계속 남는다(#124).
            val previousShareCode = settingsRepository.getShareCode()
            dispatch(ShareCodeReducerEvent.Saving)
            viewModelScope.launch {
                if (!previousShareCode.isNullOrBlank() && previousShareCode != shareCode) {
                    val unregistered = userRepository.unregisterShareCodeWatcher(previousShareCode)
                    Log.d("ShareCodeWatcher", "watcher unregistered=$unregistered shareCode=$previousShareCode")
                }
                settingsRepository.setShareCode(shareCode)
                val registered = userRepository.registerShareCodeWatcher(shareCode)
                Log.d("ShareCodeWatcher", "watcher registered=$registered shareCode=$shareCode")
                dispatch(ShareCodeReducerEvent.Saved)
            }
        }
    }
