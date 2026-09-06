// MainActivity.kt
package com.example.slowclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.slowclock.auth.AuthManager
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.domain.profile.SignOutUseCase
import com.example.slowclock.navigation.AppNavigation
import com.example.slowclock.ui.theme.SlowClockTheme
import com.firebase.ui.auth.AuthUI
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var signOutUseCase: SignOutUseCase

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    private fun handleNewInstallation() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isAppEverLaunched = prefs.getBoolean("app_launched", false)

        if (!isAppEverLaunched) {
            // 이 앱이 처음 실행됨 = 새 설치
            Log.d("INSTALL", "새 설치 감지 - Firebase 로그아웃")

            // 앱을 지웠다 다시 깐 기기다. 세션뿐 아니라 앞 설치가 남긴 알람 장부와 공유 코드도
            // 함께 비운다. 남기면 목록에 없는 알람이 계속 울린다(#165).
            signOutUseCase()
            AuthUI.getInstance().signOut(this)

            // 플래그 저장
            prefs.edit { putBoolean("app_launched", true) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MAIN", "onCreate 시작")

        handleNewInstallation()
        // API 32–34에서는 강제 종료 해제 시 BOOT_COMPLETED가 오지 않는다.
        // 설치 정리 이후, 네트워크와 독립적으로 프로세스당 한 번만 복원한다.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { alarmScheduler.restoreOnAppStart() }
                .onFailure { Log.e("ALARM", "앱 시작 알람 복원 실패", it) }
        }

        try {
            // AuthManager 초기화
            authManager = AuthManager(this, userRepository, authRepository, scheduleRepository)
            authManager.initialize(
                onSuccess = {
                    Log.d("AUTH", "로그인 성공 콜백")
                    // FCM 토큰을 Firestore에 저장
                    saveFcmTokenToFirestore()
                },
                onError = { error ->
                    Log.e("AUTH", "로그인 실패: $error")
                },
            )

// 로그인 상태 확인
            // MainActivity.kt - onCreate()에서
            val currentUser = this.authManager.getCurrentUser()
            if (currentUser == null) {
                // 취소나 진행 중인 로그인을 화면 회전으로 다시 띄우지 않는다.
                // 복원된 화면에서는 사용자가 로그인 버튼으로 다시 시작할 수 있다.
                if (savedInstanceState == null) {
                    Log.d("AUTH", "로그인 필요 - 구글 로그인 시작")
                    authManager.signInWithGoogle()
                }
            } else {
                Log.d("AUTH", "=== MainActivity에서 Firebase 사용자 정보 ===")
                Log.d("AUTH", "이미 로그인됨: ${currentUser.displayName}")
                // 이미 로그인된 경우에도 사용자 정보 확인/생성 필요!
                authManager.ensureShareCodeForUser(currentUser.uid, currentUser.displayName, currentUser.email)
            }

            enableEdgeToEdge()
            setContent {
                // 테마는 사용자가 정보 화면에서 고른 값을 따른다. 기본은 기기 설정이다.
                val themeMode by settingsRepository.observeThemeMode().collectAsStateWithLifecycle(ThemeMode.SYSTEM)
                SlowClockTheme(
                    darkTheme =
                        when (themeMode) {
                            ThemeMode.SYSTEM -> isSystemInDarkTheme()
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                        },
                ) {
                    AppNavigation(onSignIn = { authManager.signInWithGoogle() })
                }
            }
            Log.d("MAIN", "onCreate 완료")
        } catch (e: Exception) {
            Log.e("MAIN", "onCreate 실패", e)
        }
        // 알림은 메인의 설명을 읽고 설정 버튼을 누른 뒤 허용한다. 로그인 위로 권한창을 띄우지 않는다.
        // 정확한 알람 권한은 메인 화면이 이유를 설명한 뒤 사용자가 원할 때 요청한다(#83).
        createNotificationChannel() // ← 반드시 호출 필요
    }

    private fun createNotificationChannel() {
        val name = "일정 알림"
        val descriptionText = "일정 시간에 울리는 알림"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        val channel =
            NotificationChannel("schedule_channel", name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, audioAttributes) // 🔊 사운드 설정 추가
            }

        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun saveFcmTokenToFirestore() {
        lifecycleScope.launch {
            if (userRepository.saveCurrentUserFcmToken()) {
                Log.d("FCM", "로그인 후 토큰 Firestore 저장 성공")
            } else {
                Log.e("FCM", "로그인 후 토큰 Firestore 저장 실패")
            }
        }
    }
}
