package com.example.slowclock.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.util.Date

/**
 * 일정 한 줄. 메인 화면과 완료 화면이 같이 쓴다.
 *
 * 왼쪽에 시각을 세로 두 줄로 두고 오른쪽에 제목을 둔다. 시각이 같은 자리에 정렬돼야 목록을
 * 위에서 아래로 훑을 때 시간 순서가 보인다. 종전에는 화면마다 다른 카드를 써서 시각이 제목
 * 아래에 작게 붙어 있었다(#109).
 *
 * 완료 표시는 오른쪽 끝의 동그란 버튼이다. 줄을 누르면 상세, 버튼을 누르면 완료다. 종전에는
 * 목록에서 완료할 방법이 없어 상세 창을 열어야 했다.
 *
 * 모델을 받지 않고 값만 받는다. core:ui 가 데이터 모듈을 알지 않게 한다.
 */
@Composable
fun ScheduleRow(
    title: String,
    time: Date,
    completed: Boolean,
    modifier: Modifier = Modifier,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    onToggleComplete: (() -> Unit)? = null,
) {
    val meridiem = rememberMeridiemText(time)
    val clock = rememberClockText(time)
    val accent =
        if (completed) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
    val titleColor =
        if (completed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color =
            if (completed) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(64.dp)) {
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

            Spacer(modifier = Modifier.width(12.dp))

            // 시각과 제목을 가르는 가는 선. 목록에 세로 축을 만들어 시간 순서를 읽기 쉽게 한다.
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onToggleComplete != null) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector =
                            if (completed) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                        contentDescription = if (completed) "완료 취소" else "완료로 표시",
                        tint =
                            if (completed) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
