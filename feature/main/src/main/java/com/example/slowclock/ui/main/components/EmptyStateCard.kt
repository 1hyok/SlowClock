// app/src/main/java/com/example/slowclock/ui/main/components/EmptyStateCard.kt
package com.example.slowclock.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer // 하드코딩 색상 제거
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(40.dp), // 32dp → 40dp
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📅",
                fontSize = 72.sp // 64sp → 72sp (더 큰 이모지)
            )
            Spacer(modifier = Modifier.height(20.dp)) // 16dp → 20dp
            Text(
                text = "오늘 등록된 일정이 없습니다",
                style = MaterialTheme.typography.headlineSmall, // fontSize 대신 style 사용
                color = MaterialTheme.colorScheme.onTertiaryContainer // 하드코딩 색상 제거
            )
            Spacer(modifier = Modifier.height(12.dp)) // 8dp → 12dp
            Text(
                text = "아래 + 버튼을 눌러 일정을 추가해보세요",
                style = MaterialTheme.typography.bodyLarge, // fontSize 대신 style 사용
                color = MaterialTheme.colorScheme.onTertiaryContainer // 하드코딩 색상 제거
            )
        }
    }
}