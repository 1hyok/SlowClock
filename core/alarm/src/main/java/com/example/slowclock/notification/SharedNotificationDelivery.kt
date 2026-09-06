package com.example.slowclock.notification

/** 네트워크 대기 전후를 같은 로그인·공유 설정 작업으로 묶는 스냅샷. */
data class SharedNotificationSession(
    val userId: String?,
    val shareCode: String?,
    val revision: Long,
)

/** 검사와 표시 사이에 로그아웃/코드 변경이 끼어들지 않게 직렬화한다. */
internal class SharedNotificationDelivery(
    private val readSession: () -> Pair<String?, String?>,
    private val cancelNotifications: () -> Unit,
) {
    private val lock = Any()
    private var revision = 0L

    fun snapshot(): SharedNotificationSession = synchronized(lock) { currentSession() }

    fun showIfCurrent(
        message: SharedScheduleMessage,
        show: () -> Boolean,
    ): Boolean =
        synchronized(lock) {
            val session = currentSession()
            if (session.userId != message.recipientUid || session.shareCode != message.shareCode) return@synchronized false
            show()
        }

    fun changeSession(change: () -> Unit) =
        synchronized(lock) {
            revision++
            try {
                change()
            } finally {
                cancelNotifications()
            }
        }

    fun changeIfCurrent(
        expected: SharedNotificationSession,
        change: () -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (expected.userId.isNullOrBlank() || currentSession() != expected) return@synchronized false
            changeSession(change)
            true
        }

    fun runIfCurrent(
        expected: SharedNotificationSession,
        action: () -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (currentSession() != expected) return@synchronized false
            action()
            true
        }

    fun changeAfterAccountDeletion(
        expected: SharedNotificationSession,
        change: () -> Unit,
    ): Boolean =
        synchronized(lock) {
            val current = currentSession()
            if (current != expected && current != expected.copy(userId = null)) return@synchronized false
            changeSession(change)
            true
        }

    fun withCurrentSession(action: (SharedNotificationSession) -> Unit) =
        synchronized(lock) {
            val session = currentSession()
            if (!session.userId.isNullOrBlank()) action(session)
        }

    private fun currentSession(): SharedNotificationSession {
        val (uid, code) = readSession()
        return SharedNotificationSession(uid, code, revision)
    }
}
