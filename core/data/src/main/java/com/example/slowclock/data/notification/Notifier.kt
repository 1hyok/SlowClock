package com.example.slowclock.data.notification

/**
 * 데이터 레이어가 알림 전송을 요청하기 위한 추상화.
 *
 * 구현체(GuardianNotifier)는 :core:alarm 에 있고 Hilt 로 주입된다. 이로써 :core:data 가
 * :core:alarm 에 직접 의존하지 않는다. 구현체가 앱 Context 를 스스로 주입받으므로 호출자는
 * Context 를 넘기지 않는다.
 */
interface Notifier {
    fun sendReminderToUser(
        fcmToken: String,
        title: String,
        message: String,
        shareCode: String? = null,
    )

    fun sendReminderToUsers(
        fcmTokens: List<String>,
        title: String,
        message: String,
        shareCode: String? = null,
    )
}
