package com.example.slowclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.slowclock.ui.alarm.AlarmTriggerService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val title = intent.getStringExtra("title") ?: "Schedule Reminder"
        val desc = intent.getStringExtra("desc") ?: ""
        val isFullScreen = intent.getBooleanExtra("isFullScreen", false)

        val serviceIntent =
            Intent(context, AlarmTriggerService::class.java).apply {
                putExtra("title", title)
                putExtra("desc", desc)
                putExtra("isFullScreen", isFullScreen)
            }

        Log.d("AlarmReceiver", "🔔 알람 수신됨 (풀스크린: $isFullScreen), 서비스 실행: $title / $desc")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "서비스 시작 실패: ${e.message}")
        }
    }
}
