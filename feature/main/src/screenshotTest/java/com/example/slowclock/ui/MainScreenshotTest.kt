package com.example.slowclock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.done.DoneContent
import com.example.slowclock.ui.done.DoneUiState
import com.example.slowclock.ui.main.MainContent
import com.example.slowclock.ui.main.MainUiState
import com.example.slowclock.ui.theme.SlowClockTheme
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

// 화면 시각 회귀 baseline. 로그인해야 닿는 화면이라 기기에서 눈으로 보기 어렵다.
// 미리보기로 굳혀 두면 색·간격·순서가 바뀔 때 CI 가 잡는다(#109).
// 의도된 시각 변경은 PR 에 screenshot-baseline 라벨을 붙여 CI 컨테이너에서 갱신한다.

private fun at(
    hour: Int,
    minute: Int,
): Timestamp {
    val calendar = Calendar.getInstance()
    calendar.set(2026, Calendar.SEPTEMBER, 6, hour, minute, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return Timestamp(Date(calendar.timeInMillis))
}

private val sampleSchedules =
    listOf(
        Schedule(id = "1", title = "혈압약 먹기", startTime = at(8, 0), completed = true),
        Schedule(id = "2", title = "아침 산책", startTime = at(9, 30), completed = true),
        Schedule(id = "3", title = "점심 약속", description = "동네 칼국수집", startTime = at(12, 0)),
        Schedule(id = "4", title = "병원 진료", startTime = at(14, 30)),
        Schedule(id = "5", title = "저녁 약 먹기", startTime = at(19, 0)),
    )

private val loadedState =
    MainUiState(
        todaySchedules = sampleSchedules,
        currentSchedule = sampleSchedules[3],
        completedCount = 2,
        totalCount = 5,
        currentUserId = "uid-1",
        isSignedInKnown = true,
    )

@PreviewTest
@Preview(name = "메인 일정 있음", showBackground = true, device = "spec:width=411dp,height=1100dp")
@Composable
internal fun MainContentLoadedScreenshot() {
    SlowClockTheme(darkTheme = false) {
        MainContent(
            state = loadedState,
            onIntent = {},
            onAddSchedule = {},
            onEditSchedule = {},
            onNavigateToProfile = {},
            onNavigateToSettings = {},
            onSignIn = {},
        )
    }
}

@PreviewTest
@Preview(name = "메인 어두운 모드", showBackground = true, device = "spec:width=411dp,height=1100dp")
@Composable
internal fun MainContentDarkScreenshot() {
    SlowClockTheme(darkTheme = true) {
        MainContent(
            state = loadedState,
            onIntent = {},
            onAddSchedule = {},
            onEditSchedule = {},
            onNavigateToProfile = {},
            onNavigateToSettings = {},
            onSignIn = {},
        )
    }
}

@PreviewTest
@Preview(name = "메인 로그인 전", showBackground = true, device = "spec:width=411dp,height=900dp")
@Composable
internal fun MainContentSignedOutScreenshot() {
    SlowClockTheme(darkTheme = false) {
        MainContent(
            state = MainUiState(isSignedInKnown = true),
            onIntent = {},
            onAddSchedule = {},
            onEditSchedule = {},
            onNavigateToProfile = {},
            onNavigateToSettings = {},
            onSignIn = {},
        )
    }
}

@PreviewTest
@Preview(name = "완료 화면", showBackground = true, device = "spec:width=411dp,height=1000dp")
@Composable
internal fun DoneContentScreenshot() {
    SlowClockTheme(darkTheme = false) {
        DoneContent(
            state = DoneUiState(schedules = sampleSchedules),
            onIntent = {},
        )
    }
}
