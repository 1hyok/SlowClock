package com.example.slowclock.ui.timeline

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val currentUid = MutableStateFlow<String?>("owner")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.currentUid } answers { currentUid.value }
        every { authRepository.observeCurrentUid() } returns currentUid
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

            val state = TimelineViewModel(scheduleRepository, authRepository).uiState.value

            val today = Calendar.getInstance()
            assertEquals(today.get(Calendar.DAY_OF_YEAR), requested.captured.get(Calendar.DAY_OF_YEAR))
            assertEquals(0, requested.captured.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, state.selectedDate.get(Calendar.HOUR_OF_DAY))
        }

    @Test
    fun `날짜를 고르면 그 날짜로 다시 구독한다`() =
        runTest {
            val viewModel = TimelineViewModel(scheduleRepository, authRepository)

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
            val viewModel = TimelineViewModel(scheduleRepository, authRepository)
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

    @Test
    fun `로그아웃은 목록을 비우고 재로그인은 선택 날짜의 구독을 다시 시작한다`() =
        runTest {
            val requested = mutableListOf<Calendar>()
            every { scheduleRepository.observeSchedulesForDate(capture(requested), any()) } answers {
                flowOf(listOf(Schedule(id = currentUid.value.orEmpty(), title = "일정")))
            }
            val vm = TimelineViewModel(scheduleRepository, authRepository)
            vm.onIntent(TimelineIntent.NextDay)
            val selected = vm.uiState.value.selectedDate.timeInMillis
            currentUid.value = null
            assertEquals(emptyList<Schedule>(), vm.uiState.value.schedules)
            assertEquals(AppError.AuthError, vm.uiState.value.error)
            vm.onIntent(TimelineIntent.Retry)
            assertEquals(2, requested.size)
            currentUid.value = "owner"
            assertEquals(3, requested.size)
            assertEquals(selected, requested.last().timeInMillis)
            assertNull(vm.uiState.value.error)
            assertEquals(
                "owner",
                vm.uiState.value.schedules
                    .single()
                    .id,
            )
            currentUid.value = "other"
            assertEquals(4, requested.size)
            assertEquals(
                "other",
                vm.uiState.value.schedules
                    .single()
                    .id,
            )
        }

    @Test
    fun `실패로 끝난 구독도 같은 계정 재로그인 후 다시 시작한다`() =
        runTest {
            var subscriptions = 0
            every { scheduleRepository.observeSchedulesForDate(any(), any()) } answers {
                if (subscriptions++ == 0) {
                    flow { throw IllegalStateException("listener ended") }
                } else {
                    flowOf(listOf(Schedule(id = "new", title = "복구")))
                }
            }
            val vm = TimelineViewModel(scheduleRepository, authRepository)
            currentUid.value = null
            currentUid.value = "owner"
            assertEquals(2, subscriptions)
            assertNull(vm.uiState.value.error)
            assertEquals(
                "new",
                vm.uiState.value.schedules
                    .single()
                    .id,
            )
        }
}
