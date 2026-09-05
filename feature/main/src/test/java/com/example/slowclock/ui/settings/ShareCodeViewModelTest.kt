package com.example.slowclock.ui.settings

import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
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
class ShareCodeViewModelTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val userRepository = mockk<UserRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepository.getShareCode() } returns "OLD001"
        justRun { settingsRepository.setShareCode(any()) }
        coEvery { userRepository.registerShareCodeWatcher(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `저장된 공유 코드로 입력을 채운다`() =
        runTest {
            assertEquals("OLD001", ShareCodeViewModel(settingsRepository, userRepository).uiState.value.input)
        }

    @Test
    fun `저장하면 설정에 쓰고 감시자를 등록한 뒤 저장 신호를 낸다`() =
        runTest {
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput(" NEW002 "))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 1) { settingsRepository.setShareCode("NEW002") }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("NEW002") }
            assertTrue(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.isSaving)

            viewModel.onIntent(ShareCodeIntent.ConsumeSaved)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `빈 입력은 저장하지 않는다`() =
        runTest {
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("   "))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 0) { settingsRepository.setShareCode(any()) }
            assertFalse(viewModel.uiState.value.isSaved)
        }
}
