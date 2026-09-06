package com.example.slowclock.util

import java.util.Calendar
import java.util.Date

/**
 * 일정이 되풀이되는 방식.
 *
 * 문자열 값은 Firestore 문서의 `recurringType` 과 같아야 한다. 화면이 고르게 해 주는 값도
 * 이 셋뿐이다(일정 추가 화면의 반복 주기).
 */
enum class Recurrence(
    val type: String?,
) {
    NONE(null),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    companion object {
        /**
         * 문서의 두 필드에서 규칙을 읽는다.
         *
         * `recurring` 이 꺼져 있으면 `recurringType` 이 무엇이든 되풀이하지 않는다. 켜져 있는데
         * 모르는 값이면 매일로 본다 — 화면의 기본값이 「매일」 이라 사용자가 그렇게 믿고 저장했을
         * 값이고, 되풀이를 통째로 버리는 것보다 낫다.
         */
        fun of(
            recurring: Boolean,
            recurringType: String?,
        ): Recurrence {
            if (!recurring) return NONE
            // NONE 은 type 이 null 이라 후보에서 뺀다. 빼지 않으면 recurringType 이 비어 있는
            // 반복 일정이 NONE 으로 읽혀 되풀이가 통째로 사라진다.
            return entries.firstOrNull { it != NONE && it.type == recurringType } ?: DAILY
        }
    }
}

/**
 * 되풀이하는 일정의 회차를 세는 자리.
 *
 * 기준 시각은 저장된 첫 회차의 epoch 다. 이를 계산 당시 기기 시간대로 해석한 시·분·초와
 * 요일·일자를 반복한다. 생성 당시 시간대나 벽시계 시각은 따로 저장하지 않는다. 따라서 시간대가
 * 바뀌어도 생성 당시의 「8시」를 유지한다는 뜻은 아니다. 기존 epoch 와 회차 키는 이행하지 않는다.
 * DST 중복·공백 시각은 Calendar 의 기본 해석을 따른다. 세부 경계는 docs/recurrence-time-policy.md.
 */
object RecurrenceRule {
    /**
     * [dayMillis] 가 속한 날에 이 일정이 있는가. 있으면 그날의 시작 시각을, 없으면 null 을 낸다.
     *
     * 첫 회차보다 앞선 날에는 없다. 되풀이하지 않는 일정은 첫 회차가 그날일 때만 있다.
     */
    fun occurrenceOn(
        baseMillis: Long,
        recurrence: Recurrence,
        dayMillis: Long,
    ): Long? {
        val base = calendarAt(baseMillis)
        val day = calendarAt(dayMillis)
        if (startOfDay(day) < startOfDay(base)) return null

        val matches =
            when (recurrence) {
                Recurrence.NONE -> isSameDay(base, day)
                Recurrence.DAILY -> true
                Recurrence.WEEKLY -> base.get(Calendar.DAY_OF_WEEK) == day.get(Calendar.DAY_OF_WEEK)
                Recurrence.MONTHLY -> day.get(Calendar.DAY_OF_MONTH) == clampedDayOfMonth(base, day)
            }
        if (!matches) return null
        return withTimeOf(base, day)
    }

    /**
     * [afterMillis] 보다 뒤에 오는 첫 회차. 더 없으면 null.
     *
     * 되풀이하지 않는 일정은 첫 회차가 아직 안 왔을 때만 값이 있다. 되풀이하는 일정은 언제 물어도
     * 값이 있다 — 끝이 없기 때문이다.
     *
     * 재부팅 뒤 복원과 알람이 울린 뒤 다음 회차 예약이 모두 이 함수를 쓴다. 며칠치를 미리 걸어
     * 두지 않고 다음 하나만 거는 이유는, 걸어 둔 것이 사라져도(재부팅·강제 종료) 지금 시각에서
     * 다시 세면 그만이기 때문이다.
     */
    fun nextOccurrenceAfter(
        baseMillis: Long,
        recurrence: Recurrence,
        afterMillis: Long,
    ): Long? {
        if (recurrence == Recurrence.NONE) return baseMillis.takeIf { it > afterMillis }
        if (baseMillis > afterMillis) return baseMillis

        // 오늘부터 하루씩 넘기며 첫 회차를 찾는다. 매월이 가장 성기고, 하루가 빠지는 달을 건너뛰는
        // 경우까지 봐도 두 달을 넘지 않는다.
        val cursor = calendarAt(afterMillis)
        repeat(MAX_LOOKAHEAD_DAYS) {
            occurrenceOn(baseMillis, recurrence, cursor.timeInMillis)
                ?.takeIf { it > afterMillis }
                ?.let { return it }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    /** 그 날짜가 몇 회차인지 가리키는 열쇠. 회차마다 완료 여부를 따로 남길 때 쓴다. */
    fun occurrenceKey(dayMillis: Long): String {
        val day = calendarAt(dayMillis)
        return "%04d-%02d-%02d".format(
            day.get(Calendar.YEAR),
            day.get(Calendar.MONTH) + 1,
            day.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * 매월 반복에서 그 달에 실제로 쓸 날짜.
     *
     * 31일에 정한 일정은 2월에 그 날짜가 없다. 건너뛰면 두 달에 한 번씩 조용히 사라지므로,
     * 그 달의 마지막 날로 당긴다. 놓치면 안 되는 일을 챙기는 앱이라 거르는 쪽보다 낫다.
     */
    private fun clampedDayOfMonth(
        base: Calendar,
        day: Calendar,
    ): Int = minOf(base.get(Calendar.DAY_OF_MONTH), day.getActualMaximum(Calendar.DAY_OF_MONTH))

    private fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply { time = Date(millis) }

    private fun startOfDay(calendar: Calendar): Long =
        (calendar.clone() as Calendar)
            .apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    private fun isSameDay(
        first: Calendar,
        second: Calendar,
    ): Boolean = startOfDay(first) == startOfDay(second)

    /** [day] 의 날짜에 [base] 의 시·분·초를 얹는다. */
    private fun withTimeOf(
        base: Calendar,
        day: Calendar,
    ): Long =
        (day.clone() as Calendar)
            .apply {
                set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, base.get(Calendar.MINUTE))
                set(Calendar.SECOND, base.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, base.get(Calendar.MILLISECOND))
            }.timeInMillis

    /** 두 달이면 어떤 규칙이든 다음 회차가 나온다. 무한 루프를 막는 상한이다. */
    private const val MAX_LOOKAHEAD_DAYS = 70
}
