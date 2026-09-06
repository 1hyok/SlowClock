package com.example.slowclock.ui.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.theme.SlowClockTheme

@PreviewTest
@Preview(name = "내 정보 읽기 실패", showBackground = true, device = "spec:width=411dp,height=800dp", fontScale = 1.3f)
@Composable
internal fun ProfileReadFailedScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ProfileContent(
            state = ProfileUiState(isLoading = false, loadError = "내 정보를 읽지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요."),
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onSignIn = {},
        )
    }
}
