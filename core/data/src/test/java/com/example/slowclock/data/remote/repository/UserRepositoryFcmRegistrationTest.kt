package com.example.slowclock.data.remote.repository

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryFcmRegistrationTest {
    private val auth = mockk<FirebaseAuth>()
    private val firestore = mockk<FirebaseFirestore>()
    private val messaging = mockk<FirebaseMessaging>()
    private val user = mockk<FirebaseUser>()
    private val users = mockk<CollectionReference>()
    private val userDocument = mockk<DocumentReference>()
    private val watchers = mockk<CollectionReference>()
    private val watcherDocument = mockk<DocumentReference>()
    private val repository: UserRepository

    init {
        every { user.uid } returns "uid"
        every { auth.currentUser } returns user
        every { firestore.collection(any()) } returns mockk()
        every { firestore.collection("users") } returns users
        every { users.document("uid") } returns userDocument
        every { userDocument.update("fcmToken", any()) } returns Tasks.forResult(null)
        val codes = mockk<CollectionReference>()
        val code = mockk<DocumentReference>()
        every { firestore.collection("shareCodeWatchers") } returns codes
        every { codes.document("CODE01") } returns code
        every { code.collection("tokens") } returns watchers
        every { watchers.document("uid") } returns watcherDocument
        every { watcherDocument.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
        repository = UserRepository(auth, firestore, messaging)
    }

    @Test
    fun `갱신된 토큰은 사용자와 현재 공유 감시자에 같이 쓴다`() {
        repository.updateFcmRegistration("new-token", "uid", "CODE01")
        verify(exactly = 1) { userDocument.update("fcmToken", "new-token") }
        verify(exactly = 1) { watcherDocument.set(mapOf("userId" to "uid", "fcmToken" to "new-token"), any<SetOptions>()) }
        verify(exactly = 0) { watcherDocument.delete() }
    }

    @Test
    fun `코드를 해제한 세션은 감시자를 재생성하지 않는다`() {
        repository.updateFcmRegistration("new-token", "uid", null)
        verify(exactly = 1) { userDocument.update("fcmToken", "new-token") }
        verify(exactly = 0) { watchers.document(any()) }
    }

    @Test
    fun `다른 계정이나 로그아웃 뒤 도착한 갱신은 이전 경로를 쓰지 않는다`() {
        repository.updateFcmRegistration("new-token", "other-uid", "CODE01")
        every { auth.currentUser } returns null
        repository.updateFcmRegistration("new-token", "uid", "CODE01")
        verify(exactly = 0) { userDocument.update("fcmToken", any()) }
        verify(exactly = 0) { watcherDocument.set(any(), any<SetOptions>()) }
    }

    @Test
    fun `빈 토큰으로 앞 등록을 덮지 않는다`() {
        repository.updateFcmRegistration(" ", "uid", "CODE01")
        verify(exactly = 0) { userDocument.update("fcmToken", any()) }
        verify(exactly = 0) { watcherDocument.set(any(), any<SetOptions>()) }
    }

    @Test
    fun `토큰 조회 중 로그아웃하면 감시자를 뒤늦게 등록하지 않는다`() =
        runTest {
            val token = TaskCompletionSource<String>()
            every { messaging.token } returns token.task
            val result = async { repository.registerShareCodeWatcher("CODE01") }
            runCurrent()
            every { auth.currentUser } returns null
            token.setResult("late-token")
            assertFalse(result.await())
            verify(exactly = 0) { watcherDocument.set(any(), any<SetOptions>()) }
        }

    @Test
    fun `사용자 토큰 조회 중 계정이 바뀌면 앞 사용자에 쓰지 않는다`() =
        runTest {
            val token = TaskCompletionSource<String>()
            every { messaging.token } returns token.task
            val result = async { repository.saveCurrentUserFcmToken() }
            runCurrent()
            val nextUser = mockk<FirebaseUser>()
            every { nextUser.uid } returns "next"
            every { auth.currentUser } returns nextUser
            token.setResult("late-token")
            assertFalse(result.await())
            verify(exactly = 0) { userDocument.update("fcmToken", any()) }
        }
}
