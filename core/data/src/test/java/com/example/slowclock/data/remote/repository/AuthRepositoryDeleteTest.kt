package com.example.slowclock.data.remote.repository

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryDeleteTest {
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val oldUser = mockk<FirebaseUser>()
    private val newUser = mockk<FirebaseUser>()
    private val repository = AuthRepository(firebaseAuth)
    private val response = TaskCompletionSource<Void>()

    init {
        every { oldUser.uid } returns "old"
        every { newUser.uid } returns "new"
        every { firebaseAuth.currentUser } returns oldUser
        every { oldUser.delete() } returns response.task
    }

    @Test
    fun `현재 사용자가 기대한 UID와 다르면 Auth 삭제를 시작하지 않는다`() =
        runTest {
            every { firebaseAuth.currentUser } returns newUser
            assertEquals(AuthRepository.DeleteResult.NotSignedIn, repository.deleteCurrentUser("old"))
            verify(exactly = 0) { newUser.delete() }
            verify(exactly = 0) { oldUser.delete() }
        }

    @Test
    fun `삭제 응답을 기다리는 동안 계정이 바뀌어도 새 사용자를 다시 고르지 않는다`() =
        runTest {
            val result = async { repository.deleteCurrentUser("old") }
            runCurrent()
            every { firebaseAuth.currentUser } returns newUser
            response.setResult(null)
            assertEquals(AuthRepository.DeleteResult.Success, result.await())
            verify(exactly = 1) { oldUser.delete() }
            verify(exactly = 0) { newUser.delete() }
        }

    @Test
    fun `Auth 삭제 대기 취소는 일반 실패로 바꾸지 않는다`() =
        runTest {
            val result = async { repository.deleteCurrentUser("old") }
            runCurrent()
            result.cancel()
            runCurrent()
            assertTrue(result.isCancelled)
        }
}
