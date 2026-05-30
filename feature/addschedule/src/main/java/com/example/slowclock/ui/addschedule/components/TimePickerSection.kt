// app/src/main/java/com/example/slowclock/ui/addschedule/components/TimePickerSection.kt
package com.example.slowclock.ui.addschedule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TimePickerSection(
    selectedTime: Calendar,
    endTime: Calendar?,
    onTimeSelected: (Calendar) -> Unit,
    onEndTimeSelected: (Calendar?) -> Unit
) {
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)

    // 입력값을 기억할 상태
    var hour by remember { mutableStateOf(selectedTime.get(Calendar.HOUR_OF_DAY).toString()) }
    var minute by remember { mutableStateOf(selectedTime.get(Calendar.MINUTE).toString()) }

    var endHour by remember { mutableStateOf(endTime?.get(Calendar.HOUR_OF_DAY)?.toString() ?: "") }
    var endMinute by remember { mutableStateOf(endTime?.get(Calendar.MINUTE)?.toString() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("시간 설정", style = MaterialTheme.typography.titleLarge)

        // 시작 시간
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("시작 시간", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = {
                            hour = it.filter { c -> c.isDigit() }.take(2)
                            updateCalendarTime(hour, minute)?.let(onTimeSelected)
                        },
                        label = { Text("시") },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = minute,
                        onValueChange = {
                            minute = it.filter { c -> c.isDigit() }.take(2)
                            updateCalendarTime(hour, minute)?.let(onTimeSelected)
                        },
                        label = { Text("분") },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        // 종료 시간 (선택사항)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("종료 시간 (선택)", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = {
                            endHour = it.filter { c -> c.isDigit() }.take(2)
                            updateCalendarTime(endHour, endMinute)?.let(onEndTimeSelected)
                        },
                        label = { Text("시") },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = endMinute,
                        onValueChange = {
                            endMinute = it.filter { c -> c.isDigit() }.take(2)
                            updateCalendarTime(endHour, endMinute)?.let(onEndTimeSelected)
                        },
                        label = { Text("분") },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }

}

fun updateCalendarTime(hour: String, minute: String): Calendar? {
    return try {
        val h = hour.toInt()
        val m = minute.toInt()
        if (h in 0..23 && m in 0..59) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
            }
        } else null
    } catch (e: NumberFormatException) {
        null
    }
}