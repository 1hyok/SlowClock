package com.example.slowclock.domain.profile

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.FamilyGroupRepository
import com.example.slowclock.data.remote.repository.NotificationRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.notification.SharedNotificationSession
import com.example.slowclock.notification.SharedScheduleNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteAccountUseCaseTest {
    private val authRepository = mockk<AuthRepository>()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val familyGroupRepository = mockk<FamilyGroupRepository>()
    private val notificationRepository = mockk<NotificationRepository>()
    private val userRepository = mockk<UserRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val notifier =
        mockk<SharedScheduleNotifier> {
            every { snapshot() } answers { SharedNotificationSession(authRepository.currentUid, settingsRepository.getShareCode(), 0) }
            every { runIfCurrent(any(), any()) } answers {
                secondArg<() -> Unit>().invoke()
                true
            }
            every { clearDeletedAccount(any()) } answers {
                settingsRepository.clearShareCode()
                true
            }
        }
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)

    private val useCase =
        DeleteAccountUseCase(
            authRepository = authRepository,
            scheduleRepository = scheduleRepository,
            familyGroupRepository = familyGroupRepository,
            notificationRepository = notificationRepository,
            userRepository = userRepository,
            alarmScheduler = alarmScheduler,
            sharedScheduleNotifier = notifier,
        )

    private fun givenSignedIn(uid: String = "uid-1") {
        every { authRepository.currentUid } returns uid
    }

    private fun givenDataDeletionSucceeds(uid: String = "uid-1") {
        coEvery { scheduleRepository.deleteAllSchedulesOf(uid) } returns true
        coEvery { familyGroupRepository.leaveAllGroupsOf(uid) } returns true
        coEvery { notificationRepository.deleteAllNotificationsOf(uid) } returns true
        coEvery { userRepository.deleteUserDocument(uid) } returns true
        every { settingsRepository.getShareCode() } returns "FAM001"
        coEvery { userRepository.unregisterShareCodeWatcher(any(), any()) } returns true
    }

    @Test
    fun `데이터를 전부 지운 뒤 마지막에 Auth 사용자를 지운다`() =
        runTest {
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { authRepository.deleteCurrentUser("uid-1") } returns AuthRepository.DeleteResult.Success

            val result = useCase()

            assertEquals(DeleteAccountResult.Success, result)
            verify(exactly = 1) {
                notifier.clearDeletedAccount(any())
                settingsRepository.clearShareCode()
            }
            coVerifyOrder {
                scheduleRepository.deleteAllSchedulesOf("uid-1")
                familyGroupRepository.leaveAllGroupsOf("uid-1")
                notificationRepository.deleteAllNotificationsOf("uid-1")
                userRepository.unregisterShareCodeWatcher("FAM001", "uid-1")
                userRepository.deleteUserDocument("uid-1")
                authRepository.deleteCurrentUser("uid-1")
            }
        }

    @Test
    fun `Firebase 가 재로그인을 요구하면 그대로 알린다`() =
        runTest {
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { authRepository.deleteCurrentUser("uid-1") } returns AuthRepository.DeleteResult.RecentLoginRequired

            assertEquals(DeleteAccountResult.RecentLoginRequired, useCase())
        }

    @Test
    fun `일정 삭제가 실패하면 뒤 단계와 Auth 삭제로 넘어가지 않는다`() =
        runTest {
            givenSignedIn()
            coEvery { scheduleRepository.deleteAllSchedulesOf("uid-1") } returns false

            val result = useCase()

            assertEquals(DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES), result)
            coVerify(exactly = 0) { familyGroupRepository.leaveAllGroupsOf(any()) }
            coVerify(exactly = 0) { userRepository.deleteUserDocument(any()) }
            coVerify(exactly = 0) { authRepository.deleteCurrentUser("uid-1") }
        }

    @Test
    fun `사용자 문서 삭제가 실패하면 Auth 사용자를 지우지 않는다`() =
        runTest {
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { userRepository.deleteUserDocument("uid-1") } returns false

            val result = useCase()

            assertEquals(DeleteAccountResult.Failed(DeleteAccountStep.USER_DOCUMENT), result)
            coVerify(exactly = 0) { authRepository.deleteCurrentUser("uid-1") }
        }

    @Test
    fun `로그인돼 있지 않으면 아무것도 지우지 않는다`() =
        runTest {
            every { authRepository.currentUid } returns null

            assertEquals(DeleteAccountResult.NotSignedIn, useCase())
            coVerify(exactly = 0) { scheduleRepository.deleteAllSchedulesOf(any()) }
            coVerify(exactly = 0) { authRepository.deleteCurrentUser("uid-1") }
        }

    @Test
    fun `보고 있던 공유 코드의 감시자 등록을 사용자 문서보다 먼저 지운다`() =
        runTest {
            // 보안 규칙이 본인만 지우게 하므로 Auth 사용자가 사라진 뒤에는 아무도 못 지운다(#124).
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { authRepository.deleteCurrentUser("uid-1") } returns AuthRepository.DeleteResult.Success

            useCase()

            coVerifyOrder {
                userRepository.unregisterShareCodeWatcher("FAM001", "uid-1")
                userRepository.deleteUserDocument("uid-1")
            }
        }

    @Test
    fun `감시자 등록을 못 지우면 그 단계로 멈춘다`() =
        runTest {
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { userRepository.unregisterShareCodeWatcher("FAM001", "uid-1") } returns false

            val result = useCase()

            assertEquals(DeleteAccountResult.Failed(DeleteAccountStep.SHARE_CODE_WATCHERS), result)
            coVerify(exactly = 0) { userRepository.deleteUserDocument(any()) }
            coVerify(exactly = 0) { authRepository.deleteCurrentUser("uid-1") }
        }

    @Test
    fun `보고 있던 공유 코드가 없으면 감시자 해제를 시도하지 않는다`() =
        runTest {
            givenSignedIn()
            givenDataDeletionSucceeds()
            every { settingsRepository.getShareCode() } returns null
            coEvery { authRepository.deleteCurrentUser("uid-1") } returns AuthRepository.DeleteResult.Success

            val result = useCase()

            assertEquals(DeleteAccountResult.Success, result)
            coVerify(exactly = 0) { userRepository.unregisterShareCodeWatcher(any(), any()) }
        }

    @Test
    fun `일정을 지운 뒤 이 기기에 걸린 알람도 지운다`() =
        runTest {
            // 지우지 않으면 계정을 지운 뒤에도 알람이 울리고, 재부팅 뒤에도 장부를 보고
            // 되살아난다(#127).
            givenSignedIn()
            givenDataDeletionSucceeds()
            coEvery { authRepository.deleteCurrentUser("uid-1") } returns AuthRepository.DeleteResult.Success

            useCase()

            coVerifyOrder {
                scheduleRepository.deleteAllSchedulesOf("uid-1")
                alarmScheduler.cancelAll()
            }
        }

    @Test
    fun `일정 삭제가 실패하면 알람은 건드리지 않는다`() =
        runTest {
            // 서버 일정이 남아 있는데 기기 알람만 지우면, 앱을 다시 열기 전까지 그 일정이
            // 소리 없이 지나간다.
            givenSignedIn()
            coEvery { scheduleRepository.deleteAllSchedulesOf("uid-1") } returns false

            val result = useCase()

            assertEquals(DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES), result)
            verify(exactly = 0) { alarmScheduler.cancelAll() }
        }
}
