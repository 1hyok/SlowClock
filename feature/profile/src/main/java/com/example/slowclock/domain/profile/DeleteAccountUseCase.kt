package com.example.slowclock.domain.profile

import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.FamilyGroupRepository
import com.example.slowclock.data.remote.repository.NotificationRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.UserRepository
import javax.inject.Inject

/** 계정 삭제 중 실패한 단계. 사용자 안내와 재시도 판단에 쓴다. */
enum class DeleteAccountStep {
    SCHEDULES,
    FAMILY_GROUPS,
    NOTIFICATIONS,
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
 */
class DeleteAccountUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val scheduleRepository: ScheduleRepository,
        private val familyGroupRepository: FamilyGroupRepository,
        private val notificationRepository: NotificationRepository,
        private val userRepository: UserRepository,
    ) {
        suspend operator fun invoke(): DeleteAccountResult {
            val uid = authRepository.currentUid ?: return DeleteAccountResult.NotSignedIn

            if (!scheduleRepository.deleteAllSchedulesOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES)
            }
            if (!familyGroupRepository.leaveAllGroupsOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.FAMILY_GROUPS)
            }
            if (!notificationRepository.deleteAllNotificationsOf(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.NOTIFICATIONS)
            }
            if (!userRepository.deleteUserDocument(uid)) {
                return DeleteAccountResult.Failed(DeleteAccountStep.USER_DOCUMENT)
            }

            return when (authRepository.deleteCurrentUser()) {
                AuthRepository.DeleteResult.Success -> DeleteAccountResult.Success
                AuthRepository.DeleteResult.NotSignedIn -> DeleteAccountResult.NotSignedIn
                AuthRepository.DeleteResult.RecentLoginRequired -> DeleteAccountResult.RecentLoginRequired
                is AuthRepository.DeleteResult.Failure -> DeleteAccountResult.Failed(DeleteAccountStep.AUTH_USER)
            }
        }
    }
