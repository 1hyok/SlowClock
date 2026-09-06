package com.example.slowclock.data.remote.repository

import android.util.Log
import com.example.slowclock.data.FirestoreCollections
import com.example.slowclock.data.model.PublicProfile
import com.example.slowclock.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "UserRepository"

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
        private val publicProfilesCollection = firestore.collection(FirestoreCollections.PUBLIC_PROFILES)
        private val shareCodesCollection = firestore.collection(FirestoreCollections.SHARE_CODES)

        /**
         * 아직 임자가 없는 여섯 자리 코드를 하나 만들어 [uid] 앞으로 등록한다.
         *
         * 등록부는 코드 자체가 문서 이름이라, 다른 소유자의 코드에 대한 쓰기는 규칙이 거절한다.
         * 확인과 저장이 한 번으로 합쳐지므로 그 사이에 같은 코드를 두 사람이 가져가는 틈이 없다.
         * 질의로 확인하던 앞의 방식은 컬렉션을 훑을 권한을 함께 열어야 했다(#174).
         */
        private suspend fun claimShareCode(uid: String): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            repeat(SHARE_CODE_CLAIM_ATTEMPTS) {
                val code = (1..SHARE_CODE_LENGTH).map { chars.random() }.joinToString("")
                try {
                    shareCodesCollection.document(code).set(mapOf("userId" to uid)).await()
                    return code
                } catch (e: FirebaseFirestoreException) {
                    // 임자가 있는 코드였다. 그 밖의 실패는 다음 코드로 넘어가도 똑같이 실패하므로
                    // 여기서 던져 부르는 쪽이 안내로 바꾸게 한다.
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    Log.d(TAG, "이미 쓰이는 공유 코드다. 다시 만든다")
                }
            }
            error("공유 코드를 ${SHARE_CODE_CLAIM_ATTEMPTS}번 만들어 봤지만 모두 임자가 있었다")
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
                // 공유 코드 등록부는 본인만 지울 수 있다. 사용자 문서를 먼저 지우면 코드를 알
                // 길이 없어져 등록부에 영영 남고, 그 코드는 아무도 다시 쓰지 못한다(#174).
                val shareCode =
                    usersCollection
                        .document(userId)
                        .get()
                        .await()
                        .getString("shareCode")
                        .orEmpty()
                // 셋 중 하나만 지워지면 다음 시도에서 코드를 잃거나 삭제가 막힐 수 있다.
                firestore
                    .batch()
                    .apply {
                        if (shareCode.isNotBlank()) delete(shareCodesCollection.document(shareCode))
                        delete(usersCollection.document(userId))
                        delete(publicProfilesCollection.document(userId))
                    }.commit()
                    .await()
                true
            } catch (e: Exception) {
                Log.e(TAG, "사용자 문서 삭제 실패", e)
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
                            shareCode = claimShareCode(uid),
                            createdAt = existing?.createdAt ?: Timestamp.now(),
                            updatedAt = Timestamp.now(),
                        )
                    usersCollection.document(uid).set(newUser).await()
                    savePublicProfile(newUser.id, newUser.name)
                } else {
                    // 등록부 도입 전 발급된 코드도 같은 소유자로 등록한다. 타인 코드와 충돌하면
                    // 기존 코드·문서를 그대로 두고 실패한다. 자동 회전은 기존 일정을 분리한다.
                    firestore.ensureShareCodeOwner(uid, existing.shareCode)
                    val updates = mutableMapOf<String, Any>()
                    if (existing.name != name && name.isNotBlank()) updates["name"] = name
                    if (existing.email != email && email.isNotBlank()) updates["email"] = email
                    if (updates.isNotEmpty()) {
                        updates["updatedAt"] = Timestamp.now()
                        usersCollection.document(uid).update(updates).await()
                    }
                    // 공개 프로필이 아직 없거나 이름이 바뀐 경우를 함께 맞춘다.
                    savePublicProfile(uid, if (name.isNotBlank()) name else existing.name)
                }
                true
            } catch (e: Exception) {
                // 공유 코드가 없으면 그 뒤에 만든 일정이 sharedCode 없이 저장되고, 보안 규칙의
                // 공유 읽기 조건이 sharedCode != "" 라 가족이 어떤 코드로도 읽지 못한다.
                // 조용히 넘기면 사용자도 보호자도 무엇이 잘못됐는지 알 방법이 없다(#134).
                Log.e(TAG, "공유 코드 보장 실패", e)
                false
            }

        /**
         * 공개 프로필을 만들거나 갱신한다. 이름만 담으므로 다른 사용자가 읽어도 된다.
         * 실패해도 사용자 문서 저장 결과를 뒤집지 않는다. 다음 로그인에서 다시 맞춘다.
         */
        private suspend fun savePublicProfile(
            uid: String,
            name: String,
        ) {
            try {
                publicProfilesCollection
                    .document(uid)
                    .set(PublicProfile(id = uid, name = name))
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "공개 프로필 저장 실패", e)
            }
        }

        /**
         * 사용자 ID → 이름.
         *
         * 문서를 하나씩 읽는다. 공개 프로필은 목록 조회를 막아 두었다 — 목록이 열려 있으면 문서
         * 이름을 몰라도 컬렉션을 통째로 훑을 수 있기 때문이다. 여기 들어오는 uid 는 이미 읽을 수
         * 있는 공유 일정에서 나온 값이라 하나씩 읽는 것으로 충분하다(#174).
         */
        suspend fun getUserNames(userIds: List<String>): Map<String, String> {
            if (userIds.isEmpty()) return emptyMap()
            return try {
                coroutineScope {
                    userIds
                        .distinct()
                        .map { id ->
                            async {
                                id to
                                    publicProfilesCollection
                                        .document(id)
                                        .get()
                                        .await()
                                        .getString("name")
                                        .orEmpty()
                            }
                        }.awaitAll()
                        .filter { (_, name) -> name.isNotBlank() }
                        .toMap()
                }
            } catch (e: Exception) {
                Log.w(TAG, "공유 일정 소유자 이름 조회 실패", e)
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
                Log.e(TAG, "FCM 토큰 저장 실패", e)
                false
            }
        }

        /**
         * 공유 코드 감시자로 이 기기를 등록한다.
         *
         * 이 문서가 있다는 것이 곧 「그 코드를 받은 사람」 이라는 표시라, 공유 일정을 읽을 권한의
         * 근거가 된다. 그래서 FCM 토큰을 못 받아도 등록 자체는 한다. 토큰이 없으면 알림만 못 받고,
         * 등록을 거르면 가족 일정이 통째로 안 보인다(#174).
         */
        suspend fun registerShareCodeWatcher(shareCode: String): Boolean {
            val uid = auth.currentUser?.uid ?: return false
            return try {
                val token = runCatching { messaging.token.await() }.getOrNull()
                if (token == null) Log.w(TAG, "FCM 토큰 없이 감시자를 등록한다. 알림은 다음 등록에서 붙는다")
                val fields = mutableMapOf<String, Any>("userId" to uid)
                token?.let { fields["fcmToken"] = it }
                // merge 로 쓴다. 토큰을 못 받은 등록이 앞서 저장해 둔 토큰을 지우면 안 된다.
                shareCodeWatcherTokens(shareCode).document(uid).set(fields, SetOptions.merge()).await()
                true
            } catch (e: Exception) {
                Log.e(TAG, "공유 코드 감시자 등록 실패", e)
                false
            }
        }

        suspend fun unregisterShareCodeWatcher(shareCode: String): Boolean {
            val uid = auth.currentUser?.uid ?: return false
            return try {
                shareCodeWatcherTokens(shareCode).document(uid).delete().await()
                true
            } catch (e: Exception) {
                Log.e(TAG, "공유 코드 감시자 해제 실패", e)
                false
            }
        }

        private fun shareCodeWatcherTokens(shareCode: String) =
            firestore
                .collection(FirestoreCollections.SHARE_CODE_WATCHERS)
                .document(shareCode)
                .collection(FirestoreCollections.SHARE_CODE_WATCHER_TOKENS)

        private companion object {
            const val SHARE_CODE_LENGTH = 6
            const val SHARE_CODE_CLAIM_ATTEMPTS = 8
        }
    }
