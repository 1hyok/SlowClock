package com.example.slowclock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.common.components.BottomNavigationBar
import com.example.slowclock.ui.common.components.ScheduleRow
import com.example.slowclock.ui.theme.SlowClockTheme
import java.util.Calendar

@PreviewTest
@Preview(name = "일정 줄 큰 글자 320", widthDp = 320, heightDp = 240, fontScale = 2f)
@Preview(name = "일정 줄 큰 글자 360", widthDp = 360, heightDp = 240, fontScale = 2f)
@Composable
internal fun ScheduleRowLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ScheduleRow(
            time = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 6, 11, 59, 0) }.time,
            title = "혈압약 먹기",
            completed = false,
            onToggleComplete = {},
        )
    }
}

@PreviewTest
@Preview(name = "하단 탭 큰 글자 320", widthDp = 320, heightDp = 160, fontScale = 2f)
@Preview(name = "하단 탭 큰 글자 360", widthDp = 360, heightDp = 160, fontScale = 2f)
@Composable
internal fun BottomNavigationLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        BottomNavigationBar(currentRoute = "timeline", onNavigate = {})
    }
}
