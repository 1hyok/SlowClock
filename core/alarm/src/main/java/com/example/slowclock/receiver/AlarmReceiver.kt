package com.example.slowclock.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.slowclock.core.alarm.R
import com.example.slowclock.ui.alarm.AlarmTriggerService

/**
 * 예약된 시각에 시스템이 부르는 자리. 실제로 울리는 일은 [AlarmTriggerService] 가 한다.
 *
 * 브로드캐스트 수신기는 몇 초 안에 끝나야 하므로 여기서 소리를 내지 않는다. 정시 알람이
 * 깨운 직후에는 백그라운드에서도 포그라운드 서비스를 시작할 수 있다.
 */
class AlarmReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "AlarmReceiver"

        /** 서비스를 못 띄웠을 때만 쓰는 자리. 소리는 이 채널이 낸다. */
        const val FALLBACK_CHANNEL_ID = "alarm_fallback_v1"
        const val FALLBACK_NOTIFICATION_ID = 124
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val title = intent.getStringExtra("title") ?: "알람"
        val desc = intent.getStringExtra("desc").orEmpty()
        val isFullScreen = intent.getBooleanExtra("isFullScreen", true)

        Log.d(TAG, "알람 수신: $title")

        try {
            ContextCompat.startForegroundService(
                context,
                AlarmTriggerService.ringIntent(context, title, desc, isFullScreen),
            )
        } catch (e: Exception) {
            // 정시 알람 권한이 없어 부정확 알람으로 깨어난 경우에는 백그라운드에서 포그라운드
            // 서비스를 시작하지 못할 수 있다. 그때도 알람이 통째로 사라지지는 않게, 소리 나는
            // 알림이라도 남긴다(#122).
            Log.e(TAG, "알람 서비스 시작 실패, 알림으로 대신한다: ${e.message}")
            notifyWithoutService(context, title, desc)
        }
    }

    private fun notifyWithoutService(
        context: Context,
        title: String,
        desc: String,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                FALLBACK_CHANNEL_ID,
                "알람 (대체)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "알람을 울리지 못했을 때 대신 나가는 알림"
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        manager.createNotificationChannel(channel)

        val notification =
            NotificationCompat
                .Builder(context, FALLBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setContentTitle(title)
                .setContentText(desc.ifBlank { "지금 할 시간입니다" })
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()

        runCatching { manager.notify(FALLBACK_NOTIFICATION_ID, notification) }
    }
}
