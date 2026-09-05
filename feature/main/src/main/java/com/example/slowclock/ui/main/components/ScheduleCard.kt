// app/src/main/java/com/example/slowclock/ui/main/components/ScheduleCard.kt
package com.example.slowclock.ui.main.components

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SimpleDateFormat")
fun Date.toTimeString(): String {
    val format = SimpleDateFormat("a h:mm", Locale.KOREAN)
    return format.format(this)
}

// ✅ 최종 ScheduleCard
@Composable
fun ScheduleCard(
    schedule: Schedule,
    onToggleComplete: () -> Unit, // 아직 사용 중이라면 그대로 둬도 됨
    onShowDetail: () -> Unit,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (completed) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val borderColor =
        if (completed) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.primary
        }

    Card(
        onClick = onShowDetail,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ✅ 체크박스와 간격 제거

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = schedule.startTime.toDate().toTimeString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (completed) {
                Text(
                    text = "완료",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
