package com.example.slowclock.ui.alarm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.example.slowclock.core.alarm.R
import com.example.slowclock.core.alarm.SnoozePolicy
import java.text.SimpleDateFormat
import java.util.*

class AlarmFullScreenActivity : Activity() {
    private val timeHandler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    // 알람은 닫기 버튼으로만 끈다. targetSdk 36 부터 predictive back 이 기본이라
    // API 33 이상에서는 onBackPressed 가 불리지 않으므로 여기서 제스처를 삼킨다.
    // https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
    private var backGestureCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금 화면 위에 표시하고 화면을 켠다. minSdk 32 라 API 27 의 창 플래그 경로는 필요 없다.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm_fullscreen)

        val title = intent.getStringExtra("title") ?: "알림"
        val desc = intent.getStringExtra("desc") ?: ""

        findViewById<TextView>(R.id.titleText).text = title
        findViewById<TextView>(R.id.descText).text = desc

        // 현재 시간 표시
        val currentTimeText = findViewById<TextView>(R.id.currentTimeText)
        updateCurrentTime(currentTimeText)

        findViewById<Button>(R.id.dismissButton).setOnClickListener {
            dismissAlarm()
            finish()
        }

        // 다시 알림. 예약도 소리 끄기도 서비스가 한다. 화면은 시키기만 한다(#122 와 같은 자리).
        val snoozeCount = intent.getIntExtra(AlarmTriggerService.EXTRA_SNOOZE_COUNT, 0)
        val snoozeButton = findViewById<Button>(R.id.snoozeButton)
        if (SnoozePolicy.canSnooze(snoozeCount)) {
            // 라벨의 분과 실제 미루는 분이 어긋날 수 없게 상수에서 만든다.
            snoozeButton.text = getString(R.string.alarm_snooze_action, SnoozePolicy.MINUTES)
            snoozeButton.setOnClickListener {
                startService(AlarmTriggerService.snoozeIntent(this))
                finish()
            }
        } else {
            // 다 쓴 뒤 눌리지 않는 버튼을 남기면 이 이슈가 그대로 되풀이된다. 아예 감춘다(#129).
            snoozeButton.visibility = View.GONE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            backGestureCallback = callback
        }
    }

    private fun updateCurrentTime(timeTextView: TextView) {
        timeRunnable =
            object : Runnable {
                override fun run() {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    timeTextView.text = sdf.format(Date())
                    timeHandler.postDelayed(this, 1000)
                }
            }
        timeHandler.post(timeRunnable)
    }

    /** 소리와 진동은 서비스가 낸다. 여기서는 끄라고만 알린다(#122). */
    private fun dismissAlarm() {
        startService(AlarmTriggerService.dismissIntent(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backGestureCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backGestureCallback = null
        }
    }

    // API 32 기기용. 33 이상은 위의 OnBackInvokedCallback 이 대신 막는다.
    @Deprecated("API 33 미만 기기 전용", ReplaceWith(""))
    override fun onBackPressed() {
        // 사용자가 명시적으로 닫기 버튼을 눌러야 함
    }
}
