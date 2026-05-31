package com.example.slowclock.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slowclock.data.remote.repository.VertexAIRepository
import com.example.slowclock.ui.theme.SlowClockTheme
import kotlinx.coroutines.launch

class AITestActivity : ComponentActivity() {
    private lateinit var vertexAIManager: VertexAIRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 매니저 초기화
        vertexAIManager = VertexAIRepository(this)

        setContent {
            SlowClockTheme {
                AITestScreen(vertexAIManager)
            }
        }
    }
}

@Composable
fun AITestScreen(aiManager: VertexAIRepository) {
    var prompt by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize(),
        ) {
            Text(
                text = "AI 테스트",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("프롬프트") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val response = aiManager.generateScheduleRecommendation(prompt)
                            result = response ?: "응답 없음"
                        } catch (e: Exception) {
                            result = "오류: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("실행")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "결과:",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
