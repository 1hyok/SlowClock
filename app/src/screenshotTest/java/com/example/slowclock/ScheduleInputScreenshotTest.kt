package com.example.slowclock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.example.slowclock.ui.addschedule.ScheduleTimeInput
import com.example.slowclock.ui.addschedule.components.TimePickerSection
import com.example.slowclock.ui.addschedule.components.TitleInputSection
import com.example.slowclock.ui.theme.SlowClockTheme

@PreviewTest
@Preview(name = "입력 안내 큰 글자 밝게", widthDp = 320, heightDp = 1600, fontScale = 2f)
@Composable
internal fun ScheduleInputLightScreenshot() = ScheduleInputPreview(dark = false)

@PreviewTest
@Preview(name = "입력 안내 큰 글자 어둡게", widthDp = 320, heightDp = 1600, fontScale = 2f)
@Composable
internal fun ScheduleInputDarkScreenshot() = ScheduleInputPreview(dark = true)

@Composable
private fun ScheduleInputPreview(dark: Boolean) {
    SlowClockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                TitleInputSection(title = "", description = "", onTitleChange = {}, onDescriptionChange = {})
                TimePickerSection(
                    startTime = ScheduleTimeInput("14", "30"),
                    endTime = ScheduleTimeInput("25", "00"),
                    onTimeSelect = {},
                    onEndTimeSelect = {},
                )
            }
        }
    }
}
