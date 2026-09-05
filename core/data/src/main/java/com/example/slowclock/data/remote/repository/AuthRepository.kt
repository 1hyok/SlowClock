package com.example.slowclock.data.remote.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase Auth 계정 자체를 다루는 저장소. Firestore 의 사용자 문서는 [UserRepository] 가 맡는다.
 */
class AuthRepository
    @Inject
    constructor() {
        private val auth = FirebaseAuth.getInstance()

        /** 로그인한 계정의 표시 정보. Firestore 사용자 문서가 없을 때의 대체값으로 쓴다. */
        data class Profile(
            val uid: String,
            val displayName: String,
            val email: String,
        )

        sealed interface DeleteResult {
            data object Success : DeleteResult

            data object NotSignedIn : DeleteResult

            /** Firebase 가 최근 로그인을 요구한다. 다시 로그인한 뒤 재시도해야 한다. */
            data object RecentLoginRequired : DeleteResult

            data class Failure(
                val message: String,
            ) : DeleteResult
        }

        val currentUid: String?
            get() = auth.currentUser?.uid

        val currentProfile: Profile?
            get() =
                auth.currentUser?.let { user ->
                    Profile(
                        uid = user.uid,
                        displayName = user.displayName.orEmpty(),
                        email = user.email.orEmpty(),
                    )
                }

        /** Auth 사용자를 지운다. 성공하면 Firebase 가 세션도 끝낸다. */
        suspend fun deleteCurrentUser(): DeleteResult {
            val user = auth.currentUser ?: return DeleteResult.NotSignedIn
            return try {
                user.delete().await()
                DeleteResult.Success
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                DeleteResult.RecentLoginRequired
            } catch (e: Exception) {
                DeleteResult.Failure(e.message ?: "계정 삭제에 실패했습니다")
            }
        }

        fun signOut() {
            auth.signOut()
        }
    }
