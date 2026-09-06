package com.example.slowclock.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedScheduleNotifierTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<NotificationManager>(relaxed = true)
    private val auth = mockk<AuthRepository>()
    private val settings = mockk<SettingsRepository>()
    private val notifier: SharedScheduleNotifier
    private val message = SharedScheduleMessage("uid", "CODE01", "id", "일정", "본문")

    init {
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { auth.currentUid } returns "uid"
        every { settings.getShareCode() } returns "CODE01"
        every { manager.activeNotifications } returns emptyArray()
        notifier = SharedScheduleNotifier(context, auth, settings)
    }

    @Test
    fun `알림 권한이 꺼지면 알림을 만들거나 게시하지 않는다`() {
        every { manager.areNotificationsEnabled() } returns false
        assertFalse(notifier.show(message))
        verify(exactly = 0) { manager.createNotificationChannel(any()) }
        verify(exactly = 0) { manager.notify(any<String>(), any<Int>(), any<Notification>()) }
    }

    @Test
    fun `공유 채널이 꺼져 있으면 게시하지 않는다`() {
        val channel = mockk<NotificationChannel>()
        every { manager.areNotificationsEnabled() } returns true
        every { manager.getNotificationChannel(SharedScheduleNotifier.CHANNEL_ID) } returns channel
        every { channel.importance } returns NotificationManager.IMPORTANCE_NONE
        assertFalse(notifier.show(message))
        verify(exactly = 0) { manager.notify(any<String>(), any<Int>(), any<Notification>()) }
    }

    @Test
    fun `공유 알림만 지우고 정시 알람과 놓친 알림은 남긴다`() {
        every { manager.activeNotifications } returns
            arrayOf(
                active("shared_schedule:some-id", 0, "schedule_channel"),
                active(null, 0, "schedule_channel"),
                active("FCM-Notification:123", 0, "fcm_fallback_notification_channel"),
                active(null, 77, "alarm_channel"),
                active(null, 0, "missed_alarm_channel"),
                active("other-tag", 0, "schedule_channel"),
                active("FCM-Notification:456", 8, "alarm_channel"),
            )
        notifier.changeSession {}
        verify(exactly = 1) { manager.cancel("shared_schedule:some-id", 0) }
        verify(exactly = 1) { manager.cancel(null, 0) }
        verify(exactly = 1) { manager.cancel("FCM-Notification:123", 0) }
        verify(exactly = 1) { manager.activeNotifications }
        confirmVerified(manager)
        verify(exactly = 0) { manager.cancelAll() }
    }

    private fun active(
        tag: String?,
        id: Int,
        channel: String,
    ): StatusBarNotification =
        mockk {
            every { this@mockk.tag } returns tag
            every { this@mockk.id } returns id
            every { notification } returns mockk { every { channelId } returns channel }
        }
}
