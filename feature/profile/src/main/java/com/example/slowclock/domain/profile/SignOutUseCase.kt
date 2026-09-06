package com.example.slowclock.domain.profile

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import javax.inject.Inject

/**
 * 로그아웃. 세션을 끊는 것으로 끝나지 않고 이 기기에 남는 그 사람의 흔적을 함께 지운다.
 *
 * 알람은 기기 안 장부로 살아 있고 로그인 여부를 보지 않는다. 반복 일정은 회차가 무한히
 * 이어지고(#130) 재부팅에도 살아남으므로(#127), 지우지 않으면 로그아웃한 계정의 알람이 그
 * 기기에서 영구히 매일 울린다. 목록에는 없으니 사용자가 끌 방법도 없다(#165).
 *
 * 등록해 둔 가족의 공유 코드도 지운다. 남겨 두면 같은 기기에 다른 사람이 로그인했을 때 앞
 * 사람이 등록한 가족의 일정이 그대로 보인다.
 *
 * 여러 Repository 를 순서대로 조합하고 두 화면이 함께 쓰므로 UseCase 로 둔다. 계정 삭제가
 * 이미 같은 짝을 [DeleteAccountUseCase] 에서 맞추고 있어 자리도 같다.
 */
class SignOutUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val alarmScheduler: AlarmScheduler,
        private val settingsRepository: SettingsRepository,
    ) {
        operator fun invoke() {
            // 세션을 끊기 전에 기기 잔재부터 지운다. 순서가 반대면 중간에 실패했을 때
            // 로그인은 풀렸는데 알람만 남는 상태가 된다.
            alarmScheduler.cancelAll()
            settingsRepository.clearShareCode()
            authRepository.signOut()
        }
    }
