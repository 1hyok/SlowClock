package com.example.slowclock.ui.alarm

import android.app.Activity
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.slowclock.core.alarm.R
import java.text.SimpleDateFormat
import java.util.*

class AlarmFullScreenActivity : Activity() {

    private var mediaPlayer: MediaPlayer? = null
    private val timeHandler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금 화면 위에 표시 + 화면 켜기
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_alarm_fullscreen)

        val title = intent.getStringExtra("title") ?: "알림"
        val desc = intent.getStringExtra("desc") ?: ""

        findViewById<TextView>(R.id.titleText).text = title
        findViewById<TextView>(R.id.descText).text = desc

        // 현재 시간 표시
        val currentTimeText = findViewById<TextView>(R.id.currentTimeText)
        updateCurrentTime(currentTimeText)

        findViewById<Button>(R.id.dismissButton).setOnClickListener {
            stopAlarmSound()
            finish()
        }

        // 알람 소리 재생
        playAlarmSound()
    }

    private fun updateCurrentTime(timeTextView: TextView) {
        timeRunnable = object : Runnable {
            override fun run() {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                timeTextView.text = sdf.format(Date())
                timeHandler.postDelayed(this, 1000)
            }
        }
        timeHandler.post(timeRunnable)
    }

    private fun playAlarmSound() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmFullScreenActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun onBackPressed() {
        // 사용자가 명시적으로 닫기 버튼을 눌러야 함
    }
}
