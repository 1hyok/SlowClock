package com.example.slowclock.data.remote.repository

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
