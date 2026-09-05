package com.example.slowclock.ui.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 시각과 날짜 표기를 한 곳에 모은다. 종전에는 화면마다 "a h:mm" 과 "HH:mm" 이 섞여 있어 같은
 * 일정이 화면마다 다르게 보였다(#109).
 *
 * 지역은 기기 설정이 아니라 한국어로 고정한다. 앱의 글은 모두 한국어라, 기기를 영어로 둔
 * 사람에게 "9월 6일 Sunday" 처럼 반씩 섞인 날짜가 나가는 것을 막는다.
 */
private val AppLocale: Locale = Locale.KOREAN

/** 오전·오후. 목록에서 시:분 위에 두 줄로 쌓아 시각 자리를 좁게 유지한다. */
@Composable
fun rememberMeridiemText(time: Date): String {
    val format = remember { SimpleDateFormat("a", AppLocale) }
    return remember(format, time) { format.format(time) }
}

/** 시:분. 12시간제로 낸다. 오전·오후는 [rememberMeridiemText] 가 맡는다. */
@Composable
fun rememberClockText(time: Date): String {
    val format = remember { SimpleDateFormat("h:mm", AppLocale) }
    return remember(format, time) { format.format(time) }
}

/** "오후 2:30" 처럼 한 줄로 낸다. 한 줄에 넣어야 하는 자리에 쓴다. */
@Composable
fun rememberTimeText(time: Date): String {
    val format = remember { SimpleDateFormat("a h:mm", AppLocale) }
    return remember(format, time) { format.format(time) }
}

/** "9월 6일 일요일". 화면 위쪽에서 오늘이 언제인지 알리는 자리에 쓴다. */
@Composable
fun rememberDayText(day: Date): String {
    val format = remember { SimpleDateFormat("M월 d일 EEEE", AppLocale) }
    return remember(format, day) { format.format(day) }
}
