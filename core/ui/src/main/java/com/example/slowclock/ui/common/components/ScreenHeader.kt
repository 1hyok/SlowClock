package com.example.slowclock.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 탭 화면 상단의 제목 묶음. 탭마다 제목 위치와 여백이 달라 화면을 옮길 때마다 내용이 튀던 것을
 * 한 형태로 모은다. 상세 화면은 뒤로가기가 필요하므로 이 대신 `CenterAlignedTopAppBar` 를 쓴다.
 *
 * 왼쪽 정렬이다. 가운데 정렬은 제목 길이가 바뀔 때마다 글자가 좌우로 흔들리고, 아래 목록의
 * 왼쪽 축과도 어긋난다(#109).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
