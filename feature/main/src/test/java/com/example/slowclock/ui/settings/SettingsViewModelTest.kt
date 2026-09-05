package com.example.slowclock.ui.settings

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepository.observeThemeMode() } returns themeMode
        every { settingsRepository.setThemeMode(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(settingsRepository)

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
}
