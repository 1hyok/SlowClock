package com.example.slowclock.ui.addschedule

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository.ScheduleResult
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AddScheduleEditTest {
    private val repository = mockk<ScheduleRepository>()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val start = date(2032, Calendar.JANUARY, 5, 22, 10)
    private val end = date(2032, Calendar.JANUARY, 6, 1, 20)
    private val existing = Schedule("s1", title = "기존 일정", startTime = Timestamp(start.time), endTime = Timestamp(end.time))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loadedViewModel(): AddScheduleViewModel {
        coEvery { repository.getScheduleById("s1") } returns ScheduleResult.Success(existing)
        return AddScheduleViewModel(repository, alarms).also { it.onIntent(AddScheduleIntent.LoadForEdit("s1")) }
    }

    @Test
    fun `늦게 불러온 편집 시각이 입력칸 상태에도 반영된다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Schedule>>()
            coEvery { repository.getScheduleById("s1") } coAnswers { pending.await() }
            val viewModel = AddScheduleViewModel(repository, alarms)

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            assertFalse(viewModel.uiState.value.canSave)
            pending.complete(ScheduleResult.Success(existing))

            val state = viewModel.uiState.value
            assertEquals(ScheduleTimeInput("22", "10"), state.startTimeInput)
            assertEquals(ScheduleTimeInput("1", "20"), state.endTimeInput)
            assertEquals(start.timeInMillis, state.selectedTime.timeInMillis)
            assertEquals(end.timeInMillis, state.endTime?.timeInMillis)
            assertTrue(state.canSave)
        }

    @Test
    fun `조회 중 같은 ID 재진입은 중복 요청을 보내지 않는다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Schedule>>()
            coEvery { repository.getScheduleById("s1") } coAnswers { pending.await() }
            val viewModel = AddScheduleViewModel(repository, alarms)

            repeat(2) { viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1")) }
            pending.complete(ScheduleResult.Success(existing))

            coVerify(exactly = 1) { repository.getScheduleById("s1") }
        }

    @Test
    fun `조회가 끝난 같은 ID 재진입은 입력 중인 제목과 시간을 덮지 않는다`() =
        runTest {
            val viewModel = loadedViewModel()
            val input = ScheduleTimeInput("23", "")
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("작성 중인 제목"))
            viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(input))

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))

            assertEquals("작성 중인 제목", viewModel.uiState.value.title)
            assertEquals(input, viewModel.uiState.value.startTimeInput)
            coVerify(exactly = 1) { repository.getScheduleById("s1") }
        }

    @Test
    fun `다른 ID를 불러온 뒤 먼저 요청한 일정의 늦은 성공은 무시한다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Schedule>>()
            coEvery { repository.getScheduleById("s1") } coAnswers { withContext(NonCancellable) { pending.await() } }
            val other = existing.copy(id = "s2", title = "다른 일정")
            coEvery { repository.getScheduleById("s2") } returns ScheduleResult.Success(other)
            val viewModel = AddScheduleViewModel(repository, alarms)

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s2"))
            pending.complete(ScheduleResult.Success(existing))

            assertEquals(
                "s2",
                viewModel.uiState.value.editingSchedule
                    ?.id,
            )
            assertEquals("다른 일정", viewModel.uiState.value.title)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `다른 ID를 불러온 뒤 먼저 요청한 일정의 늦은 실패는 무시한다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Schedule>>()
            coEvery { repository.getScheduleById("s1") } coAnswers { withContext(NonCancellable) { pending.await() } }
            coEvery { repository.getScheduleById("s2") } returns ScheduleResult.Success(existing.copy(id = "s2"))
            val viewModel = AddScheduleViewModel(repository, alarms)

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s2"))
            pending.complete(ScheduleResult.Error(AppError.NetworkError))

            assertEquals(
                "s2",
                viewModel.uiState.value.editingSchedule
                    ?.id,
            )
            assertNull(viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `편집 조회 실패의 재시도는 새 일정을 저장하지 않고 다시 읽는다`() =
        runTest {
            coEvery { repository.getScheduleById("s1") } returns ScheduleResult.Error(AppError.NetworkError) andThen
                ScheduleResult.Success(existing)
            val viewModel = AddScheduleViewModel(repository, alarms)
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            assertTrue(viewModel.uiState.value.canRetry)
            assertFalse(viewModel.uiState.value.canSave)

            viewModel.onIntent(AddScheduleIntent.Retry)

            assertEquals(existing, viewModel.uiState.value.editingSchedule)
            assertNull(viewModel.uiState.value.error)
            coVerify(exactly = 2) { repository.getScheduleById("s1") }
            coVerify(exactly = 0) { repository.addSchedule(any()) }
            coVerify(exactly = 0) { repository.updateSchedule(any()) }
        }

    @Test
    fun `편집 추천 제목은 초기 조회가 성공한 뒤에만 받을 수 있다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Schedule>>()
            coEvery { repository.getScheduleById("s1") } coAnswers { pending.await() }
            val viewModel = AddScheduleViewModel(repository, alarms)
            assertFalse(viewModel.uiState.value.canApplyRecommendedTitle("s1"))
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            assertFalse(viewModel.uiState.value.canApplyRecommendedTitle("s1"))

            pending.complete(ScheduleResult.Success(existing))

            assertTrue(viewModel.uiState.value.canApplyRecommendedTitle("s1"))
            assertFalse(viewModel.uiState.value.canApplyRecommendedTitle("s2"))
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("추천 제목"))
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("직접 고친 제목"))
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            assertEquals("직접 고친 제목", viewModel.uiState.value.title)
        }

    @Test
    fun `편집 조회가 실패하면 추천 결과는 재시도가 성공할 때까지 기다린다`() =
        runTest {
            coEvery { repository.getScheduleById("s1") } returns ScheduleResult.Error(AppError.NetworkError) andThen
                ScheduleResult.Success(existing)
            val viewModel = AddScheduleViewModel(repository, alarms)
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            assertFalse(viewModel.uiState.value.canApplyRecommendedTitle("s1"))

            viewModel.onIntent(AddScheduleIntent.Retry)

            assertTrue(viewModel.uiState.value.canApplyRecommendedTitle("s1"))
        }

    @Test
    fun `편집 조회가 실패한 상태에서 저장을 눌러도 빈 일정이 추가되지 않는다`() =
        runTest {
            coEvery { repository.getScheduleById("s1") } returns ScheduleResult.Error(AppError.NetworkError)
            val viewModel = AddScheduleViewModel(repository, alarms)
            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("제목"))

            viewModel.onIntent(AddScheduleIntent.Save)

            coVerify(exactly = 0) { repository.addSchedule(any()) }
            coVerify(exactly = 0) { repository.updateSchedule(any()) }
        }

    @Test
    fun `시작 시간의 잘못된 입력은 화면과 저장 동작에서 함께 막는다`() =
        runTest {
            val viewModel = loadedViewModel()
            listOf(ScheduleTimeInput("", "10"), ScheduleTimeInput("24", "10"), ScheduleTimeInput("22", "60")).forEach { input ->
                viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(input))
                assertEquals(input, viewModel.uiState.value.startTimeInput)
                assertFalse(viewModel.uiState.value.canSave)
                viewModel.onIntent(AddScheduleIntent.Save)
            }

            coVerify(exactly = 0) { repository.updateSchedule(any()) }
        }

    @Test
    fun `시간을 편집해도 시작 날짜와 자정을 넘은 종료 날짜는 보존한다`() =
        runTest {
            val updated = slot<Schedule>()
            coEvery { repository.updateSchedule(capture(updated)) } returns ScheduleResult.Success(Unit)
            val viewModel = loadedViewModel()

            viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(ScheduleTimeInput("23", "30")))
            viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(ScheduleTimeInput("2", "40"), isEnd = true))
            viewModel.onIntent(AddScheduleIntent.Save)

            assertEquals(
                date(2032, Calendar.JANUARY, 5, 23, 30).timeInMillis,
                updated.captured.startTime
                    .toDate()
                    .time,
            )
            assertEquals(
                date(2032, Calendar.JANUARY, 6, 2, 40).timeInMillis,
                updated.captured.endTime
                    ?.toDate()
                    ?.time,
            )
            assertEquals(start.timeInMillis, existing.startTime.toDate().time)
        }

    @Test
    fun `종료 시간의 일부 빈칸은 저장을 막고 모두 비우면 종료를 제거한다`() =
        runTest {
            val updated = slot<Schedule>()
            coEvery { repository.updateSchedule(capture(updated)) } returns ScheduleResult.Success(Unit)
            val viewModel = loadedViewModel()
            viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(ScheduleTimeInput("", "20"), isEnd = true))
            assertFalse(viewModel.uiState.value.canSave)
            viewModel.onIntent(AddScheduleIntent.Save)
            coVerify(exactly = 0) { repository.updateSchedule(any()) }

            viewModel.onIntent(AddScheduleIntent.UpdateTimeInput(ScheduleTimeInput(), isEnd = true))
            assertTrue(viewModel.uiState.value.canSave)
            assertNull(viewModel.uiState.value.endTime)
            viewModel.onIntent(AddScheduleIntent.Save)

            assertNull(updated.captured.endTime)
        }

    @Test
    fun `저장 중과 완료 신호가 남은 동안 다시 눌러도 한 번만 저장한다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Unit>>()
            coEvery { repository.updateSchedule(any()) } coAnswers { pending.await() }
            val viewModel = loadedViewModel()

            repeat(2) { viewModel.onIntent(AddScheduleIntent.Save) }
            viewModel.onIntent(AddScheduleIntent.Retry)
            pending.complete(ScheduleResult.Success(Unit))
            viewModel.onIntent(AddScheduleIntent.Save)

            coVerify(exactly = 1) { repository.updateSchedule(any()) }
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `저장 중 다른 일정 조회가 현재 저장 결과의 대상을 바꾸지 않는다`() =
        runTest {
            val pending = CompletableDeferred<ScheduleResult<Unit>>()
            coEvery { repository.updateSchedule(any()) } coAnswers { pending.await() }
            coEvery { repository.getScheduleById("s2") } returns ScheduleResult.Success(existing.copy(id = "s2"))
            val viewModel = loadedViewModel()
            viewModel.onIntent(AddScheduleIntent.Save)

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s2"))
            pending.complete(ScheduleResult.Success(Unit))

            coVerify(exactly = 0) { repository.getScheduleById("s2") }
            assertEquals(
                "s1",
                viewModel.uiState.value.editingSchedule
                    ?.id,
            )
            assertTrue(viewModel.uiState.value.isSaved)
        }

    private fun date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }
}
