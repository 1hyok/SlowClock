package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.ScheduleRow

/**
 * 오늘의 일정. 남은 일정을 먼저, 끝낸 일정을 아래에 둔다.
 *
 * 종전에는 끝낸 일정이 위에 있었다. 할 일을 보러 온 사람에게 이미 끝난 것을 먼저 보여 주는
 * 순서였다(#109).
 */
@Composable
fun TodayScheduleSection(
    schedules: List<Schedule>,
    onToggleComplete: (String) -> Unit,
    onShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (completed, remaining) = schedules.partition { it.completed }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (remaining.isNotEmpty()) {
            SectionLabel(text = "남은 일정")
            remaining.forEach { schedule ->
                ScheduleRow(
                    title = schedule.title,
                    time = schedule.startTime.toDate(),
                    completed = false,
                    onClick = { onShowDetail(schedule.id) },
                    onToggleComplete = { onToggleComplete(schedule.id) },
                )
            }
        }

        if (completed.isNotEmpty()) {
            SectionLabel(
                text = "끝낸 일정",
                modifier = Modifier.padding(top = if (remaining.isNotEmpty()) 12.dp else 0.dp),
            )
            completed.forEach { schedule ->
                ScheduleRow(
                    title = schedule.title,
                    time = schedule.startTime.toDate(),
                    completed = true,
                    onClick = { onShowDetail(schedule.id) },
                    onToggleComplete = { onToggleComplete(schedule.id) },
                )
            }
        }
    }
}

/** 목록 위의 구역 이름. 아이콘 없이 글자만 둔다. 아이콘이 뜻을 더하지 않는 자리다. */
@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
