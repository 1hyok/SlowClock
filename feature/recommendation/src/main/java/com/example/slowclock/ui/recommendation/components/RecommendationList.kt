package com.example.slowclock.ui.recommendation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Recommendation

/**
 * 추천 일정 목록.
 *
 * 종전에는 노인·ADHD·학생용으로 같은 파일이 세 벌 있었고 걸러내는 문자열 하나만 달랐다. 한 벌로
 * 합치고 어떤 목록을 볼지는 화면이 정한다(#109).
 */
@Composable
fun RecommendationList(
    recommendations: List<Recommendation>,
    onSelectRecommendation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recommendations, key = { it.title }) { item ->
            RecommendationRow(
                title = item.title,
                onSelect = { onSelectRecommendation(item.title) },
            )
        }
    }
}

@Composable
private fun RecommendationRow(
    title: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                    .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSelect,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(text = "고르기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
