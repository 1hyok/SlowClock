package com.example.slowclock.ui.recommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Recommendation
import com.example.slowclock.ui.common.components.ScreenHeader
import com.example.slowclock.ui.recommendation.components.RecommendationList

private enum class RecommendationGroup(
    val label: String,
    val type: String,
) {
    ELDERLY("어르신", "노인"),
    ADHD("ADHD", "ADHD"),
    STUDENT("학생", "학생"),
}

private val allRecommendations =
    listOf(
        Recommendation("밥 먹기"),
        Recommendation("잠 자기"),
        Recommendation("약 먹기"),
        Recommendation("병원 예약하기"),
        Recommendation("운동하기"),
        Recommendation("감정 일기 쓰기", "ADHD"),
        Recommendation("회피 행동 돌아보기", "ADHD"),
        Recommendation("명상하기", "ADHD"),
        Recommendation("자습", "학생"),
        Recommendation("공부량 확인", "학생"),
        Recommendation("휴식하기", "학생"),
        Recommendation("복습하기", "학생"),
        Recommendation("햇빛 쬐기", "노인"),
        Recommendation("일기 쓰기", "노인"),
        Recommendation("노인정에서 교류하기", "노인"),
        Recommendation("음악 감상하기", "노인"),
        Recommendation("새로운 공부하기", "노인"),
        Recommendation("사회봉사하기", "노인"),
    )

/**
 * 일정 추천 화면. 서버 상태가 없어 ViewModel 을 두지 않는다. 고른 제목은 [onSelectRecommendation]
 * 으로 넘기고, 어느 화면에 돌려줄지는 NavGraph 가 정한다.
 *
 * 무리 고르기는 칩으로 둔다. 종전에는 세 개가 모두 꽉 찬 버튼이라 어느 것이 지금 보고 있는
 * 목록인지 알 수 없었다(#109).
 */
@Composable
fun RecommendationScreen(
    onSelectRecommendation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var group by rememberSaveable { mutableStateOf(RecommendationGroup.ELDERLY) }
    val shown = allRecommendations.filter { it.type == group.type || it.type == "일반" }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(title = "추천 일정", subtitle = "고르면 일정 제목으로 들어갑니다")

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecommendationGroup.entries.forEach { entry ->
                FilterChip(
                    selected = group == entry,
                    onClick = { group = entry },
                    shape = MaterialTheme.shapes.small,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    label = {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                )
            }
        }

        RecommendationList(
            recommendations = shown,
            onSelectRecommendation = onSelectRecommendation,
        )
    }
}
