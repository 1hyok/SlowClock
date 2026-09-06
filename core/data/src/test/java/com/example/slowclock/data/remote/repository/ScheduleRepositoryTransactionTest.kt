package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Date

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleRepositoryTransactionTest {
    private val input = Schedule(id = "s1", title = "약", startTime = Timestamp(Date(1_900_000_000_000)))

    @Test
    fun `신규 일정과 등록부를 같은 transaction으로 쓰고 저장된 모델을 반환한다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            val result = f.repository.addSchedule(input) as ScheduleRepository.ScheduleResult.Success
            assertEquals("s1", result.data.id)
            assertEquals("owner", result.data.userId)
            assertEquals("ABC123", result.data.sharedCode)
            verify(exactly = 1) { f.transaction.set(f.registryRef, mapOf("userId" to "owner")) }
            verify(exactly = 1) { f.transaction.set(f.scheduleRef, result.data) }
            verify(exactly = 0) { f.scheduleRef.set(any()) }
        }

    @Test
    fun `등록부가 빈 계정은 비공유 일정만 쓴다`() =
        runTest {
            val f = ScheduleTransactionFixture().apply { shareCode = "" }
            val result = f.repository.addSchedule(input) as ScheduleRepository.ScheduleResult.Success
            assertEquals("", result.data.sharedCode)
            verify(exactly = 0) { f.transaction.set(f.registryRef, any()) }
        }

    @Test
    fun `같은 제출 ID의 늦은 성공을 재시도하면 최신 서버 문서를 반환하고 쓰지 않는다`() =
        runTest {
            val saved = input.copy(userId = "owner", sharedCode = "NEW123", completed = true, completedDates = listOf("2030-03-17"))
            val f = ScheduleTransactionFixture(server = saved)
            assertEquals(ScheduleRepository.ScheduleResult.Success(saved), f.repository.addSchedule(input))
            verify(exactly = 0) { f.transaction.set(any(), any()) }
            verify(exactly = 0) { f.transaction.update(any<DocumentReference>(), any<Map<String, Any?>>()) }
        }

    @Test
    fun `같은 ID의 다른 내용이나 다른 소유자를 성공으로 처리하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(server = input.copy(userId = "owner", title = "다른 내용"))
            assertTrue(f.repository.addSchedule(input) is ScheduleRepository.ScheduleResult.Error)
            f.server = input.copy(userId = "other")
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.PermissionError), f.repository.addSchedule(input))
            verify(exactly = 0) { f.transaction.set(any(), any()) }
        }

    @Test
    fun `온라인 확인에 실패하면 생성 편집 삭제 완료 모두 연결 안내를 돌려준다`() =
        runTest {
            val f = ScheduleTransactionFixture(server = input.copy(userId = "owner"))
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } returns
                Tasks.forException(
                    FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE),
                )
            val results =
                listOf(
                    f.repository.addSchedule(input),
                    f.repository.updateSchedule(input),
                    f.repository.deleteSchedule("s1"),
                    f.repository.markScheduleAsCompleted("s1"),
                )
            results.forEach { assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.OnlineWriteError), it) }
            verify(exactly = 0) { f.scheduleRef.set(any()) }
            verify(exactly = 0) { f.scheduleRef.update(any<Map<String, Any?>>()) }
            verify(exactly = 0) { f.scheduleRef.delete() }
        }

    @Test
    fun `이미 지운 일정 삭제는 서버 부재 확인 뒤 성공하고 다시 쓰지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            assertEquals(ScheduleRepository.ScheduleResult.Success(Unit), f.repository.deleteSchedule("s1"))
            verify(exactly = 1) { f.transaction.get(f.scheduleRef) }
            verify(exactly = 0) { f.transaction.delete(any()) }
        }

    @Test
    fun `다른 소유자의 일정은 편집하거나 지우지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture(server = input.copy(userId = "other"))
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.PermissionError), f.repository.updateSchedule(input))
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.PermissionError), f.repository.deleteSchedule("s1"))
            verify(exactly = 0) { f.transaction.delete(any()) }
            verify(exactly = 0) { f.transaction.update(any<DocumentReference>(), any<Map<String, Any?>>()) }
        }

    @Test
    fun `보호자 완료는 등록부를 쓰지 않고 회차 필드만 갱신한다`() =
        runTest {
            val f = ScheduleTransactionFixture(server = input.copy(userId = "other", sharedCode = "ABC123"))
            val fields = slot<Map<String, Any?>>()
            every { f.transaction.update(f.scheduleRef, capture(fields)) } returns f.transaction
            assertEquals(ScheduleRepository.ScheduleResult.Success(Unit), f.repository.markScheduleAsCompleted("s1", true, "2030-03-17"))
            assertEquals(setOf("completedDates", "updatedAt"), fields.captured.keys)
            verify(exactly = 0) { f.transaction.set(any(), any()) }
        }

    @Test
    fun `없는 일정의 편집과 완료는 문서를 다시 만들지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.NotFoundError), f.repository.updateSchedule(input))
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.NotFoundError), f.repository.markScheduleAsCompleted("s1"))
            verify(exactly = 0) { f.transaction.set(any(), any()) }
            verify(exactly = 0) { f.transaction.update(any<DocumentReference>(), any<Map<String, Any?>>()) }
        }

    @Test
    fun `취소는 오류 결과로 삼키지 않고 늦은 callback도 새 쓰기를 하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            val pending = TaskCompletionSource<Any?>()
            val callback = slot<Transaction.Function<Any?>>()
            every { f.firestore.runTransaction(capture(callback)) } returns pending.task
            val operation = async { f.repository.addSchedule(input) }
            runCurrent()
            operation.cancel()
            try {
                operation.await()
                fail("CancellationException expected")
            } catch (_: CancellationException) {
            }
            try {
                callback.captured.apply(f.transaction)
                fail("Cancelled callback must stop")
            } catch (_: CancellationException) {
            }
            verify(exactly = 0) { f.transaction.set(any(), any()) }
        }

    @Test
    fun `취소 뒤 이미 제출한 commit이 성공해도 같은 ID 재시도는 중복 쓰지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            val pending = TaskCompletionSource<Any?>()
            var submitted: Schedule? = null
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
                submitted = firstArg<Transaction.Function<Any?>>().apply(f.transaction) as Schedule
                pending.task
            }
            val operation = async { f.repository.addSchedule(input) }
            runCurrent()
            operation.cancel()
            f.server = requireNotNull(submitted)
            pending.setResult(submitted)
            every { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
                Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(f.transaction))
            }
            assertEquals(ScheduleRepository.ScheduleResult.Success(submitted), f.repository.addSchedule(input))
            verify(exactly = 1) { f.transaction.set(f.scheduleRef, any()) }
        }

    @Test
    fun `로그아웃이나 빈 제출 ID는 transaction을 시작하지 않는다`() =
        runTest {
            val f = ScheduleTransactionFixture()
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.InvalidDataError), f.repository.addSchedule(input.copy(id = "")))
            every { f.auth.currentUser } returns null
            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.AuthError), f.repository.addSchedule(input))
            verify(exactly = 0) { f.firestore.runTransaction(any<Transaction.Function<Any?>>()) }
        }
}
