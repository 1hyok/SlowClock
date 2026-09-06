package com.example.slowclock.domain.profile

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Test

/**
 * 로그아웃이 기기에 남는 흔적을 함께 지우는지 본다.
 *
 * 지우지 않으면 로그아웃한 계정의 반복 알람이 그 기기에서 영구히 매일 울린다. 목록에는 없으니
 * 사용자가 끌 방법도 없다(#165).
 */
class SignOutUseCaseTest {
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val signOut = SignOutUseCase(authRepository, alarmScheduler, settingsRepository)

    @Test
    fun `세션을 끊기 전에 기기 잔재를 먼저 지운다`() {
        // 순서가 반대면 중간에 실패했을 때 로그인은 풀렸는데 알람만 남는다.
        signOut()

        verifyOrder {
            alarmScheduler.cancelAll()
            settingsRepository.clearShareCode()
            authRepository.signOut()
        }
    }
}
