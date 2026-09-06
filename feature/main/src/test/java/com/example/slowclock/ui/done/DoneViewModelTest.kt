package com.example.slowclock.ui.done

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class DoneViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val currentUid = MutableStateFlow<String?>("owner")
    private val todaySchedules = MutableStateFlow<List<Schedule>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.currentUid } answers { currentUid.value }
        every { authRepository.observeCurrentUid() } returns currentUid
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

            val state = DoneViewModel(scheduleRepository, authRepository).uiState.value

            assertEquals(listOf("a"), state.completed.map { it.id })
            assertEquals(listOf("b"), state.remaining.map { it.id })
        }

    @Test
    fun `토글 실패는 완료 표시를 되돌리고 오류를 둔다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "a"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } returns
                ScheduleRepository.ScheduleResult.Error(AppError.NetworkError)
            val viewModel = DoneViewModel(scheduleRepository, authRepository)

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
            val vm = DoneViewModel(scheduleRepository, authRepository)
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

    @Test
    fun `반복 일정 완료와 취소는 표시된 발생일을 전달한다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "산책", occurrenceDate = "2026-09-06"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", any(), "2026-09-06") } returns
                ScheduleRepository.ScheduleResult.Success(Unit)
            val viewModel = DoneViewModel(scheduleRepository, authRepository)
            viewModel.onIntent(DoneIntent.ToggleComplete("a"))
            viewModel.onIntent(DoneIntent.ToggleComplete("a"))
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", true, "2026-09-06") }
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("a", false, "2026-09-06") }
        }

    @Test
    fun `로그아웃과 다른 계정으로 전환한 뒤 이전 완료 실패를 표시하지 않는다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "이전 일정"))
            val pending = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } coAnswers { pending.await() }
            val vm = DoneViewModel(scheduleRepository, authRepository)
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            currentUid.value = null
            assertEquals(emptyList<Schedule>(), vm.uiState.value.schedules)
            currentUid.value = "other"
            todaySchedules.value = listOf(Schedule(id = "b", title = "새 일정"))
            pending.complete(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError))
            assertNull(vm.uiState.value.error)
            assertEquals(
                listOf("b"),
                vm.uiState.value.remaining
                    .map { it.id },
            )
        }

    @Test
    fun `같은 계정 재로그인도 이전 요청을 분리하고 새 완료 입력을 허용한다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "일정"))
            val previous = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            val next = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            var calls = 0
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } coAnswers {
                if (calls++ == 0) previous.await() else next.await()
            }
            val vm = DoneViewModel(scheduleRepository, authRepository)
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            currentUid.value = null
            currentUid.value = "owner"
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            coVerify(exactly = 2) { scheduleRepository.markScheduleAsCompleted("a", true) }
            previous.complete(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError))
            assertNull(vm.uiState.value.error)
            assertEquals(
                listOf("a"),
                vm.uiState.value.completed
                    .map { it.id },
            )
            next.complete(ScheduleRepository.ScheduleResult.Success(Unit))
        }

    @Test
    fun `완료 요청 취소도 현재 표시를 되돌리고 오류를 남기지 않는다`() =
        runTest {
            todaySchedules.value = listOf(Schedule(id = "a", title = "일정"))
            coEvery { scheduleRepository.markScheduleAsCompleted("a", true) } throws CancellationException("cancelled")
            val vm = DoneViewModel(scheduleRepository, authRepository)
            vm.onIntent(DoneIntent.ToggleComplete("a"))
            assertEquals(
                listOf("a"),
                vm.uiState.value.remaining
                    .map { it.id },
            )
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `완료 구독 실패 후 재로그인하면 구독을 복구하고 로그아웃 재시도는 기다린다`() =
        runTest {
            var subscriptions = 0
            every { scheduleRepository.observeSchedulesForDate(any(), any()) } answers {
                if (subscriptions++ == 0) {
                    flow { throw IllegalStateException("listener ended") }
                } else {
                    flowOf(listOf(Schedule(id = "a", title = "복구")))
                }
            }
            val vm = DoneViewModel(scheduleRepository, authRepository)
            currentUid.value = null
            assertEquals(AppError.AuthError, vm.uiState.value.error)
            vm.onIntent(DoneIntent.Retry)
            verify(exactly = 1) { scheduleRepository.observeSchedulesForDate(any(), any()) }
            currentUid.value = "owner"
            assertEquals(2, subscriptions)
            assertNull(vm.uiState.value.error)
            assertEquals(
                "a",
                vm.uiState.value.remaining
                    .single()
                    .id,
            )
        }
}
