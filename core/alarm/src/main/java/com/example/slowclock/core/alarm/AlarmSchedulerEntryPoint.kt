package com.example.slowclock.core.alarm

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt 가 못 넣어 주는 자리에서 [AlarmScheduler] 를 꺼내는 통로.
 *
 * `@AndroidEntryPoint` 는 Kotlin BroadcastReceiver 에서 `super.onReceive` 를 부를 수 없어 쓰지
 * 않는다. 서비스도 같은 통로를 쓰게 모아 둔다 — 예약과 장부를 한 자리에 묶어 두는 것이
 * [AlarmScheduler] 의 몫이라, 이 통로를 안 지나면 장부가 샌다(#127 · #177).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmSchedulerEntryPoint {
    fun alarmScheduler(): AlarmScheduler

    companion object {
        fun from(context: Context): AlarmScheduler =
            EntryPointAccessors
                .fromApplication(context.applicationContext, AlarmSchedulerEntryPoint::class.java)
                .alarmScheduler()
    }
}
