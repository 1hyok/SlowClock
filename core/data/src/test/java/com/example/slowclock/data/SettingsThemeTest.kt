package com.example.slowclock.data

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.data.remote.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SettingsThemeTest {
    private val preferences = mockk<SharedPreferences>()
    private val manager = mockk<UiModeManager>(relaxed = true)
    private val context =
        mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
            every { getSystemService(UiModeManager::class.java) } returns manager
        }

    @Test
    fun savedDarkModeOverridesLightSystemBeforeFirstScreen() {
        every { preferences.getString("theme_mode", null) } returns ThemeMode.DARK.name
        SettingsRepository(context).applyThemeMode()
        verify(exactly = 1) { manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES) }
    }

    @Test
    fun systemModeClearsAppOverride() {
        every { preferences.getString("theme_mode", null) } returns ThemeMode.SYSTEM.name
        SettingsRepository(context).applyThemeMode()
        verify(exactly = 1) { manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO) }
    }

    @Test
    fun savedLightModeOverridesDarkSystem() {
        every { preferences.getString("theme_mode", null) } returns ThemeMode.LIGHT.name
        SettingsRepository(context).applyThemeMode()
        verify(exactly = 1) { manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO) }
    }
}
