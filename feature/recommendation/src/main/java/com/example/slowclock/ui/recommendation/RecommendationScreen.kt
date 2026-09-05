package com.example.slowclock.ui.recommendation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.model.Recommendation
import com.example.slowclock.ui.recommendation.components.ADHDRecommendation
import com.example.slowclock.ui.recommendation.components.ElderlyRecommendation
import com.example.slowclock.ui.recommendation.components.StudentRecommendation

private enum class RecommendationGroup {
    ELDERLY,
    ADHD,
    STUDENT,
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
 */
@Composable
fun RecommendationScreen(
    onSelectRecommendation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var group by rememberSaveable { mutableStateOf(RecommendationGroup.ELDERLY) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            Button(onClick = { group = RecommendationGroup.ELDERLY }, modifier = Modifier.padding(4.dp)) {
                Text(text = "노인")
            }
            Button(onClick = { group = RecommendationGroup.ADHD }, modifier = Modifier.padding(4.dp)) {
                Text(text = "ADHD")
            }
            Button(onClick = { group = RecommendationGroup.STUDENT }, modifier = Modifier.padding(4.dp)) {
                Text(text = "학생")
            }
        }

        when (group) {
            RecommendationGroup.ELDERLY -> {
                ElderlyRecommendation(
                    list = allRecommendations,
                    onSelectRecommendation = onSelectRecommendation,
                )
            }

            RecommendationGroup.ADHD -> {
                ADHDRecommendation(
                    list = allRecommendations,
                    onSelectRecommendation = onSelectRecommendation,
                )
            }

            RecommendationGroup.STUDENT -> {
                StudentRecommendation(
                    list = allRecommendations,
                    onSelectRecommendation = onSelectRecommendation,
                )
            }
        }
    }
}
