package com.example.slowclock.ui.done

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.ErrorCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 완료 화면(stateful). */
@Composable
fun DoneScreen(
    modifier: Modifier = Modifier,
    viewModel: DoneViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DoneContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** 완료 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다. */
@Composable
internal fun DoneContent(
    state: DoneUiState,
    onIntent: (DoneIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = state.completed
    val remaining = state.remaining
    val formatter = remember { SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN) }
    val timeFormatter = remember { SimpleDateFormat("a h:mm", Locale.KOREAN) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "오늘의 일정",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = formatter.format(Date()),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center,
        )

        state.error?.let { error ->
            ErrorCard(
                error = error,
                canRetry = true,
                onRetry = { onIntent(DoneIntent.Retry) },
                onDismiss = { onIntent(DoneIntent.ConsumeError) },
            )
        }

        if (completed.isNotEmpty()) {
            Section(title = "완료한 일정", icon = Icons.Default.CheckCircle, color = MaterialTheme.colorScheme.secondary) {
                completed.forEach {
                    ScheduleCard(
                        schedule = it,
                        timeFormatter = timeFormatter,
                        completed = true,
                        onClick = { schedule -> onIntent(DoneIntent.ToggleComplete(schedule.id)) },
                    )
                }
            }
        }

        if (remaining.isNotEmpty()) {
            Section(title = "남은 일정", icon = Icons.Default.Notifications, color = MaterialTheme.colorScheme.secondary) {
                remaining.forEach {
                    ScheduleCard(
                        schedule = it,
                        timeFormatter = timeFormatter,
                        completed = false,
                        onClick = { schedule -> onIntent(DoneIntent.ToggleComplete(schedule.id)) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "오늘 ${completed.size}개의 일정을 완료했어요!",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinearProgressIndicator(
            progress = {
                if ((completed.size + remaining.size) == 0) {
                    0f
                } else {
                    completed.size.toFloat() / (completed.size + remaining.size)
                }
            },
            color = MaterialTheme.colorScheme.tertiary,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
        )
    }
}

@Composable
fun Section(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ScheduleCard(
    schedule: Schedule,
    timeFormatter: SimpleDateFormat,
    completed: Boolean,
    onClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = if (completed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick(schedule) },
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = { onClick(schedule) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = schedule.title, fontWeight = FontWeight.Bold)
                    Text(
                        text = timeFormatter.format(schedule.startTime.toDate()),
                        fontSize = 12.sp,
                    )
                }
            }

            if (completed) {
                Text(
                    text = "완료",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
