package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import java.text.SimpleDateFormat

@Composable
fun SharedRemindersSection(
    sharedReminders: List<Schedule>,
    currentUserUid: String?,
    timeFormat: SimpleDateFormat,
    onToggleComplete: (String) -> Unit
) {
    if (sharedReminders.isEmpty()) return
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "공유 일정",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            sharedReminders.forEach { schedule ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedule.userId == currentUserUid) {
                            IconButton(
                                onClick = { onToggleComplete(schedule.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = if (schedule.completed) "완료됨" else "미완료",
                                    tint = if (schedule.completed) Color(0xFF388E3C) else Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = if (schedule.completed) "완료됨" else "미완료",
                                tint = if (schedule.completed) Color(0xFF388E3C) else Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (schedule.completed) "완료됨" else "미완료",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (schedule.completed) Color(0xFF388E3C) else Color.Red,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                        )
                        Text(
                            text = schedule.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (schedule.description.isNotBlank()) {
                        Text(
                            text = schedule.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val start = schedule.startTime.toDate()
                    val end = schedule.endTime?.toDate()
                    val timeText = if (end != null) {
                        "${timeFormat.format(start)} ~ ${timeFormat.format(end)}"
                    } else {
                        timeFormat.format(start)
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

