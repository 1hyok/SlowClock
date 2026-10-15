package com.example.slowclock.ui.addschedule.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.slowclock.feature.addschedule.R

@Composable
fun TitleInputSection(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // 필수 표시는 머리글이 지고 칸에는 label 을 두지 않는다. label 을 두면 Material3 가
            // 빈 칸에서 placeholder 를 감춰, 처음 보는 화면에서 "무엇을 할까요?" 가 사라진다.
            Text(
                text = stringResource(R.string.schedule_title_required),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                // 저장 조건은 빈칸일 때만 알리지 않는다. 글자를 처음 넣는 순간 안내가 사라지면
                // 그 높이만큼 아래 칸들이 딸려 올라온다. 큰 글자에서는 100dp 넘게 튄다.
                supportingText = { Text(stringResource(R.string.schedule_title_required_hint)) },
                placeholder = {
                    Text(
                        "무엇을 할까요?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "상세 내용 (선택사항)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = {
                    Text(
                        "자세한 내용을 입력하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}
