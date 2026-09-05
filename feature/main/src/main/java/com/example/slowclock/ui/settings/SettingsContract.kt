package com.example.slowclock.ui.settings

import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState

/** 정보 화면에서 사용자가 하려는 것. */
sealed interface SettingsIntent : MviIntent {
    data class SelectThemeMode(
        val mode: ThemeMode,
    ) : SettingsIntent
}

/** 정보 화면의 단일 UI 상태. */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) : UiState

/** 정보 화면의 상태가 겪은 것. */
sealed interface SettingsReducerEvent : ReducerEvent {
    data class ThemeModeLoaded(
        val mode: ThemeMode,
    ) : SettingsReducerEvent
}
