package com.example.slowclock.data.remote.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기에만 남는 사용자 설정. 지금은 공유 일정을 볼 공유 코드 하나다.
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
        }
    }
