package com.example.slowclock.core.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.example.slowclock.ui.alarm.AlarmTriggerService

/** OS에 남아 있는 알림이 조작 권한의 수명이다. 취소된 알림의 늦은 버튼은 받지 않는다. */
internal object AlarmNotifications {
    const val EXTRA_TOKEN = "alarmCommandToken"
    const val EXTRA_SCHEDULE = "alarmCommandSchedule"
    const val RINGING_ID = 123
    const val CHANNEL_ID = "alarm_ringing_v2"

    private val revokedTokens =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    fun canShowControls(context: Context): Boolean {
        val manager = manager(context)
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun isUsableToken(token: String): Boolean = token !in revokedTokens

    fun isCurrent(
        context: Context,
        notificationId: Int,
        token: String?,
    ): Boolean =
        !token.isNullOrBlank() && isUsableToken(token) &&
            manager(context).activeNotifications.any {
                it.id == notificationId && it.notification.extras.getString(EXTRA_TOKEN) == token
            }

    fun revoke(token: String) {
        revokedTokens.add(token)
    }

    fun invalidate(
        context: Context,
        scheduleId: String,
    ) = removeMatching(context) { it == scheduleId }

    fun retainSchedules(
        context: Context,
        scheduleIds: Set<String>,
    ) = removeMatching(context) { it !in scheduleIds }

    fun clear(context: Context) = removeMatching(context) { true }

    private fun removeMatching(
        context: Context,
        predicate: (String) -> Boolean,
    ) {
        val manager = manager(context)
        manager.activeNotifications.forEach { active ->
            val scheduleId = active.notification.extras.getString(EXTRA_SCHEDULE) ?: return@forEach
            if (!predicate(scheduleId)) return@forEach
            active.notification.extras
                .getString(EXTRA_TOKEN)
                ?.let(revokedTokens::add)
            manager.cancel(active.tag, active.id)
            active.notification.actions?.forEach { it.actionIntent?.cancel() }
            active.notification.contentIntent?.cancel()
            active.notification.fullScreenIntent?.cancel()
            if (active.id == RINGING_ID) {
                context.stopService(Intent(context, AlarmTriggerService::class.java))
            }
        }
    }

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
