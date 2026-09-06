package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleRepositoryShareCodeTest {
    private val uid = "owner"
    private val code = "ABC123"
    private val auth = mockk<FirebaseAuth>()
    private val firestore = mockk<FirebaseFirestore>()
    private val users = mockk<CollectionReference>()
    private val schedules = mockk<CollectionReference>()
    private val registry = mockk<CollectionReference>()
    private val userRef = mockk<DocumentReference>()
    private val scheduleRef = mockk<DocumentReference>()
    private val codeRef = mockk<DocumentReference>()
    private val snapshot = mockk<DocumentSnapshot>()
    private val repository: ScheduleRepository
    private val schedule = Schedule(id = "schedule", title = "혈압약", userId = uid, sharedCode = code)

    init {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        every { auth.currentUser } returns user
        every { firestore.collection("users") } returns users
        every { firestore.collection("schedules") } returns schedules
        every { firestore.collection("shareCodes") } returns registry
        every { users.document(uid) } returns userRef
        every { schedules.document() } returns scheduleRef
        every { schedules.document(schedule.id) } returns scheduleRef
        every { scheduleRef.id } returns schedule.id
        every { registry.document(code) } returns codeRef
        every { userRef.get() } returns Tasks.forResult(snapshot)
        every { snapshot.getString("shareCode") } returns code
        every { codeRef.set(any()) } returns Tasks.forResult(null)
        every { scheduleRef.set(any()) } returns Tasks.forResult(null)
        every { scheduleRef.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)
        repository = ScheduleRepository(auth, firestore)
    }

    @Test
    fun `신규 일정과 등록부는 같은 commit 확인까지 성공을 반환하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id)
            val pending = TaskCompletionSource<Any?>()
            var saved: Any? = null
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
                saved = firstArg<Transaction.Function<Any?>>().apply(f.transaction)
                pending.task
            }
            val result = async { f.repository.addSchedule(schedule) }
            runCurrent()
            assertFalse(result.isCompleted)
            verify(exactly = 1) { f.transaction.set(f.registryRef, mapOf("userId" to uid)) }
            verify(exactly = 1) { f.transaction.set(f.scheduleRef, any()) }
            verify(exactly = 0) { f.registryRef.set(any()) }
            verify(exactly = 0) { f.scheduleRef.set(any()) }
            pending.setResult(saved)
            assertEquals(ScheduleRepository.ScheduleResult.Success(saved), result.await())
        }

    @Test
    fun `편집과 등록부도 같은 commit 확인까지 성공을 반환하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id, server = schedule)
            val pending = TaskCompletionSource<Any?>()
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
                firstArg<Transaction.Function<Any?>>().apply(f.transaction)
                pending.task
            }
            val result = async { f.repository.updateSchedule(schedule) }
            runCurrent()
            assertFalse(result.isCompleted)
            verify(exactly = 1) { f.transaction.set(f.registryRef, mapOf("userId" to uid)) }
            verify(exactly = 1) { f.transaction.update(f.scheduleRef, any<Map<String, Any?>>()) }
            verify(exactly = 0) { f.registryRef.set(any()) }
            pending.setResult(Unit)
            assertEquals(ScheduleRepository.ScheduleResult.Success(Unit), result.await())
        }

    @Test
    fun `다른 계정의 이전 일정은 등록부와 편집 쓰기를 모두 막는다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id, server = schedule).apply { uid = "next-user" }
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.PermissionError), f.repository.updateSchedule(schedule))
            verify(exactly = 0) { f.transaction.set(any(), any()) }
            verify(exactly = 0) { f.transaction.update(any<DocumentReference>(), any<Map<String, Any?>>()) }
        }

    @Test
    fun `등록부 충돌로 거절된 commit은 저장과 편집을 성공으로 반환하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id, server = schedule)
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } returns Tasks.forException(denied())
            val results = listOf(f.repository.addSchedule(schedule), f.repository.updateSchedule(schedule))
            results.forEach { assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.PermissionError), it) }
            verify(exactly = 0) { f.registryRef.set(any()) }
            verify(exactly = 0) { f.scheduleRef.set(any()) }
            verify(exactly = 0) { f.scheduleRef.update(any<Map<String, Any?>>()) }
        }

    @Test
    fun `등록부를 포함한 commit의 연결 실패는 온라인 저장 안내로 돌아온다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id)
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } returns
                Tasks.forException(FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE))
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError), f.repository.addSchedule(schedule))
            verify(exactly = 0) { f.registryRef.set(any()) }
            verify(exactly = 0) { f.scheduleRef.set(any()) }
        }

    @Test
    fun `비공유 저장과 편집은 등록부를 호출하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(id = schedule.id).apply { shareCode = "" }
            assertTrue(f.repository.addSchedule(schedule) is ScheduleRepository.ScheduleResult.Success)
            f.server = schedule.copy(sharedCode = "")
            assertTrue(f.repository.updateSchedule(schedule) is ScheduleRepository.ScheduleResult.Success)
            verify(exactly = 0) { f.transaction.set(f.registryRef, any()) }
            verify(exactly = 1) { f.transaction.set(f.scheduleRef, match<Schedule> { it.sharedCode == "" }) }
        }

    @Test
    fun `코드 복구가 실패하면 빈 코드 일정 backfill을 시작하지 않는다`() =
        runTest {
            every { codeRef.set(any()) } returns Tasks.forException(denied())

            assertEquals(0, repository.fillMissingSharedCode(uid))

            verify(exactly = 0) { schedules.whereEqualTo(any<String>(), any()) }
            verify(exactly = 0) { firestore.batch() }
        }

    @Test
    fun `로그아웃 뒤에는 등록부와 일정 저장을 시작하지 않는다`() =
        runTest {
            every { auth.currentUser } returns null

            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.AuthError), repository.addSchedule(schedule))
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.AuthError), repository.updateSchedule(schedule))
            verify(exactly = 0) { codeRef.set(any()) }
            verify(exactly = 0) { scheduleRef.set(any()) }
            verify(exactly = 0) { scheduleRef.update(any<Map<String, Any>>()) }
        }

    private fun denied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
}
