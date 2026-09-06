package com.example.slowclock.data

/** Firestore 컬렉션 이름. 문자열을 흩어 두지 않기 위한 단일 정의다. */
object FirestoreCollections {
    const val USERS = "users"

    /** 이름만 담는 공개 프로필. `users` 는 본인만 읽는다. */
    const val PUBLIC_PROFILES = "publicProfiles"
    const val SCHEDULES = "schedules"
    const val NOTIFICATIONS = "notifications"
    const val SCHEDULE_RECOMMENDATIONS = "scheduleRecommendations"
    const val FAMILY_GROUPS = "familyGroups"

    /**
     * 공유 코드 등록부. 코드 자체가 문서 이름이라 만들기 한 번으로 중복이 걸러진다.
     * 아무도 읽지 못한다 — 코드 목록은 그 자체가 사람을 찾는 열쇠 꾸러미다(#174).
     */
    const val SHARE_CODES = "shareCodes"
    const val SHARE_CODE_WATCHERS = "shareCodeWatchers"
    const val SHARE_CODE_WATCHER_TOKENS = "tokens"

    /** Firestore 쓰기 배치 한 건의 최대 작업 수 */
    const val BATCH_LIMIT = 500
}
