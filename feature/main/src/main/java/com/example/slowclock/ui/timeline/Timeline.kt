package com.example.slowclock.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.rememberClockText
import com.example.slowclock.ui.common.components.rememberMeridiemText

/**
 * 하루의 일정을 시간 순서대로 세운다.
 *
 * 종전에는 가운데 세로선을 두고 카드를 좌우로 번갈아 붙였다. 보기에는 그럴듯하지만 읽을 때
 * 눈이 좌우로 튀어 순서를 따라가기 어렵고, 카드 폭이 화면의 절반뿐이라 제목이 금방 줄바꿈됐다.
 * 왼쪽에 시각, 가운데에 축, 오른쪽에 일정을 두어 위에서 아래로 한 방향으로 읽게 한다(#109).
 */
@Composable
fun Timeline(
    items: List<Schedule>,
    modifier: Modifier = Modifier,
) {
    val sortedItems = items.sortedBy { it.startTime.seconds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
        itemsIndexed(sortedItems, key = { _, item -> item.id }) { index, item ->
            TimelineEntry(
                schedule = item,
                isFirst = index == 0,
                isLast = index == sortedItems.lastIndex,
            )
        }
    }
}

@Composable
private fun TimelineEntry(
    schedule: Schedule,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val startTime = schedule.startTime.toDate()
    val meridiem = rememberMeridiemText(startTime)
    val clock = rememberClockText(startTime)
    val accent =
        if (schedule.completed) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
    val titleColor =
        if (schedule.completed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier.width(64.dp).padding(top = 18.dp),
        ) {
            Text(
                text = meridiem,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = clock,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
        }

        TimelineRail(accent = accent, isFirst = isFirst, isLast = isLast)

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            shape = MaterialTheme.shapes.medium,
            color =
                if (schedule.completed) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    textDecoration = if (schedule.completed) TextDecoration.LineThrough else null,
                )
                if (schedule.description.isNotBlank()) {
                    Text(
                        text = schedule.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 시각과 일정 사이의 세로 축. 첫 줄 위와 마지막 줄 아래는 선을 끊어 목록의 시작과 끝을 알린다. */
@Composable
private fun TimelineRail(
    accent: Color,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier.width(24.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(if (isFirst) Color.Transparent else line),
            )
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .background(accent, CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(if (isLast) Color.Transparent else line),
            )
        }
    }
}
