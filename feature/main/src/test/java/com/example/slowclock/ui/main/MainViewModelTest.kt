package com.example.slowclock.ui.main

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val userRepository = mockk<UserRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)

    private val todaySchedules = MutableStateFlow<List<Schedule>>(emptyList())
    private val shareCode = MutableStateFlow<String?>(null)

    private val soon = Schedule(id = "s1", title = "약 먹기", startTime = Timestamp(Date(System.currentTimeMillis() + 60_000)))
    private val done =
        Schedule(id = "s2", title = "산책", startTime = Timestamp(Date(System.currentTimeMillis() - 3_600_000)), completed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.currentUid } returns "uid-1"
        every { scheduleRepository.observeSchedulesForDate(any()) } returns todaySchedules
        every { settingsRepository.observeShareCode() } returns shareCode
        every { scheduleRepository.observeSchedulesBySharedCode(any()) } returns flowOf(emptyList())
        coEvery { userRepository.getUserNames(any()) } returns emptyMap()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MainViewModel(scheduleRepository, userRepository, authRepository, settingsRepository, alarmScheduler)

    @Test
    fun `리스너가 낸 오늘 일정으로 상태를 채우고 지금 할 일을 고른다`() =
        runTest {
            todaySchedules.value = listOf(done, soon)

            val state = createViewModel().uiState.value

            assertEquals("uid-1", state.currentUserId)
            assertFalse(state.isLoading)
            assertEquals(2, state.totalCount)
            assertEquals(1, state.completedCount)
            assertEquals("s1", state.currentSchedule?.id)
        }

    @Test
    fun `완료 토글은 먼저 반영하고 저장소를 부른다`() =
        runTest {
            todaySchedules.value = listOf(soon)
            coEvery { scheduleRepository.markScheduleAsCompleted("s1", true) } returns ScheduleRepository.ScheduleResult.Success(Unit)
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.ToggleComplete("s1"))

            assertTrue(
                viewModel.uiState.value.todaySchedules
                    .single()
                    .completed,
            )
            assertEquals(1, viewModel.uiState.value.completedCount)
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("s1", true) }
        }

    @Test
    fun `저장소 실패는 재시도 없는 오류로 남고 ConsumeError 로 지운다`() =
        runTest {
            todaySchedules.value = listOf(soon)
            coEvery { scheduleRepository.markScheduleAsCompleted("s1", true) } returns
                ScheduleRepository.ScheduleResult.Error(AppError.NetworkError)
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.ToggleComplete("s1"))
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.canRetry)

            viewModel.onIntent(MainIntent.ConsumeError)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `삭제는 확인 다이얼로그를 거쳐 목록에서 뺀다`() =
        runTest {
            todaySchedules.value = listOf(soon, done)
            coEvery { scheduleRepository.deleteSchedule("s1") } returns ScheduleRepository.ScheduleResult.Success(Unit)
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.RequestDelete("s1"))
            assertEquals(
                "s1",
                viewModel.uiState.value.scheduleToDelete
                    ?.id,
            )

            viewModel.onIntent(MainIntent.ConfirmDelete)

            val state = viewModel.uiState.value
            assertNull(state.scheduleToDelete)
            assertFalse(state.isLoading)
            assertEquals(listOf("s2"), state.todaySchedules.map { it.id })
            coVerify(exactly = 1) { scheduleRepository.deleteSchedule("s1") }
            verify(exactly = 1) { alarmScheduler.cancel(match { it.id == "s1" }) }
        }

    @Test
    fun `삭제를 취소하면 저장소를 부르지 않는다`() =
        runTest {
            todaySchedules.value = listOf(soon)
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.RequestDelete("s1"))
            viewModel.onIntent(MainIntent.DismissDelete)

            assertNull(viewModel.uiState.value.scheduleToDelete)
            coVerify(exactly = 0) { scheduleRepository.deleteSchedule(any()) }
        }

    @Test
    fun `일정 구독이 실패하면 재시도 가능한 오류를 두고 Retry 가 다시 구독한다`() =
        runTest {
            every { scheduleRepository.observeSchedulesForDate(any()) } returns flow { throw IllegalStateException("network down") }
            val viewModel = createViewModel()
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canRetry)

            every { scheduleRepository.observeSchedulesForDate(any()) } returns flowOf(listOf(soon))
            viewModel.onIntent(MainIntent.Retry)

            assertNull(viewModel.uiState.value.error)
            assertEquals(
                listOf("s1"),
                viewModel.uiState.value.todaySchedules
                    .map { it.id },
            )
        }

    @Test
    fun `공유 코드가 있으면 오늘 공유 일정과 소유자 이름을 채운다`() =
        runTest {
            val shared = soon.copy(id = "shared-1", userId = "owner-1", sharedCode = "ABC123")
            every { scheduleRepository.observeSchedulesBySharedCode("ABC123") } returns flowOf(listOf(shared))
            coEvery { userRepository.getUserNames(listOf("owner-1")) } returns mapOf("owner-1" to "어머니")
            shareCode.value = "ABC123"

            val state = createViewModel().uiState.value

            assertEquals(listOf("shared-1"), state.sharedReminders.map { it.id })
            assertEquals("어머니", state.sharedReminderOwners["owner-1"])
        }

    @Test
    fun `공유 일정 완료 토글은 공유 코드 구성원에게 알린다`() =
        runTest {
            val shared = soon.copy(id = "shared-1", userId = "owner-1", sharedCode = "ABC123")
            every { scheduleRepository.observeSchedulesBySharedCode("ABC123") } returns flowOf(listOf(shared))
            coEvery { scheduleRepository.markScheduleAsCompleted("shared-1", true) } returns ScheduleRepository.ScheduleResult.Success(Unit)
            coEvery { scheduleRepository.sendNotificationToShareCodeMembers(any(), any(), any()) } returns Unit
            shareCode.value = "ABC123"
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.ToggleSharedReminderComplete("shared-1"))

            assertTrue(
                viewModel.uiState.value.sharedReminders
                    .single()
                    .completed,
            )
            coVerify(exactly = 1) { scheduleRepository.sendNotificationToShareCodeMembers("ABC123", "일정이 완료됨", any()) }
        }
}
