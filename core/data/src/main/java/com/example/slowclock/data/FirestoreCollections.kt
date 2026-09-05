package com.example.slowclock.data

/** Firestore 컬렉션 이름. 문자열을 흩어 두지 않기 위한 단일 정의다. */
object FirestoreCollections {
    const val USERS = "users"
    const val SCHEDULES = "schedules"
    const val NOTIFICATIONS = "notifications"
    const val SCHEDULE_RECOMMENDATIONS = "scheduleRecommendations"
    const val FAMILY_GROUPS = "familyGroups"
    const val SHARE_CODE_WATCHERS = "shareCodeWatchers"
    const val SHARE_CODE_WATCHER_TOKENS = "tokens"

    /** Firestore 쓰기 배치 한 건의 최대 작업 수 */
    const val BATCH_LIMIT = 500
}
