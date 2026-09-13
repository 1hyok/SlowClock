package com.example.slowclock.ui.mvi

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * MVI 베이스 계약.
 *
 * 1. 상태는 `dispatch` → `reduce` 경로로만 바뀐다. Intent 를 받아도 dispatch 하지 않으면 그대로다.
 * 2. 일회성 신호는 `Intent.ConsumeXxx` 로 reset 된다.
 * 3. collector 가 관측하고 소비한 뒤 같은 신호가 다시 오면 두 번 관측된다.
 * 빠른 연속 쓰기의 모든 중간값 전달이나 제품 화면의 lifecycle 수집까지 보장하는 테스트는 아니다.
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
    fun `실제 collector가 소비한 뒤 같은 신호를 다시 관측한다`() =
        runBlocking {
            val viewModel = CounterViewModel()
            val observed = mutableListOf<String?>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiState
                        .map { it.errorMessage }
                        .take(5)
                        .toList(observed)
                }

            repeat(2) {
                viewModel.onIntent(CounterIntent.Fail("저장할 수 없습니다"))
                yield()
                viewModel.onIntent(CounterIntent.ConsumeError)
                yield()
            }
            withTimeout(1_000) { collector.join() }

            assertEquals(listOf(null, "저장할 수 없습니다", null, "저장할 수 없습니다", null), observed)
            assertNull(viewModel.uiState.value.errorMessage)
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
