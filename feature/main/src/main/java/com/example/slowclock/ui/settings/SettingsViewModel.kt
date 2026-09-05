package com.example.slowclock.ui.settings

import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 정보 화면. 지금 다루는 설정은 화면 테마 하나다. 저장은 기기에만 남고, 저장소가 내는 흐름을
 * 다시 받아 상태로 옮긴다. 그래서 다른 화면에서 값이 바뀌어도 이 화면이 따라간다.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : MviViewModel<SettingsIntent, SettingsUiState, SettingsReducerEvent>(SettingsUiState()) {
        init {
            viewModelScope.launch {
                settingsRepository.observeThemeMode().collect { mode ->
                    dispatch(SettingsReducerEvent.ThemeModeLoaded(mode))
                }
            }
        }

        override fun onIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.SelectThemeMode -> settingsRepository.setThemeMode(intent.mode)
            }
        }

        override fun reduce(
            state: SettingsUiState,
            event: SettingsReducerEvent,
        ): SettingsUiState =
            when (event) {
                is SettingsReducerEvent.ThemeModeLoaded -> state.copy(themeMode = event.mode)
            }
    }
