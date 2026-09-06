package com.example.slowclock.ui.main

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        every { authRepository.observeCurrentUid() } returns flowOf("uid-1")
        every { scheduleRepository.observeSchedulesForDate(any(), any()) } returns todaySchedules
        every { settingsRepository.observeShareCode() } returns shareCode
        every { scheduleRepository.observeSchedulesBySharedCode(any()) } returns flowOf(emptyList())
        coEvery { userRepository.getUserNames(any()) } returns emptyMap()
        coEvery { scheduleRepository.getSchedulesOf(any()) } returns emptyList()
        coEvery { userRepository.registerShareCodeWatcher(any()) } returns true
        // 기본값은 정시 알람이 허용된 기기다. 안내 다이얼로그를 다루는 테스트만 이 값을 뒤집는다.
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        every { settingsRepository.hasSeenExactAlarmNotice() } returns false
        every { settingsRepository.markExactAlarmNoticeSeen() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MainViewModel(scheduleRepository, userRepository, authRepository, settingsRepository, alarmScheduler)

    @Test
    fun `로그인하면 서버 일정으로 이 기기의 알람을 맞춘다`() =
        runTest {
            // 알람 장부는 기기 안에만 있어 새 기기·재설치에서는 비어 있다. 맞추지 않으면 화면에
            // 일정은 다 보이는데 알람은 하나도 걸리지 않는다(#176).
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } returns listOf(soon, done)

            createViewModel()

            verify(exactly = 1) { alarmScheduler.syncWith(listOf(soon, done)) }
        }

    @Test
    fun `일정 목록을 못 읽으면 알람을 건드리지 않는다`() =
        runTest {
            // 빈 목록으로 맞추면 걸려 있던 알람을 전부 지운다. 신호가 약한 것과 일정이 없는 것은
            // 다른 사실이다(#176).
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } returns null

            createViewModel()

            verify(exactly = 0) { alarmScheduler.syncWith(any()) }
        }

    @Test
    fun `로그아웃 뒤 돌아온 일정 목록은 알람을 되살리지 않는다`() =
        runTest {
            val uid = MutableStateFlow<String?>("uid-1")
            every { authRepository.observeCurrentUid() } returns uid
            every { authRepository.currentUid } answers { uid.value }
            val pending = CompletableDeferred<List<Schedule>?>()
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } coAnswers {
                withContext(NonCancellable) { pending.await() }
            }
            createViewModel()

            uid.value = null
            pending.complete(listOf(soon))

            verify(exactly = 0) { alarmScheduler.syncWith(any()) }
        }

    @Test
    fun `계정을 바꾼 뒤 앞 계정의 응답이 새 계정 알람을 덮지 않는다`() =
        runTest {
            val uid = MutableStateFlow<String?>("uid-1")
            every { authRepository.observeCurrentUid() } returns uid
            every { authRepository.currentUid } answers { uid.value }
            val pending = CompletableDeferred<List<Schedule>?>()
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } coAnswers {
                withContext(NonCancellable) { pending.await() }
            }
            val other = soon.copy(id = "other-schedule", userId = "uid-2")
            coEvery { scheduleRepository.getSchedulesOf("uid-2") } returns listOf(other)
            createViewModel()

            uid.value = "uid-2"
            pending.complete(listOf(soon))

            verify(exactly = 1) { alarmScheduler.syncWith(listOf(other)) }
            verify(exactly = 0) { alarmScheduler.syncWith(listOf(soon)) }
        }

    @Test
    fun `같은 계정으로 다시 로그인해도 비운 알람을 다시 맞춘다`() =
        runTest {
            val uid = MutableStateFlow<String?>("uid-1")
            every { authRepository.observeCurrentUid() } returns uid
            every { authRepository.currentUid } answers { uid.value }
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } returns listOf(soon)
            createViewModel()

            uid.value = null
            uid.value = "uid-1"

            verify(exactly = 2) { alarmScheduler.syncWith(listOf(soon)) }
        }

    @Test
    fun `서버를 못 읽었다가 화면으로 돌아오면 알람 맞추기를 다시 시도한다`() =
        runTest {
            coEvery { scheduleRepository.getSchedulesOf("uid-1") } returnsMany listOf(null, listOf(soon))
            val viewModel = createViewModel()
            verify(exactly = 0) { alarmScheduler.syncWith(any()) }

            viewModel.onIntent(MainIntent.ScreenResumed)
            viewModel.onIntent(MainIntent.ScreenResumed)

            verify(exactly = 1) { alarmScheduler.syncWith(listOf(soon)) }
            coVerify(exactly = 2) { scheduleRepository.getSchedulesOf("uid-1") }
        }

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
            assertFalse(
                viewModel.uiState.value.todaySchedules
                    .single()
                    .completed,
            )

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
            every { scheduleRepository.observeSchedulesForDate(any(), any()) } returns flow { throw IllegalStateException("network down") }
            val viewModel = createViewModel()
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canRetry)

            every { scheduleRepository.observeSchedulesForDate(any(), any()) } returns flowOf(listOf(soon))
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
    fun `공유 일정 완료 토글은 저장소에 반영하고 화면 상태를 바꾼다`() =
        runTest {
            // 감시자 알림은 Firestore 트리거가 보낸다. ViewModel 은 상태만 바꾼다(#93).
            val shared = soon.copy(id = "shared-1", userId = "owner-1", sharedCode = "ABC123")
            every { scheduleRepository.observeSchedulesBySharedCode("ABC123") } returns flowOf(listOf(shared))
            coEvery { scheduleRepository.markScheduleAsCompleted("shared-1", true) } returns ScheduleRepository.ScheduleResult.Success(Unit)
            shareCode.value = "ABC123"
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.ToggleSharedReminderComplete("shared-1"))

            assertTrue(
                viewModel.uiState.value.sharedReminders
                    .single()
                    .completed,
            )
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("shared-1", true) }
        }

    @Test
    fun `정시 알람을 못 걸고 안내를 본 적 없으면 안내 다이얼로그를 띄운다`() =
        runTest {
            every { alarmScheduler.canScheduleExactAlarms() } returns false

            val state = createViewModel().uiState.value

            assertTrue(state.showExactAlarmNotice)
            assertNull(state.openExactAlarmSettings)
        }

    @Test
    fun `안내를 이미 봤으면 다시 띄우지 않는다`() =
        runTest {
            every { alarmScheduler.canScheduleExactAlarms() } returns false
            every { settingsRepository.hasSeenExactAlarmNotice() } returns true

            assertFalse(createViewModel().uiState.value.showExactAlarmNotice)
        }

    @Test
    fun `설정 열기는 본 것으로 표시하고 설정 열기 신호를 냈다가 소비한다`() =
        runTest {
            every { alarmScheduler.canScheduleExactAlarms() } returns false
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.OpenExactAlarmSettings)

            val requested = viewModel.uiState.value
            assertFalse(requested.showExactAlarmNotice)
            assertNotNull(requested.openExactAlarmSettings)
            verify { settingsRepository.markExactAlarmNoticeSeen() }

            viewModel.onIntent(MainIntent.ConsumeExactAlarmSettingsRequest)

            assertNull(viewModel.uiState.value.openExactAlarmSettings)
        }

    @Test
    fun `나중에를 누르면 안내를 닫고 본 것으로 표시한다`() =
        runTest {
            every { alarmScheduler.canScheduleExactAlarms() } returns false
            val viewModel = createViewModel()

            viewModel.onIntent(MainIntent.DismissExactAlarmNotice)

            assertFalse(viewModel.uiState.value.showExactAlarmNotice)
            verify { settingsRepository.markExactAlarmNoticeSeen() }
        }

    @Test
    fun `로그아웃하면 앞 사용자의 일정과 집계를 비운다`() =
        runTest {
            // 비우지 않으면 다른 계정으로 로그인한 직후, Firestore 첫 응답이 오기 전까지
            // 앞 사람 일정이 그대로 보인다(#137).
            val uid = MutableStateFlow<String?>("uid-1")
            every { authRepository.observeCurrentUid() } returns uid
            todaySchedules.value = listOf(done, soon)
            val viewModel = createViewModel()
            assertEquals(2, viewModel.uiState.value.todaySchedules.size)

            uid.value = null

            val state = viewModel.uiState.value
            assertTrue(state.todaySchedules.isEmpty())
            assertTrue(state.sharedReminders.isEmpty())
            assertNull(state.currentSchedule)
            assertEquals(0, state.completedCount)
            assertEquals(0, state.totalCount)
            assertEquals("", state.currentUserId)
        }

    @Test
    fun `로그아웃했다 다시 로그인하면 공유 일정 구독이 다시 붙는다`() =
        runTest {
            // 공유 코드만 키로 삼으면, 안쪽 흐름이 한 번 끝난 뒤 코드 값이 실제로 바뀌기
            // 전까지 새 구독을 걸지 않는다(#134).
            val uid = MutableStateFlow<String?>("uid-1")
            every { authRepository.observeCurrentUid() } returns uid
            shareCode.value = "ABC123"
            val shared = Schedule(id = "shared-1", title = "어머니 약", startTime = Timestamp(Date()), userId = "uid-2")
            every { scheduleRepository.observeSchedulesBySharedCode("ABC123") } returns flowOf(listOf(shared))
            val viewModel = createViewModel()

            uid.value = null
            assertTrue(
                viewModel.uiState.value.sharedReminders
                    .isEmpty(),
            )
            uid.value = "uid-1"

            assertEquals(listOf(shared), viewModel.uiState.value.sharedReminders)
        }

    @Test
    fun `로그인하지 않았으면 공유 코드가 있어도 구독하지 않는다`() =
        runTest {
            // 보안 규칙이 로그인 전 읽기를 막으므로, 걸어 봐야 리스너가 그 자리에서 닫힌다(#134).
            val uid = MutableStateFlow<String?>(null)
            every { authRepository.observeCurrentUid() } returns uid
            shareCode.value = "ABC123"

            createViewModel()

            verify(exactly = 0) { scheduleRepository.observeSchedulesBySharedCode(any()) }
        }

    @Test
    fun `오늘 일정은 날짜를 다시 읽는 구독으로 건다`() =
        runTest {
            // 붙잡아 두면 자정을 넘긴 뒤에도 어제 회차로 펼쳐지고, 그 회차 식별자가 완료 기록의
            // 열쇠라 어제 날짜가 서버에 남는다(#171).
            createViewModel()

            verify { scheduleRepository.observeSchedulesForDate(any(), today = true) }
        }

    @Test
    fun `같은 날 화면이 다시 보이면 구독을 다시 걸지 않는다`() =
        runTest {
            val viewModel = createViewModel()
            clearMocks(scheduleRepository, answers = false, recordedCalls = true)

            viewModel.onIntent(MainIntent.ScreenResumed)

            verify(exactly = 0) { scheduleRepository.observeSchedulesForDate(any(), any()) }
        }

    @Test
    fun `완료 확인 중 중복 토글을 막고 실패하면 원래 상태로 돌아간다`() =
        runTest {
            todaySchedules.value = listOf(soon)
            val pending = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            coEvery { scheduleRepository.markScheduleAsCompleted("s1", true) } coAnswers { pending.await() }
            val vm = createViewModel()
            vm.onIntent(MainIntent.ToggleComplete("s1"))
            vm.onIntent(MainIntent.ToggleComplete("s1"))
            assertTrue(
                vm.uiState.value.todaySchedules
                    .single()
                    .completed,
            )
            coVerify(exactly = 1) { scheduleRepository.markScheduleAsCompleted("s1", true) }
            pending.complete(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError))
            assertFalse(
                vm.uiState.value.todaySchedules
                    .single()
                    .completed,
            )
        }

    @Test
    fun `완료 실패의 늦은 복구는 새 서버 스냅샷을 되돌리지 않는다`() =
        runTest {
            todaySchedules.value = listOf(soon)
            val pending = CompletableDeferred<ScheduleRepository.ScheduleResult<Unit>>()
            coEvery { scheduleRepository.markScheduleAsCompleted("s1", true) } coAnswers { pending.await() }
            val vm = createViewModel()
            vm.onIntent(MainIntent.ToggleComplete("s1"))
            todaySchedules.value = listOf(soon.copy(title = "다른 기기 수정", completed = true))
            pending.complete(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError))
            assertTrue(
                vm.uiState.value.todaySchedules
                    .single()
                    .completed,
            )
            assertEquals(
                "다른 기기 수정",
                vm.uiState.value.todaySchedules
                    .single()
                    .title,
            )
        }
}
