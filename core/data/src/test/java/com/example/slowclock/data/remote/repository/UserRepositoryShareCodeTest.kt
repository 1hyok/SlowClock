package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.PublicProfile
import com.example.slowclock.data.model.User
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UserRepositoryShareCodeTest {
    private val uid = "owner"
    private val code = "ABC123"
    private val firestore = mockk<FirebaseFirestore>()
    private val users = mockk<CollectionReference>()
    private val profiles = mockk<CollectionReference>()
    private val registry = mockk<CollectionReference>()
    private val userRef = mockk<DocumentReference>()
    private val profileRef = mockk<DocumentReference>()
    private val codeRef = mockk<DocumentReference>()
    private val snapshot = mockk<DocumentSnapshot>()
    private val batch = mockk<WriteBatch>()
    private val repository: UserRepository

    init {
        every { firestore.collection("users") } returns users
        every { firestore.collection("publicProfiles") } returns profiles
        every { firestore.collection("shareCodes") } returns registry
        every { users.document(uid) } returns userRef
        every { profiles.document(uid) } returns profileRef
        every { registry.document(any()) } returns codeRef
        every { userRef.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObject(User::class.java) } returns User(id = uid, name = "기존 이름", shareCode = code)
        every { snapshot.getString("shareCode") } returns code
        every { codeRef.set(any()) } returns Tasks.forResult(null)
        every { userRef.set(any()) } returns Tasks.forResult(null)
        every { userRef.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)
        every { profileRef.set(any()) } returns Tasks.forResult(null)
        every { firestore.batch() } returns batch
        every { batch.delete(any()) } returns batch
        every { batch.commit() } returns Tasks.forResult(null)
        repository = UserRepository(mockk<FirebaseAuth>(), firestore, mockk<FirebaseMessaging>())
    }

    @Test
    fun `이전 계정은 등록부 확인이 끝나야 사용자와 공개 프로필을 갱신한다`() =
        runTest {
            val pending = TaskCompletionSource<Void>()
            every { codeRef.set(mapOf("userId" to uid)) } returns pending.task

            val result = async { repository.ensureShareCode(uid, "새 이름", "new@example.com") }
            runCurrent()

            assertFalse(result.isCompleted)
            verify(exactly = 0) { userRef.set(any()) }
            verify(exactly = 0) { userRef.update(any<Map<String, Any>>()) }
            verify(exactly = 0) { profileRef.set(any()) }
            pending.setResult(null)
            assertTrue(result.await())
            verify(exactly = 1) { userRef.update(match<Map<String, Any>> { it["name"] == "새 이름" }) }
            verify(exactly = 1) { profileRef.set(PublicProfile(id = uid, name = "새 이름")) }
        }

    @Test
    fun `기존 코드가 충돌하면 자동 회전하지 않고 사용자와 일정을 보존한다`() =
        runTest {
            every { codeRef.set(any()) } returns Tasks.forException(denied())

            assertFalse(repository.ensureShareCode(uid, "새 이름", "new@example.com"))

            verify(exactly = 1) { registry.document(code) }
            verify(exactly = 1) { codeRef.set(mapOf("userId" to uid)) }
            verify(exactly = 0) { userRef.set(any()) }
            verify(exactly = 0) { userRef.update(any<Map<String, Any>>()) }
            verify(exactly = 0) { profileRef.set(any()) }
            verify(exactly = 0) { firestore.collection("schedules") }
        }

    @Test
    fun `새 사용자는 등록한 여섯 자리 코드로 문서를 만든다`() =
        runTest {
            every { snapshot.toObject(User::class.java) } returns null
            val claimedCode = slot<String>()
            val savedUser = slot<User>()
            every { registry.document(capture(claimedCode)) } returns codeRef
            every { userRef.set(capture(savedUser)) } returns Tasks.forResult(null)

            assertTrue(repository.ensureShareCode(uid, "이름", "new@example.com"))

            assertTrue(claimedCode.captured.matches(Regex("[A-Z0-9]{6}")))
            assertEquals(claimedCode.captured, savedUser.captured.shareCode)
            assertEquals(uid, savedUser.captured.id)
            verify(exactly = 1) { codeRef.set(mapOf("userId" to uid)) }
        }

    @Test
    fun `계정 삭제는 등록부 사용자 공개 프로필을 한 batch에 넣는다`() =
        runTest {
            assertTrue(repository.deleteUserDocument(uid))

            verify(exactly = 1) { batch.delete(codeRef) }
            verify(exactly = 1) { batch.delete(userRef) }
            verify(exactly = 1) { batch.delete(profileRef) }
            verify(exactly = 1) { batch.commit() }
            verify(exactly = 0) { codeRef.delete() }
            verify(exactly = 0) { userRef.delete() }
            verify(exactly = 0) { profileRef.delete() }
        }

    @Test
    fun `공유 코드가 없으면 사용자와 프로필만 batch로 지운다`() =
        runTest {
            every { snapshot.getString("shareCode") } returns null

            assertTrue(repository.deleteUserDocument(uid))

            verify(exactly = 2) { batch.delete(any()) }
            verify(exactly = 0) { registry.document(any()) }
            verify(exactly = 1) { batch.commit() }
        }

    @Test
    fun `삭제는 batch 응답을 기다리며 실패 뒤 같은 문서들로 재시도한다`() =
        runTest {
            val pending = TaskCompletionSource<Void>()
            every { batch.commit() } returns pending.task
            val result = async { repository.deleteUserDocument(uid) }
            runCurrent()
            assertFalse(result.isCompleted)
            pending.setException(denied())
            assertFalse(result.await())

            every { batch.commit() } returns Tasks.forResult(null)
            assertTrue(repository.deleteUserDocument(uid))
            verify(exactly = 2) { batch.delete(codeRef) }
            verify(exactly = 2) { batch.delete(userRef) }
            verify(exactly = 2) { batch.delete(profileRef) }
            verify(exactly = 2) { batch.commit() }
        }

    @Test
    fun `사용자 코드 읽기가 실패하면 어떤 삭제도 시작하지 않는다`() =
        runTest {
            every { userRef.get() } returns Tasks.forException(denied())

            assertFalse(repository.deleteUserDocument(uid))

            verify(exactly = 0) { firestore.batch() }
            verify(exactly = 0) { batch.commit() }
        }

    private fun denied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
}
