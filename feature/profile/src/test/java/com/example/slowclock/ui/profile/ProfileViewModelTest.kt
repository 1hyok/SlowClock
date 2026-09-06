package com.example.slowclock.ui.profile

import com.example.slowclock.data.model.User
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.domain.profile.DeleteAccountResult
import com.example.slowclock.domain.profile.DeleteAccountStep
import com.example.slowclock.domain.profile.DeleteAccountUseCase
import com.example.slowclock.domain.profile.SignOutUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
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

/**
 * 내 정보 화면 ViewModel 의 전이. Intent 를 넣고 UiState 만 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val userRepository = mockk<UserRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val deleteAccount = mockk<DeleteAccountUseCase>()
    private val signOutUseCase = mockk<SignOutUseCase>(relaxed = true)
    private val scheduleRepository = mockk<ScheduleRepository>()

    private val authUid = MutableStateFlow<String?>("uid-1")
    private val userUpdates =
        MutableStateFlow<User?>(
            User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "ABC123"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.currentProfile } returns
            AuthRepository.Profile(uid = "uid-1", displayName = "Auth 이름", email = "auth@example.com")
        every { authRepository.currentUid } answers { authUid.value }
        every { authRepository.observeCurrentUid() } returns authUid
        every { userRepository.observeUser("uid-1") } returns userUpdates
        justRun { authRepository.signOut() }
        coEvery { scheduleRepository.fillMissingSharedCode(any()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProfileViewModel(userRepository, authRepository, scheduleRepository, deleteAccount, signOutUseCase)

    @Test
    fun `로그인 전에 만든 화면이 로그인과 늦은 사용자 문서 생성을 따라간다`() =
        runTest {
            authUid.value = null
            userUpdates.value = null
            val viewModel = createViewModel()
            assertTrue(viewModel.uiState.value.isSignedOut)

            authUid.value = "uid-1"
            assertFalse(viewModel.uiState.value.isSignedOut)
            assertEquals("Auth 이름", viewModel.uiState.value.name)
            userUpdates.value = User(id = "uid-1", name = "새 이름", shareCode = "NEW123")

            assertEquals("새 이름", viewModel.uiState.value.name)
            assertEquals("NEW123", viewModel.uiState.value.shareCode)
        }

    @Test
    fun `계정이 바뀌면 앞 프로필을 즉시 비우고 새 계정 문서만 받는다`() =
        runTest {
            val nextUser = MutableStateFlow<User?>(null)
            every { userRepository.observeUser("uid-2") } returns nextUser
            val viewModel = createViewModel()
            viewModel.onIntent(ProfileIntent.RequestDeleteAccount)
            every { authRepository.currentProfile } returns
                AuthRepository.Profile("uid-2", "두 번째 계정", "second@example.com")

            authUid.value = "uid-2"
            userUpdates.value = User(id = "uid-1", name = "늦은 옛 이름", shareCode = "OLD999")

            assertEquals("두 번째 계정", viewModel.uiState.value.name)
            assertEquals("", viewModel.uiState.value.shareCode)
            assertFalse(viewModel.uiState.value.isDeleteConfirmVisible)
            nextUser.value = User(id = "uid-2", name = "현재 이름", shareCode = "NEW222")
            assertEquals("NEW222", viewModel.uiState.value.shareCode)

            authUid.value = null
            assertTrue(viewModel.uiState.value.isSignedOut)
            assertEquals("", viewModel.uiState.value.name)
            assertEquals("", viewModel.uiState.value.email)
            assertEquals("", viewModel.uiState.value.shareCode)
        }

    @Test
    fun `인증 알림보다 늦게 도착한 앞 계정 문서는 반영하지 않는다`() =
        runTest {
            val viewModel = createViewModel()
            // Firebase의 현재 계정은 바뀌었지만 UID Flow 알림은 아직 대기 중인 경우다.
            every { authRepository.currentUid } returns "uid-2"
            userUpdates.value = User(id = "uid-1", name = "늦은 결과", shareCode = "OLD999")

            assertEquals("정일혁", viewModel.uiState.value.name)
            assertEquals("ABC123", viewModel.uiState.value.shareCode)
        }

    @Test
    fun `읽기 실패는 코드 부재와 구분하고 다시 읽을 수 있다`() =
        runTest {
            every { userRepository.observeUser("uid-1") } returns flow { throw IllegalStateException("offline") }
            val viewModel = createViewModel()

            assertFalse(viewModel.uiState.value.isLoading)
            assertNotNull(viewModel.uiState.value.loadError)
            assertFalse(viewModel.uiState.value.isSignedOut)

            every { userRepository.observeUser("uid-1") } returns userUpdates
            viewModel.onIntent(ProfileIntent.RetryProfile)
            assertNull(viewModel.uiState.value.loadError)
            assertEquals("ABC123", viewModel.uiState.value.shareCode)
        }

    @Test
    fun `앞 계정의 늦은 코드 재생성 결과가 새 계정 상태를 바꾸지 않는다`() =
        runTest {
            val finished = CompletableDeferred<Boolean>()
            coEvery { userRepository.ensureShareCode("uid-1", any(), any()) } coAnswers {
                withContext(NonCancellable) { finished.await() }
            }
            every { userRepository.observeUser("uid-2") } returns flowOf(User(id = "uid-2", shareCode = "NEW222"))
            val viewModel = createViewModel()
            viewModel.onIntent(ProfileIntent.RetryShareCode)
            assertTrue(viewModel.uiState.value.isRetryingShareCode)
            every { authRepository.currentProfile } returns AuthRepository.Profile("uid-2", "새 계정", "")
            authUid.value = "uid-2"
            finished.complete(true)

            coVerify(exactly = 0) { scheduleRepository.fillMissingSharedCode("uid-1") }
            assertEquals("NEW222", viewModel.uiState.value.shareCode)
            assertFalse(viewModel.uiState.value.isRetryingShareCode)
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `Firestore 사용자 문서로 이름·이메일·공유 코드를 채운다`() =
        runTest {
            val state = createViewModel().uiState.value

            assertFalse(state.isLoading)
            assertEquals("정일혁", state.name)
            assertEquals("user@example.com", state.email)
            assertEquals("ABC123", state.shareCode)
        }

    @Test
    fun `사용자 문서가 없으면 Auth 프로필로 채운다`() =
        runTest {
            userUpdates.value = null

            val state = createViewModel().uiState.value

            assertEquals("Auth 이름", state.name)
            assertEquals("auth@example.com", state.email)
            assertEquals("", state.shareCode)
        }

    @Test
    fun `로그인돼 있지 않으면 로그인 안내 상태가 된다`() =
        runTest {
            every { authRepository.currentProfile } returns null
            authUid.value = null

            val state = createViewModel().uiState.value

            assertTrue(state.isSignedOut)
            assertNull(state.loadError)
        }

    @Test
    fun `로그아웃은 저장소를 부르고 떠나기 신호를 낸다`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.SignOut)

            verify(exactly = 1) { signOutUseCase() }
            assertEquals(ProfileLeaveReason.SIGNED_OUT, viewModel.uiState.value.leave)

            viewModel.onIntent(ProfileIntent.ConsumeLeave)
            assertNull(viewModel.uiState.value.leave)
        }

    @Test
    fun `계정 삭제는 확인 다이얼로그를 거쳐 성공하면 떠나기 신호를 낸다`() =
        runTest {
            coEvery { deleteAccount() } returns DeleteAccountResult.Success
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.RequestDeleteAccount)
            assertTrue(viewModel.uiState.value.isDeleteConfirmVisible)

            viewModel.onIntent(ProfileIntent.ConfirmDeleteAccount)

            val state = viewModel.uiState.value
            assertFalse(state.isDeleteConfirmVisible)
            assertFalse(state.isDeleting)
            assertEquals(ProfileLeaveReason.ACCOUNT_DELETED, state.leave)
            coVerify(exactly = 1) { deleteAccount() }
        }

    @Test
    fun `취소하면 다이얼로그만 닫히고 UseCase 를 부르지 않는다`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.RequestDeleteAccount)
            viewModel.onIntent(ProfileIntent.DismissDeleteConfirm)

            assertFalse(viewModel.uiState.value.isDeleteConfirmVisible)
            coVerify(exactly = 0) { deleteAccount() }
        }

    @Test
    fun `재로그인이 필요하면 안내 문구를 내고 화면에 남는다`() =
        runTest {
            coEvery { deleteAccount() } returns DeleteAccountResult.RecentLoginRequired
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.ConfirmDeleteAccount)

            val state = viewModel.uiState.value
            assertNull(state.leave)
            assertFalse(state.isDeleting)
            assertEquals("보안을 위해 로그아웃한 뒤 다시 로그인하고 계정 삭제를 다시 눌러 주세요.", state.userMessage)

            viewModel.onIntent(ProfileIntent.ConsumeUserMessage)
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `단계 실패는 단계별 문구로 알린다`() =
        runTest {
            coEvery { deleteAccount() } returns DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES)
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.ConfirmDeleteAccount)

            assertEquals("일정을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.", viewModel.uiState.value.userMessage)
            assertNull(viewModel.uiState.value.leave)
        }

    @Test
    fun `공유 코드를 다시 만들면 화면에 반영한다`() =
        runTest {
            // 신호가 약한 곳에서 처음 로그인하면 코드가 비어 있는 채로 남고, 그 뒤에 만든 일정은
            // 가족이 어떤 코드로도 읽지 못한다(#134).
            userUpdates.value =
                User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "")
            val viewModel = createViewModel()
            assertEquals("", viewModel.uiState.value.shareCode)

            coEvery { userRepository.ensureShareCode("uid-1", any(), any()) } coAnswers {
                userUpdates.value =
                    User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "XYZ789")
                true
            }
            viewModel.onIntent(ProfileIntent.RetryShareCode)

            assertEquals("XYZ789", viewModel.uiState.value.shareCode)
            assertFalse(viewModel.uiState.value.isRetryingShareCode)
        }

    @Test
    fun `공유 코드를 만들면 코드 없이 저장된 일정도 함께 맞춘다`() =
        runTest {
            // 코드만 만들면 그 전에 저장한 일정은 sharedCode 가 빈 채라 가족이 영영 못 읽는다.
            // 다시 시도 버튼이 「고쳤다」 는 잘못된 안심을 주면 안 된다(#178).
            userUpdates.value =
                User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "")
            coEvery { userRepository.ensureShareCode("uid-1", any(), any()) } returns true
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.RetryShareCode)

            coVerify(exactly = 1) { scheduleRepository.fillMissingSharedCode("uid-1") }
        }

    @Test
    fun `공유 코드를 못 만들면 일정은 건드리지 않는다`() =
        runTest {
            userUpdates.value =
                User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "")
            coEvery { userRepository.ensureShareCode(any(), any(), any()) } returns false
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.RetryShareCode)

            coVerify(exactly = 0) { scheduleRepository.fillMissingSharedCode(any()) }
        }

    @Test
    fun `공유 코드를 또 못 만들면 이유를 알린다`() =
        runTest {
            userUpdates.value =
                User(id = "uid-1", name = "정일혁", email = "user@example.com", shareCode = "")
            coEvery { userRepository.ensureShareCode(any(), any(), any()) } returns false
            val viewModel = createViewModel()

            viewModel.onIntent(ProfileIntent.RetryShareCode)

            val state = viewModel.uiState.value
            assertNotNull(state.userMessage)
            assertFalse(state.isRetryingShareCode)
        }
}
