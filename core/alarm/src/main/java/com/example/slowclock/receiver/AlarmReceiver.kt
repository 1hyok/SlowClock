package com.example.slowclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.slowclock.ui.alarm.AlarmTriggerService

/**
 * 예약된 시각에 시스템이 부르는 자리. 실제로 울리는 일은 [AlarmTriggerService] 가 한다.
 *
 * 브로드캐스트 수신기는 몇 초 안에 끝나야 하므로 여기서 소리를 내지 않는다. 정시 알람이
 * 깨운 직후에는 백그라운드에서도 포그라운드 서비스를 시작할 수 있다.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val title = intent.getStringExtra("title") ?: "알람"
        val desc = intent.getStringExtra("desc").orEmpty()
        val isFullScreen = intent.getBooleanExtra("isFullScreen", true)

        Log.d("AlarmReceiver", "알람 수신: $title")

        try {
            ContextCompat.startForegroundService(
                context,
                AlarmTriggerService.ringIntent(context, title, desc, isFullScreen),
            )
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "알람 서비스 시작 실패: ${e.message}")
        }
    }
}
