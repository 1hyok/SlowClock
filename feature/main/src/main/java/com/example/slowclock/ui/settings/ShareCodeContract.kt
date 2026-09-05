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

/** [isSaved] 는 일회성 신호다. 화면이 소비하고 [ShareCodeIntent.ConsumeSaved] 로 되돌린다. */
data class ShareCodeUiState(
    val input: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) : UiState {
    val canSave: Boolean get() = input.isNotBlank() && !isSaving
}

sealed interface ShareCodeReducerEvent : ReducerEvent {
    data class InputChanged(
        val value: String,
    ) : ShareCodeReducerEvent

    data object Saving : ShareCodeReducerEvent

    data object Saved : ShareCodeReducerEvent

    data object SavedConsumed : ShareCodeReducerEvent
}
