package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.ui.common.components.DayProgress

/**
 * 날짜와 오늘 진행 상황. 화면 맨 위에서 "오늘이 어떤 날이고 얼마나 남았는가" 를 답한다.
 *
 * 종전에는 날짜가 목록 안에 가운데 정렬된 한 줄로 떠 있어 어디에도 속하지 않았다(#109).
 */
@Composable
fun DayHeader(
    dateText: String,
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayProgress(completedCount = completedCount, totalCount = totalCount)
    }
}
