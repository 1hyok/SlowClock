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
    fun `등록부 복구가 완료되기 전에 새 일정을 쓰지 않는다`() =
        runTest {
            val pending = TaskCompletionSource<Void>()
            every { codeRef.set(any()) } returns pending.task

            val result = async { repository.addSchedule(schedule) }
            runCurrent()
            assertFalse(result.isCompleted)
            verify(exactly = 0) { scheduleRef.set(any()) }
            pending.setResult(null)

            assertEquals(ScheduleRepository.ScheduleResult.Success(schedule.id), result.await())
            verify(exactly = 1) { codeRef.set(mapOf("userId" to uid)) }
            verify(exactly = 1) { scheduleRef.set(match<Schedule> { it.sharedCode == code && it.userId == uid }) }
        }

    @Test
    fun `등록부 확인이 끝나야 편집을 저장한다`() =
        runTest {
            val pending = TaskCompletionSource<Void>()
            every { codeRef.set(any()) } returns pending.task

            val result = async { repository.updateSchedule(schedule) }
            runCurrent()
            assertFalse(result.isCompleted)
            verify(exactly = 0) { scheduleRef.set(any()) }
            verify(exactly = 0) { scheduleRef.update(any<Map<String, Any>>()) }
            pending.setResult(null)

            assertEquals(ScheduleRepository.ScheduleResult.Success(Unit), result.await())
        }

    @Test
    fun `다른 계정의 이전 일정을 수정하려 하면 현재 계정으로 확인하고 쓰기를 막는다`() =
        runTest {
            val nextUser = mockk<FirebaseUser>()
            every { nextUser.uid } returns "next-user"
            every { auth.currentUser } returns nextUser
            every { codeRef.set(mapOf("userId" to "next-user")) } returns Tasks.forException(denied())

            assertTrue(repository.updateSchedule(schedule) is ScheduleRepository.ScheduleResult.Error)

            verify(exactly = 1) { codeRef.set(mapOf("userId" to "next-user")) }
            verify(exactly = 0) { scheduleRef.set(any()) }
            verify(exactly = 0) { scheduleRef.update(any<Map<String, Any>>()) }
        }

    @Test
    fun `등록부 충돌은 저장과 편집을 막고 코드 유지 안내를 돌려준다`() =
        runTest {
            every { codeRef.set(any()) } returns Tasks.forException(denied())

            val results = listOf(repository.addSchedule(schedule), repository.updateSchedule(schedule))

            results.forEach { result ->
                val error = (result as ScheduleRepository.ScheduleResult.Error).error
                assertTrue(error is AppError.GeneralError)
                assertTrue((error as AppError.GeneralError).message.contains("기존 코드는 유지됩니다"))
            }
            verify(exactly = 0) { scheduleRef.set(any()) }
            verify(exactly = 0) { scheduleRef.update(any<Map<String, Any>>()) }
        }

    @Test
    fun `등록부 네트워크 실패도 일정 저장 없이 결과로 돌아온다`() =
        runTest {
            every { codeRef.set(any()) } returns
                Tasks.forException(
                    FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE),
                )

            val result = repository.addSchedule(schedule) as ScheduleRepository.ScheduleResult.Error

            assertEquals(AppError.NetworkError, result.error)
            verify(exactly = 0) { scheduleRef.set(any()) }
        }

    @Test
    fun `비공유 저장은 등록부를 호출하지 않는다`() =
        runTest {
            every { snapshot.getString("shareCode") } returns ""

            assertTrue(repository.addSchedule(schedule) is ScheduleRepository.ScheduleResult.Success)
            assertTrue(repository.updateSchedule(schedule.copy(sharedCode = "")) is ScheduleRepository.ScheduleResult.Success)

            verify(exactly = 0) { firestore.collection("shareCodes") }
            verify(atLeast = 1) { scheduleRef.set(match<Schedule> { it.sharedCode == "" }) }
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
