package com.example.slowclock.ui.settings

import com.example.slowclock.ui.mvi.MviIntent
import com.example.slowclock.ui.mvi.ReducerEvent
import com.example.slowclock.ui.mvi.UiState

sealed interface ShareCodeIntent : MviIntent {
    data class UpdateInput(
        val value: String,
    ) : ShareCodeIntent

    data object Save : ShareCodeIntent

    data object ConsumeSaved : ShareCodeIntent
}

/**
 * [isSaved] 는 일회성 신호다. 화면이 소비하고 [ShareCodeIntent.ConsumeSaved] 로 되돌린다.
 *
 * [saveError] 는 신호가 아니라 화면에 남는 값이다. 잠깐 떴다 사라지는 안내로는 이 화면에서 할
 * 일을 알 수 없다 — 저장이 안 됐다는 사실과 다시 눌러야 한다는 것이 계속 보여야 한다.
 */
data class ShareCodeUiState(
    val input: String = "",
    val hasRegisteredCode: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveError: String? = null,
) : UiState {
    val canSave: Boolean get() = (input.isNotBlank() || hasRegisteredCode) && !isSaving
}

sealed interface ShareCodeReducerEvent : ReducerEvent {
    data class Initialized(
        val value: String,
    ) : ShareCodeReducerEvent

    data class InputChanged(
        val value: String,
    ) : ShareCodeReducerEvent

    data object Saving : ShareCodeReducerEvent

    data object Saved : ShareCodeReducerEvent

    data object SavedConsumed : ShareCodeReducerEvent

    data class SaveFailed(
        val message: String = "코드를 등록하지 못했습니다. 인터넷에 연결한 뒤 다시 눌러 주세요.",
    ) : ShareCodeReducerEvent
}
