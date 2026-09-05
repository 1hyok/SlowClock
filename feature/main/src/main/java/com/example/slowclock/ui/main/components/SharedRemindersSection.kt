package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.ScheduleRow

/**
 * 가족이 공유한 일정. 오늘의 일정과 같은 줄 모양을 쓴다.
 *
 * 종전에는 파란 카드 안에 작은 글씨로 완료·미완료 배지를 붙여 다른 목록과 생김새가 달랐다.
 * 같은 성격의 정보는 같은 모양으로 보여야 읽는 규칙이 하나로 유지된다(#109).
 *
 * 내가 만든 일정만 완료 버튼을 준다. 남의 일정은 누를 수 없으니 버튼을 두지 않는다.
 */
@Composable
fun SharedRemindersSection(
    sharedReminders: List<Schedule>,
    currentUserUid: String?,
    ownerNames: Map<String, String>,
    onToggleComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sharedReminders.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(text = "가족과 나눈 일정")
        sharedReminders.forEach { schedule ->
            val isMine = schedule.userId == currentUserUid
            ScheduleRow(
                title = schedule.title,
                time = schedule.startTime.toDate(),
                completed = schedule.completed,
                note = if (isMine) null else ownerNames[schedule.userId],
                onToggleComplete = if (isMine) ({ onToggleComplete(schedule.id) }) else null,
            )
        }
    }
}
