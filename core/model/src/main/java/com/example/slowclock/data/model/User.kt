package com.example.slowclock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// User 모델
data class User(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val shareCode: String = "", // 6-character sharing code
    val fcmToken: String = "", // FCM 토큰 필드 추가
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)