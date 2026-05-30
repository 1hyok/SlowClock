// MainActivity.kt
package com.example.slowclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.slowclock.auth.AuthManager
import com.example.slowclock.data.DummyDataManager
import com.example.slowclock.navigation.AppNavigation
import com.example.slowclock.notification.ForegroundService
import com.example.slowclock.notification.requestExactAlarmPermissionIfNeeded
import com.example.slowclock.ui.theme.SlowClockTheme
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val dummyDataManager = DummyDataManager()

    private fun handleNewInstallation() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isAppEverLaunched = prefs.getBoolean("app_launched", false)

        if (!isAppEverLaunched) {
            // 이 앱이 처음 실행됨 = 새 설치
            Log.d("INSTALL", "새 설치 감지 - Firebase 로그아웃")

            FirebaseAuth.getInstance().signOut()
            AuthUI.getInstance().signOut(this)

            // 플래그 저장
            prefs.edit { putBoolean("app_launched", true) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MAIN", "onCreate 시작")

        handleNewInstallation()

        try {
            // AuthManager 초기화
            authManager = AuthManager(this)
            authManager.initialize(
                onSuccess = {
                    Log.d("AUTH", "로그인 성공 콜백")
                    addDummyData()
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
                Log.d("AUTH", "로그인 필요 - 구글 로그인 시작")
                authManager.signInWithGoogle()
            } else {
                Log.d("AUTH", "=== MainActivity에서 Firebase 사용자 정보 ===")
                Log.d("AUTH", "displayName: '${currentUser.displayName}'")
                Log.d("AUTH", "email: '${currentUser.email}'")
                Log.d("AUTH", "photoUrl: '${currentUser.photoUrl}'")
                Log.d("AUTH", "uid: '${currentUser.uid}'")

                Log.d("AUTH", "이미 로그인됨: ${currentUser.displayName}")
                // 이미 로그인된 경우에도 사용자 정보 확인/생성 필요!
                lifecycleScope.launch {
                    authManager.ensureShareCodeForUser(
                        currentUser.uid,
                        currentUser.displayName ?: "",
                        currentUser.email ?: "",
                    )
                }
                addDummyData()
            }

            enableEdgeToEdge()
            setContent {
                SlowClockTheme {
                    AppNavigation()
                }
            }
            Log.d("MAIN", "onCreate 완료")
        } catch (e: Exception) {
            Log.e("MAIN", "onCreate 실패", e)
        }
        requestNotificationPermission() // 알림 권한 요청
        requestExactAlarmPermissionIfNeeded(this) // 알림 권한 요청
        createNotificationChannel() // ← 반드시 호출 필요

        // NotificationChannel 생성  ForegroundService에서 알림 사용을 위해
        val serviceIntent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun addDummyData() {
        lifecycleScope.launch {
            try {
                dummyDataManager.addDummyDataIfNeeded()
                Log.d("MAIN", "더미 데이터 처리 완료")
            } catch (e: Exception) {
                Log.e("MAIN", "더미 데이터 처리 실패", e)
            }
        }
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

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }
    }

    private fun saveFcmTokenToFirestore() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val db =
                    com.google.firebase.firestore.FirebaseFirestore
                        .getInstance()
                db
                    .collection("users")
                    .document(user.uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener { Log.d("FCM", "로그인 후 토큰 Firestore 저장 성공") }
                    .addOnFailureListener { e -> Log.e("FCM", "로그인 후 토큰 Firestore 저장 실패", e) }
            }
        }
    }
}
