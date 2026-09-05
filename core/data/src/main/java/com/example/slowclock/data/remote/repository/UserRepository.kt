package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.FirestoreCollections
import com.example.slowclock.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * User 컬렉션에 대한 저장소 클래스
 */
class UserRepository
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        private val firestore: FirebaseFirestore,
        private val messaging: FirebaseMessaging,
    ) {
        private val usersCollection = firestore.collection(FirestoreCollections.USERS)

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

        // 계정 삭제용: 사용자 문서 삭제
        suspend fun deleteUserDocument(userId: String): Boolean =
            try {
                usersCollection.document(userId).delete().await()
                true
            } catch (e: Exception) {
                false
            }

        /**
         * 로그인 직후 사용자 문서를 보장한다. 문서가 없거나 공유 코드가 비어 있으면 새 코드로
         * 만들고, 있으면 이름·이메일이 바뀐 경우만 맞춘다.
         */
        suspend fun ensureShareCode(
            uid: String,
            name: String,
            email: String,
        ): Boolean =
            try {
                val document = usersCollection.document(uid).get().await()
                val existing = document.toObject<User>()
                if (existing == null || existing.shareCode.isBlank()) {
                    val newUser =
                        User(
                            id = uid,
                            name = name,
                            email = email,
                            shareCode = generateUniqueShareCode(),
                            createdAt = existing?.createdAt ?: Timestamp.now(),
                            updatedAt = Timestamp.now(),
                        )
                    usersCollection.document(uid).set(newUser).await()
                } else {
                    val updates = mutableMapOf<String, Any>()
                    if (existing.name != name && name.isNotBlank()) updates["name"] = name
                    if (existing.email != email && email.isNotBlank()) updates["email"] = email
                    if (updates.isNotEmpty()) {
                        updates["updatedAt"] = Timestamp.now()
                        usersCollection.document(uid).update(updates).await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }

        /** 사용자 ID → 이름. Firestore `whereIn` 은 10개까지라 나눠 조회한다. */
        suspend fun getUserNames(userIds: List<String>): Map<String, String> {
            if (userIds.isEmpty()) return emptyMap()
            return try {
                val result = mutableMapOf<String, String>()
                userIds.distinct().chunked(FIRESTORE_WHERE_IN_LIMIT).forEach { chunk ->
                    usersCollection
                        .whereIn("id", chunk)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { it.toObject<User>() }
                        .forEach { result[it.id] = it.name }
                }
                result
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /** 현재 기기의 FCM 토큰을 현재 사용자 문서에 저장한다. */
        suspend fun saveCurrentUserFcmToken(): Boolean {
            val uid = auth.currentUser?.uid ?: return false
            return try {
                val token = messaging.token.await()
                usersCollection.document(uid).update("fcmToken", token).await()
                true
            } catch (e: Exception) {
                false
            }
        }

        suspend fun getCurrentUserFcmToken(): String? {
            val uid = auth.currentUser?.uid ?: return null
            return try {
                usersCollection
                    .document(uid)
                    .get()
                    .await()
                    .getString("fcmToken")
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }

        /** 공유 코드 감시자로 현재 기기 토큰을 등록한다. 공유 일정 변경 알림을 받기 위한 것이다. */
        suspend fun registerShareCodeWatcher(shareCode: String): Boolean {
            val uid = auth.currentUser?.uid ?: return false
            return try {
                val token = messaging.token.await()
                shareCodeWatcherTokens(shareCode).document(uid).set(mapOf("fcmToken" to token)).await()
                true
            } catch (e: Exception) {
                false
            }
        }

        suspend fun unregisterShareCodeWatcher(shareCode: String): Boolean {
            val uid = auth.currentUser?.uid ?: return false
            return try {
                shareCodeWatcherTokens(shareCode).document(uid).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        }

        private fun shareCodeWatcherTokens(shareCode: String) =
            firestore
                .collection(FirestoreCollections.SHARE_CODE_WATCHERS)
                .document(shareCode)
                .collection(FirestoreCollections.SHARE_CODE_WATCHER_TOKENS)

        private companion object {
            const val FIRESTORE_WHERE_IN_LIMIT = 10
        }
    }
