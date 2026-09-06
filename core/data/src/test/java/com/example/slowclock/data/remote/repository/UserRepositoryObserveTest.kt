package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.User
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryObserveTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val document = mockk<DocumentReference>()
    private val registration = mockk<ListenerRegistration>()
    private val listener = slot<EventListener<DocumentSnapshot>>()
    private val initialSnapshot = mockk<DocumentSnapshot>()

    private fun repository(): UserRepository {
        every { firestore.collection(any()) } returns collection
        every { collection.document("uid-1") } returns document
        every { document.get() } returns Tasks.forResult(initialSnapshot)
        every { initialSnapshot.toObject(User::class.java) } returns null
        every { document.addSnapshotListener(capture(listener)) } returns registration
        justRun { registration.remove() }
        return UserRepository(mockk<FirebaseAuth>(), firestore, mockk<FirebaseMessaging>())
    }

    @Test
    fun `없는 문서 뒤에 생성된 문서를 받고 구독을 취소하면 리스너를 지운다`() =
        runTest {
            val values = mutableListOf<User?>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository().observeUser("uid-1").collect { values.add(it) }
                }
            val missing = mockk<DocumentSnapshot>()
            every { missing.toObject(User::class.java) } returns null
            val snapshot = mockk<DocumentSnapshot>()
            val user = User(id = "uid-1", shareCode = "NEW123")
            every { snapshot.toObject(User::class.java) } returns user

            listener.captured.onEvent(missing, null)
            listener.captured.onEvent(snapshot, null)
            assertEquals(listOf(null, user), values)
            job.cancel()
            verify(exactly = 1) { registration.remove() }
        }

    @Test
    fun `읽기 오류는 null 문서 대신 실패로 전달하고 리스너를 지운다`() =
        runTest {
            var failure: Throwable? = null
            val values = mutableListOf<User?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository().observeUser("uid-1").catch { failure = it }.collect { values.add(it) }
            }
            val error = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            listener.captured.onEvent(null, error)

            assertSame(error, failure)
            assertEquals(listOf<User?>(null), values)
            verify(exactly = 1) { registration.remove() }
        }

    @Test
    fun `캐시 없는 최초 읽기 실패는 빈 사용자로 바꾸지 않는다`() =
        runTest {
            val repository = repository()
            val error = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            every { document.get() } returns Tasks.forException(error)
            var failure: Throwable? = null
            val values = mutableListOf<User?>()

            repository.observeUser("uid-1").catch { failure = it }.collect { values.add(it) }

            assertSame(error, failure)
            assertEquals(emptyList<User?>(), values)
            verify(exactly = 0) { document.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) }
        }
}
