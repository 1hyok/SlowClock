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

    /** 화면이 다시 보일 때마다 전체 화면 알람 권한을 다시 읽는다. 설정 화면은 결과를 돌려주지 않는다. */
    data object RefreshAlarmPermission : SettingsIntent

    /** 전체 화면 알람 안내에서 「설정 열기」 를 눌렀다. */
    data object OpenFullScreenAlarmSettings : SettingsIntent

    data object ConsumeFullScreenAlarmSettingsRequest : SettingsIntent
}

/** 정보 화면의 단일 UI 상태. */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** true 면 전체 화면 알람 권한 안내 카드를 보여 준다. 허용돼 있으면 카드가 아예 없다. */
    val showFullScreenAlarmNotice: Boolean = false,
    /** null 이 아니면 화면이 시스템 설정을 한 번 연다. */
    val openFullScreenAlarmSettings: Unit? = null,
) : UiState

/** 정보 화면의 상태가 겪은 것. */
sealed interface SettingsReducerEvent : ReducerEvent {
    data class ThemeModeLoaded(
        val mode: ThemeMode,
    ) : SettingsReducerEvent

    data class FullScreenAlarmPermissionChecked(
        val granted: Boolean,
    ) : SettingsReducerEvent

    data object FullScreenAlarmSettingsRequested : SettingsReducerEvent

    data object FullScreenAlarmSettingsRequestConsumed : SettingsReducerEvent
}
