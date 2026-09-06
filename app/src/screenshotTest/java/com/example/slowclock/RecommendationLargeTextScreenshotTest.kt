package com.example.slowclock

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.addschedule.components.RecommendationPlaceholder
import com.example.slowclock.ui.theme.SlowClockTheme

@PreviewTest
@Preview(name = "추천 카드 큰 글자 320", widthDp = 320, heightDp = 480, fontScale = 2f)
@Preview(name = "추천 카드 큰 글자 360", widthDp = 360, heightDp = 480, fontScale = 2f)
@Composable
internal fun RecommendationLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        Column {
            RecommendationPlaceholder(onNavigateToRecommendation = {})
        }
    }
}
