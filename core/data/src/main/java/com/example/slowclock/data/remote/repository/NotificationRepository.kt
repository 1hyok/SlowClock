package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * notifications 컬렉션. 앱에 알림 목록 화면이 없어 지금은 계정 삭제 때 정리하는 용도만 남았다.
 */
class NotificationRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) {
        private val notificationsCollection = firestore.collection(FirestoreCollections.NOTIFICATIONS)

        // 계정 삭제용: 사용자의 알림 기록 전부 삭제
        suspend fun deleteAllNotificationsOf(userId: String): Boolean =
            try {
                val documents =
                    notificationsCollection
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                documents.documents.chunked(FirestoreCollections.BATCH_LIMIT).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
    }
