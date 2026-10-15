package com.example.slowclock

import android.app.Application
import com.example.slowclock.data.remote.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Firebase 초기화는 google-services 플러그인이 넣는 FirebaseInitProvider 가 앱 시작 시 한다.
 * Firestore 오프라인 캐시 설정은 인스턴스를 제공하는 `FirebaseModule` 이 맡는다.
 */
@HiltAndroidApp
class SlowClockApplication : Application() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository.applyThemeMode()
    }
}
