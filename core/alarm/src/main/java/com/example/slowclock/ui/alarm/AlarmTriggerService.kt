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
import com.example.slowclock.core.alarm.R

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

        const val EXTRA_TITLE = "title"
        const val EXTRA_DESC = "desc"
        const val EXTRA_FULL_SCREEN = "isFullScreen"

        private const val TAG = "AlarmTriggerService"
        private const val NOTIFICATION_ID = 123

        // 채널은 한 번 만들면 앱이 소리·중요도를 다시 못 바꾼다. 종전 채널은 오디오 속성이 없어
        // 알람이 아니라 알림 소리로 취급됐고, 그 채널이 남은 기기에서는 코드를 고쳐도 그대로다.
        // 그래서 새 id 를 쓴다(#122).
        private const val CHANNEL_ID = "alarm_ringing_v2"

        /** 종전 채널. 오디오 속성이 없어 알람 소리로 취급되지 않았다. 남아 있으면 지운다. */
        private const val LEGACY_CHANNEL_ID = "alarm_notification_channel"

        /** 아무도 끄지 않아도 이만큼 지나면 멈춘다. 배터리를 계속 태우지 않기 위해서다. */
        private const val MAX_RINGING_MILLIS = 5 * 60 * 1000L

        /** 끊었다 이었다 하는 진동. 0 은 시작까지의 대기다. */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600)

        /** 이 서비스에 알람을 울리라고 시킨다. */
        fun ringIntent(
            context: Context,
            title: String,
            desc: String,
            isFullScreen: Boolean,
        ): Intent =
            Intent(context, AlarmTriggerService::class.java)
                .setClass(context, AlarmTriggerService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DESC, desc)
                .putExtra(EXTRA_FULL_SCREEN, isFullScreen)

        /** 울리는 알람을 끈다. */
        fun dismissIntent(context: Context): Intent =
            Intent(context, AlarmTriggerService::class.java)
                .setClass(context, AlarmTriggerService::class.java)
                .setAction(ACTION_DISMISS)
    }

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
            stopRinging("사용자가 껐다")
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "알람"
        val desc = intent?.getStringExtra(EXTRA_DESC).orEmpty()
        val wantsFullScreen = intent?.getBooleanExtra(EXTRA_FULL_SCREEN, true) ?: true

        Log.d(TAG, "알람 울림 시작: $title")

        // 포그라운드로 먼저 올린다. 알림을 띄우기 전에 소리를 내면 시스템이 서비스를 죽일 수 있다.
        startForegroundCompat(buildNotification(title, desc, wantsFullScreen))

        acquireWakeLock()
        startSound()
        startVibration()

        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, MAX_RINGING_MILLIS)

        return START_NOT_STICKY
    }

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

    private fun buildNotification(
        title: String,
        desc: String,
        wantsFullScreen: Boolean,
    ): Notification {
        val dismissPendingIntent =
            PendingIntent.getService(
                this,
                0,
                dismissIntent(this),
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
                .setOngoing(true)
                .setAutoCancel(false)
                // 포그라운드 서비스 알림은 기본적으로 표시가 최대 10초 미뤄진다. 알람에서는
                // 그 사이에 아무것도 안 보이므로 즉시 띄우게 한다(#122).
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(R.drawable.baseline_close_24, "알람 끄기", dismissPendingIntent)
                .setDeleteIntent(dismissPendingIntent)

        // 전체 화면은 권한이 있을 때만 붙인다. 없으면 시스템이 헤드업으로 내리는데, 그 경우에도
        // 소리와 진동은 이 서비스가 내므로 알람 구실은 한다.
        if (wantsFullScreen && canUseFullScreen()) {
            val fullScreenIntent =
                Intent(this, AlarmFullScreenActivity::class.java)
                    .setClass(this, AlarmFullScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_DESC, desc)
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

    private fun canUseFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canUseFullScreenIntent()
    }

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
            mediaPlayer =
                MediaPlayer().apply {
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
                    prepare()
                    start()
                }
        } catch (e: Exception) {
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

    private fun acquireWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            power
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlowClock:alarm")
                .apply { acquire(MAX_RINGING_MILLIS) }
    }

    private fun stopRinging(reason: String) {
        Log.d(TAG, "알람 울림 종료: $reason")
        stopHandler.removeCallbacks(stopRunnable)
        stopSound()
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSound() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
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

        // 옛 채널은 오디오 속성 없이 만들어져 알람이 아니라 알림 소리로 취급됐다. 채널은 만든 뒤
        // 고칠 수 없으므로 새 id 로 갈아탔고, 남은 것은 사용자 설정 화면에서 지운다.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHandler.removeCallbacks(stopRunnable)
        stopSound()
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        Log.d(TAG, "알람 서비스 종료")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
