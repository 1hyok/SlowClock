package com.example.slowclock.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * [UiState] 안의 일회성 신호를 소비하는 정본 관용구.
 *
 * [signal] 이 null 이 아니게 되면 [onSignal] 을 한 번 부르고, 곧바로 [consumed] Intent 를
 * 되쏘아 신호를 null 로 되돌린다. 화면마다 `LaunchedEffect` 를 다시 쓰면 규약이 갈린다.
 *
 * ```kotlin
 * ObserveSignal(
 *     signal = state.userMessage,
 *     consumed = ProfileIntent.ConsumeUserMessage,
 *     onIntent = viewModel::onIntent,
 * ) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
 * ```
 *
 * ## 같은 신호가 연속 두 번 와도 두 번 소비된다
 *
 * 소비가 [signal] 을 null 로 되돌리므로 `A → null → A` 로 키가 두 번 바뀐다. 값이 같아도
 * [LaunchedEffect] 가 다시 시작하는 이유다.
 *
 * ## [onSignal] 안에서 suspend 를 직접 기다리지 않는다
 *
 * 소비 직후의 상태 변화가 이 [LaunchedEffect] 를 재시작시키며 이전 코루틴을 취소한다.
 * 스낵바처럼 시간이 걸리는 표출은 `rememberCoroutineScope()` 에 launch 해 effect 수명과
 * 분리한다.
 */
@Composable
fun <I : MviIntent, T : Any> ObserveSignal(
    signal: T?,
    consumed: I,
    onIntent: (I) -> Unit,
    onSignal: (T) -> Unit,
) {
    // 키는 signal 하나뿐이라, 콜백만 바뀐 재구성에서는 effect 가 다시 시작하지 않는다.
    // 그대로 캡처하면 옛 람다가 남으므로 최신 값을 읽어 쓴다.
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentOnSignal by rememberUpdatedState(onSignal)

    LaunchedEffect(signal) {
        if (signal == null) return@LaunchedEffect
        currentOnSignal(signal)
        currentOnIntent(consumed)
    }
}
