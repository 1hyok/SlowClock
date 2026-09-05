package com.example.slowclock.ui.timeline

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.common.components.ErrorCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

/** 타임라인 화면(stateless). 날짜 선택은 DatePickerDialog, 좌우 스와이프로 하루씩 이동한다. */
@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    onIntent: (TimelineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA) }
    var hasSwiped by remember { mutableStateOf(false) }
    val selectedDate = state.selectedDate

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = 50.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { hasSwiped = false },
                        ) { _, dragAmount ->
                            if (!hasSwiped) {
                                if (dragAmount > SWIPE_THRESHOLD_PX) {
                                    onIntent(TimelineIntent.PreviousDay)
                                    hasSwiped = true
                                } else if (dragAmount < -SWIPE_THRESHOLD_PX) {
                                    onIntent(TimelineIntent.NextDay)
                                    hasSwiped = true
                                }
                            }
                        }
                    },
        ) {
            Text(
                text = "일정 타임라인",
                fontSize = 20.sp,
                color = Color.Blue,
                fontWeight = Bold,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { showDatePicker(context, selectedDate, onIntent) },
            )
            Text(
                text = formatter.format(selectedDate.time),
                fontSize = 15.sp,
                color = Color.Gray,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { showDatePicker(context, selectedDate, onIntent) },
            )

            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    canRetry = true,
                    onRetry = { onIntent(TimelineIntent.Retry) },
                    onDismiss = { onIntent(TimelineIntent.ConsumeError) },
                )
            }

            Timeline(
                items = state.schedules,
                height = this@BoxWithConstraints.maxHeight,
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
