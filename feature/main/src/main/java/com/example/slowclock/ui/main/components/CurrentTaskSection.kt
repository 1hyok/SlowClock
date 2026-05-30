// app/src/main/java/com/example/slowclock/ui/main/components/CurrentTaskSection.kt
package com.example.slowclock.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.hasExtraInfo
import com.example.slowclock.util.isOngoing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CurrentTaskSection(
    schedule: Schedule,
    onShowDetail: () -> Unit
) {
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    val currentTime = System.currentTimeMillis()

    val displayTime = if (isOngoing(schedule, currentTime)) {
        "~${
            timeFormat.format(
                schedule.endTime?.toDate() ?: Date(schedule.startTime.toDate().time + 60 * 60 * 1000)
            )
        }"
    } else {
        timeFormat.format(schedule.startTime.toDate())
    }

    Column {
        // 섹션 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, // 하드코딩 색상 제거
                modifier = Modifier.size(24.dp) // 20dp → 24dp
            )
            Spacer(modifier = Modifier.width(8.dp)) // 4dp → 8dp
            Text(
                text = "지금 할 일",
                style = MaterialTheme.typography.titleLarge, // fontSize 대신 style 사용
                color = MaterialTheme.colorScheme.onSurface // 하드코딩 색상 제거
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodyLarge, // fontSize 대신 style 사용
                color = MaterialTheme.colorScheme.onSurfaceVariant // 하드코딩 색상 제거
            )
        }

        // 현재 할 일 카드 (노란색 → tertiary 색상으로)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowDetail() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer // 하드코딩 색상 제거
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 왼쪽 강조 바 (더 두껍게)
                Box(
                    modifier = Modifier
                        .width(6.dp) // 4dp → 6dp
                        .height(80.dp) // 60dp → 80dp
                        .background(MaterialTheme.colorScheme.tertiary) // 하드코딩 색상 제거
                        .align(Alignment.CenterStart)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp) // 16dp → 20dp
                        .padding(start = 12.dp), // 8dp → 12dp
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary, // 하드코딩 색상 제거
                        modifier = Modifier.size(28.dp) // 24dp → 28dp
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // 12dp → 16dp

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = schedule.title,
                            style = MaterialTheme.typography.bodyLarge, // fontSize 대신 style 사용
                            color = MaterialTheme.colorScheme.onTertiaryContainer // 하드코딩 색상 제거
                        )

                        // 힌트 표시
                        if (hasExtraInfo(schedule)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = "상세정보 있음",
                                    tint = MaterialTheme.colorScheme.tertiary, // 하드코딩 색상 제거
                                    modifier = Modifier.size(20.dp) // 16dp → 20dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "터치하여 상세보기",
                                    style = MaterialTheme.typography.bodySmall, // fontSize 대신 style 사용
                                    color = MaterialTheme.colorScheme.tertiary // 하드코딩 색상 제거
                                )
                            }
                        }
                    }

                    // 진행 상태 표시
                    Text(
                        text = if (isOngoing(schedule, currentTime)) "진행중" else "예정",
                        style = MaterialTheme.typography.bodyMedium, // fontSize 대신 style 사용
                        color = MaterialTheme.colorScheme.tertiary, // 하드코딩 색상 제거
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}