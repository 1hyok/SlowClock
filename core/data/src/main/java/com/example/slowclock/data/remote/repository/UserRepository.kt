package com.example.slowclock.data.remote.repository

import android.content.Context
import com.example.slowclock.data.FirestoreDB
import com.example.slowclock.data.model.User
import com.example.slowclock.notification.GuardianNotifier
import com.example.slowclock.util.GroupIdHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * User 컬렉션에 대한 저장소 클래스
 */
class UserRepository
    @Inject
    constructor() {
        private val auth = FirebaseAuth.getInstance()
        private val usersCollection = FirestoreDB.users

        // 6-character code generator
        private suspend fun generateUniqueShareCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            while (true) {
                val code =
                    (1..6)
                        .map { chars.random() }
                        .joinToString("")
                // Check uniqueness in Firestore
                val exists =
                    usersCollection
                        .whereEqualTo("shareCode", code)
                        .get()
                        .await()
                        .documents
                        .isNotEmpty()
                if (!exists) return code
            }
        }

        // 현재 로그인한 사용자 정보 가져오기
        suspend fun getCurrentUser(): User? {
            val uid = auth.currentUser?.uid ?: return null
            return try {
                val document = usersCollection.document(uid).get().await()
                document.toObject<User>()
            } catch (e: Exception) {
                null
            }
        }

        // 사용자 정보 저장/업데이트
        suspend fun saveUser(user: User): Boolean =
            try {
                val userToSave =
                    if (user.shareCode.isBlank()) {
                        user.copy(shareCode = generateUniqueShareCode())
                    } else {
                        user
                    }
                usersCollection.document(user.id).set(userToSave).await()
                true
            } catch (e: Exception) {
                false
            }

        // ID로 사용자 정보 가져오기
        suspend fun getUserById(userId: String): User? =
            try {
                val document = usersCollection.document(userId).get().await()
                document.toObject<User>()
            } catch (e: Exception) {
                null
            }

        suspend fun createOrJoinGroup(inputGroupId: String? = null): Boolean {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
            val db = FirebaseFirestore.getInstance()
            val groupId = inputGroupId ?: GroupIdHelper.generateGroupId(6) // Use input or generate new
            val userMap = mapOf("familyGroupId" to groupId)
            return try {
                db
                    .collection("users")
                    .document(uid)
                    .set(userMap, SetOptions.merge())
                    .await()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun fetchFamilyTokens(
            groupId: String,
            onTokens: (List<String>) -> Unit,
        ) {
            val db = FirebaseFirestore.getInstance()
            db
                .collection("users")
                .whereEqualTo("familyGroupId", groupId)
                .get()
                .addOnSuccessListener { result ->
                    // fcmToken이 null이 아닌 것만 리스트로 반환
                    val tokens = result.documents.mapNotNull { it.getString("fcmToken") }
                    onTokens(tokens)
                }.addOnFailureListener {
                    onTokens(emptyList()) // 실패 시 빈 리스트 반환
                }
        }

        fun sendAlertToFamily(
            context: Context,
            groupId: String,
        ) {
            fetchFamilyTokens(groupId) { tokens ->
                tokens.forEach { token ->
                    GuardianNotifier.sendReminderToUser(
                        context = context,
                        fcmToken = token,
                        title = "가족 일정 알림",
                        message = "가족 그룹에서 새로운 알림이 도착했습니다.",
                    )
                }
            }
        }
    }
