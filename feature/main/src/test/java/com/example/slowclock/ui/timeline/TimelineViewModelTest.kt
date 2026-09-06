package com.example.slowclock.ui.timeline

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { scheduleRepository.observeSchedulesForDate(any(), any()) } returns flowOf(listOf(Schedule(id = "a", title = "a")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `처음에는 오늘 자정을 기준으로 구독한다`() =
        runTest {
            val requested = slot<Calendar>()
            every { scheduleRepository.observeSchedulesForDate(capture(requested), any()) } returns flowOf(emptyList())

            val state = TimelineViewModel(scheduleRepository).uiState.value

            val today = Calendar.getInstance()
            assertEquals(today.get(Calendar.DAY_OF_YEAR), requested.captured.get(Calendar.DAY_OF_YEAR))
            assertEquals(0, requested.captured.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, state.selectedDate.get(Calendar.HOUR_OF_DAY))
        }

    @Test
    fun `날짜를 고르면 그 날짜로 다시 구독한다`() =
        runTest {
            val viewModel = TimelineViewModel(scheduleRepository)

            val requested = mutableListOf<Calendar>()
            every { scheduleRepository.observeSchedulesForDate(capture(requested), any()) } returns
                flowOf(listOf(Schedule(id = "a", title = "a")))
            viewModel.onIntent(TimelineIntent.SelectDate(2026, Calendar.SEPTEMBER, 20))

            val selected = viewModel.uiState.value.selectedDate
            assertEquals(2026, selected.get(Calendar.YEAR))
            assertEquals(Calendar.SEPTEMBER, selected.get(Calendar.MONTH))
            assertEquals(20, selected.get(Calendar.DAY_OF_MONTH))
            assertEquals(2026, requested.single().get(Calendar.YEAR))
            assertEquals(Calendar.SEPTEMBER, requested.single().get(Calendar.MONTH))
            assertEquals(20, requested.single().get(Calendar.DAY_OF_MONTH))
            assertEquals(0, requested.single().get(Calendar.HOUR_OF_DAY))
            assertEquals(0, requested.single().get(Calendar.MINUTE))
            assertEquals(0, requested.single().get(Calendar.SECOND))
            assertEquals(0, requested.single().get(Calendar.MILLISECOND))
            assertEquals(
                listOf("a"),
                viewModel.uiState.value.schedules
                    .map { it.id },
            )
            verify(exactly = 2) { scheduleRepository.observeSchedulesForDate(any(), any()) }
        }

    @Test
    fun `다음 날과 이전 날은 하루씩 옮긴다`() =
        runTest {
            val viewModel = TimelineViewModel(scheduleRepository)
            val start = viewModel.uiState.value.selectedDate
            val tomorrow = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val requested = mutableListOf<Calendar>()
            every { scheduleRepository.observeSchedulesForDate(capture(requested), any()) } returns flowOf(emptyList())

            viewModel.onIntent(TimelineIntent.NextDay)
            assertEquals(tomorrow.timeInMillis, viewModel.uiState.value.selectedDate.timeInMillis)
            assertEquals(tomorrow.timeInMillis, requested.last().timeInMillis)

            viewModel.onIntent(TimelineIntent.PreviousDay)
            assertEquals(start.timeInMillis, viewModel.uiState.value.selectedDate.timeInMillis)
            assertEquals(start.timeInMillis, requested.last().timeInMillis)
        }
}
