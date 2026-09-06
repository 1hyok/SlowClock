package com.example.slowclock.ui.timeline

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.common.ScheduleLoadingIndicator
import com.example.slowclock.ui.common.components.EmptyState
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.common.components.ScreenHeader
import com.example.slowclock.ui.common.components.rememberDayText
import java.util.Calendar

private const val SWIPE_THRESHOLD_PX = 100f

/** 타임라인 화면(stateful). */
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimelineContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * 타임라인 화면(stateless). 날짜는 좌우 화살표로 하루씩 옮기고, 가운데 날짜를 누르면 달력이 열린다.
 *
 * 종전에는 좌우 스와이프만으로 날짜를 옮길 수 있었다. 눌러야 할 것이 화면에 보이지 않으면
 * 그 기능은 없는 것과 같다. 스와이프는 그대로 두고 버튼을 함께 둔다(#109).
 */
@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    onIntent: (TimelineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnIntent by rememberUpdatedState(onIntent)
    val selectedDate = state.selectedDate
    val dayText = rememberDayText(selectedDate.time)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .pointerInput(Unit) {
                    val gesture = DaySwipeGesture(SWIPE_THRESHOLD_PX)
                    detectHorizontalDragGestures(
                        onDragStart = { gesture.reset() },
                        onDragEnd = { gesture.reset() },
                        onDragCancel = { gesture.reset() },
                    ) { _, dragAmount ->
                        gesture.dragBy(dragAmount)?.let(currentOnIntent)
                    }
                },
    ) {
        ScreenHeader(title = "시간표")

        DayPicker(
            dayText = dayText,
            onPrevious = { onIntent(TimelineIntent.PreviousDay) },
            onNext = { onIntent(TimelineIntent.NextDay) },
            onPickDate = { showDatePicker(context, selectedDate, onIntent) },
        )

        state.error?.let { error ->
            ErrorCard(
                error = error,
                canRetry = true,
                onRetry = { onIntent(TimelineIntent.Retry) },
                onDismiss = { onIntent(TimelineIntent.ConsumeError) },
            )
        }

        if (state.isLoading) {
            ScheduleLoadingIndicator()
        }
        if (state.schedules.isEmpty()) {
            if (state.isLoading || state.error != null) return@Column
            EmptyState(
                icon = Icons.Outlined.Schedule,
                title = "이 날은 일정이 없습니다",
                description = "화살표를 누르거나 좌우로 넘겨 다른 날을 볼 수 있습니다",
            )
        } else {
            Timeline(items = state.schedules)
        }
    }
}

/** 날짜 이동 줄. 화살표는 하루씩, 가운데 날짜는 달력을 연다. */
@Composable
private fun DayPicker(
    dayText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "하루 앞으로",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        TextButton(
            onClick = onPickDate,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Text(
                text = dayText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "하루 뒤로",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    selectedDate: Calendar,
    onIntent: (TimelineIntent) -> Unit,
) {
    DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            onIntent(TimelineIntent.SelectDate(year, month, dayOfMonth))
        },
        selectedDate.get(Calendar.YEAR),
        selectedDate.get(Calendar.MONTH),
        selectedDate.get(Calendar.DAY_OF_MONTH),
    ).show()
}
