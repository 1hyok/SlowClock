package com.example.slowclock.ui.done

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.common.components.DayProgress
import com.example.slowclock.ui.common.components.EmptyState
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.common.components.ScheduleRow
import com.example.slowclock.ui.common.components.ScreenHeader
import com.example.slowclock.ui.common.components.rememberDayText
import java.util.Date

/** 완료 화면(stateful). */
@Composable
fun DoneScreen(
    modifier: Modifier = Modifier,
    viewModel: DoneViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DoneContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * 완료 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다.
 *
 * 진행 막대를 맨 위에 두어 얼마나 남았는지 먼저 보이게 한다. 목록은 남은 일정이 먼저다.
 * 종전에는 완료한 일정이 위였고 진행 막대가 화면 맨 아래에 있었다(#109).
 */
@Composable
internal fun DoneContent(
    state: DoneUiState,
    onIntent: (DoneIntent) -> Unit,
    modifier: Modifier = Modifier,
    // 날짜 머리글의 기준이 되는 날. 미리보기와 스크린샷 테스트는 고정된 날을 넣는다(#143).
    today: Date = Date(),
) {
    val completed = state.completed
    val remaining = state.remaining
    val dateText = rememberDayText(today)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "완료한 일", subtitle = dateText)

        state.error?.let { error ->
            ErrorCard(
                error = error,
                canRetry = true,
                onRetry = { onIntent(DoneIntent.Retry) },
                onDismiss = { onIntent(DoneIntent.ConsumeError) },
            )
        }

        if (completed.isEmpty() && remaining.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.TaskAlt,
                title = "오늘 일정이 없습니다",
                description = "메인 화면에서 일정을 추가하면 여기에 진행 상황이 보입니다",
            )
            return@Column
        }

        DayProgress(
            completedCount = completed.size,
            totalCount = completed.size + remaining.size,
        )

        if (remaining.isNotEmpty()) {
            DoneSectionLabel(text = "남은 일정")
            remaining.forEach { schedule ->
                ScheduleRow(
                    title = schedule.title,
                    time = schedule.startTime.toDate(),
                    completed = false,
                    onToggleComplete = { onIntent(DoneIntent.ToggleComplete(schedule.id)) },
                )
            }
        }

        if (completed.isNotEmpty()) {
            DoneSectionLabel(text = "끝낸 일정", topPadding = if (remaining.isEmpty()) 0.dp else 12.dp)
            completed.forEach { schedule ->
                ScheduleRow(
                    title = schedule.title,
                    time = schedule.startTime.toDate(),
                    completed = true,
                    onToggleComplete = { onIntent(DoneIntent.ToggleComplete(schedule.id)) },
                )
            }
        }
    }
}

@Composable
private fun DoneSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding),
    )
}
