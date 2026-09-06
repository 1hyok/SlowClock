package com.example.slowclock.ui.alarm

import android.app.Notification
import android.content.Intent
import com.example.slowclock.core.alarm.AlarmNotifications
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.core.alarm.AlarmSchedulerEntryPoint
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 플랫폼 자원 경계만 대체하고 실제 onStartCommand와 미룸 분기를 연결한다. */
class AlarmTriggerServiceTest {
    private lateinit var service: AlarmTriggerService
    private val scheduler = mockk<AlarmScheduler>()

    @Before
    fun setUp() {
        mockkObject(AlarmNotifications, AlarmSchedulerEntryPoint.Companion)
        every { AlarmNotifications.canShowControls(any()) } returns true
        every { AlarmNotifications.isCurrent(any(), any(), any()) } returns true
        every { AlarmNotifications.isUsableToken(any()) } returns true
        justRun { AlarmNotifications.revoke(any()) }
        every { AlarmSchedulerEntryPoint.from(any()) } returns scheduler
        service = spyk(AlarmTriggerService(), recordPrivateCalls = true)
        every { service["buildNotification"](any<AlarmTriggerService.Ringing>()) } returns mockk<Notification>()
        justRun { service["startForegroundCompat"](any<Notification>()) }
        justRun { service["acquireWakeLock"]() }
        justRun { service["startSound"]() }
        justRun { service["startVibration"]() }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `알림 거부 때 foreground와 소리와 진동을 시작하지 않는다`() {
        every { AlarmNotifications.canShowControls(any()) } returns false
        service.onStartCommand(ring(0), 0, 1)
        verify(exactly = 0) { service["startForegroundCompat"](any<Notification>()) }
        verify(exactly = 0) { service["startSound"]() }
        verify { service.stopSelf() }
    }

    @Test
    fun `승격 예외가 나면 소리와 자원 없이 종료한다`() {
        every { service["startForegroundCompat"](any<Notification>()) } throws SecurityException("promotion")
        service.onStartCommand(ring(0), 0, 1)
        verify(exactly = 0) { service["startSound"]() }
        verify(exactly = 0) { service["acquireWakeLock"]() }
        verify { service.stopSelf() }
    }

    @Test
    fun `빈 서비스의 늦은 끄기와 미룸 요청은 서비스를 남기지 않는다`() {
        service.onStartCommand(action(AlarmTriggerService.ACTION_DISMISS), 0, 1)
        service.onStartCommand(action(AlarmTriggerService.ACTION_SNOOZE), 0, 2)
        service.onStartCommand(null, 0, 3)
        verify { service.stopSelf(1) }
        verify { service.stopSelf(2) }
        verify { service.stopSelf(3) }
        verify(exactly = 0) { service["startSound"]() }
    }

    @Test
    fun `미룸 실패는 현재 울림을 남기고 성공한 다음 요청만 멈춘다`() {
        every { scheduler.snoozeFromNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns false
        service.onStartCommand(ring(0), 0, 1)
        val token = currentToken()
        service.onStartCommand(action(AlarmTriggerService.ACTION_SNOOZE, token), 0, 2)
        verify(exactly = 0) { service.stopSelf() }
        assertEquals(token, currentToken())
        every { scheduler.snoozeFromNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns true
        service.onStartCommand(action(AlarmTriggerService.ACTION_SNOOZE, token), 0, 3)
        verify(exactly = 1) { service.stopSelf() }
    }

    @Test
    fun `실제 서비스 재수신은 0에서1에서2로 미루고 세번째 예약은 하지 않는다`() {
        every { scheduler.snoozeFromNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns true
        for (count in 0..2) {
            service.onStartCommand(ring(count), 0, count * 2 + 1)
            service.onStartCommand(action(AlarmTriggerService.ACTION_SNOOZE, currentToken()), 0, count * 2 + 2)
        }
        verify(exactly = 1) { scheduler.snoozeFromNotification(42, "s1", "약", "", true, 1, 123, any()) }
        verify(exactly = 1) { scheduler.snoozeFromNotification(42, "s1", "약", "", true, 2, 123, any()) }
        verify(exactly = 0) { scheduler.snoozeFromNotification(any(), any(), any(), any(), any(), 3, any(), any()) }
    }

    @Test
    fun `알림 스와이프 뒤 deleteIntent는 OS목록에서 사라진 현재 알람도 끈다`() {
        service.onStartCommand(ring(0), 0, 1)
        val token = currentToken()
        every { AlarmNotifications.isCurrent(any(), any(), any()) } returns false
        service.onStartCommand(action(AlarmTriggerService.ACTION_DISMISS, token), 0, 2)
        verify(exactly = 1) { service.stopSelf() }
    }

    private fun ring(count: Int): Intent =
        mockk<Intent>(relaxed = true).also {
            every { it.action } returns AlarmTriggerService.ACTION_RING
            every { it.getStringExtra(AlarmTriggerService.EXTRA_TITLE) } returns "약"
            every { it.getStringExtra(AlarmTriggerService.EXTRA_DESC) } returns ""
            every { it.getStringExtra(AlarmTriggerService.EXTRA_SCHEDULE_ID) } returns "s1"
            every { it.getBooleanExtra(AlarmTriggerService.EXTRA_FULL_SCREEN, true) } returns true
            every { it.getIntExtra(AlarmTriggerService.EXTRA_REQUEST_CODE, 0) } returns 42
            every { it.getIntExtra(AlarmTriggerService.EXTRA_SNOOZE_COUNT, 0) } returns count
        }

    private fun action(
        action: String,
        token: String? = null,
    ): Intent =
        mockk<Intent>(relaxed = true).also {
            every { it.action } returns action
            every { it.getIntExtra(AlarmTriggerService.EXTRA_REQUEST_CODE, Int.MIN_VALUE) } returns 42
            every { it.getStringExtra(AlarmNotifications.EXTRA_TOKEN) } returns token
        }

    private fun currentToken(): String {
        val current =
            AlarmTriggerService::class.java
                .getDeclaredField("ringing")
                .apply { isAccessible = true }
                .get(service)
        return current.javaClass
            .getDeclaredField("token")
            .apply { isAccessible = true }
            .get(current) as String
    }
}
