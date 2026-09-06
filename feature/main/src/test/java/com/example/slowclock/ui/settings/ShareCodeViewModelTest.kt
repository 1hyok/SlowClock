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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        coEvery { userRepository.unregisterShareCodeWatcher(any()) } returns true
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
            // 이전 코드의 등록을 지우지 않으면 그 사람에게 내 토큰이 계속 남는다(#124).
            coVerify(exactly = 1) { userRepository.unregisterShareCodeWatcher("OLD001") }
            assertTrue(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.isSaving)

            viewModel.onIntent(ShareCodeIntent.ConsumeSaved)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `감시자 등록에 실패하면 코드를 저장하지 않고 앞 코드도 그대로 둔다`() =
        runTest {
            // 등록이 곧 공유 일정을 읽을 권한이다. 등록 못 한 코드를 저장하면 가족 일정이 빈 채로
            // 남고, 앞 코드까지 지우면 되돌아갈 자리도 없다(#174).
            coEvery { userRepository.registerShareCodeWatcher("NEW002") } returns false
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 0) { settingsRepository.setShareCode(any()) }
            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any()) }
            assertFalse(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.isSaving)
            assertNotNull(viewModel.uiState.value.saveError)
        }

    @Test
    fun `입력을 고치면 실패 안내가 사라진다`() =
        runTest {
            coEvery { userRepository.registerShareCodeWatcher(any()) } returns false
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)
            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW003"))

            assertNull(viewModel.uiState.value.saveError)
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

    @Test
    fun `같은 코드를 다시 저장하면 해제하지 않는다`() =
        runTest {
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("OLD001"))
            viewModel.onIntent(ShareCodeIntent.Save)

            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any()) }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("OLD001") }
        }

    @Test
    fun `이전 코드가 없으면 해제하지 않는다`() =
        runTest {
            every { settingsRepository.getShareCode() } returns null
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)

            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any()) }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("NEW002") }
        }
}
