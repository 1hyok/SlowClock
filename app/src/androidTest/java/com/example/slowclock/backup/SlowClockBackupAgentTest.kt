package com.example.slowclock.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlowClockBackupAgentTest {
    @Test
    fun restoredDevicePreferencesAreClearedBeforeTheNextLaunch() {
        // 테스트 전용 파일명으로 격리해 앱의 로그인과 설정을 건드리지 않는다.
        val context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = super.getSharedPreferences("backup_test_201_$name", mode)
            }
        val names = listOf("app_state", "settings", "scheduled_alarms", "snoozed_alarms")
        val unrelated = context.getSharedPreferences("backup_test_unrelated", Context.MODE_PRIVATE)
        try {
            names.forEach { name ->
                assertTrue(
                    context
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit()
                        .putString("restored", "old-install")
                        .commit(),
                )
            }
            val appState = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
            assertTrue(appState.edit().putBoolean("app_launched", true).commit())
            val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            assertTrue(settings.edit().putString("share_code", "ABC123").commit())
            assertTrue(unrelated.edit().putString("preserved", "value").commit())

            val agent = SlowClockBackupAgent()
            // 시스템이 붙이는 Context를 파일명이 격리된 테스트 Context로 대신한다.
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
                isAccessible = true
                invoke(agent, context)
            }
            agent.onRestoreFinished()

            names.forEach { name -> assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty()) }
            assertFalse(appState.getBoolean("app_launched", false))
            assertFalse(settings.contains("share_code"))
            assertEquals("value", unrelated.getString("preserved", null))
            agent.onRestoreFinished()
            assertFalse(appState.getBoolean("app_launched", false))
        } finally {
            (names + "backup_test_unrelated").forEach { name ->
                context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }
    }
}
