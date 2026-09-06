package com.example.slowclock.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import kotlinx.coroutines.launch

/**
 * Firebase UI 로그인 흐름을 Activity 에 붙인다. Firestore 사용자 문서는 [UserRepository] 가,
 * Auth 세션은 [AuthRepository] 가 맡는다.
 */
class AuthManager(
    private val activity: ComponentActivity,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
) {
    private companion object {
        // GitHub Pages(docs/) 에 게시된 문서. Play 콘솔의 개인정보처리방침 URL 과 같은 주소를 쓴다.
        const val TERMS_OF_SERVICE_URL = "https://1hyok.github.io/SlowClock/terms.html"
        const val PRIVACY_POLICY_URL = "https://1hyok.github.io/SlowClock/privacy.html"
    }

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
                    val profile = authRepository.currentProfile
                    Log.d("AUTH", "로그인 성공: ${profile?.displayName} (${profile?.email})")
                    profile?.let { ensureShareCodeForUser(it.uid, it.displayName, it.email) }
                    onSuccess()
                } else {
                    val error = response?.error?.message ?: "로그인이 취소되었습니다"
                    Log.e("AUTH", "로그인 실패: $error")
                    onError(error)
                }
            }
    }

    /**
     * 사용자 문서와 공유 코드를 보장한다. 이름·이메일이 바뀌었으면 함께 맞춘다.
     *
     * 코드를 얻은 뒤에는 코드 없이 저장돼 있던 일정에도 채운다. 코드만 만들면 그 전에 만든
     * 일정은 가족이 영영 못 읽는다(#178).
     */
    fun ensureShareCodeForUser(
        uid: String,
        name: String,
        email: String,
    ) {
        activity.lifecycleScope.launch {
            if (userRepository.ensureShareCode(uid, name, email)) {
                scheduleRepository.fillMissingSharedCode(uid)
            } else {
                Log.e("AUTH", "공유 코드 생성/저장 실패")
            }
        }
    }

    fun getCurrentUser(): AuthRepository.Profile? = authRepository.currentProfile

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
                            ),
                        ).build(),
                )

            val signInIntent =
                AuthUI
                    .getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .setTosAndPrivacyPolicyUrls(
                        TERMS_OF_SERVICE_URL,
                        PRIVACY_POLICY_URL,
                    ).build()

            Log.d("AUTH", "구글 로그인 시작")
            signInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e("AUTH", "구글 로그인 시작 실패", e)
        }
    }
}
