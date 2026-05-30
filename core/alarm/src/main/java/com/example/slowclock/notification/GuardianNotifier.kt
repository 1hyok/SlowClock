package com.example.slowclock.notification

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.lang.reflect.Method

object GuardianNotifier {
    // Replace with your deployed Cloud Function URL
    private const val CLOUD_FUNCTION_URL = "https://us-central1-slow-clock-scheduler.cloudfunctions.net/sendFcmNotification"

    fun sendReminderToUser(context: Context, fcmToken: String, title: String, message: String, shareCode: String? = null) {
        val json = JSONObject().apply {
            put("token", fcmToken)
            put("title", title)
            put("body", message)
            if (shareCode != null) {
                put("shareCode", shareCode)
            }
        }

        val request = object : JsonObjectRequest(
            Method.POST,
            CLOUD_FUNCTION_URL,
            json,
            { Log.d("FCM", "Push sent successfully via Cloud Function") },
            { error -> Log.e("FCM", "Push failed via Cloud Function", error) }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf(
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    fun sendReminderToUsers(context: Context, fcmTokens: List<String>, title: String, message: String, shareCode: String? = null) {
        for (token in fcmTokens) {
            sendReminderToUser(context, token, title, message, shareCode)
        }
    }
}
