package com.example.slowclock

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.ui.alarm.AlarmFullScreenActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.example.slowclock.core.alarm.R as AlarmR

class ThemeAndAlarmAccessibilityTest {
    @Test
    fun applicationModeOverridesSystemAndCanReturnToSystem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val previous = settings.getThemeMode()
        val systemNight = context.getSystemService(UiModeManager::class.java).nightMode
        try {
            settings.setThemeMode(ThemeMode.DARK)
            awaitNightMode(context, Configuration.UI_MODE_NIGHT_YES)
            assertEquals(0xFF14120F.toInt(), context.getColor(R.color.window_background))
            settings.setThemeMode(ThemeMode.LIGHT)
            awaitNightMode(context, Configuration.UI_MODE_NIGHT_NO)
            assertEquals(0xFFFAF6F0.toInt(), context.getColor(R.color.window_background))
            settings.setThemeMode(ThemeMode.SYSTEM)
            if (systemNight == UiModeManager.MODE_NIGHT_YES || systemNight == UiModeManager.MODE_NIGHT_NO) {
                awaitNightMode(
                    context,
                    if (systemNight == UiModeManager.MODE_NIGHT_YES) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                )
            }
        } finally {
            settings.setThemeMode(previous)
        }
    }

    // 글자 배율은 기기 전역 설정이라 계측 테스트에서 바꾸지 않는다. 큰 글자에서의 알람 화면
    // 확인은 실기기 몫으로 남긴다(#220). 여기서는 기본 배율의 표기와 도달 가능성만 본다.
    @Test
    fun alarmClockReadsTwelveHourTimeAndActionsStayReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, AlarmFullScreenActivity::class.java)
                .putExtra("title", "혈압약 먹기")
                .putExtra("desc", "식사 후 물과 함께 복용해 주세요.")
        ActivityScenario.launch<AlarmFullScreenActivity>(intent).use { scenario ->
            onView(withId(AlarmR.id.currentTimeText)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val clock = activity.findViewById<TextView>(AlarmR.id.currentTimeText)
                assertTrue(clock.text.toString().matches(Regex("(오전|오후) [0-9]{1,2}:[0-9]{2}")))
                val layout = clock.layout
                for (line in 0 until layout.lineCount) {
                    assertEquals(0, layout.getEllipsisCount(line))
                    assertTrue(layout.getLineRight(line) <= clock.width - clock.totalPaddingLeft - clock.totalPaddingRight)
                }
            }
            capture(context, "alarm-clock.png")
            onView(withId(AlarmR.id.dismissButton)).perform(scrollTo()).check(matches(isDisplayed()))
            capture(context, "alarm-actions.png")
        }
    }

    private fun awaitNightMode(
        context: Context,
        expected: Int,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != expected &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
        assertEquals(expected, context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    }

    private fun capture(
        context: Context,
        name: String,
    ) {
        // 창 진입 애니메이션의 중간 프레임을 증빙으로 저장하지 않는다.
        Thread.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
