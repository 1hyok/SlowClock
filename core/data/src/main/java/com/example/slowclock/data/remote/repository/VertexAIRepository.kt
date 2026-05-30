package com.example.slowclock.data.remote.repository

import android.content.Context
import android.util.Log
import com.example.slowclock.core.data.R
import com.example.slowclock.constants.AIConfig
import com.example.slowclock.data.remote.api.Content
import com.example.slowclock.data.remote.api.GenerateContentRequest
import com.example.slowclock.data.remote.api.GenerationConfig
import com.example.slowclock.data.remote.api.Part
import com.example.slowclock.data.remote.api.VertexAIService
import com.example.slowclock.data.remote.api.VertexAIServiceFactory
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class VertexAIRepository(private val context: Context) {
    private val TAG = "VertexAI_SLOWCLOCK"
    private val vertexAIService: VertexAIService = VertexAIServiceFactory.create()

    suspend fun generateScheduleRecommendation(
        prompt: String
    ): String? {
        return try {
            Log.d(TAG, "자격 증명 로드 시작")
            // Load service account credentials from raw resource
            val credentials = withContext(Dispatchers.IO) {
                val creds = ServiceAccountCredentials.fromStream(
                    context.resources.openRawResource(R.raw.service_account)
                ).createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
                creds.refresh()
                creds
            }

            Log.d(TAG, "인증 토큰 획득 성공")
            val token = "Bearer ${credentials.accessToken.tokenValue}"

            Log.d(TAG, "API 요청 시작: $prompt")
            val response = withContext(Dispatchers.IO) {
                vertexAIService.generateContent(
                    projectId = AIConfig.PROJECT_ID,
                    location = AIConfig.LOCATION,
                    modelId = AIConfig.MODEL_ID,
                    request = GenerateContentRequest(
                        contents = listOf(
                            Content(role = "user", parts = listOf(Part(text = prompt)))
                        ),
                        generationConfig = GenerationConfig(
                            maxOutputTokens = 256,
                            temperature = 0.2f
                        )
                    ),
                    auth = token
                )
            }

            val result = response.candidates.firstOrNull()?.content?.parts
                ?.joinToString("\n") { it.text }

            result

        } catch (e: HttpException) {
            Log.e(TAG, "일정 추천 생성 실패: HTTP ${e.code()}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "상세 에러: ${e.stackTraceToString()}")
            null
        }
    }
}