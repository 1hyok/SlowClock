package com.example.slowclock.ui.settings

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.data.remote.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val alarmScheduler = mockk<AlarmScheduler>()
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepository.observeThemeMode() } returns themeMode
        every { settingsRepository.setThemeMode(any()) } returns Unit
        // 기본값은 전체 화면 알람이 허용된 기기다. 안내 카드를 다루는 테스트만 이 값을 뒤집는다.
        every { alarmScheduler.canUseFullScreenAlarm() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(settingsRepository, alarmScheduler)

    @Test
    fun `저장된 테마를 상태로 낸다`() =
        runTest {
            themeMode.value = ThemeMode.DARK

            assertEquals(ThemeMode.DARK, createViewModel().uiState.value.themeMode)
        }

    @Test
    fun `테마를 고르면 저장소에 남긴다`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.SelectThemeMode(ThemeMode.LIGHT))

            verify { settingsRepository.setThemeMode(ThemeMode.LIGHT) }
        }

    @Test
    fun `저장소가 낸 새 값이 상태에 반영된다`() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

            themeMode.value = ThemeMode.LIGHT

            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        }

    @Test
    fun `전체 화면 알람 권한이 없으면 안내를 보여 준다`() =
        runTest {
            every { alarmScheduler.canUseFullScreenAlarm() } returns false

            assertTrue(createViewModel().uiState.value.showFullScreenAlarmNotice)
        }

    @Test
    fun `이미 허용돼 있으면 안내를 보여 주지 않는다`() =
        runTest {
            assertFalse(createViewModel().uiState.value.showFullScreenAlarmNotice)
        }

    @Test
    fun `설정에서 허용하고 돌아오면 안내가 사라진다`() =
        runTest {
            // 시스템 설정 화면은 결과를 돌려주지 않는다. 화면이 다시 보일 때 이 Intent 를 쏜다(#128).
            every { alarmScheduler.canUseFullScreenAlarm() } returns false
            val viewModel = createViewModel()
            assertTrue(viewModel.uiState.value.showFullScreenAlarmNotice)

            every { alarmScheduler.canUseFullScreenAlarm() } returns true
            viewModel.onIntent(SettingsIntent.RefreshAlarmPermission)

            assertFalse(viewModel.uiState.value.showFullScreenAlarmNotice)
        }

    @Test
    fun `허용했다가 회수하면 안내가 다시 뜬다`() =
        runTest {
            // 「봤음」 표식을 두지 않으므로 권한 상태가 곧 진실이다.
            val viewModel = createViewModel()
            assertFalse(viewModel.uiState.value.showFullScreenAlarmNotice)

            every { alarmScheduler.canUseFullScreenAlarm() } returns false
            viewModel.onIntent(SettingsIntent.RefreshAlarmPermission)

            assertTrue(viewModel.uiState.value.showFullScreenAlarmNotice)
        }

    @Test
    fun `설정 열기를 누르면 화면에 한 번만 신호가 간다`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.OpenFullScreenAlarmSettings)
            assertNotNull(viewModel.uiState.value.openFullScreenAlarmSettings)

            viewModel.onIntent(SettingsIntent.ConsumeFullScreenAlarmSettingsRequest)
            assertNull(viewModel.uiState.value.openFullScreenAlarmSettings)
        }

    @Test
    fun `의료 링크 요청을 소비하고 열기 실패 안내를 닫는다`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.OpenMedicalNews)
            assertNotNull(viewModel.uiState.value.openMedicalNews)
            viewModel.onIntent(SettingsIntent.ConsumeMedicalNewsRequest)
            assertNull(viewModel.uiState.value.openMedicalNews)
            viewModel.onIntent(SettingsIntent.MedicalNewsUnavailable)
            assertTrue(
                viewModel.uiState.value.error
                    ?.message
                    .orEmpty()
                    .contains("브라우저를 열 수 없습니다"),
            )
            viewModel.onIntent(SettingsIntent.ConsumeError)
            assertNull(viewModel.uiState.value.error)
        }
}
