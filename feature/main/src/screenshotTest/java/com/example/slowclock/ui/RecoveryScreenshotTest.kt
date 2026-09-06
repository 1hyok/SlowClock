package com.example.slowclock.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.done.DoneContent
import com.example.slowclock.ui.done.DoneUiState
import com.example.slowclock.ui.main.MainContent
import com.example.slowclock.ui.main.MainUiState
import com.example.slowclock.ui.main.components.ScheduleDetailContent
import com.example.slowclock.ui.theme.SlowClockTheme
import com.example.slowclock.ui.timeline.TimelineContent
import com.example.slowclock.ui.timeline.TimelineUiState
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import java.util.Calendar

private val recoveryDate =
    Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 6, 11, 59, 0)
        set(Calendar.MILLISECOND, 0)
    }

@PreviewTest
@Preview(name = "메인 로딩", widthDp = 320, heightDp = 600)
@Composable
internal fun MainLoadingScreenshot() = MainRecoveryPreview(MainUiState(isLoading = true))

@PreviewTest
@Preview(name = "메인 조회 오류", widthDp = 320, heightDp = 600)
@Composable
internal fun MainErrorScreenshot() = MainRecoveryPreview(MainUiState(error = AppError.NetworkError, canRetry = true))

@Composable
private fun MainRecoveryPreview(state: MainUiState) {
    SlowClockTheme(darkTheme = false) {
        MainContent(
            state = state.copy(currentUserId = "uid-1", isSignedInKnown = true),
            onIntent = {},
            onAddSchedule = {},
            onEditSchedule = {},
            onNavigateToProfile = {},
            onNavigateToSettings = {},
            onSignIn = {},
            today = recoveryDate.time,
        )
    }
}

@PreviewTest
@Preview(name = "완료 로딩", widthDp = 320, heightDp = 420)
@Composable
internal fun DoneLoadingScreenshot() {
    SlowClockTheme(darkTheme = false) {
        DoneContent(state = DoneUiState(isLoading = true), onIntent = {}, today = recoveryDate.time)
    }
}

@PreviewTest
@Preview(name = "완료 조회 오류", widthDp = 320, heightDp = 600)
@Composable
internal fun DoneErrorScreenshot() {
    SlowClockTheme(darkTheme = false) {
        DoneContent(state = DoneUiState(error = AppError.NetworkError), onIntent = {}, today = recoveryDate.time)
    }
}

@PreviewTest
@Preview(name = "시간표 로딩", widthDp = 360, heightDp = 420)
@Composable
internal fun TimelineLoadingScreenshot() {
    SlowClockTheme(darkTheme = false) {
        TimelineContent(state = TimelineUiState(selectedDate = recoveryDate, isLoading = true), onIntent = {})
    }
}

@PreviewTest
@Preview(name = "시간표 조회 오류", widthDp = 360, heightDp = 600)
@Composable
internal fun TimelineErrorScreenshot() {
    SlowClockTheme(darkTheme = false) {
        TimelineContent(state = TimelineUiState(selectedDate = recoveryDate, error = AppError.NetworkError), onIntent = {})
    }
}

@PreviewTest
@Preview(name = "재시도 없는 오류 닫기", widthDp = 320, heightDp = 400, fontScale = 2f)
@Composable
internal fun DismissErrorLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ErrorCard(error = AppError.PermissionError, canRetry = false, onDismiss = {})
    }
}

private val longDetail =
    Schedule(
        id = "detail",
        title = "혈압약 먹기",
        description = List(20) { "식사 후 물과 함께 복용해주세요." }.joinToString("\n"),
        startTime = Timestamp(recoveryDate.time),
        completed = true,
    )

@PreviewTest
@Preview(name = "오류 재시도 큰 글자", widthDp = 320, heightDp = 600, fontScale = 2f)
@Composable
internal fun RetryErrorLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ErrorCard(error = AppError.NetworkError, canRetry = true, onRetry = {}, onDismiss = {})
    }
}

@PreviewTest
@Preview(name = "상세 큰 글자 상단 320", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "상세 큰 글자 상단 360", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
internal fun DetailTopLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ScheduleDetailContent(schedule = longDetail, onDismiss = {}, onEdit = {}, onDelete = {})
    }
}

@PreviewTest
@Preview(name = "상세 큰 글자 하단 320", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "상세 큰 글자 하단 360", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
internal fun DetailBottomLargeTextScreenshot() {
    SlowClockTheme(darkTheme = false) {
        ScheduleDetailContent(
            schedule = longDetail,
            onDismiss = {},
            onEdit = {},
            onDelete = {},
            scrollState = ScrollState(Int.MAX_VALUE),
        )
    }
}
