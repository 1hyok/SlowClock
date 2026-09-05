package com.example.slowclock.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * 오늘 몇 개 중 몇 개를 끝냈는지. 메인 화면과 완료 화면이 같이 쓴다.
 *
 * 막대에는 접근성 설명을 두지 않는다. 바로 위 문장이 같은 내용을 말하므로 화면 낭독기가 두 번
 * 읽으면 오히려 방해가 된다(#109).
 */
@Composable
fun DayProgress(
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    if (totalCount <= 0) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "오늘 일정 ${totalCount}개",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${completedCount}개 완료",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        LinearProgressIndicator(
            progress = { completedCount.toFloat() / totalCount },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clearAndSetSemantics {},
        )
    }
}
