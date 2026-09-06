package com.example.slowclock.notification

import com.example.slowclock.data.remote.repository.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {
    @Inject lateinit var sharedScheduleNotifier: SharedScheduleNotifier

    @Inject lateinit var userRepository: UserRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // notification payload는 background에서 SDK가 직접 표시하므로 서버도 data-only로 바꾼다.
        // https://firebase.google.com/docs/cloud-messaging/android/receive-messages
        if (remoteMessage.notification != null) return
        val message = SharedScheduleMessage.fromData(remoteMessage.data) ?: return
        sharedScheduleNotifier.show(message)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sharedScheduleNotifier.withCurrentSession { session ->
            userRepository.updateFcmRegistration(token, session.userId.orEmpty(), session.shareCode)
        }
    }
}
