package com.example.slowclock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

// Schedule 모델
data class Schedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val familyGroupId: String = "",
    val sharedCode: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp? = null,
    val completed: Boolean = false,
    val recurring: Boolean = false,
    val recurringType: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)