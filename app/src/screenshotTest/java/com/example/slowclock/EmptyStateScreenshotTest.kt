package com.example.slowclock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.main.components.EmptyStateCard
import com.example.slowclock.ui.theme.SlowClockTheme

// EmptyStateCard 의 시각 회귀 baseline. main 의 @Preview 는 Android Studio 미리보기용으로 유지하고,
// 이 함수는 baseline PNG 생성용이다. 의도된 시각 변경은 PR 에 screenshot-baseline 라벨을 붙여 CI 컨테이너에서 갱신한다.
@PreviewTest
@Preview(showBackground = true)
@Composable
internal fun EmptyStateCardScreenshot() {
    SlowClockTheme {
        EmptyStateCard()
    }
}
