package com.example.slowclock.ui.recommendation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.slowclock.data.model.Recommendation
import com.example.slowclock.ui.recommendation.components.ADHDRecommendation
import com.example.slowclock.ui.recommendation.components.ElderlyRecommendation
import com.example.slowclock.ui.recommendation.components.StudentRecommendation

@Composable
fun RecommendationScreen(navController: NavController, modifier: Modifier = Modifier) {
    val userInfo = remember { mutableStateOf("Elderly") }
    val allRecommendation = listOf(
        Recommendation("밥 먹기"),
        Recommendation("잠 자기"),
        Recommendation("약 먹기"),
        Recommendation("병원 예약하기"),
        Recommendation("운동하기"),
        Recommendation("감정 일기 쓰기","ADHD"),
        Recommendation("회피 행동 돌아보기","ADHD"),
        Recommendation("명상하기","ADHD"),
        Recommendation("자습","학생"),
        Recommendation("공부량 확인","학생"),
        Recommendation("휴식하기","학생"),
        Recommendation("복습하기","학생"),
        Recommendation("햇빛 쬐기", "노인"),
        Recommendation("일기 쓰기","노인"),
        Recommendation("노인정에서 교류하기", "노인"),
        Recommendation("음악 감상하기","노인"),
        Recommendation("새로운 공부하기","노인"),
        Recommendation("사회봉사하기","노인")
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            Button(onClick = { userInfo.value = "Elderly" }, modifier = Modifier.padding(4.dp)) {
                Text(text = "노인")
            }
            Button(onClick = { userInfo.value = "ADHD" }, modifier = Modifier.padding(4.dp)) {
                Text(text = "ADHD")
            }
            Button(onClick = { userInfo.value = "Student" }, modifier = Modifier.padding(4.dp)) {
                Text(text = "학생")
            }
        }

        when (userInfo.value) {
            "Elderly" -> ElderlyRecommendation(list = allRecommendation,
                onSelectRecommendation = { selectedTitle ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("initial_title", selectedTitle)
                    navController.popBackStack()
                })
            "ADHD" -> ADHDRecommendation(list = allRecommendation,
                onSelectRecommendation = { selectedTitle ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("initial_title", selectedTitle)
                    navController.popBackStack()
                })
            else -> StudentRecommendation(list = allRecommendation,
                onSelectRecommendation = { selectedTitle ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("initial_title", selectedTitle)
                    navController.popBackStack()
                })
        }
    }
}