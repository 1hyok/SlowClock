package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.FirestoreCollections
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * familyGroups 컬렉션. 앱에 가족 그룹 화면이 없어 지금은 계정 삭제 때 정리하는 용도만 남았다.
 * 그룹 기능을 다시 붙이면 여기에 조회·생성을 더한다.
 */
class FamilyGroupRepository
    @Inject
    constructor(
        firestore: FirebaseFirestore,
    ) {
        private val familyGroupsCollection = firestore.collection(FirestoreCollections.FAMILY_GROUPS)

        // 계정 삭제용: 소유한 그룹은 지우고, 참여한 그룹에서는 구성원에서 뺀다
        suspend fun leaveAllGroupsOf(userId: String): Boolean =
            try {
                val owned =
                    familyGroupsCollection
                        .whereEqualTo("ownerUserId", userId)
                        .get()
                        .await()
                owned.documents.forEach { it.reference.delete().await() }

                val joined =
                    familyGroupsCollection
                        .whereArrayContains("memberIds", userId)
                        .get()
                        .await()
                joined.documents.forEach { document ->
                    document.reference
                        .update(
                            mapOf(
                                "memberIds" to FieldValue.arrayRemove(userId),
                                "updatedAt" to Timestamp.now(),
                            ),
                        ).await()
                }
                true
            } catch (e: Exception) {
                false
            }
    }
