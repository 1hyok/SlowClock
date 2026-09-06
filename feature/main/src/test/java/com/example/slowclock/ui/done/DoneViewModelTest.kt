package com.example.slowclock.ui.done

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoneViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val todaySchedules = MutableStateFlow<List<Schedule>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { scheduleRepository.observeSchedulesForDate(any(), any()) } returns todaySchedules
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `오늘 일정을 완료·남은 일정으로 나눈다`() =
        runTest {
            todaySchedules.value =
                listOf(
                    Schedule(id = "a", title = "a", completed = true),
                    Schedule(id = "b", title = "b"),
                )

            val state = DoneViewModel(scheduleRepository).uiState.value

            assertEquals(listOf("a"), state.completed.map { it.id })
            assertEquals(listOf("b"), state.remaining.map { it.id })
        }

    @Test
    fun `토글 실패는 완료 표시를 되돌리고 오류를 둔다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "a"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } returns
                ScheduleRepository.ScheduleResult.Error(AppError.NetworkError)
            val viewModel = DoneViewModel(scheduleRepository)

            viewModel.onIntent(DoneIntent.ToggleComplete("a"))

            assertEquals(
                emptyList<String>(),
                viewModel.uiState.value.completed
                    .map { it.id },
            )
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", true) }

            viewModel.onIntent(DoneIntent.ConsumeError)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `완료 목록의 늦은 실패도 새 서버 내용을 덮지 않는다`() =
        runTest {
            val original = Schedule(id = "a", title = "a")
            todaySchedules.value = listOf(original)
            val pending = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } coAnswers { pending.await() }
            val vm = DoneViewModel(scheduleRepository)
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", true) }
            todaySchedules.value = listOf(original.copy(completed = true, title = "새 서버 내용"))
            pending.complete(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError))
            assertEquals(
                "새 서버 내용",
                vm.uiState.value.completed
                    .single()
                    .title,
            )
        }
}
