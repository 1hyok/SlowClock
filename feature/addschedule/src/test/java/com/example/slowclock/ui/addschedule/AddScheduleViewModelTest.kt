package com.example.slowclock.ui.addschedule

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AddScheduleViewModelTest {
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val alarmScheduler = mockk<AlarmScheduler>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        justRun { alarmScheduler.schedule(any()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `서버 저장 후 알람 실패 재시도는 같은 ID를 예약하고 서버 저장을 반복하지 않는다`() =
        runTest {
            coEvery { scheduleRepository.addSchedule(any()) } returns ScheduleRepository.ScheduleResult.Success("saved-id")
            every { alarmScheduler.schedule(any()) } throws IllegalStateException("quota")
            val viewModel = createViewModel()
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("약"))
            viewModel.onIntent(AddScheduleIntent.Save)
            assertEquals(
                "saved-id",
                viewModel.uiState.value.pendingAlarmSchedule
                    ?.id,
            )
            assertFalse(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.canSave)
            assertTrue(
                viewModel.uiState.value.error!!
                    .message
                    .contains("일정은 저장됐지만"),
            )

            viewModel.onIntent(AddScheduleIntent.Save)
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("변경되지 않아야 함"))
            justRun { alarmScheduler.schedule(any()) }
            viewModel.onIntent(AddScheduleIntent.Retry)

            assertTrue(viewModel.uiState.value.isSaved)
            assertNull(viewModel.uiState.value.pendingAlarmSchedule)
            coVerify(exactly = 1) { scheduleRepository.addSchedule(any()) }
            verify(exactly = 2) { alarmScheduler.schedule(match { it.id == "saved-id" && it.title == "약" }) }
        }

    @Test
    fun `알람 실패 안내를 닫으면 저장된 일정으로 돌아가고 재저장하지 않는다`() =
        runTest {
            coEvery { scheduleRepository.addSchedule(any()) } returns ScheduleRepository.ScheduleResult.Success("saved-id")
            every { alarmScheduler.schedule(any()) } throws IllegalStateException("quota")
            val viewModel = createViewModel()
            viewModel.onIntent(AddScheduleIntent.UpdateTitle("약"))
            viewModel.onIntent(AddScheduleIntent.Save)
            viewModel.onIntent(AddScheduleIntent.ConsumeError)
            assertTrue(viewModel.uiState.value.isSaved)
            coVerify(exactly = 1) { scheduleRepository.addSchedule(any()) }
        }

    private fun createViewModel() = AddScheduleViewModel(scheduleRepository, alarmScheduler)

    @Test
    fun `제목이 비어 있으면 저장하지 않고 안내한다`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(AddScheduleIntent.Save)

            assertEquals(
                "할 일을 입력해주세요",
                viewModel.uiState.value.error
                    ?.message,
            )
            assertFalse(viewModel.uiState.value.canRetry)
            coVerify(exactly = 0) { scheduleRepository.addSchedule(any()) }
        }

    @Test
    fun `종료 시각이 시작보다 빠르면 저장하지 않는다`() =
        runTest {
            val viewModel = createViewModel()
            val start = Calendar.getInstance()
            val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -1) }

            viewModel.onIntent(AddScheduleIntent.UpdateTitle("약 먹기"))
            viewModel.onIntent(AddScheduleIntent.UpdateTime(start))
            viewModel.onIntent(AddScheduleIntent.UpdateEndTime(end))
            viewModel.onIntent(AddScheduleIntent.Save)

            assertEquals(
                "종료 시간은 시작 시간보다 늦어야 합니다",
                viewModel.uiState.value.error
                    ?.message,
            )
            coVerify(exactly = 0) { scheduleRepository.addSchedule(any()) }
        }

    @Test
    fun `새 일정을 저장하면 Firestore 가 준 ID 로 알람을 걸고 저장 신호를 낸다`() =
        runTest {
            coEvery { scheduleRepository.addSchedule(any()) } returns ScheduleRepository.ScheduleResult.Success("new-id")
            val scheduled = slot<Schedule>()
            justRun { alarmScheduler.schedule(capture(scheduled)) }
            val viewModel = createViewModel()

            viewModel.onIntent(AddScheduleIntent.UpdateTitle("  약 먹기 "))
            viewModel.onIntent(AddScheduleIntent.UpdateDescription("식후 30분"))
            viewModel.onIntent(AddScheduleIntent.Save)

            val state = viewModel.uiState.value
            assertTrue(state.isSaved)
            assertFalse(state.isLoading)
            assertEquals("new-id", scheduled.captured.id)
            assertEquals("약 먹기", scheduled.captured.title)
            assertEquals("식후 30분", scheduled.captured.description)

            viewModel.onIntent(AddScheduleIntent.ConsumeSaved)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `수정 모드는 기존 일정을 불러오고 같은 ID 로 갱신한다`() =
        runTest {
            val existing =
                Schedule(
                    id = "s1",
                    title = "산책",
                    description = "공원",
                    startTime = Timestamp(Date(1_800_000_000_000L)),
                    recurring = true,
                    recurringType = "weekly",
                )
            coEvery { scheduleRepository.getScheduleById("s1") } returns ScheduleRepository.ScheduleResult.Success(existing)
            val updated = slot<Schedule>()
            coEvery { scheduleRepository.updateSchedule(capture(updated)) } returns ScheduleRepository.ScheduleResult.Success(Unit)
            val viewModel = createViewModel()

            viewModel.onIntent(AddScheduleIntent.LoadForEdit("s1"))
            val loaded = viewModel.uiState.value
            assertTrue(loaded.isEditMode)
            assertEquals("산책", loaded.title)
            assertEquals("weekly", loaded.recurringType)
            assertEquals(1_800_000_000_000L, loaded.selectedTime.timeInMillis)

            viewModel.onIntent(AddScheduleIntent.UpdateTitle("긴 산책"))
            viewModel.onIntent(AddScheduleIntent.Save)

            assertEquals("s1", updated.captured.id)
            assertEquals("긴 산책", updated.captured.title)
            assertTrue(viewModel.uiState.value.isSaved)
            verify(exactly = 1) { alarmScheduler.schedule(match { it.id == "s1" }) }
        }

    @Test
    fun `저장이 실패하면 재시도 가능한 오류를 두고 Retry 가 다시 저장한다`() =
        runTest {
            coEvery { scheduleRepository.addSchedule(any()) } returns ScheduleRepository.ScheduleResult.Error(AppError.NetworkError) andThen
                ScheduleRepository.ScheduleResult.Success("new-id")
            val viewModel = createViewModel()

            viewModel.onIntent(AddScheduleIntent.UpdateTitle("약 먹기"))
            viewModel.onIntent(AddScheduleIntent.Save)
            assertEquals(AppError.NetworkError, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canRetry)

            viewModel.onIntent(AddScheduleIntent.Retry)

            assertNull(viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.isSaved)
            coVerify(exactly = 2) { scheduleRepository.addSchedule(any()) }
        }
}
