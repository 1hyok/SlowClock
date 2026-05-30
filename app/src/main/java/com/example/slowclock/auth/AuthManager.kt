// auth/AuthManager.kt
package com.example.slowclock.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.slowclock.data.FirestoreDB
import com.example.slowclock.data.model.User
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val activity: ComponentActivity,
) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    fun initialize(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        signInLauncher =
            activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val response = IdpResponse.fromResultIntent(result.data)

                if (result.resultCode == Activity.RESULT_OK) {
                    val user = firebaseAuth.currentUser
                    Log.d("AUTH", "로그인 성공: ${user?.displayName} (${user?.email})")
                    Log.d("AUTH", "=== 사용자 정보 확인 ===")
                    Log.d("AUTH", "displayName: '${user?.displayName}'")
                    Log.d("AUTH", "email: '${user?.email}'")
                    Log.d("AUTH", "photoUrl: '${user?.photoUrl}'")
                    Log.d("AUTH", "uid: '${user?.uid}'")
                    // Ensure shareCode exists for this user
                    user?.let { ensureShareCodeForUser(it.uid, it.displayName ?: "", it.email ?: "") }
                    onSuccess()
                } else {
                    val error = response?.error?.message ?: "로그인이 취소되었습니다"
                    Log.e("AUTH", "로그인 실패: $error")
                    onError(error)
                }
            }
    }

    fun ensureShareCodeForUser(
        uid: String,
        name: String,
        email: String,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc =
                    FirestoreDB.users
                        .document(uid)
                        .get()
                        .await()
                val userModel = userDoc.toObject(User::class.java)
                if (userModel == null || userModel.shareCode.isBlank()) {
                    // Generate unique 6-character code
                    val code = generateUniqueShareCode()
                    val newUser =
                        User(
                            id = uid,
                            name = name,
                            email = email,
                            shareCode = code,
                            createdAt = userModel?.createdAt ?: Timestamp.now(),
                            updatedAt = Timestamp.now(),
                        )
                    FirestoreDB.users
                        .document(uid)
                        .set(newUser)
                        .await()
                } else {
                    // Ensure name and email are always up to date
                    val updates = mutableMapOf<String, Any>()
                    if (userModel.name != name && name.isNotBlank()) updates["name"] = name
                    if (userModel.email != email && email.isNotBlank()) updates["email"] = email
                    if (updates.isNotEmpty()) {
                        updates["updatedAt"] = Timestamp.now()
                        FirestoreDB.users
                            .document(uid)
                            .update(updates)
                            .await()
                    }
                }
            } catch (e: Exception) {
                Log.e("AUTH", "공유 코드 생성/저장 실패", e)
            }
        }
    }

    private suspend fun generateUniqueShareCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        while (true) {
            val code = (1..6).map { chars.random() }.joinToString("")
            val exists =
                FirestoreDB.users
                    .whereEqualTo("shareCode", code)
                    .get()
                    .await()
                    .documents
                    .isNotEmpty()
            if (!exists) return code
        }
    }

    fun getCurrentUser() = firebaseAuth.currentUser

    fun signInWithGoogle() {
        try {
            val providers =
                arrayListOf(
                    AuthUI.IdpConfig
                        .GoogleBuilder()
                        .setScopes(
                            listOf(
                                "https://www.googleapis.com/auth/userinfo.profile", // 프로필 이름
                                "https://www.googleapis.com/auth/userinfo.email", // 이메일
                                "https://www.googleapis.com/auth/calendar", // 기존 캘린더
                            ),
                        ).build(),
                )

            val signInIntent =
                AuthUI
                    .getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .setTosAndPrivacyPolicyUrls(
                        "https://example.com/terms.html",
                        "https://example.com/privacy.html",
                    ).build()

            Log.d("AUTH", "구글 로그인 시작")
            signInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e("AUTH", "구글 로그인 시작 실패", e)
        }
    }

//    fun signOut(context: Context, onComplete: () -> Unit = {}) {
//        AuthUI.getInstance()
//            .signOut(context)
//            .addOnCompleteListener {
//                Log.d("AUTH", "로그아웃 완료")
//                onComplete()
//            }
//            .addOnFailureListener { e ->
//                Log.e("AUTH", "로그아웃 실패", e)
//                onComplete() // 실패해도 콜백 호출
//            }
//    }
}
