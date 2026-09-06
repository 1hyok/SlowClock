package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일정을 저장할 때 나는 실패가 예외가 아니라 결과로 나오는지 본다.
 *
 * 공유 코드 읽기가 try 밖에 있던 동안에는 이 실패가 ViewModel 의 코루틴을 그대로 빠져나가
 * 앱을 강제 종료시켰다. 저장 쓰기가 실패했을 때는 안내가 떴는데 이 읽기 하나만 앱을
 * 죽였다(#133).
 */
class ScheduleRepositoryAddScheduleTest {
    private val uid = "uid-1"

    private fun repositoryWith(userDocTask: com.google.android.gms.tasks.Task<DocumentSnapshot>): ScheduleRepository {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns user

        val userDocRef = mockk<DocumentReference>()
        every { userDocRef.get() } returns userDocTask
        val usersCollection = mockk<CollectionReference>()
        every { usersCollection.document(uid) } returns userDocRef
        val schedulesCollection = mockk<CollectionReference>()
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("schedules") } returns schedulesCollection
        every { firestore.collection("users") } returns usersCollection

        return ScheduleRepository(auth, firestore)
    }

    @Test
    fun `공유 코드 읽기가 권한 오류로 실패하면 앱을 죽이지 않고 오류를 돌려준다`() =
        runTest {
            val denied =
                FirebaseFirestoreException(
                    "denied",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            val repository = repositoryWith(Tasks.forException(denied))

            val result = repository.addSchedule(Schedule(title = "혈압약 먹기"))

            assertTrue(result is ScheduleRepository.ScheduleResult.Error)
            assertEquals(
                AppError.PermissionError,
                (result as ScheduleRepository.ScheduleResult.Error).error,
            )
        }

    @Test
    fun `공유 코드 읽기가 네트워크 오류로 실패해도 결과로 돌아온다`() =
        runTest {
            val unavailable =
                FirebaseFirestoreException(
                    "offline",
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                )
            val repository = repositoryWith(Tasks.forException(unavailable))

            val result = repository.addSchedule(Schedule(title = "병원 가기"))

            assertEquals(
                AppError.NetworkError,
                (result as ScheduleRepository.ScheduleResult.Error).error,
            )
        }

    /**
     * 공유 코드 없이 저장된 일정을 찾아 채우는 경로를 세운다.
     *
     * [shareCode] 를 사용자 문서가 낸다고 보고, 빈 코드로 저장된 일정 [staleCount] 건이
     * 걸린다고 둔다. 반환하는 batch 로 실제 갱신 건수를 센다.
     */
    private fun repositoryForFill(
        shareCode: String,
        staleCount: Int,
    ): Pair<ScheduleRepository, WriteBatch> {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns user

        val userSnapshot = mockk<DocumentSnapshot>()
        every { userSnapshot.getString("shareCode") } returns shareCode
        val userDocRef = mockk<DocumentReference>()
        every { userDocRef.get() } returns Tasks.forResult(userSnapshot)
        val usersCollection = mockk<CollectionReference>()
        every { usersCollection.document(uid) } returns userDocRef

        val stale =
            (1..staleCount).map {
                mockk<DocumentSnapshot>().also { document ->
                    every { document.reference } returns mockk<DocumentReference>()
                }
            }
        val querySnapshot = mockk<QuerySnapshot>()
        every { querySnapshot.documents } returns stale
        val byShareCode = mockk<Query>()
        every { byShareCode.get() } returns Tasks.forResult(querySnapshot)
        val byUser = mockk<Query>()
        every { byUser.whereEqualTo("sharedCode", "") } returns byShareCode
        val schedulesCollection = mockk<CollectionReference>()
        every { schedulesCollection.whereEqualTo("userId", uid) } returns byUser

        val batch = mockk<WriteBatch>()
        every { batch.update(any<DocumentReference>(), any<Map<String, Any>>()) } returns batch
        every { batch.commit() } returns Tasks.forResult(null)

        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("schedules") } returns schedulesCollection
        every { firestore.collection("users") } returns usersCollection
        every { firestore.batch() } returns batch
        val registry = mockk<CollectionReference>()
        val codeRef = mockk<DocumentReference>()
        every { firestore.collection("shareCodes") } returns registry
        every { registry.document(shareCode) } returns codeRef
        every { codeRef.set(mapOf("userId" to uid)) } returns Tasks.forResult(null)

        return ScheduleRepository(auth, firestore) to batch
    }

    @Test
    fun `코드 없이 저장된 일정에 공유 코드를 채운다`() =
        runTest {
            // 코드가 빈 채로 저장된 일정은 보안 규칙의 공유 읽기가 sharedCode != "" 를 요구해
            // 가족이 어떤 코드로도 읽지 못한다. 코드를 만든 자리에서 함께 맞춘다(#178).
            val (repository, batch) = repositoryForFill(shareCode = "ABC123", staleCount = 3)

            assertEquals(3, repository.fillMissingSharedCode(uid))
            verify(exactly = 3) { batch.update(any<DocumentReference>(), any<Map<String, Any>>()) }
            verify(exactly = 1) { batch.commit() }
        }

    @Test
    fun `채울 일정이 없으면 아무것도 쓰지 않는다`() =
        runTest {
            val (repository, batch) = repositoryForFill(shareCode = "ABC123", staleCount = 0)

            assertEquals(0, repository.fillMissingSharedCode(uid))
            verify(exactly = 0) { batch.commit() }
        }

    @Test
    fun `내 공유 코드가 아직 없으면 일정을 건드리지 않는다`() =
        runTest {
            // 빈 코드로 덮어쓰면 아무것도 고쳐지지 않고 updatedAt 만 흔들린다.
            val (repository, batch) = repositoryForFill(shareCode = "", staleCount = 3)

            assertEquals(0, repository.fillMissingSharedCode(uid))
            verify(exactly = 0) { batch.update(any<DocumentReference>(), any<Map<String, Any>>()) }
        }

    @Test
    fun `제목이 비어 있으면 Firestore 를 부르기 전에 막는다`() =
        runTest {
            val repository = repositoryWith(Tasks.forResult(mockk()))

            val result = repository.addSchedule(Schedule(title = "  "))

            assertEquals(
                AppError.InvalidDataError,
                (result as ScheduleRepository.ScheduleResult.Error).error,
            )
        }
}
