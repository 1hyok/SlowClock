// app/src/main/java/com/example/slowclock/ui/addschedule/components/QuickTimeSelector.kt
package com.example.slowclock.ui.addschedule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun QuickTimeSelector(
    onTimeSelected: (Calendar) -> Unit
) {
    val quickTimes = remember { getQuickTimeOptions() }

    Column {
        Text(
            text = "빠른 선택",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickTimes.take(3).forEach { (label, calendar) ->
                OutlinedButton(
                    onClick = { onTimeSelected(calendar) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickTimes.drop(3).forEach { (label, calendar) ->
                OutlinedButton(
                    onClick = { onTimeSelected(calendar) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun getQuickTimeOptions(): List<Pair<String, Calendar>> {
    return listOf(
        "지금" to Calendar.getInstance(),
        "30분 후" to Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30)
        },
        "1시간 후" to Calendar.getInstance().apply {
            add(Calendar.HOUR, 1)
        },
        "오후 2시" to Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        },
        "오후 6시" to Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        },
        "오후 8시" to Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    )
}