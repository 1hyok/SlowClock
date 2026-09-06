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
import io.mockk.every
import io.mockk.mockk
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
