package com.example.slowclock.notification

/** 서버 data-only 공유 알림 계약. 필수 대상이 빠진 메시지를 일반 알림으로 바꾸지 않는다. */
data class SharedScheduleMessage(
    val recipientUid: String,
    val shareCode: String,
    val scheduleId: String,
    val title: String,
    val body: String,
) {
    companion object {
        fun fromData(data: Map<String, String>): SharedScheduleMessage? {
            if (data["type"] != "shared_schedule" || data["schemaVersion"] != "1") return null
            val uid = data["recipientUid"]?.takeIf { it.isNotBlank() } ?: return null
            val code = data["shareCode"]?.takeIf { it.isNotBlank() } ?: return null
            val id = data["scheduleId"]?.takeIf { it.isNotBlank() } ?: return null
            val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
            val body = data["body"] ?: return null
            return SharedScheduleMessage(uid, code, id, title, body)
        }
    }
}
