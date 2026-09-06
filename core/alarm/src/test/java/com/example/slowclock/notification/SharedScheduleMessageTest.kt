package com.example.slowclock.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SharedScheduleMessageTest {
    private val data =
        mapOf(
            "type" to "shared_schedule",
            "schemaVersion" to "1",
            "recipientUid" to "uid",
            "shareCode" to "CODE01",
            "scheduleId" to "id",
            "title" to "추가됨",
            "body" to "약 먹기",
        )

    @Test
    fun `필수 필드가 빠지거나 계약이 다르면 기본 알림을 만들지 않는다`() {
        for (key in data.keys) assertNull("누락된 필드: $key", SharedScheduleMessage.fromData(data - key))
        for (key in data.keys - "body") assertNull("빈 필드: $key", SharedScheduleMessage.fromData(data + (key to " ")))
        assertNull(SharedScheduleMessage.fromData(data + ("schemaVersion" to "2")))
        assertNull(SharedScheduleMessage.fromData(data + ("type" to "other")))
        assertNull(SharedScheduleMessage.fromData(emptyMap()))
    }

    @Test
    fun `일정 제목이 없는 삭제라도 UID 코드 일정ID가 있으면 처리한다`() {
        assertNotNull(SharedScheduleMessage.fromData(data + ("body" to "")))
    }

    @Test
    fun `같은 일정은 갱신하고 다른 일정이나 계정의 tag는 겹치지 않는다`() {
        val message = SharedScheduleMessage.fromData(data)!!
        val tag = SharedScheduleNotifier.notificationTag(message)
        assertEquals(tag, SharedScheduleNotifier.notificationTag(message.copy(title = "삭제됨")))
        assertNotEquals(tag, SharedScheduleNotifier.notificationTag(message.copy(scheduleId = "other")))
        assertNotEquals(tag, SharedScheduleNotifier.notificationTag(message.copy(recipientUid = "other")))
        val left = message.copy(recipientUid = "a", shareCode = "bc", scheduleId = "d")
        val right = message.copy(recipientUid = "ab", shareCode = "c", scheduleId = "d")
        assertNotEquals(SharedScheduleNotifier.notificationTag(left), SharedScheduleNotifier.notificationTag(right))
        // Java hash collision처럼 일정 ID가 같은 정수로 줄어들어도 tag 문자열은 다르다.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(
            SharedScheduleNotifier.notificationTag(message.copy(scheduleId = "Aa")),
            SharedScheduleNotifier.notificationTag(message.copy(scheduleId = "BB")),
        )
    }
}
