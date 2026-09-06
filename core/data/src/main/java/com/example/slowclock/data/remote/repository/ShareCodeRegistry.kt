package com.example.slowclock.data.remote.repository

import com.example.slowclock.data.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

/** 기존 코드의 등록부를 보완한다. 같은 소유자의 동일값 쓰기만 규칙이 허용한다. */
internal suspend fun FirebaseFirestore.ensureShareCodeOwner(
    userId: String,
    shareCode: String,
) {
    if (shareCode.isBlank()) return
    try {
        collection(FirestoreCollections.SHARE_CODES)
            .document(shareCode)
            .set(mapOf("userId" to userId))
            .await()
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            throw ShareCodeOwnershipException(e)
        }
        throw e
    }
}

internal class ShareCodeOwnershipException(
    cause: Throwable,
) : Exception(
        "공유 코드의 소유권을 확인하지 못했습니다. 기존 코드는 유지됩니다. 관리자에게 문의해 주세요.",
        cause,
    )
