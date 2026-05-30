package com.example.slowclock.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmDismissReceiver", "알림 닫기 버튼 클릭됨")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll() // 모든 알림 제거

        // 또는 특정 알림만 제거하려면:
        // val notificationId = intent.getIntExtra("notificationId", -1)
        // if (notificationId != -1) {
        //     notificationManager.cancel(notificationId)
        // }
    }
}