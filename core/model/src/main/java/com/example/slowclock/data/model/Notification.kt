package com.example.slowclock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Notification 모델
data class Notification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val relatedScheduleId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)