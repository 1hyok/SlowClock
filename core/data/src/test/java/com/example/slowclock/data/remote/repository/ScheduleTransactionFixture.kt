package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.model.Schedule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk

internal class ScheduleTransactionFixture(
    val id: String = "s1",
    var server: Schedule? = null,
) {
    val auth = mockk<FirebaseAuth>()
    val firestore = mockk<FirebaseFirestore>()
    val transaction = mockk<Transaction>()
    val scheduleRef = mockk<DocumentReference>()
    val userRef = mockk<DocumentReference>()
    val registryRef = mockk<DocumentReference>()
    val scheduleSnapshot = mockk<DocumentSnapshot>()
    val userSnapshot = mockk<DocumentSnapshot>()
    val repository: ScheduleRepository
    var uid = "owner"
    var shareCode = "ABC123"

    init {
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } answers { uid }
        val schedules = mockk<CollectionReference>()
        val users = mockk<CollectionReference>()
        val registry = mockk<CollectionReference>()
        every { firestore.collection("schedules") } returns schedules
        every { firestore.collection("users") } returns users
        every { firestore.collection("shareCodes") } returns registry
        every { schedules.document(id) } returns scheduleRef
        every { users.document(any()) } returns userRef
        every { registry.document(any()) } returns registryRef
        every { transaction.get(scheduleRef) } returns scheduleSnapshot
        every { transaction.get(userRef) } returns userSnapshot
        every { scheduleSnapshot.exists() } answers { server != null }
        every { scheduleSnapshot.toObject(Schedule::class.java) } answers { server }
        every { scheduleSnapshot.getString("userId") } answers { server?.userId }
        every { scheduleSnapshot.getString("sharedCode") } answers { server?.sharedCode }
        every { userSnapshot.getString("shareCode") } answers { shareCode }
        every { transaction.set(any(), any()) } returns transaction
        every { transaction.update(any<DocumentReference>(), any<Map<String, Any?>>()) } returns transaction
        every { transaction.delete(any()) } returns transaction
        every { firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
            try {
                Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
            } catch (e: Exception) {
                Tasks.forException(e)
            }
        }
        repository = ScheduleRepository(auth, firestore)
    }
}
