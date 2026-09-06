package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ScheduleRepositoryUpdateTest {
    private val updatedFields = slot<Map<String, Any?>>()
    private val document = mockk<DocumentReference>()

    private fun repositoryWith(response: Task<Void>): ScheduleRepository {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "owner"
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns user
        every { document.update(capture(updatedFields)) } returns response
        val schedules = mockk<CollectionReference>()
        every { schedules.document("s1") } returns document
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("schedules") } returns schedules
        every { firestore.collection("users") } returns mockk()
        return ScheduleRepository(auth, firestore)
    }

    @Test
    fun `편집은 서버의 완료 기록 공유 코드 소유자 생성 시각을 덮지 않는다`() =
        runTest {
            val repository = repositoryWith(Tasks.forResult(null))
            val originalCreatedAt = Timestamp(Date(1_700_000_000_000))
            val currentServer =
                mutableMapOf<String, Any?>(
                    "userId" to "owner",
                    "sharedCode" to "NEW123",
                    "familyGroupId" to "family",
                    "completed" to true,
                    "completedDates" to listOf("2032-01-05"),
                    "createdAt" to originalCreatedAt,
                )
            val staleForm =
                Schedule(
                    id = "s1",
                    userId = "old-owner",
                    sharedCode = "OLD123",
                    title = "새 제목",
                    description = "새 설명",
                    startTime = Timestamp(Date(1_900_000_000_000)),
                    endTime = null,
                    recurring = true,
                    recurringType = "weekly",
                    completed = false,
                    completedDates = emptyList(),
                )

            assertTrue(repository.updateSchedule(staleForm) is ScheduleRepository.ScheduleResult.Success)
            currentServer.putAll(updatedFields.captured)

            assertEquals("owner", currentServer["userId"])
            assertEquals("NEW123", currentServer["sharedCode"])
            assertEquals("family", currentServer["familyGroupId"])
            assertEquals(true, currentServer["completed"])
            assertEquals(listOf("2032-01-05"), currentServer["completedDates"])
            assertEquals(originalCreatedAt, currentServer["createdAt"])
            assertEquals("새 제목", currentServer["title"])
            assertEquals("새 설명", currentServer["description"])
            assertEquals(staleForm.startTime, currentServer["startTime"])
            assertEquals(true, currentServer["recurring"])
            assertEquals("weekly", currentServer["recurringType"])
            assertTrue(currentServer["updatedAt"] is Timestamp)
            verify(exactly = 0) { document.set(any()) }
        }

    @Test
    fun `종료 시각과 반복을 지우면 null도 갱신에 포함한다`() =
        runTest {
            val repository = repositoryWith(Tasks.forResult(null))

            repository.updateSchedule(Schedule(id = "s1", title = "일정", endTime = null, recurring = false, recurringType = null))

            assertTrue(updatedFields.captured.containsKey("endTime"))
            assertNull(updatedFields.captured["endTime"])
            assertFalse(updatedFields.captured["recurring"] as Boolean)
            assertTrue(updatedFields.captured.containsKey("recurringType"))
            assertNull(updatedFields.captured["recurringType"])
        }

    @Test
    fun `이미 삭제된 일정의 편집은 문서를 재생성하지 않고 찾을 수 없음으로 끝난다`() =
        runTest {
            val missing = FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
            val repository = repositoryWith(Tasks.forException(missing))

            val result = repository.updateSchedule(Schedule(id = "s1", title = "삭제된 일정"))

            assertEquals(ScheduleRepository.ScheduleResult.Error(AppError.NotFoundError), result)
            verify(exactly = 0) { document.set(any()) }
        }
}
