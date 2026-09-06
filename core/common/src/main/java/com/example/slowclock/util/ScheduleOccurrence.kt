package com.example.slowclock.util

import com.example.slowclock.data.model.Schedule
import com.google.firebase.Timestamp
import java.util.Date

/**
 * 일정 문서를 [dayMillis] 가 속한 날의 회차로 펼친다. 그날에 없으면 null.
 *
 * 반복 일정은 문서가 하나지만 날마다 다시 온다. 시작·종료 시각을 그날로 옮기고, 완료 여부는
 * 그 회차의 것만 본다. 이 펼침이 없으면 반복 일정이 만든 날 하루만 보이고 다음 날부터 목록에서도
 * 알람에서도 사라진다(#130).
 *
 * 저장소 안 private 함수가 아니라 여기 둔 이유는 검증 때문이다. Firestore 없이 이 규칙만 따로
 * 시험할 수 없으면 「완료가 되돌아간다」 같은 회귀가 그냥 지나간다(#157).
 */
fun Schedule.occurrenceOn(dayMillis: Long): Schedule? {
    val recurrence = Recurrence.of(recurring, recurringType)
    val start =
        RecurrenceRule.occurrenceOn(
            baseMillis = startTime.toDate().time,
            recurrence = recurrence,
            dayMillis = dayMillis,
        ) ?: return null

    if (recurrence == Recurrence.NONE) {
        // 되풀이하지 않는 일정은 문서가 곧 그 회차다. 옮길 것도, 회차를 가릴 것도 없다.
        //
        // 회차 식별자를 채우면 안 된다. 화면이 그 값을 완료 처리에 넘기고 저장소는 값이 있으면
        // completedDates 를 고치는 갈래로 가는데, 읽을 때는 completed 를 보므로 쓴 곳과 읽는 곳이
        // 어긋난다. 그러면 완료 표시가 곧바로 되돌아간다(#157).
        return this
    }

    // 종료 시각은 시작에서 떨어진 만큼 그대로 옮긴다. 자정을 넘는 일정도 길이가 유지된다.
    val shifted = start - startTime.toDate().time
    val key = RecurrenceRule.occurrenceKey(start)
    return copy(
        startTime = Timestamp(Date(start)),
        endTime = endTime?.let { Timestamp(Date(it.toDate().time + shifted)) },
        completed = completedDates.contains(key),
        occurrenceDate = key,
    )
}
