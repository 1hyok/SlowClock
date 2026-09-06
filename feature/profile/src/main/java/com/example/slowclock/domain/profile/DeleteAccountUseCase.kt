package com.example.slowclock.domain.profile

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.FamilyGroupRepository
import com.example.slowclock.data.remote.repository.NotificationRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.notification.SharedScheduleNotifier
import javax.inject.Inject

/** 계정 삭제 중 실패한 단계. 사용자 안내와 재시도 판단에 쓴다. */
enum class DeleteAccountStep {
    SCHEDULES,
    FAMILY_GROUPS,
    NOTIFICATIONS,
    SHARE_CODE_WATCHERS,
    USER_DOCUMENT,
    AUTH_USER,
}

sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    data object NotSignedIn : DeleteAccountResult

    /** Firebase 가 최근 로그인을 요구했다. 데이터는 지워졌고 Auth 계정만 남았다. 재로그인 뒤 다시 실행하면 끝난다. */
    data object RecentLoginRequired : DeleteAccountResult

    data class Failed(
        val step: DeleteAccountStep,
    ) : DeleteAccountResult
}

/**
 * 계정 삭제. 여러 Repository 를 순서대로 조합한다.
 *
 * Firestore 데이터를 먼저 지우고 마지막에 Auth 사용자를 지운다. Auth 사용자가 먼저 사라지면
 * 남은 문서를 지울 권한이 없어지므로, 데이터 단계가 하나라도 실패하면 Auth 삭제로 넘어가지 않는다.
 * 공유 코드 감시자 등록도 같은 이유로 이 안에서 지운다. 밖에 두면 지울 수 있는 사람이 없어진다.
 */
class DeleteAccountUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val scheduleRepository: ScheduleRepository,
        private val familyGroupRepository: FamilyGroupRepository,
        private val notificationRepository: NotificationRepository,
        private val userRepository: UserRepository,
        private val settingsRepository: SettingsRepository,
        private val alarmScheduler: AlarmScheduler,
        private val sharedScheduleNotifier: SharedScheduleNotifier,
    ) {
        suspend operator fun invoke(): DeleteAccountResult {
            val uid = authRepository.currentUid ?: return DeleteAccountResult.NotSignedIn

            if (!scheduleRepository.deleteAllSchedulesOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES)
            }
            // 서버 일정이 사라지면 이 기기에 걸린 알람은 근거를 잃는다. 지우지 않으면 계정을
            // 지운 뒤에도 울리고, 재부팅 뒤에도 장부를 보고 되살아난다(#127).
            alarmScheduler.cancelAll()
            if (!familyGroupRepository.leaveAllGroupsOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.FAMILY_GROUPS)
            }
            if (!notificationRepository.deleteAllNotificationsOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.NOTIFICATIONS)
            }
            // 가족의 공유 코드에 걸어 둔 내 감시자 등록. 보안 규칙이 본인만 지우게 하므로
            // Auth 사용자가 사라진 뒤에는 아무도 못 지운다. 반드시 여기서 먼저 지운다(#124).
            val watchedShareCode = settingsRepository.getShareCode()
            if (!watchedShareCode.isNullOrBlank() &&
                !userRepository.unregisterShareCodeWatcher(watchedShareCode)
            ) {
                return DeleteAccountResult.Failed(DeleteAccountStep.SHARE_CODE_WATCHERS)
            }
            if (!userRepository.deleteUserDocument(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.USER_DOCUMENT)
            }

            return when (authRepository.deleteCurrentUser()) {
                AuthRepository.DeleteResult.Success -> {
                    sharedScheduleNotifier.changeSession { settingsRepository.clearShareCode() }
                    DeleteAccountResult.Success
                }

                AuthRepository.DeleteResult.NotSignedIn -> {
                    sharedScheduleNotifier.changeSession { settingsRepository.clearShareCode() }
                    DeleteAccountResult.NotSignedIn
                }

                AuthRepository.DeleteResult.RecentLoginRequired -> {
                    DeleteAccountResult.RecentLoginRequired
                }

                is AuthRepository.DeleteResult.Failure -> {
                    DeleteAccountResult.Failed(DeleteAccountStep.AUTH_USER)
                }
            }
        }
    }
