package com.example.slowclock.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.slowclock.core.alarm.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title ?: "일정 알림"
        val body = remoteMessage.notification?.body ?: "새로운 알림이 있습니다."
        android.util.Log.d("FCMService", "onMessageReceived: title=$title, body=$body, data=${remoteMessage.data}")
        sendNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Firestore에 FCM 토큰 저장
        val user =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser
        user?.let {
            val db =
                com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
            db
                .collection("users")
                .document(it.uid)
                .update("fcmToken", token)
                .addOnSuccessListener { android.util.Log.d("FCM", "토큰 Firestore 저장 성공") }
                .addOnFailureListener { e -> android.util.Log.e("FCM", "토큰 Firestore 저장 실패", e) }
        }
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
    ) {
        val intent =
            Intent().setClassName(this, "com.example.slowclock.MainActivity").apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val channelId = "schedule_channel"
        val notificationBuilder =
            NotificationCompat
                .Builder(this, channelId)
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        // 여기서 Context.NOTIFICATION_SERVICE 대신 NOTIFICATION_SERVICE만 써보기
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(
                channelId,
                "일정 알림",
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(0, notificationBuilder.build())
    }
}
