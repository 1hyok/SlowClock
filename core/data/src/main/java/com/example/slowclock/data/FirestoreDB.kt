package com.example.slowclock.data

import android.annotation.SuppressLint
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firestore 데이터베이스 연결 및 컬렉션 접근을 위한 클래스
 */
@SuppressLint("StaticFieldLeak")
object FirestoreDB {
    internal val db = FirebaseFirestore.getInstance()

    /** Firestore 쓰기 배치 한 건의 최대 작업 수 */
    internal const val BATCH_LIMIT = 500

    // 컬렉션 참조
    val users: CollectionReference = db.collection("users")
    val schedules: CollectionReference = db.collection("schedules")
    val notifications: CollectionReference = db.collection("notifications")
    val scheduleRecommendations: CollectionReference = db.collection("scheduleRecommendations")
    val familyGroups: CollectionReference = db.collection("familyGroups")
}
