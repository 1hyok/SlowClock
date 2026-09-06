package com.example.slowclock.ui.addschedule

import androidx.lifecycle.SavedStateHandle
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddScheduleDraftTest {
    private val repository = mockk<ScheduleRepository>()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `연결 실패와 입력 변경 후에도 같은 제출 ID로 재시도한다`() =
        runTest {
            val requests = mutableListOf<Schedule>()
            coEvery { repository.addSchedule(capture(requests)) } returns ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError)
            val handle = SavedStateHandle()
            val vm = AddScheduleViewModel(repository, alarms, handle)
            val id = requireNotNull(handle.get<String>("new_schedule_id"))
            vm.onIntent(AddScheduleIntent.UpdateTitle("약"))
            vm.onIntent(AddScheduleIntent.Save)
            assertFalse(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.canRetry)
            assertEquals("약", vm.uiState.value.title)
            vm.onIntent(AddScheduleIntent.UpdateDescription("식후"))
            vm.onIntent(AddScheduleIntent.Retry)
            assertEquals(listOf(id, id), requests.map { it.id })
            verify(exactly = 0) { alarms.schedule(any()) }
        }

    @Test fun `시스템 화면 복원에서는 ID와 유효하지 않은 입력까지 복원한다`() =
        runTest {
            val handle = SavedStateHandle()
            val vm = AddScheduleViewModel(repository, alarms, handle)
            vm.onIntent(AddScheduleIntent.UpdateTitle("약"))
            vm.onIntent(AddScheduleIntent.UpdateDescription("설명"))
            vm.onIntent(AddScheduleIntent.UpdateTimeInput(ScheduleTimeInput("2", "")))
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val restored = AddScheduleViewModel(repository, alarms, restoredHandle)
            assertEquals(handle.get<String>("new_schedule_id"), restoredHandle.get<String>("new_schedule_id"))
            assertEquals("약", restored.uiState.value.title)
            assertEquals("설명", restored.uiState.value.description)
            assertEquals(ScheduleTimeInput("2", ""), restored.uiState.value.startTimeInput)
            assertFalse(restored.uiState.value.canSave)
            coVerify(exactly = 0) { repository.addSchedule(any()) }
        }

    @Test fun `복원 후 재시도의 서버 모델로 알람 후처리를 한다`() =
        runTest {
            val handle = SavedStateHandle(mapOf("new_schedule_id" to "stable-id", "draft_title" to "약"))
            val saved = Schedule(id = "stable-id", title = "약", completed = true)
            coEvery { repository.addSchedule(match { it.id == "stable-id" }) } returns ScheduleRepository.ScheduleResult.Success(saved)
            val vm = AddScheduleViewModel(repository, alarms, handle)
            vm.onIntent(AddScheduleIntent.Save)
            verify(exactly = 1) { alarms.schedule(saved) }
            assertTrue(vm.uiState.value.isSaved)
        }

    @Test fun `같은 ID의 다른 내용은 반복 저장 대신 목록 확인을 안내한다`() =
        runTest {
            coEvery { repository.addSchedule(any()) } returns ScheduleRepository.ScheduleResult.Error(AppError.ScheduleConflictError)
            val vm = AddScheduleViewModel(repository, alarms, SavedStateHandle())
            vm.onIntent(AddScheduleIntent.UpdateTitle("수정한 입력"))
            vm.onIntent(AddScheduleIntent.Save)
            assertEquals(AppError.ScheduleConflictError, vm.uiState.value.error)
            assertFalse(vm.uiState.value.canRetry)
            assertEquals("수정한 입력", vm.uiState.value.title)
            verify(exactly = 0) { alarms.schedule(any()) }
        }
}
