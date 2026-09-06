package com.example.slowclock.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.theme.SlowClockTheme

@PreviewTest
@Preview(name = "공유 코드 해제", showBackground = true, device = "spec:width=411dp,height=800dp", fontScale = 1.3f)
@Composable
internal fun ShareCodeClearScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ShareCodeContent(state = ShareCodeUiState(hasRegisteredCode = true), onIntent = {})
    }
}

@PreviewTest
@Preview(name = "공유 코드 해제 실패", showBackground = true, device = "spec:width=411dp,height=800dp", fontScale = 1.3f)
@Composable
internal fun ShareCodeClearFailedScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ShareCodeContent(
            state =
                ShareCodeUiState(
                    hasRegisteredCode = true,
                    saveError = "공유 설정을 해제하지 못했습니다. 기존 코드는 유지됩니다. 인터넷에 연결한 뒤 다시 눌러 주세요.",
                ),
            onIntent = {},
        )
    }
}
