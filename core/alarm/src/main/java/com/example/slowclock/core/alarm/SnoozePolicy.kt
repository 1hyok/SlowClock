package com.example.slowclock.core.alarm

/**
 * 다시 알림 규칙. 화면 버튼과 알림 액션과 예약 코드가 모두 이 값을 본다.
 *
 * 안드로이드 타입을 쓰지 않아 단위 테스트로 그대로 검증된다. 라벨의 「5분」 과 실제로 미루는
 * 시간이 어긋날 자리를 없애려고 분 값도 여기서 낸다(#129).
 */
object SnoozePolicy {
    /** 다시 알림 한 번에 미루는 시간. 화면 버튼과 알림 액션의 라벨도 이 값으로 만든다. */
    const val MINUTES = 5

    /**
     * 다시 알림을 쓸 수 있는 횟수. 두 번까지만 미루면 원래 시각에서 최대 10분이다.
     *
     * 무한히 미룰 수 있으면 고령자가 계속 미루다 일정을 놓친다. 알람 앱이 미루기를 허용하는
     * 이유는 「지금 당장은 손이 비지 않는다」 이지 「오늘은 건너뛴다」 가 아니다.
     */
    const val MAX_COUNT = 2

    private const val MILLIS_PER_MINUTE = 60_000L

    /** 이미 [snoozeCount] 번 미룬 알람을 또 미룰 수 있는지. */
    fun canSnooze(snoozeCount: Int): Boolean = snoozeCount < MAX_COUNT

    /** [now] 기준으로 다시 울릴 시각. */
    fun nextTriggerAt(now: Long): Long = now + MINUTES * MILLIS_PER_MINUTE
}
