package com.example.slowclock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.done.DoneContent
import com.example.slowclock.ui.done.DoneUiState
import com.example.slowclock.ui.main.MainContent
import com.example.slowclock.ui.main.MainUiState
import com.example.slowclock.ui.theme.SlowClockTheme
import com.example.slowclock.ui.timeline.TimelineContent
import com.example.slowclock.ui.timeline.TimelineUiState
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

// Play 스토어 등록 정보에 올릴 화면. 로그인한 기기가 있어야 찍을 수 있는 화면을 실제 화면
// 코드로 그려 낸다. 없는 기능을 보여 주지 않고, 화면을 그리는 코드도 앱과 같은 것이다(#115).
//
// 크기는 Play 가 받는 1080x2400 이다. 360dp x 800dp 에 dpi 480(밀도 3.0)을 곱하면 그 값이 된다.
// 여기서 나온 PNG 를 docs/play/screenshots/ 에 옮겨 둔다. 다시 만들려면 이 파일의 미리보기를
// 렌더한 뒤 같은 자리에 덮어쓴다.

private const val STORE_DEVICE = "spec:width=360dp,height=800dp,dpi=480"

private fun storeTime(
    hour: Int,
    minute: Int,
): Timestamp {
    val calendar = Calendar.getInstance()
    calendar.set(2026, Calendar.SEPTEMBER, 6, hour, minute, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return Timestamp(Date(calendar.timeInMillis))
}

private val storeSchedules =
    listOf(
        Schedule(id = "1", title = "혈압약 먹기", startTime = storeTime(8, 0), completed = true),
        Schedule(id = "2", title = "아침 산책", startTime = storeTime(9, 30), completed = true),
        Schedule(id = "3", title = "경로당 가기", startTime = storeTime(11, 0), completed = true),
        Schedule(id = "4", title = "점심 약속", description = "동네 칼국수집", startTime = storeTime(12, 30)),
        Schedule(id = "5", title = "병원 진료", description = "내과 3층", startTime = storeTime(14, 30)),
        Schedule(id = "6", title = "저녁 약 먹기", startTime = storeTime(19, 0)),
    )

private val storeMainState =
    MainUiState(
        todaySchedules = storeSchedules,
        currentSchedule = storeSchedules[4],
        completedCount = 3,
        totalCount = 6,
        currentUserId = "uid-store",
        isSignedInKnown = true,
    )

@PreviewTest
@Preview(name = "스토어 메인", showBackground = true, device = STORE_DEVICE)
@Composable
internal fun StoreMainScreenshot() {
    SlowClockTheme(darkTheme = false) {
        MainContent(
            state = storeMainState,
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
@Preview(name = "스토어 완료", showBackground = true, device = STORE_DEVICE)
@Composable
internal fun StoreDoneScreenshot() {
    SlowClockTheme(darkTheme = false) {
        AppBackground {
            DoneContent(state = DoneUiState(schedules = storeSchedules), onIntent = {})
        }
    }
}

@PreviewTest
@Preview(name = "스토어 시간표", showBackground = true, device = STORE_DEVICE)
@Composable
internal fun StoreTimelineScreenshot() {
    val day =
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 6, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    SlowClockTheme(darkTheme = false) {
        AppBackground {
            TimelineContent(
                state = TimelineUiState(selectedDate = day, schedules = storeSchedules),
                onIntent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "스토어 어두운 모드", showBackground = true, device = STORE_DEVICE)
@Composable
internal fun StoreDarkScreenshot() {
    SlowClockTheme(darkTheme = true) {
        MainContent(
            state = storeMainState,
            onIntent = {},
            onAddSchedule = {},
            onEditSchedule = {},
            onNavigateToProfile = {},
            onNavigateToSettings = {},
            onSignIn = {},
        )
    }
}

/** 탭 화면은 배경을 앱의 Scaffold 에서 받는다. 스토어 그림도 같은 바탕 위에 놓는다. */
@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}
