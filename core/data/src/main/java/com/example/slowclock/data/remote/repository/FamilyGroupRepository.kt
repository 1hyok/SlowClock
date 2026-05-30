package com.example.slowclock.data.remote.repository

import android.content.Context
import com.example.slowclock.data.FirestoreDB
import com.example.slowclock.data.model.FamilyGroup
import com.example.slowclock.notification.GuardianNotifier
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.tasks.await

/**
 * FamilyGroup 컬렉션에 대한 저장소 클래스
 */
class FamilyGroupRepository {
    private val auth = FirebaseAuth.getInstance()
    private val familyGroupsCollection = FirestoreDB.familyGroups
    private val usersCollection = FirestoreDB.users

    // 사용자가 속한 가족 그룹 목록 가져오기
    suspend fun getUserFamilyGroups(): List<FamilyGroup> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        return try {
            familyGroupsCollection
                .whereArrayContains("memberIds", uid)
                .get()
                .await()
                .toObjects()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 사용자가 소유한 가족 그룹 목록 가져오기
    suspend fun getOwnedFamilyGroups(): List<FamilyGroup> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        return try {
            familyGroupsCollection
                .whereEqualTo("ownerUserId", uid)
                .get()
                .await()
                .toObjects()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 새 가족 그룹 생성
    suspend fun createFamilyGroup(name: String): String? {
        val uid = auth.currentUser?.uid ?: return null

        val familyGroup = FamilyGroup(
            name = name,
            ownerUserId = uid,
            memberIds = listOf(uid),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        return try {
            val docRef = familyGroupsCollection.document()
            val groupWithId = familyGroup.copy(id = docRef.id)
            docRef.set(groupWithId).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    // 가족 그룹에 멤버 추가
    suspend fun addMemberToGroup(groupId: String, memberId: String): Boolean {
        return try {
            familyGroupsCollection.document(groupId)
                .update(
                    mapOf(
                        "memberIds" to FieldValue.arrayUnion(memberId),
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 가족 그룹에서 멤버 제거
    suspend fun removeMemberFromGroup(groupId: String, memberId: String): Boolean {
        return try {
            familyGroupsCollection.document(groupId)
                .update(
                    mapOf(
                        "memberIds" to FieldValue.arrayRemove(memberId),
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 가족 그룹 정보 가져오기
    suspend fun getFamilyGroupById(groupId: String): FamilyGroup? {
        return try {
            val document = familyGroupsCollection.document(groupId).get().await()
            document.toObject<FamilyGroup>()
        } catch (e: Exception) {
            null
        }
    }

    // 가족 그룹 삭제
    suspend fun deleteFamilyGroup(groupId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false

        try {
            val group = getFamilyGroupById(groupId) ?: return false
            if (group.ownerUserId != uid) return false

            familyGroupsCollection.document(groupId).delete().await()
            return true
        } catch (e: Exception) {
            return false
        }
    }
    // 4. 해당 그룹 전체의 FCM 토큰 쿼리 (코루틴)
    suspend fun fetchGroupMembersFcmTokens(groupId: String): List<String> {
        val group = getFamilyGroupById(groupId) ?: return emptyList()
        val memberIds = group.memberIds
        if (memberIds.isEmpty()) return emptyList()
        return usersCollection
            .whereIn("id", memberIds)
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("fcmToken") }
    }

    // 5. 그룹 전체에게 FCM 알림 발송
    suspend fun sendAlertToGroup(context: Context, groupId: String, title: String, message: String) {
        val tokens = fetchGroupMembersFcmTokens(groupId)
        tokens.forEach { token ->
            GuardianNotifier.sendReminderToUser(
                context = context,
                fcmToken = token,
                title = title,
                message = message
            )
        }
    }
}