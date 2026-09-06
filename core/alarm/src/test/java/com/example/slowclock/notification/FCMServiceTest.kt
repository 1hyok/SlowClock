package com.example.slowclock.notification

import com.example.slowclock.data.remote.repository.UserRepository
import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class FCMServiceTest {
    private val notifier = mockk<SharedScheduleNotifier>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val service =
        FCMService().apply {
            sharedScheduleNotifier = notifier
            userRepository = users
        }
    private val payload =
        mapOf(
            "type" to "shared_schedule",
            "schemaVersion" to "1",
            "recipientUid" to "uid",
            "shareCode" to "CODE01",
            "scheduleId" to "id",
            "title" to "일정",
            "body" to "본문",
        )

    @Test
    fun `data-only 계약만 현재 세션 확인 경로로 보낸다`() {
        val remote = mockk<RemoteMessage>()
        every { remote.notification } returns null
        every { remote.data } returns payload
        service.onMessageReceived(remote)
        verify(exactly = 1) { notifier.show(SharedScheduleMessage.fromData(payload)!!) }
    }

    @Test
    fun `legacy notification과 불완전한 data는 기본 문구로 표시하지 않는다`() {
        val legacy = mockk<RemoteMessage>()
        every { legacy.notification } returns mockk()
        service.onMessageReceived(legacy)
        val invalid = mockk<RemoteMessage>()
        every { invalid.notification } returns null
        every { invalid.data } returns payload - "recipientUid"
        service.onMessageReceived(invalid)
        verify(exactly = 0) { notifier.show(any()) }
    }

    @Test
    fun `토큰 갱신은 잠금 안의 현재 세션으로 저장소에 전달한다`() {
        every { notifier.withCurrentSession(any()) } answers {
            firstArg<(SharedNotificationSession) -> Unit>().invoke(SharedNotificationSession("uid", "CODE01", 1))
        }
        service.onNewToken("refreshed")
        verify(exactly = 1) { users.updateFcmRegistration("refreshed", "uid", "CODE01") }
    }
}
