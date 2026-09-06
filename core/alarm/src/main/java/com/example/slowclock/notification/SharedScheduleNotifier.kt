package com.example.slowclock.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.slowclock.core.alarm.R
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 공유 푸시의 표시와 세션 변경을 한 인스턴스에서 관리한다. 정시 알람에는 관여하지 않는다. */
@Singleton
class SharedScheduleNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val authRepository: AuthRepository,
        private val settingsRepository: SettingsRepository,
    ) {
        private val manager: NotificationManager
            get() = context.getSystemService(NotificationManager::class.java)
        private val delivery =
            SharedNotificationDelivery(
                readSession = { authRepository.currentUid to settingsRepository.getShareCode() },
                cancelNotifications = ::cancelSharedNotifications,
            )

        fun snapshot(): SharedNotificationSession = delivery.snapshot()

        /** 호출자는 서버 응답 대기나 suspend 작업을 이 동기 구간 안에 넣지 않는다. */
        fun changeSession(change: () -> Unit) = delivery.changeSession(change)

        fun replaceShareCode(
            expected: SharedNotificationSession,
            code: String?,
        ): Boolean =
            delivery.changeIfCurrent(expected) {
                if (code.isNullOrBlank()) settingsRepository.clearShareCode() else settingsRepository.setShareCode(code)
            }

        fun withCurrentSession(action: (SharedNotificationSession) -> Unit) = delivery.withCurrentSession(action)

        fun show(message: SharedScheduleMessage): Boolean =
            delivery.showIfCurrent(message) {
                if (!manager.areNotificationsEnabled()) return@showIfCurrent false
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.shared_notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
                ) {
                    return@showIfCurrent false
                }
                val intent =
                    Intent().setClassName(context, "com.example.slowclock.MainActivity").apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                val notification =
                    NotificationCompat
                        .Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.baseline_access_alarm_24)
                        .setContentTitle(message.title)
                        .setContentText(message.body)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build()
                try {
                    manager.notify(notificationTag(message), 0, notification)
                    true
                } catch (_: SecurityException) {
                    false
                }
            }

        private fun cancelSharedNotifications() {
            manager.activeNotifications
                .filter {
                    it.tag?.startsWith(TAG_PREFIX) == true ||
                        (it.tag == null && it.id == 0 && it.notification.channelId == CHANNEL_ID) ||
                        // 구 sender의 notification payload는 Firebase SDK가 별도 tag/기본 채널로 표시했다.
                        (
                            it.id == 0 && it.tag?.startsWith("FCM-Notification:") == true &&
                                it.notification.channelId == "fcm_fallback_notification_channel"
                        )
                }.forEach { manager.cancel(it.tag, it.id) }
        }

        companion object {
            const val CHANNEL_ID = "schedule_channel"
            const val TAG_PREFIX = "shared_schedule:"

            internal fun notificationTag(message: SharedScheduleMessage): String =
                "$TAG_PREFIX${message.recipientUid.length}:${message.recipientUid}${message.shareCode.length}:${message.shareCode}${message.scheduleId}"
        }
    }
