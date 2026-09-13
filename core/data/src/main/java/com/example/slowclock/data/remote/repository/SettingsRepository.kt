package com.example.slowclock.data.remote.repository

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import com.example.slowclock.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기에만 남는 사용자 설정. 공유 코드, 정확한 알람 안내 표시 여부, 화면 테마다.
 *
 * 화면이 SharedPreferences 를 직접 읽지 않도록 여기로 모은다.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val uiModeManager by lazy { context.getSystemService(UiModeManager::class.java) }

        fun getShareCode(): String? = prefs.getString(KEY_SHARE_CODE, null)?.takeIf { it.isNotBlank() }

        fun setShareCode(shareCode: String) {
            prefs.edit().putString(KEY_SHARE_CODE, shareCode.trim()).apply()
        }

        /**
         * 등록해 둔 가족의 공유 코드를 지운다. 세션이 끝날 때 부른다.
         *
         * 남겨 두면 같은 기기에 다른 사람이 로그인했을 때 앞 사람이 등록한 가족의 일정이
         * 그대로 보인다. 어르신 폰을 자녀가 잠깐 쓰는 일이 이 앱에서는 드물지 않다(#165).
         */
        fun clearShareCode() {
            prefs.edit().remove(KEY_SHARE_CODE).apply()
        }

        fun getThemeMode(): ThemeMode = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))

        fun setThemeMode(mode: ThemeMode) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
            applyThemeMode()
        }

        /**
         * Android 12 이상의 시작 창도 Compose에서 고른 테마를 따른다.
         * https://developer.android.com/develop/ui/views/theming/darktheme
         *
         * SYSTEM 에 MODE_NIGHT_AUTO 를 주는 것은 상수 이름만 보면 어긋나 보인다. 문서상 AUTO 는
         * "위치·시간에 따라 자동 전환" 이지만, UiModeManagerService 는 YES/NO 가 아닌 값을 모두
         * UI_MODE_NIGHT_UNDEFINED 로 바꿔 앱별 override 를 지운다. 그래서 실제 동작이 "기기 설정을
         * 따른다" 가 된다. override 를 지우는 별도 API 는 없다. 이름만 보고 MODE_NIGHT_NO 로
         * 고치면 SYSTEM 이 항상 밝은 테마로 굳으니 바꾸지 말 것.
         */
        fun applyThemeMode() {
            val nightMode =
                when (getThemeMode()) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                }
            uiModeManager?.setApplicationNightMode(nightMode)
        }

        /** 현재 값을 먼저 내고, 바뀔 때마다 다시 낸다. */
        fun observeThemeMode(): Flow<ThemeMode> =
            callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == KEY_THEME_MODE) trySend(getThemeMode())
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(getThemeMode())
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        /** 정확한 알람 권한 안내를 이미 보여 줬는지. 한 번 보여 주면 다시 띄우지 않는다. */
        fun hasSeenExactAlarmNotice(): Boolean = prefs.getBoolean(KEY_EXACT_ALARM_NOTICE_SEEN, false)

        fun markExactAlarmNoticeSeen() {
            prefs.edit().putBoolean(KEY_EXACT_ALARM_NOTICE_SEEN, true).apply()
        }

        /** 현재 값을 먼저 내고, 바뀔 때마다 다시 낸다. */
        fun observeShareCode(): Flow<String?> =
            callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == KEY_SHARE_CODE) trySend(getShareCode())
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(getShareCode())
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        private companion object {
            const val PREFS_NAME = "settings"
            const val KEY_SHARE_CODE = "share_code"
            const val KEY_EXACT_ALARM_NOTICE_SEEN = "exact_alarm_notice_seen"
            const val KEY_THEME_MODE = "theme_mode"
        }
    }
