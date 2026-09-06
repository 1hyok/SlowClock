package com.example.slowclock.ui.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.slowclock.core.alarm.AlarmNotifications
import com.example.slowclock.core.alarm.AlarmSchedulerEntryPoint
import com.example.slowclock.core.alarm.R
import com.example.slowclock.core.alarm.ScheduleAlarmHelper
import com.example.slowclock.core.alarm.SnoozePolicy
import com.example.slowclock.core.alarm.canUseFullScreenAlarm
import java.util.UUID

/**
 * 알람을 실제로 울리는 서비스.
 *
 * 소리와 진동을 이 서비스가 직접 낸다. 종전에는 전체 화면 액티비티가 소리를 냈는데, Android 14
 * 부터 전체 화면 알림 권한이 기본으로 없고 권한이 있어도 화면이 켜져 잠금이 풀린 상태에서는
 * 그 액티비티가 뜨지 않는다. 그래서 소리를 액티비티에 두면 구조적으로 무음이 된다(#122).
 *
 * 서비스는 사용자가 끄기 전까지 살아 있는다. 종전에는 1초 뒤 스스로 죽었고, 포그라운드 서비스
 * 알림은 최대 10초까지 표시가 미뤄질 수 있어 알림이 화면에 뜨기도 전에 사라졌다. 그래서
 * 소리도 진동도 헤드업도 아무것도 없었다.
 */
class AlarmTriggerService : Service() {
    companion object {
        /** 알람 하나를 울린다. 제목·설명·전체화면 여부를 extras 로 받는다. */
        const val ACTION_RING = "com.example.slowclock.action.RING_ALARM"

        /** 울리는 알람을 끈다. 알림의 끄기 버튼과 전체 화면의 닫기 버튼이 보낸다. */
        const val ACTION_DISMISS = "com.example.slowclock.action.DISMISS_ALARM"

        /** 울리는 알람을 지금 멈추고 몇 분 뒤에 다시 울리게 한다(#129). */
        const val ACTION_SNOOZE = "com.example.slowclock.action.SNOOZE_RINGING"

        /** 겹쳐서 밀려난 알람을 미룬다. 지금 울리는 알람과 무관하다(#167). */
        const val ACTION_SNOOZE_MISSED = "com.example.slowclock.action.SNOOZE_MISSED"

        const val EXTRA_TITLE = "title"
        const val EXTRA_DESC = "desc"
        const val EXTRA_FULL_SCREEN = "isFullScreen"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_REQUEST_CODE = "requestCode"
        const val EXTRA_SNOOZE_COUNT = "snoozeCount"

        private const val TAG = "AlarmTriggerService"
        private const val NOTIFICATION_ID = AlarmNotifications.RINGING_ID

        // 채널은 한 번 만들면 앱이 소리·중요도를 다시 못 바꾼다. 종전 채널은 오디오 속성이 없어
        // 알람이 아니라 알림 소리로 취급됐고, 그 채널이 남은 기기에서는 코드를 고쳐도 그대로다.
        // 그래서 새 id 를 쓴다(#122).
        private const val CHANNEL_ID = AlarmNotifications.CHANNEL_ID

        /** 겹친 알람을 옮겨 두는 채널. 울리지 않고 「이 일정이 지나갔다」 만 보여 준다(#131). */
        private const val MISSED_CHANNEL_ID = "alarm_overlapped_v1"

        /** 겹친 알람의 알림 자리. 알람 자리 번호를 더해 서로 덮지 않게 한다. */
        private const val MISSED_NOTIFICATION_ID_BASE = 1000

        /** 자리 번호가 실려 오지 않은 요청. 옛 판에서 걸린 알림의 버튼이다. */
        private const val UNKNOWN_REQUEST_CODE = Int.MIN_VALUE

        /** 밀려난 알람의 다시 알림 자리. 울리는 쪽(0·1·2)과 겹치지 않게 띄워 둔다. */
        private const val MISSED_SNOOZE_REQUEST_BASE = 10_000

        /** 종전 채널. 오디오 속성이 없어 알람 소리로 취급되지 않았다. 남아 있으면 지운다. */
        private const val LEGACY_CHANNEL_ID = "alarm_notification_channel"

        /** 아무도 끄지 않아도 이만큼 지나면 멈춘다. 배터리를 계속 태우지 않기 위해서다. */
        private const val MAX_RINGING_MILLIS = 5 * 60 * 1000L

        /** 끊었다 이었다 하는 진동. 0 은 시작까지의 대기다. */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600)

        /**
         * 이 서비스에 알람을 울리라고 시킨다.
         *
         * 다시 알림을 걸려면 어느 자리의 알람인지 알아야 한다. 그래서 제목·설명뿐 아니라
         * 일정 id·자리 번호·지금까지 미룬 횟수도 함께 넘긴다(#129).
         */
        fun ringIntent(
            context: Context,
            title: String,
            desc: String,
            isFullScreen: Boolean,
            scheduleId: String,
            requestCode: Int,
            snoozeCount: Int,
        ): Intent =
            Intent(context, AlarmTriggerService::class.java)
                .setClass(context, AlarmTriggerService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DESC, desc)
                .putExtra(EXTRA_FULL_SCREEN, isFullScreen)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)

        /** 울리는 알람을 끈다. */
        fun dismissIntent(
            context: Context,
            requestCode: Int,
            token: String? = null,
        ): Intent =
            Intent(context, AlarmTriggerService::class.java)
                .setClass(context, AlarmTriggerService::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(AlarmNotifications.EXTRA_TOKEN, token)

        /**
         * 서비스가 알람 울리기를 끝냈다. 전체 화면이 떠 있으면 이 방송을 받아 함께 닫는다.
         *
         * 앱 안에서만 오가는 방송이라 패키지를 못박아 보낸다.
         */
        const val ACTION_RINGING_FINISHED = "com.example.slowclock.action.ALARM_RINGING_FINISHED"

        /** 울리는 알람을 미룬다. 다시 걸 시각과 횟수 상한은 [SnoozePolicy] 가 정한다. */
        fun snoozeIntent(
            context: Context,
            requestCode: Int,
            token: String? = null,
        ): Intent =
            Intent(context, AlarmTriggerService::class.java)
                .setClass(context, AlarmTriggerService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(AlarmNotifications.EXTRA_TOKEN, token)
    }

    /**
     * 지금 울리고 있는 알람. 다시 알림을 걸려면 이 값이 필요하다.
     *
     * 알림의 다시 알림 버튼은 서비스로 action 만 보낸다. 무엇을 미룰지는 서비스가 이미 알고
     * 있으므로 PendingIntent 에 일정 정보를 싣지 않는다.
     */
    internal data class Ringing(
        val title: String,
        val desc: String,
        val isFullScreen: Boolean,
        val scheduleId: String,
        val requestCode: Int,
        val snoozeCount: Int,
        val token: String = UUID.randomUUID().toString(),
    )

    private var ringing: Ringing? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopRinging("시간 초과") }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_DISMISS) {
            if (isForCurrentRinging(intent)) stopRinging("사용자가 껐다")
            if (ringing == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SNOOZE) {
            if (isForCurrentRinging(intent)) snoozeRinging()
            if (ringing == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SNOOZE_MISSED) {
            snoozeMissed(intent)
            if (ringing == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_RING) {
            if (ringing == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        // 알림을 거부하면 FGS 승격은 가능하지만 끄기 버튼은 보이지 않는다.
        // 사용자가 제어할 수 없는 소리·진동은 시작하지 않는다.
        if (!AlarmNotifications.canShowControls(this)) {
            stopRinging("알림 조작을 표시할 수 없다")
            return START_NOT_STICKY
        }

        // 이미 울리는 알람이 있으면 그 알림을 별도 자리로 옮겨 남긴다. 알림 자리가 하나뿐이라
        // 그냥 두면 뒤에 온 알람이 앞의 것을 화면에서 지우고, 끄기 한 번에 둘 다 꺼진다(#131).
        ringing?.let { previous -> keepAsSeparateNotification(previous) }

        val current =
            Ringing(
                title = intent.getStringExtra(EXTRA_TITLE) ?: "알람",
                desc = intent.getStringExtra(EXTRA_DESC).orEmpty(),
                isFullScreen = intent.getBooleanExtra(EXTRA_FULL_SCREEN, true),
                scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID).orEmpty(),
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
                snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0),
            )
        ringing = current

        Log.d(TAG, "알람 울림 시작: ${current.title} (미룬 횟수 ${current.snoozeCount})")

        // 포그라운드로 먼저 올린다. 알림을 띄우기 전에 소리를 내면 시스템이 서비스를 죽일 수 있다.
        try {
            startForegroundCompat(buildNotification(current))
            acquireWakeLock()
            startSound()
            startVibration()
        } catch (error: Exception) {
            Log.e(TAG, "알람을 시작하지 못했다", error)
            stopRinging("알람 시작 실패")
            return START_NOT_STICKY
        }

        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, MAX_RINGING_MILLIS)

        return START_NOT_STICKY
    }

    /**
     * 앞서 울리던 알람을 별도 알림으로 옮긴다.
     *
     * 포그라운드 서비스 알림 자리는 하나뿐이라, 겹친 알람을 그대로 두면 뒤에 온 것이 앞의 것을
     * 덮어써 사용자에게는 「알람이 하나 안 울렸다」 로만 보인다. 소리와 진동은 어차피 한 벌이면
     * 충분하므로 옮긴 알림은 조용하고, 어떤 일정이 지나갔는지 보여 주는 몫만 한다(#131).
     */

    private fun keepAsSeparateNotification(previous: Ringing) {
        val builder =
            NotificationCompat
                .Builder(this, MISSED_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setContentTitle(previous.title)
                .setContentText(previous.desc.ifBlank { "지금 할 시간입니다" })
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .addExtras(commandExtras(previous))
                .setContentIntent(openAppIntent())

        // 밀려난 알람에도 다시 알림을 남긴다. 없으면 그 일정만 다시 울릴 기회가 아예 없어,
        // 겹친 순간에 뒤에 온 알람이 앞의 것을 통째로 삼킨 셈이 된다(#167).
        if (SnoozePolicy.canSnooze(previous.snoozeCount)) {
            builder.addAction(
                R.drawable.baseline_access_alarm_24,
                getString(R.string.alarm_snooze_action, SnoozePolicy.MINUTES),
                PendingIntent.getService(
                    this,
                    // 자리 번호로 갈라 두어야 겹친 알람마다 자기 것을 미룬다.
                    MISSED_SNOOZE_REQUEST_BASE + previous.requestCode,
                    snoozeMissedIntent(previous),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val notification = builder.build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 자리 번호로 갈라 두어야 두 알람이 서로 덮지 않는다.
        runCatching { manager.notify(MISSED_NOTIFICATION_ID_BASE + previous.requestCode, notification) }
    }

    /**
     * 겹쳐서 밀려난 알람을 미루라는 요청. 서비스가 그 알람을 더 이상 들고 있지 않으므로
     * 필요한 값을 전부 실어 보낸다(#167).
     *
     * [Ringing] 이 이 클래스 안에만 있는 타입이라 companion 이 아니라 여기 둔다.
     */
    private fun snoozeMissedIntent(missed: Ringing): Intent =
        Intent(this, AlarmTriggerService::class.java)
            .setClass(this, AlarmTriggerService::class.java)
            .setAction(ACTION_SNOOZE_MISSED)
            .putExtra(EXTRA_TITLE, missed.title)
            .putExtra(EXTRA_DESC, missed.desc)
            .putExtra(EXTRA_FULL_SCREEN, missed.isFullScreen)
            .putExtra(EXTRA_SCHEDULE_ID, missed.scheduleId)
            .putExtra(EXTRA_REQUEST_CODE, missed.requestCode)
            .putExtra(EXTRA_SNOOZE_COUNT, missed.snoozeCount)
            .putExtra(AlarmNotifications.EXTRA_TOKEN, missed.token)

    private fun startForegroundCompat(notification: Notification) {
        // 알람이 울리는 동안 소리를 재생하므로 미디어 재생 타입이다. 종전의 shortService 는
        // 시간 제한이 있고 배경 오디오의 문턱 아래라 알람에 맞지 않는다(#122).
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(current: Ringing): Notification {
        val title = current.title
        val desc = current.desc
        val dismissPendingIntent =
            PendingIntent.getService(
                this,
                0,
                dismissIntent(this, current.requestCode, current.token),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setContentTitle(title)
                .setContentText(desc.ifBlank { "지금 할 시간입니다" })
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addExtras(commandExtras(current))
                .setOngoing(true)
                .setAutoCancel(false)
                // 포그라운드 서비스 알림은 기본적으로 표시가 최대 10초 미뤄진다. 알람에서는
                // 그 사이에 아무것도 안 보이므로 즉시 띄우게 한다(#122).
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(
                    R.drawable.baseline_close_24,
                    getString(R.string.alarm_dismiss_action),
                    dismissPendingIntent,
                ).setDeleteIntent(dismissPendingIntent)

        // 다시 알림은 알림에도 둔다. 전체 화면 권한이 없거나 화면이 켜져 잠금이 풀려 있으면
        // 액티비티가 아예 뜨지 않고 이 알림만 남는다. 그때 다시 알림이 사라지면 안 된다(#122 · #129).
        if (SnoozePolicy.canSnooze(current.snoozeCount)) {
            builder.addAction(
                R.drawable.baseline_access_alarm_24,
                getString(R.string.alarm_snooze_action, SnoozePolicy.MINUTES),
                // 자리 2. 끄기는 0, 전체 화면은 1 을 쓴다. action 도 달라 서로 덮지 않는다.
                PendingIntent.getService(
                    this,
                    2,
                    snoozeIntent(this, current.requestCode, current.token),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        // 전체 화면은 권한이 있을 때만 붙인다. 없으면 시스템이 헤드업으로 내리는데, 그 경우에도
        // 소리와 진동은 이 서비스가 내므로 알람 구실은 한다.
        if (current.isFullScreen && canUseFullScreen()) {
            val fullScreenIntent =
                Intent(this, AlarmFullScreenActivity::class.java)
                    .setClass(this, AlarmFullScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_DESC, desc)
                    .putExtra(EXTRA_SNOOZE_COUNT, current.snoozeCount)
                    // 화면이 되돌려 줄 신원. 겹친 알람에서 조작 대상이 어긋나지 않게 한다(#167).
                    .putExtra(EXTRA_REQUEST_CODE, current.requestCode)
                    .putExtra(AlarmNotifications.EXTRA_TOKEN, current.token)
            builder.setFullScreenIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
                true,
            )
        }

        return builder.build()
    }

    // 같은 질문의 답이 서비스와 정보 화면에서 갈리지 않도록 한 함수를 본다(#128).
    private fun canUseFullScreen(): Boolean = canUseFullScreenAlarm()

    private fun startSound() {
        stopSound()
        // 알람 볼륨이 0 이면 소리를 포기하고 진동만 남긴다. 무음으로 두면 재생 자원만 쓴다.
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            Log.w(TAG, "알람 볼륨이 0 이라 진동만 울린다")
            return
        }
        val uri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.apply {
                // 알람 스트림으로 재생한다. 이것을 지정하지 않으면 미디어 볼륨을 타서
                // 미디어 소리를 줄여 둔 기기에서는 울려도 들리지 않는다(#122).
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmTriggerService, uri)
                isLooping = true
                setOnPreparedListener { ready ->
                    if (mediaPlayer === ready && ringing != null) ready.start() else ready.release()
                }
                setOnErrorListener { failed, _, _ ->
                    if (mediaPlayer === failed) mediaPlayer = null
                    failed.release()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            stopSound()
            Log.e(TAG, "알람 소리 재생 실패: ${e.message}")
        }
    }

    private fun startVibration() {
        val service =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        vibrator = service
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        // 두 번째 인자 0 은 패턴을 처음부터 반복하라는 뜻이다.
        service.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), attributes)
    }

    /**
     * 화면이 꺼져 있어도 소리를 이어 가도록 CPU 를 깨워 둔다.
     *
     * 이미 잡고 있으면 먼저 놓는다. 알람이 겹쳐 두 번째로 들어올 때 그냥 덮어쓰면 앞의 잠금은
     * 참조를 잃어 아무도 놓지 못하고, 타임아웃까지 CPU 를 깨워 둔 채 남는다(#131).
     */
    private fun acquireWakeLock() {
        releaseWakeLock()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            power
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlowClock:alarm")
                .apply { acquire(MAX_RINGING_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    /**
     * 이 요청이 지금 울리는 알람에 대한 것인가.
     *
     * 알람이 겹치면 뒤에 온 것이 ringing 을 덮는다. 그런데 화면은 잠금이 풀린 상태에서 전체
     * 화면이 안 뜨면 onNewIntent 를 못 받아 앞 알람 제목을 그대로 보여 준다. 그 상태에서 신원
     * 없는 요청을 그대로 받으면, 사용자가 화면에서 본 일정이 아니라 마지막에 도착한 알람이
     * 미뤄지거나 꺼진다(#167).
     *
     * 회차 토큰이 없는 옛 요청은 현재 알람을 조작하지 못한다.
     */
    private fun isForCurrentRinging(intent: Intent): Boolean {
        val current = ringing ?: return false
        val requested = intent.getIntExtra(EXTRA_REQUEST_CODE, UNKNOWN_REQUEST_CODE)
        val token = intent.getStringExtra(AlarmNotifications.EXTRA_TOKEN)
        if (requested == current.requestCode && token == current.token && AlarmNotifications.isUsableToken(token)) {
            // 스와이프의 deleteIntent는 OS가 알림을 지운 뒤 도착한다. 끄기는 토큰만 확인한다.
            if (intent.action == ACTION_DISMISS || AlarmNotifications.isCurrent(this, NOTIFICATION_ID, token)) return true
        }
        Log.w(TAG, "지금 울리는 알람이 아니라 무시한다: 요청=$requested 현재=${current.requestCode}")
        return false
    }

    /**
     * 겹쳐서 밀려난 알람을 미룬다. 지금 울리는 알람은 건드리지 않는다.
     *
     * 이 요청은 조용한 「겹친 알람」 알림에서 온다. 서비스가 그 알람을 더 이상 들고 있지 않으므로
     * 필요한 값이 요청에 전부 실려 온다(#167).
     */
    private fun snoozeMissed(intent: Intent) {
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        if (!SnoozePolicy.canSnooze(snoozeCount)) return
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        if (!AlarmNotifications.isCurrent(
                this,
                MISSED_NOTIFICATION_ID_BASE + requestCode,
                intent.getStringExtra(AlarmNotifications.EXTRA_TOKEN),
            )
        ) {
            return
        }
        val scheduled =
            snoozeThrough(
                baseRequestCode = requestCode,
                scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE) ?: "알람",
                desc = intent.getStringExtra(EXTRA_DESC).orEmpty(),
                isFullScreen = intent.getBooleanExtra(EXTRA_FULL_SCREEN, true),
                snoozeCount = snoozeCount + 1,
                notificationId = MISSED_NOTIFICATION_ID_BASE + requestCode,
                token = intent.getStringExtra(AlarmNotifications.EXTRA_TOKEN).orEmpty(),
            )
        if (scheduled) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(MISSED_NOTIFICATION_ID_BASE + requestCode)
        }
    }

    /**
     * 울리는 알람을 미룬다. 다시 걸고 나서 지금 울리는 것을 멈춘다.
     *
     * 미룰 수 없는 상태(횟수를 다 썼거나 울리는 알람 정보가 없다)면 미루지 않고 끄기만 한다.
     * 화면과 알림이 이미 남은 횟수를 보고 버튼을 감추므로 여기까지 오는 일은 드물지만,
     * 서비스가 스스로 멈춘 뒤 늦게 도착한 요청이 조용히 알람을 되살리지 않게 막는다.
     */
    private fun snoozeRinging() {
        val current = ringing
        if (current == null || !SnoozePolicy.canSnooze(current.snoozeCount)) {
            Log.w(TAG, "다시 알림을 걸 상태가 아니다: $current")
            stopRinging("다시 알림을 걸 수 없다")
            return
        }
        val scheduled =
            snoozeThrough(
                baseRequestCode = current.requestCode,
                scheduleId = current.scheduleId,
                title = current.title,
                desc = current.desc,
                isFullScreen = current.isFullScreen,
                snoozeCount = current.snoozeCount + 1,
                notificationId = NOTIFICATION_ID,
                token = current.token,
            )
        if (scheduled) stopRinging("다시 알림")
    }

    /**
     * 다시 알림을 [AlarmScheduler] 를 거쳐 건다.
     *
     * 여기서 [ScheduleAlarmHelper] 를 바로 부르면 예약만 되고 기기 안 장부에는 아무것도 남지
     * 않는다. 그러면 재부팅이나 앱 교체로 미뤄 둔 알람이 조용히 사라진다(#177).
     *
     * 예약 실패 때 현재 울림을 유지한다. 성공을 확인하기 전에는 조작 알림도 지우지 않는다.
     */
    private fun snoozeThrough(
        baseRequestCode: Int,
        scheduleId: String,
        title: String,
        desc: String,
        isFullScreen: Boolean,
        snoozeCount: Int,
        notificationId: Int,
        token: String,
    ): Boolean =
        try {
            AlarmSchedulerEntryPoint.from(this).snoozeFromNotification(
                baseRequestCode = baseRequestCode,
                scheduleId = scheduleId,
                title = title,
                desc = desc,
                isFullScreen = isFullScreen,
                snoozeCount = snoozeCount,
                notificationId = notificationId,
                token = token,
            )
        } catch (failure: Exception) {
            Log.e(TAG, "다시 알림 예약 실패, 현재 알람을 유지한다", failure)
            android.widget.Toast
                .makeText(this, R.string.alarm_snooze_failed, android.widget.Toast.LENGTH_LONG)
                .show()
            false
        }

    private fun commandExtras(current: Ringing) =
        Bundle().apply {
            putString(AlarmNotifications.EXTRA_TOKEN, current.token)
            putString(AlarmNotifications.EXTRA_SCHEDULE, current.scheduleId)
        }

    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 3, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    private fun stopRinging(reason: String) {
        Log.d(TAG, "알람 울림 종료: $reason")
        ringing?.token?.let(AlarmNotifications::revoke)
        ringing = null
        stopHandler.removeCallbacks(stopRunnable)
        stopSound()
        vibrator?.cancel()
        vibrator = null
        releaseWakeLock()
        // 화면이 아직 떠 있으면 함께 닫는다. 서비스가 멈춰도 액티비티는 스스로 끝나지 않아
        // 화면이 켜진 채 남고 1초 타이머가 아침까지 돈다(#131).
        sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSound() {
        val player = mediaPlayer
        mediaPlayer = null
        player?.runCatching { release() }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 채널은 소리도 진동도 내지 않는다. 둘 다 이 서비스가 알람 용도로 직접 내므로,
        // 채널까지 울리면 소리가 두 겹으로 겹치고 채널 쪽은 알람 볼륨을 타지도 않는다(#122).
        val channel =
            NotificationChannel(CHANNEL_ID, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "정해 둔 시각에 울리는 알람"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(true)
                setBypassDnd(true)
                setSound(null, null)
            }
        manager.createNotificationChannel(channel)

        // 겹친 알람을 옮겨 두는 자리. 소리와 진동은 울리는 쪽 한 벌이면 충분하므로 조용하다(#131).
        manager.createNotificationChannel(
            NotificationChannel(MISSED_CHANNEL_ID, "겹친 알람", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "같은 시각에 알람이 둘 이상 겹쳤을 때 남는 알림"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            },
        )

        // 옛 채널은 오디오 속성 없이 만들어져 알람이 아니라 알림 소리로 취급됐다. 채널은 만든 뒤
        // 고칠 수 없으므로 새 id 로 갈아탔고, 남은 것은 사용자 설정 화면에서 지운다.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHandler.removeCallbacks(stopRunnable)
        stopSound()
        vibrator?.cancel()
        releaseWakeLock()
        ringing = null
        sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))
        Log.d(TAG, "알람 서비스 종료")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
