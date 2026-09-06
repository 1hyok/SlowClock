package com.example.slowclock.core.alarm

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * 전체 화면 알람을 띄울 수 있는지.
 *
 * Android 14 부터 이 권한을 사용자가 끌 수 있고, 스토어가 통화·알람 앱이 아닌 앱에서 회수하기도
 * 한다. 꺼져 있으면 시스템이 전체 화면 대신 헤드업 알림으로 내린다. API 34 미만에는 이 통제
 * 자체가 없어 늘 true 다.
 *
 * 같은 질문의 답이 두 곳에서 갈리지 않도록 서비스와 화면이 모두 이 함수를 본다.
 *
 * https://developer.android.com/about/versions/14/behavior-changes-14
 */
internal fun Context.canUseFullScreenAlarm(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        NotificationManagerCompat.from(this).canUseFullScreenIntent()
