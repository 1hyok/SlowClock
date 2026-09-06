package com.example.slowclock.domain.profile

import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.FamilyGroupRepository
import com.example.slowclock.data.remote.repository.NotificationRepository
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.data.remote.repository.UserRepository
import com.example.slowclock.notification.SharedScheduleNotifier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        private val alarmScheduler: AlarmScheduler,
        private val sharedScheduleNotifier: SharedScheduleNotifier,
    ) {
        suspend operator fun invoke(): DeleteAccountResult {
            currentCoroutineContext().ensureActive()
            val session = sharedScheduleNotifier.snapshot()
            val uid =
                session.userId ?: run {
                    sharedScheduleNotifier.clearDeletedAccount(session)
                    return DeleteAccountResult.NotSignedIn
                }

            suspend fun sessionIsCurrent(): Boolean {
                currentCoroutineContext().ensureActive()
                return sharedScheduleNotifier.snapshot() == session && authRepository.currentUid == uid
            }

            val schedulesDeleted = scheduleRepository.deleteAllSchedulesOf(uid)
            if (!sessionIsCurrent()) return DeleteAccountResult.NotSignedIn
            if (!schedulesDeleted) return DeleteAccountResult.Failed(DeleteAccountStep.SCHEDULES)
            // 앞 계정의 서버 응답이 새 계정의 기기 알람을 취소하지 않게 같은 세션 잠금에서 처리한다.
            if (!sharedScheduleNotifier.runIfCurrent(session) { alarmScheduler.cancelAll() }) return DeleteAccountResult.NotSignedIn

            val groupsLeft = familyGroupRepository.leaveAllGroupsOf(uid)
            if (!sessionIsCurrent()) return DeleteAccountResult.NotSignedIn
            if (!groupsLeft) return DeleteAccountResult.Failed(DeleteAccountStep.FAMILY_GROUPS)

            val notificationsDeleted = notificationRepository.deleteAllNotificationsOf(uid)
            if (!sessionIsCurrent()) return DeleteAccountResult.NotSignedIn
            if (!notificationsDeleted) return DeleteAccountResult.Failed(DeleteAccountStep.NOTIFICATIONS)

            // 늦은 콜백에서 현재 설정/현재 UID를 다시 선택하지 않는다.
            val watchedShareCode = session.shareCode
            if (!watchedShareCode.isNullOrBlank()) {
                val unregistered = userRepository.unregisterShareCodeWatcher(watchedShareCode, uid)
                if (!sessionIsCurrent()) return DeleteAccountResult.NotSignedIn
                if (!unregistered) return DeleteAccountResult.Failed(DeleteAccountStep.SHARE_CODE_WATCHERS)
            }
            val userDeleted = userRepository.deleteUserDocument(uid)
            if (!sessionIsCurrent()) return DeleteAccountResult.NotSignedIn
            if (!userDeleted) return DeleteAccountResult.Failed(DeleteAccountStep.USER_DOCUMENT)

            try {
                val authResult = authRepository.deleteCurrentUser(uid)
                currentCoroutineContext().ensureActive()
                val current = sharedScheduleNotifier.snapshot()
                if (current != session && current != session.copy(userId = null)) return DeleteAccountResult.NotSignedIn
                return when (authResult) {
                    AuthRepository.DeleteResult.Success -> DeleteAccountResult.Success
                    AuthRepository.DeleteResult.NotSignedIn -> DeleteAccountResult.NotSignedIn
                    AuthRepository.DeleteResult.RecentLoginRequired -> DeleteAccountResult.RecentLoginRequired
                    is AuthRepository.DeleteResult.Failure -> DeleteAccountResult.Failed(DeleteAccountStep.AUTH_USER)
                }
            } finally {
                // 원격 데이터/감시자를 이미 지웠다. Auth 대기 취소/실패에도 이 세션의 로컬 공유 흔적은 정리한다.
                sharedScheduleNotifier.clearDeletedAccount(session)
            }
        }
    }
