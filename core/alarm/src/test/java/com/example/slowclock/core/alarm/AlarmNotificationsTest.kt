package com.example.slowclock.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AlarmNotificationsTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<NotificationManager>(relaxed = true)

    init {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager
    }

    @Test
    fun `앱 알림 거부와 알람 채널 차단은 모두 울림 조작 불가다`() {
        every { manager.areNotificationsEnabled() } returns false
        assertFalse(AlarmNotifications.canShowControls(context))
        every { manager.areNotificationsEnabled() } returns true
        val channel = mockk<NotificationChannel>()
        every { manager.getNotificationChannel(AlarmNotifications.CHANNEL_ID) } returns channel
        every { channel.importance } returns NotificationManager.IMPORTANCE_NONE
        assertFalse(AlarmNotifications.canShowControls(context))
        every { channel.importance } returns NotificationManager.IMPORTANCE_HIGH
        assertTrue(AlarmNotifications.canShowControls(context))
    }

    @Test
    fun `취소가 OS 알림목록에 늦게 반영돼도 토큰은 즉시 무효다`() {
        val token = UUID.randomUUID().toString()
        every { manager.activeNotifications } returns arrayOf(notification("deleted", 1042, token))
        assertTrue(AlarmNotifications.isCurrent(context, 1042, token))

        AlarmNotifications.invalidate(context, "deleted")

        // 비동기 cancel 완료 전처럼 activeNotifications가 여전히 옛 알림을 반환한다.
        assertFalse(AlarmNotifications.isCurrent(context, 1042, token))
        verify { manager.cancel(null, 1042) }
    }

    @Test
    fun `같은 알림번호의 새로운 회차는 예전 토큰으로 조작할 수 없다`() {
        every { manager.activeNotifications } returns arrayOf(notification("s1", 1042, "new"))
        assertFalse(AlarmNotifications.isCurrent(context, 1042, "old"))
        assertFalse(AlarmNotifications.isCurrent(context, 1043, "new"))
        assertTrue(AlarmNotifications.isCurrent(context, 1042, "new"))
    }

    @Test
    fun `서버에서 지운 마지막 단발 일정의 겹침 알림도 무효화한다`() {
        val removedToken = UUID.randomUUID().toString()
        val retainedToken = UUID.randomUUID().toString()
        every { manager.activeNotifications } returns
            arrayOf(
                notification("gone", 1042, removedToken),
                notification("remaining", 1043, retainedToken),
            )
        AlarmNotifications.retainSchedules(context, setOf("remaining"))
        assertFalse(AlarmNotifications.isCurrent(context, 1042, removedToken))
        assertTrue(AlarmNotifications.isCurrent(context, 1043, retainedToken))
    }

    private fun notification(
        scheduleId: String,
        id: Int,
        token: String,
    ): StatusBarNotification {
        val extras = mockk<Bundle>()
        every { extras.getString(AlarmNotifications.EXTRA_TOKEN) } returns token
        every { extras.getString(AlarmNotifications.EXTRA_SCHEDULE) } returns scheduleId
        val notification = Notification().apply { this.extras = extras }
        return mockk<StatusBarNotification>().also {
            every { it.id } returns id
            every { it.tag } returns null
            every { it.notification } returns notification
        }
    }
}
