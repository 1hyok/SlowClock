package com.example.slowclock.data.remote.repository

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryAlarmSyncTest {
    private fun repositoryWith(serverResponse: Task<QuerySnapshot>): Pair<ScheduleRepository, Query> {
        val emptyCache = mockk<QuerySnapshot>()
        every { emptyCache.iterator() } returns mutableListOf<QueryDocumentSnapshot>().iterator()
        val query = mockk<Query>()
        every { query.get() } returns Tasks.forResult(emptyCache)
        every { query.get(Source.SERVER) } returns serverResponse
        val schedules = mockk<CollectionReference>()
        every { schedules.whereEqualTo("userId", "uid-1") } returns query
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("schedules") } returns schedules
        every { firestore.collection("users") } returns mockk()
        return ScheduleRepository(mockk<FirebaseAuth>(), firestore) to query
    }

    @Test
    fun `서버에 닿지 못하면 빈 캐시를 일정이 없는 것으로 돌려주지 않는다`() =
        runTest {
            val unavailable = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            val (repository, query) = repositoryWith(Tasks.forException(unavailable))

            assertNull(repository.getSchedulesOf("uid-1"))
            verify(exactly = 0) { query.get() }
        }

    @Test
    fun `서버가 빈 목록을 확인했으면 빈 목록으로 돌려준다`() =
        runTest {
            val emptyServer = mockk<QuerySnapshot>()
            every { emptyServer.iterator() } returns mutableListOf<QueryDocumentSnapshot>().iterator()
            val (repository, _) = repositoryWith(Tasks.forResult(emptyServer))

            assertEquals(emptyList<Any>(), repository.getSchedulesOf("uid-1"))
        }

    @Test
    fun `취소한 알람 목록 조회는 일반 실패로 삼키지 않는다`() =
        runTest {
            val (repository, _) = repositoryWith(Tasks.forCanceled())

            val failure = runCatching { repository.getSchedulesOf("uid-1") }.exceptionOrNull()

            assertTrue(failure is CancellationException)
        }
}
