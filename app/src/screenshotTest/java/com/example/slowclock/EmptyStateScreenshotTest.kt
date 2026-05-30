package com.example.slowclock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.main.components.EmptyStateCard
import com.example.slowclock.ui.theme.SlowClockTheme

/**
 * [EmptyStateCard] 의 시각 회귀 baseline.
 *
 * main 의 `@Preview` 는 Android Studio 미리보기 용도로 유지. 본 함수는 *baseline PNG 생성용*.
 * 의도된 시각 변경 시 `./gradlew :app:updateScreenshotTest` 로 baseline 갱신.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
internal fun emptyStateCardScreenshot() {
    SlowClockTheme {
        EmptyStateCard()
    }
}
