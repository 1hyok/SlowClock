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
import androidx.compose.runtime.collectAsState
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
import com.example.slowclock.ui.main.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TimelineScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {

    val context = LocalContext.current
    // Calendar 및 날짜 수정 Method
    // 0000년 00월 00일 Format
    val formatter = remember{ SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA)}

    // 드래그 중첩 방지
    var hasSwiped by remember { mutableStateOf(false) }

    // Timeline 날짜 선택 및 이동
    var calendar = remember{Calendar.getInstance()}
    val Date = remember { mutableStateOf(formatter.format(calendar.time))}

    val datePickerDialog =
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year,month,dayOfMonth)
                Date.value = formatter.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),

            )

    val uiState by viewModel.uiState.collectAsState()
    val filteredSchedules = uiState.todaySchedules.filter { schedule ->
        val scheduleDate = formatter.format(schedule.startTime.toDate())
        scheduleDate == Date.value
    }

    // Timeline Screen 컨텐츠
    BoxWithConstraints(modifier= Modifier.fillMaxSize().padding(top=50.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasSwiped = false
                        }
                    ) { _, dragAmount ->
                        if(!hasSwiped) {
                            if (dragAmount > 100) {
                                calendar.add(Calendar.DAY_OF_MONTH, -1)
                                Date.value = formatter.format(calendar.time)
                                hasSwiped = true
                            } else if (dragAmount < -100) {
                                calendar.add(Calendar.DAY_OF_MONTH, 1)
                                Date.value = formatter.format(calendar.time)
                                hasSwiped = true
                            }
                        }
                    }
                }
        ) {
            // 제목 Text
            Text(
                text = "일정 타임라인",
                fontSize = 20.sp,
                color = Color.Blue,
                fontWeight = Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        datePickerDialog.show()
                    }

            )
            // Timeline 날짜 Text
            Text(
                text = Date.value,
                fontSize = 15.sp,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        datePickerDialog.show()
                    }
            )
            // Default : 오늘 날짜, 이후 캘린더 조작 혹은 버튼 클릭으로 날짜 변경 가능

            Timeline(
                items= filteredSchedules, // Items : DB에서 사용자의 해당 날짜에 존재하는 일정들을 가져와서 사용
                height= this@BoxWithConstraints.maxHeight
            )

        }

    }

}