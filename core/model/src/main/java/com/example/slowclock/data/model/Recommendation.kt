package com.example.slowclock.data.model

@kotlinx.serialization.Serializable
data class Recommendation(
    val title: String,
    val type: String = "일반"
)