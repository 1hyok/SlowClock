package com.example.slowclock.data.remote.repository

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AccountCleanupCancellationTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val owned = mockk<Query>()
    private val joined = mockk<Query>()
    private val snapshot = mockk<QuerySnapshot>()
    private val document = mockk<DocumentSnapshot>()
    private val reference = mockk<DocumentReference>()
    private val batch = mockk<WriteBatch>()

    init {
        every { firestore.collection(any()) } returns collection
        every { collection.whereEqualTo(any<String>(), any()) } returns owned
        every { collection.whereArrayContains("memberIds", "owner") } returns joined
        every { owned.get() } returns Tasks.forResult(snapshot)
        every { joined.get() } returns Tasks.forResult(snapshot)
        every { snapshot.documents } returns listOf(document)
        every { document.reference } returns reference
        every { reference.delete() } returns Tasks.forResult(null)
        every { firestore.batch() } returns batch
        every { batch.delete(reference) } returns batch
    }

    @Test
    fun `알림 조회 취소는 삭제 batch를 시작하지 않는다`() =
        runTest {
            every { owned.get() } returns TaskCompletionSource<QuerySnapshot>().task
            assertCancellationStops { NotificationRepository(firestore).deleteAllNotificationsOf("owner") }
            verify(exactly = 0) { firestore.batch() }
        }

    @Test
    fun `알림 삭제 commit 대기 취소는 실패 반환으로 바꾸지 않는다`() =
        runTest {
            every { batch.commit() } returns TaskCompletionSource<Void>().task
            assertCancellationStops { NotificationRepository(firestore).deleteAllNotificationsOf("owner") }
        }

    @Test
    fun `소유 그룹 조회 취소는 그룹을 삭제하지 않는다`() =
        runTest {
            every { owned.get() } returns TaskCompletionSource<QuerySnapshot>().task
            assertCancellationStops { FamilyGroupRepository(firestore).leaveAllGroupsOf("owner") }
            verify(exactly = 0) { reference.delete() }
        }

    @Test
    fun `소유 그룹 삭제 대기 취소는 참여 그룹 조회를 시작하지 않는다`() =
        runTest {
            every { reference.delete() } returns TaskCompletionSource<Void>().task
            assertCancellationStops { FamilyGroupRepository(firestore).leaveAllGroupsOf("owner") }
            verify(exactly = 0) { joined.get() }
        }

    @Test
    fun `참여 그룹 조회 취소는 실패 반환으로 바꾸지 않는다`() =
        runTest {
            every { joined.get() } returns TaskCompletionSource<QuerySnapshot>().task
            assertCancellationStops { FamilyGroupRepository(firestore).leaveAllGroupsOf("owner") }
        }

    @Test
    fun `참여 그룹 갱신 대기 취소는 실패 반환으로 바꾸지 않는다`() =
        runTest {
            every { reference.update(any<Map<String, Any>>()) } returns TaskCompletionSource<Void>().task
            assertCancellationStops { FamilyGroupRepository(firestore).leaveAllGroupsOf("owner") }
        }
}
