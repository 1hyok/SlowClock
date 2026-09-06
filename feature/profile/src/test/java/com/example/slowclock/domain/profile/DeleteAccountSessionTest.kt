package com.example.slowclock.domain.profile

import android.app.NotificationManager
import android.content.Context
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.FamilyGroupRepository
import com.example.slowclock.data.remote.repository.NotificationRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.notification.SharedScheduleNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class DeleteAccountSessionTest(
    private val pausedStep: Int,
) {
    private var uid: String? = "old-uid"
    private var code: String? = "OLD001"
    private val gate = CompletableDeferred<Unit>()
    private val auth = mockk<AuthRepository>()
    private val schedules = mockk<ScheduleRepository>()
    private val groups = mockk<FamilyGroupRepository>()
    private val notifications = mockk<NotificationRepository>()
    private val users = mockk<UserRepository>()
    private val settings = mockk<SettingsRepository>()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val context = mockk<Context>()
    private val manager = mockk<NotificationManager>()
    private val notifier: SharedScheduleNotifier
    private val delete: DeleteAccountUseCase
    private val visited = mutableListOf<Int>()

    init {
        every { auth.currentUid } answers { uid }
        every { settings.getShareCode() } answers { code }
        every { settings.clearShareCode() } answers { code = null }
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.activeNotifications } returns emptyArray()
        notifier = SharedScheduleNotifier(context, auth, settings)
        coEvery { schedules.deleteAllSchedulesOf("old-uid") } coAnswers {
            pauseAt(0)
            true
        }
        coEvery { groups.leaveAllGroupsOf("old-uid") } coAnswers {
            pauseAt(1)
            true
        }
        coEvery { notifications.deleteAllNotificationsOf("old-uid") } coAnswers {
            pauseAt(2)
            true
        }
        coEvery { users.unregisterShareCodeWatcher("OLD001", "old-uid") } coAnswers {
            pauseAt(3)
            true
        }
        coEvery { users.deleteUserDocument("old-uid") } coAnswers {
            pauseAt(4)
            true
        }
        coEvery { auth.deleteCurrentUser("old-uid") } coAnswers {
            pauseAt(5)
            AuthRepository.DeleteResult.Success
        }
        delete = DeleteAccountUseCase(auth, schedules, groups, notifications, users, alarms, notifier)
    }

    private suspend fun pauseAt(step: Int) {
        visited.add(step)
        if (pausedStep == step) {
            try {
                gate.await()
            } catch (_: CancellationException) {
                // 기존 저장소가 취소를 Boolean으로 바꾸더라도 UseCase의 다음 파괴 단계를 막아야 한다.
            }
        }
    }

    @Test
    fun `각 단계의 늦은 완료는 새 계정으로 삭제를 이어가지 않는다`() =
        runTest {
            val result = async { delete() }
            runCurrent()
            assertEquals(pausedStep, visited.last())
            notifier.changeSession {
                uid = "new-uid"
                code = "NEW002"
            }
            gate.complete(Unit)
            assertEquals(DeleteAccountResult.NotSignedIn, result.await())
            assertEquals((0..pausedStep).toList(), visited)
            assertEquals("NEW002", code)
            coVerify(exactly = 0) { users.unregisterShareCodeWatcher(any(), "new-uid") }
            coVerify(exactly = 0) { auth.deleteCurrentUser("new-uid") }
            if (pausedStep == 0) verify(exactly = 0) { alarms.cancelAll() }
        }

    @Test
    fun `각 단계의 취소가 일반 결과로 돌아와도 다음 삭제를 하지 않는다`() =
        runTest {
            val result = async { delete() }
            runCurrent()
            assertEquals(pausedStep, visited.last())
            result.cancel()
            runCurrent()
            assertTrue(result.isCancelled)
            assertEquals((0..pausedStep).toList(), visited)
            if (pausedStep == 0) verify(exactly = 0) { alarms.cancelAll() }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "pause after deletion step {0}")
        fun steps(): List<Array<Int>> = (0..5).map { arrayOf(it) }
    }
}
