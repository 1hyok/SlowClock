package com.example.slowclock.ui.settings

import android.app.NotificationManager
import android.content.Context
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.notification.SharedScheduleNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ShareCodeViewModelTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val userRepository = mockk<UserRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val manager = mockk<NotificationManager>(relaxed = true)
    private val context = mockk<Context>()
    private lateinit var notifier: SharedScheduleNotifier

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.currentUid } returns "uid-1"
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.activeNotifications } returns emptyArray()
        notifier = SharedScheduleNotifier(context, authRepository, settingsRepository)
        every { settingsRepository.getShareCode() } returns "OLD001"
        justRun { settingsRepository.setShareCode(any()) }
        justRun { settingsRepository.clearShareCode() }
        coEvery { userRepository.registerShareCodeWatcher(any()) } returns true
        coEvery { userRepository.unregisterShareCodeWatcher(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `저장된 공유 코드로 입력을 채운다`() =
        runTest {
            assertEquals("OLD001", ShareCodeViewModel(settingsRepository, userRepository, notifier).uiState.value.input)
        }

    @Test
    fun `저장하면 설정에 쓰고 감시자를 등록한 뒤 저장 신호를 낸다`() =
        runTest {
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput(" NEW002 "))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 1) { settingsRepository.setShareCode("NEW002") }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("NEW002") }
            // 이전 코드의 등록을 지우지 않으면 그 사람에게 내 토큰이 계속 남는다(#124).
            coVerify(exactly = 1) { userRepository.unregisterShareCodeWatcher("OLD001", "uid-1") }
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
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 0) { settingsRepository.setShareCode(any()) }
            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any(), any()) }
            assertFalse(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.isSaving)
            assertNotNull(viewModel.uiState.value.saveError)
        }

    @Test
    fun `입력을 고치면 실패 안내가 사라진다`() =
        runTest {
            coEvery { userRepository.registerShareCodeWatcher(any()) } returns false
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)
            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW003"))

            assertNull(viewModel.uiState.value.saveError)
        }

    @Test
    fun `빈 입력을 저장하면 감시자 해제 뒤 로컬 코드도 지운다`() =
        runTest {
            val unregistered = CompletableDeferred<Boolean>()
            coEvery { userRepository.unregisterShareCodeWatcher("OLD001", "uid-1") } coAnswers { unregistered.await() }
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("   "))
            viewModel.onIntent(ShareCodeIntent.Save)

            assertTrue(viewModel.uiState.value.isSaving)
            verify(exactly = 0) { settingsRepository.clearShareCode() }
            unregistered.complete(true)
            verify(exactly = 0) { settingsRepository.setShareCode(any()) }
            verify(exactly = 1) { settingsRepository.clearShareCode() }
            coVerify(exactly = 0) { userRepository.registerShareCodeWatcher(any()) }
            assertTrue(viewModel.uiState.value.isSaved)
            assertFalse(viewModel.uiState.value.hasRegisteredCode)
        }

    @Test
    fun `감시자 해제 실패는 기존 코드를 유지하고 다시 시도할 수 있다`() =
        runTest {
            coEvery { userRepository.unregisterShareCodeWatcher("OLD001", "uid-1") } returns false
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
            viewModel.onIntent(ShareCodeIntent.UpdateInput(""))
            viewModel.onIntent(ShareCodeIntent.Save)

            verify(exactly = 0) { settingsRepository.clearShareCode() }
            assertTrue(viewModel.uiState.value.hasRegisteredCode)
            assertTrue(viewModel.uiState.value.canSave)
            assertNotNull(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaved)

            coEvery { userRepository.unregisterShareCodeWatcher("OLD001", "uid-1") } returns true
            viewModel.onIntent(ShareCodeIntent.Save)
            verify(exactly = 1) { settingsRepository.clearShareCode() }
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `저장된 코드가 없으면 빈 입력은 저장하지 않는다`() =
        runTest {
            every { settingsRepository.getShareCode() } returns null
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
            viewModel.onIntent(ShareCodeIntent.Save)
            assertFalse(viewModel.uiState.value.canSave)
            verify(exactly = 0) { settingsRepository.clearShareCode() }
        }

    @Test
    fun `소문자 입력은 기기 언어와 관계없이 대문자로 등록한다`() =
        runTest {
            val previousLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"))
                val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
                viewModel.onIntent(ShareCodeIntent.UpdateInput(" ai123b "))
                assertEquals(" AI123B ", viewModel.uiState.value.input)
                viewModel.onIntent(ShareCodeIntent.Save)
                coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("AI123B") }
                verify(exactly = 1) { settingsRepository.setShareCode("AI123B") }
            } finally {
                Locale.setDefault(previousLocale)
            }
        }

    @Test
    fun `저장 중에는 중복 저장이나 입력 변경을 받지 않는다`() =
        runTest {
            val registered = CompletableDeferred<Boolean>()
            coEvery { userRepository.registerShareCodeWatcher("NEW002") } coAnswers { registered.await() }
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)
            viewModel.onIntent(ShareCodeIntent.UpdateInput(""))
            viewModel.onIntent(ShareCodeIntent.Save)
            assertEquals("NEW002", viewModel.uiState.value.input)
            registered.complete(true)
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("NEW002") }
            verify(exactly = 1) { settingsRepository.setShareCode("NEW002") }
        }

    @Test
    fun `같은 코드를 다시 저장하면 해제하지 않는다`() =
        runTest {
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("OLD001"))
            viewModel.onIntent(ShareCodeIntent.Save)

            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any(), any()) }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("OLD001") }
        }

    @Test
    fun `이전 코드가 없으면 해제하지 않는다`() =
        runTest {
            every { settingsRepository.getShareCode() } returns null
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)

            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)

            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any(), any()) }
            coVerify(exactly = 1) { userRepository.registerShareCodeWatcher("NEW002") }
        }

    @Test
    fun `감시자 등록 대기 중 로그아웃하면 늦은 성공이 코드를 되살리지 않는다`() =
        runTest {
            val registered = CompletableDeferred<Boolean>()
            coEvery { userRepository.registerShareCodeWatcher("NEW002") } coAnswers { registered.await() }
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
            viewModel.onIntent(ShareCodeIntent.UpdateInput("NEW002"))
            viewModel.onIntent(ShareCodeIntent.Save)
            notifier.changeSession { every { authRepository.currentUid } returns null }
            registered.complete(true)
            verify(exactly = 0) { settingsRepository.setShareCode(any()) }
            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any(), any()) }
            assertNotNull(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `해제 대기 중 새 세션이 코드를 저장하면 늦은 해제가 새 코드를 지우지 않는다`() =
        runTest {
            val unregistered = CompletableDeferred<Boolean>()
            coEvery { userRepository.unregisterShareCodeWatcher("OLD001", "uid-1") } coAnswers { unregistered.await() }
            val viewModel = ShareCodeViewModel(settingsRepository, userRepository, notifier)
            viewModel.onIntent(ShareCodeIntent.UpdateInput(""))
            viewModel.onIntent(ShareCodeIntent.Save)
            notifier.changeSession { every { settingsRepository.getShareCode() } returns "NEW002" }
            unregistered.complete(true)
            verify(exactly = 0) { settingsRepository.clearShareCode() }
            assertEquals("NEW002", settingsRepository.getShareCode())
            assertFalse(viewModel.uiState.value.isSaved)
        }
}
