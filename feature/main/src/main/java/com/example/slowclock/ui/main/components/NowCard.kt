package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.rememberTimeText
import com.example.slowclock.util.hasExtraInfo
import com.example.slowclock.util.isOngoing
import java.util.Date

/**
 * 화면의 주인공. 지금 해야 할 일 하나를 크게 보여 준다.
 *
 * 고령자가 앱을 켜서 알고 싶은 것은 "지금 무엇을 해야 하는가" 하나다. 종전에는 이 정보가 다른
 * 카드와 같은 크기로 목록 가운데 섞여 있었다(#109). 시각을 가장 큰 글자로 두고, 완료 버튼을
 * 카드 안에 두어 손이 닿는 자리에서 끝낼 수 있게 한다.
 *
 * 색은 주황 계열 하나만 쓴다. 이 자리에서만 쓰는 색이라 화면에서 저절로 눈에 띈다.
 */
@Composable
fun NowCard(
    schedule: Schedule,
    onShowDetail: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val ongoing = isOngoing(schedule, nowMillis)
    val endTime = schedule.endTime?.toDate()
    val startText = rememberTimeText(schedule.startTime.toDate())
    val endText = rememberTimeText(endTime ?: Date(schedule.startTime.toDate().time))

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ongoing) "지금 하고 있어요" else "다음 할 일",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Text(
                text = if (ongoing && endTime != null) "$endText 까지" else startText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Text(
                text = schedule.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (schedule.description.isNotBlank()) {
                Text(
                    text = schedule.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onComplete,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "다 했어요", style = MaterialTheme.typography.titleMedium)
            }

            if (hasExtraInfo(schedule)) {
                TextButton(
                    onClick = onShowDetail,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "자세히 보기",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
