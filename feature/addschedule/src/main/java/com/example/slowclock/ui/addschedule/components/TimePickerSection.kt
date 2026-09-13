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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.slowclock.feature.addschedule.R
import com.example.slowclock.ui.addschedule.ScheduleTimeInput
import com.example.slowclock.ui.common.components.rememberTimeText
import java.util.Calendar

@Composable
fun TimePickerSection(
    startTime: ScheduleTimeInput,
    endTime: ScheduleTimeInput,
    onTimeSelect: (ScheduleTimeInput) -> Unit,
    onEndTimeSelect: (ScheduleTimeInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("시간 설정", style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.schedule_time_format_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 시작 시간
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        value = startTime.hour,
                        onValueChange = {
                            onTimeSelect(startTime.copy(hour = it.filter { c -> c.isDigit() }.take(2)))
                        },
                        label = { Text("시") },
                        isError = !startTime.isValid,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = startTime.minute,
                        onValueChange = {
                            onTimeSelect(startTime.copy(minute = it.filter { c -> c.isDigit() }.take(2)))
                        },
                        label = { Text("분") },
                        isError = !startTime.isValid,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    )
                }
                TimeInputFeedback(startTime, required = true)
            }
        }

        // 종료 시간 (선택사항)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        value = endTime.hour,
                        onValueChange = {
                            onEndTimeSelect(endTime.copy(hour = it.filter { c -> c.isDigit() }.take(2)))
                        },
                        label = { Text("시") },
                        isError = !endTime.isEmpty && !endTime.isValid,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = endTime.minute,
                        onValueChange = {
                            onEndTimeSelect(endTime.copy(minute = it.filter { c -> c.isDigit() }.take(2)))
                        },
                        label = { Text("분") },
                        isError = !endTime.isEmpty && !endTime.isValid,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    )
                }
                TimeInputFeedback(endTime, required = false)
            }
        }
    }
}

/**
 * 입력한 시각을 글로 되읽어 준다. [required] 인 시작 시간은 비어 있어도 안내를 내야 한다.
 * 종전에는 시작 시간을 다 지우면 저장 버튼만 잠기고 화면 어디에도 이유가 없었다.
 *
 * liveRegion 을 두는 이유: 안내가 칸의 형제 Text 라 화면 낭독기가 칸에 머무는 동안에는 읽지
 * 않는다. 값이 바뀔 때 스스로 읽히게 해야 눈으로 보지 않는 사람도 되읽기를 받는다.
 */
@Composable
private fun TimeInputFeedback(
    input: ScheduleTimeInput,
    required: Boolean,
) {
    val date = input.onDate(remember { Calendar.getInstance() })
    val feedbackModifier =
        Modifier
            .padding(top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    if (date != null) {
        Text(
            stringResource(R.string.schedule_time_readback, rememberTimeText(date.time)),
            modifier = feedbackModifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (required || !input.isEmpty) {
        Text(
            stringResource(R.string.schedule_time_range_hint),
            modifier = feedbackModifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
