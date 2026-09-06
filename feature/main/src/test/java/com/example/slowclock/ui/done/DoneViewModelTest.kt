package com.example.slowclock.ui.done

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    fun `토글은 먼저 반영하고 실패하면 오류를 둔다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "a"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } returns
                ScheduleRepository.ScheduleResult.Error(AppError.NetworkError)
            val viewModel = DoneViewModel(scheduleRepository)

            viewModel.onIntent(DoneIntent.ToggleComplete("a"))

            assertEquals(
                listOf("a"),
                viewModel.uiState.value.completed
                    .map { it.id },
            )
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", true) }

            viewModel.onIntent(DoneIntent.ConsumeError)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `반복 일정 완료와 취소는 표시된 발생일을 전달한다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "산책", occurrenceDate = "2026-09-06"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", any(), "2026-09-06") } returns
                ScheduleRepository.ScheduleResult.Success(Unit)
            val viewModel = DoneViewModel(scheduleRepository)
            viewModel.onIntent(DoneIntent.ToggleComplete("a"))
            viewModel.onIntent(DoneIntent.ToggleComplete("a"))
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", true, "2026-09-06") }
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", false, "2026-09-06") }
        }
}
