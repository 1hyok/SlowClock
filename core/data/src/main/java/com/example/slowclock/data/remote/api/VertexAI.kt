package com.example.slowclock.data.remote.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// API 인터페이스 정의
interface VertexAIService {
    @POST("v1/projects/{projectId}/locations/{location}/publishers/google/models/{modelId}:generateContent")
    suspend fun generateContent(
        @Path("projectId") projectId: String,
        @Path("location") location: String,
        @Path("modelId") modelId: String,
        @Body request: GenerateContentRequest,
        @Header("Authorization") auth: String
    ): GenerateContentResponse
}

// 요청/응답 모델
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)

data class Content(
    val role: String,
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val maxOutputTokens: Int,
    val temperature: Float
)

data class GenerateContentResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: Content
)