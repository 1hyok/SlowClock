package com.example.slowclock.notification

import android.content.Context
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.slowclock.data.notification.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Cloud Function 을 통해 FCM 푸시를 보낸다. 앱 Context 로 Volley 큐를 하나만 유지한다. */
@Singleton
class GuardianNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Notifier {
        private val requestQueue: RequestQueue by lazy { Volley.newRequestQueue(context) }

        override fun sendReminderToUser(
            fcmToken: String,
            title: String,
            message: String,
            shareCode: String?,
        ) {
            val json =
                JSONObject().apply {
                    put("token", fcmToken)
                    put("title", title)
                    put("body", message)
                    if (shareCode != null) {
                        put("shareCode", shareCode)
                    }
                }

            val request =
                object : JsonObjectRequest(
                    Method.POST,
                    CLOUD_FUNCTION_URL,
                    json,
                    { Log.d("FCM", "Push sent successfully via Cloud Function") },
                    { error -> Log.e("FCM", "Push failed via Cloud Function", error) },
                ) {
                    override fun getHeaders(): MutableMap<String, String> =
                        mutableMapOf(
                            "Content-Type" to "application/json",
                        )
                }

            requestQueue.add(request)
        }

        override fun sendReminderToUsers(
            fcmTokens: List<String>,
            title: String,
            message: String,
            shareCode: String?,
        ) {
            for (token in fcmTokens) {
                sendReminderToUser(token, title, message, shareCode)
            }
        }

        private companion object {
            const val CLOUD_FUNCTION_URL = "https://us-central1-slow-clock-scheduler.cloudfunctions.net/sendFcmNotification"
        }
    }
