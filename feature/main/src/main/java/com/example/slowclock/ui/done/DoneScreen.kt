// app/src/main/java/com/example/slowclock/ui/done/DoneScreen.kt
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.main.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun DoneScreen(
    mainViewModel: MainViewModel
) {
    val uiState by mainViewModel.uiState.collectAsState()

    val completed = uiState.todaySchedules.filter { it.completed }
    val remaining = uiState.todaySchedules.filter { !it.completed }

    val formatter = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN)
    val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREAN)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "오늘의 일정",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF3A5CCC),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = formatter.format(Date()),
            fontSize = 16.sp,
            color = Color.DarkGray,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )

        if (completed.isNotEmpty()) {
            Section(title = "완료한 일정", icon = Icons.Default.CheckCircle, color = Color(0xFF3A5CCC)) {
                completed.forEach {
                    ScheduleCard(
                        schedule = it,
                        timeFormatter = timeFormatter,
                        completed = true,
                        onClick = { schedule ->
                            mainViewModel.toggleScheduleComplete(schedule.id)
                        }
                    )
                }
            }
        }

        if (remaining.isNotEmpty()) {
            Section(title = "남은 일정", icon = Icons.Default.Notifications, color = Color(0xFF3A5CCC)) {
                remaining.forEach {
                    ScheduleCard(
                        schedule = it,
                        timeFormatter = timeFormatter,
                        completed = false,
                        onClick = { schedule ->
                            mainViewModel.toggleScheduleComplete(schedule.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "오늘 ${completed.size}개의 일정을 완료했어요!",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.DarkGray
        )

        // 🔥 수정된 LinearProgressIndicator
        LinearProgressIndicator(
            progress = {
                if ((completed.size + remaining.size) == 0) 0f
                else completed.size.toFloat() / (completed.size + remaining.size)
            },
            color = Color(0xFF00A152),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun Section(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FB))
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
    onClick: (Schedule) -> Unit
) {
    val cardColor = if (completed) Color(0xFFE0F8E0) else Color(0xFFEAF1FF)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(schedule) },
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = { onClick(schedule) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = schedule.title, fontWeight = FontWeight.Bold)
                    Text(
                        text = timeFormatter.format(schedule.startTime.toDate()),
                        fontSize = 12.sp
                    )
                }
            }

            if (completed) {
                Text(
                    text = "완료",
                    color = Color(0xFF00A152),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}