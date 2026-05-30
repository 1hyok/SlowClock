package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.FirestoreDB
import com.example.slowclock.data.model.Notification
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.tasks.await

/**
 * Notification 컬렉션에 대한 저장소 클래스
 */
class NotificationRepository {
    private val auth = FirebaseAuth.getInstance()
    private val notificationsCollection = FirestoreDB.notifications

    // 현재 사용자의 알림 목록 가져오기
    suspend fun getUserNotifications(limit: Int = 50): List<Notification> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        return try {
            notificationsCollection
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 알림 추가
    suspend fun addNotification(notification: Notification): String? {
        val uid = auth.currentUser?.uid ?: return null

        val newNotification = notification.copy(
            userId = uid,
            createdAt = Timestamp.now()
        )

        return try {
            val docRef = notificationsCollection.document()
            val notificationWithId = newNotification.copy(id = docRef.id)
            docRef.set(notificationWithId).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    // 알림 읽음 상태 변경
    suspend fun markNotificationAsRead(notificationId: String): Boolean {
        return try {
            notificationsCollection.document(notificationId)
                .update("isRead", true)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 모든 알림 읽음 상태로 변경
    suspend fun markAllNotificationsAsRead(): Boolean {
        val uid = auth.currentUser?.uid ?: return false

        return try {
            val batch = FirestoreDB.db.batch()

            val unreadNotifications = notificationsCollection
                .whereEqualTo("userId", uid)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            unreadNotifications.documents.forEach { document ->
                batch.update(document.reference, "isRead", true)
            }

            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 알림 삭제
    suspend fun deleteNotification(notificationId: String): Boolean {
        return try {
            notificationsCollection.document(notificationId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}