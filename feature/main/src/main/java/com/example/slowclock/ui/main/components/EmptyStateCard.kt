package com.example.slowclock.ui.main.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.slowclock.ui.common.components.EmptyState

/** 오늘 일정이 없을 때. 표현은 공통 [EmptyState] 가 맡는다. */
@Composable
fun EmptyStateCard(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.EditCalendar,
        title = "오늘 등록된 일정이 없습니다",
        description = "아래 + 버튼으로 일정을 추가하세요",
        modifier = modifier,
    )
}
