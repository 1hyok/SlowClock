package com.example.slowclock.ui.mvi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * MVI 베이스 계약.
 *
 * 1. 상태는 `dispatch` → `reduce` 경로로만 바뀐다. Intent 를 받아도 dispatch 하지 않으면 그대로다.
 * 2. 일회성 신호는 `Intent.ConsumeXxx` 로 reset 된다.
 * 3. 같은 신호가 연속 두 번 나도 두 번 소비된다.
 */
class MviViewModelTest {
    @Test
    fun `dispatch 한 event 만 reduce 를 지나 상태가 된다`() {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Increase)

        assertEquals(listOf<CounterEvent>(CounterEvent.Increased), viewModel.reducedEvents)
        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `dispatch 하지 않는 Intent 는 상태를 바꾸지 않는다`() {
        val viewModel = CounterViewModel()
        val before = viewModel.uiState.value

        viewModel.onIntent(CounterIntent.Navigate)

        assertEquals(1, viewModel.navigations)
        assertEquals(emptyList<CounterEvent>(), viewModel.reducedEvents)
        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun `일회성 신호는 ConsumeXxx Intent 로 reset 된다`() {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Fail("저장할 수 없습니다"))
        assertEquals("저장할 수 없습니다", viewModel.uiState.value.errorMessage)

        viewModel.onIntent(CounterIntent.ConsumeError)

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `같은 신호가 연속 두 번 나도 소비 사이에 reset 되어 두 번 다 관측된다`() {
        val viewModel = CounterViewModel()
        val observed = mutableListOf<String>()

        repeat(2) {
            viewModel.onIntent(CounterIntent.Fail("저장할 수 없습니다"))
            viewModel.uiState.value.errorMessage
                ?.let(observed::add)
            viewModel.onIntent(CounterIntent.ConsumeError)
        }

        assertEquals(listOf("저장할 수 없습니다", "저장할 수 없습니다"), observed)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `reduce 는 같은 입력에 같은 결과를 낸다`() {
        val viewModel = CounterViewModel()
        val start = viewModel.uiState.value

        viewModel.onIntent(CounterIntent.Increase)

        assertEquals(start.copy(count = start.count + 1), viewModel.uiState.value)
    }
}

private sealed interface CounterIntent : MviIntent {
    data object Increase : CounterIntent

    data object Navigate : CounterIntent

    data class Fail(
        val message: String,
    ) : CounterIntent

    data object ConsumeError : CounterIntent
}

private sealed interface CounterEvent : ReducerEvent {
    data object Increased : CounterEvent

    data class Failed(
        val message: String,
    ) : CounterEvent

    data object ErrorConsumed : CounterEvent
}

private data class CounterUiState(
    val count: Int = 0,
    val errorMessage: String? = null,
) : UiState

/**
 * 테스트용 최소 상속체. [reducedEvents] 는 `reduce` 안에서 기록한다. 리듀서에 부수효과를
 * 두지 말라는 계약을 이 fake 만 어긴다. 전이 경로가 `private` 뒤에 있어 밖에서 관측할 다른
 * 수단이 없어서다.
 */
private class CounterViewModel : MviViewModel<CounterIntent, CounterUiState, CounterEvent>(CounterUiState()) {
    val reducedEvents = mutableListOf<CounterEvent>()
    var navigations = 0
        private set

    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increase -> dispatch(CounterEvent.Increased)
            CounterIntent.Navigate -> navigations++
            is CounterIntent.Fail -> dispatch(CounterEvent.Failed(intent.message))
            CounterIntent.ConsumeError -> dispatch(CounterEvent.ErrorConsumed)
        }
    }

    override fun reduce(
        state: CounterUiState,
        event: CounterEvent,
    ): CounterUiState {
        reducedEvents += event
        return when (event) {
            CounterEvent.Increased -> state.copy(count = state.count + 1)
            is CounterEvent.Failed -> state.copy(errorMessage = event.message)
            CounterEvent.ErrorConsumed -> state.copy(errorMessage = null)
        }
    }
}
