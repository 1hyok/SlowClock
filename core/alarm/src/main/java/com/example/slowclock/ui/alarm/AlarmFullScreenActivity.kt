package com.example.slowclock.ui.alarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import com.example.slowclock.core.alarm.R
import com.example.slowclock.core.alarm.SnoozePolicy
import java.text.SimpleDateFormat
import java.util.*

class AlarmFullScreenActivity : Activity() {
    private val timeHandler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    /**
     * 서비스가 알람 울리기를 끝냈다는 신호. 받으면 이 화면도 닫는다.
     *
     * 종전에는 이 화면을 끝내는 길이 닫기 버튼 하나뿐이었다. 서비스가 5분 뒤 스스로 멈춰도
     * 화면은 그대로 남아, `FLAG_KEEP_SCREEN_ON` 때문에 아침까지 켜진 채 1초마다 시계를 다시
     * 그렸다. 뒤로가기는 막혀 있고 최근 앱에도 없어 홈 버튼 말고는 치울 방법이 없었다(#131).
     */
    private val ringingFinishedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                finish()
            }
        }

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

        bindSnoozeButton(intent.getIntExtra(AlarmTriggerService.EXTRA_SNOOZE_COUNT, 0))

        ContextCompat.registerReceiver(
            this,
            ringingFinishedReceiver,
            IntentFilter(AlarmTriggerService.ACTION_RINGING_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            backGestureCallback = callback
        }
    }

    /**
     * 이 액티비티는 `singleInstance` 라 이미 떠 있으면 새 알람이 여기로 온다.
     *
     * 재정의하지 않으면 `intent` 가 옛 값 그대로라 화면에는 앞 알람의 제목이 남는다. 어르신은
     * 무슨 일정인지 잘못 알게 된다(#131).
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        findViewById<TextView>(R.id.titleText).text = intent.getStringExtra("title") ?: "알림"
        findViewById<TextView>(R.id.descText).text = intent.getStringExtra("desc") ?: ""
        bindSnoozeButton(intent.getIntExtra(AlarmTriggerService.EXTRA_SNOOZE_COUNT, 0))
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

    /** 다시 알림. 예약도 소리 끄기도 서비스가 한다. 화면은 시키기만 한다(#122 와 같은 자리). */
    private fun bindSnoozeButton(snoozeCount: Int) {
        val snoozeButton = findViewById<Button>(R.id.snoozeButton)
        if (SnoozePolicy.canSnooze(snoozeCount)) {
            // 라벨의 분과 실제 미루는 분이 어긋날 수 없게 상수에서 만든다.
            snoozeButton.text = getString(R.string.alarm_snooze_action, SnoozePolicy.MINUTES)
            snoozeButton.visibility = View.VISIBLE
            snoozeButton.setOnClickListener {
                startService(AlarmTriggerService.snoozeIntent(this, ringingRequestCode()))
                finish()
            }
        } else {
            // 다 쓴 뒤 눌리지 않는 버튼을 남기면 #129 가 그대로 되풀이된다. 아예 감춘다.
            snoozeButton.visibility = View.GONE
        }
    }

    /**
     * 이 화면이 지금 보여 주는 알람의 자리 번호.
     *
     * 알람이 겹쳤을 때 화면에 보이는 일정과 서비스가 들고 있는 일정이 어긋날 수 있다. 화면이
     * 자기가 받은 값을 그대로 되돌려 주면 서비스가 그 어긋남을 알아챈다(#167).
     */
    private fun ringingRequestCode(): Int = intent.getIntExtra(AlarmTriggerService.EXTRA_REQUEST_CODE, 0)

    /** 소리와 진동은 서비스가 낸다. 여기서는 끄라고만 알린다(#122). */
    private fun dismissAlarm() {
        startService(AlarmTriggerService.dismissIntent(this, ringingRequestCode()))
    }

    /** 화면이 안 보이면 시계를 멈춘다. 보이지 않는 화면을 1초마다 다시 그릴 이유가 없다(#131). */
    override fun onStop() {
        super.onStop()
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun onRestart() {
        super.onRestart()
        timeHandler.post(timeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable)
        runCatching { unregisterReceiver(ringingFinishedReceiver) }
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
