package com.example.slowclock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// FamilyGroup 모델
data class FamilyGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUserId: String = "",
    val memberIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)