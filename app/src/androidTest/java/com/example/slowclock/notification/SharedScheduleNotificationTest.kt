package com.example.slowclock.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.slowclock.data.remote.repository.AuthRepository
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Opt in on a disposable emulator with -e sharedAuthEmulatorPort 9096. Never connects to production Auth. */
@RunWith(AndroidJUnit4::class)
class SharedScheduleNotificationTest {
    private lateinit var firebaseApp: FirebaseApp
    private lateinit var auth: AuthRepository
    private lateinit var settings: SettingsRepository
    private lateinit var notifier: SharedScheduleNotifier
    private lateinit var manager: NotificationManager
    private lateinit var context: Context
    private lateinit var message: SharedScheduleMessage

    @Before
    fun setUp() {
        val port = InstrumentationRegistry.getArguments().getString("sharedAuthEmulatorPort")?.toIntOrNull()
        assumeTrue("Requires an explicit local Auth emulator fixture", port != null)
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context =
            object : ContextWrapper(targetContext) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ) = super.getSharedPreferences("fcm_fixture_$name", mode)
            }
        manager = context.getSystemService(NotificationManager::class.java)
        firebaseApp = FirebaseApp.getApps(context).firstOrNull { it.name == "fcm-notification-fixture" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions
                    .Builder()
                    .setProjectId("demo-slowclock-fcm")
                    .setApplicationId("1:123456789:android:abcdef")
                    .setApiKey("fake-api-key-for-local-emulator-only")
                    .build(),
                "fcm-notification-fixture",
            )
        val firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
        firebaseAuth.useEmulator("10.0.2.2", port!!)
        Tasks.await(firebaseAuth.signInAnonymously(), 20, TimeUnit.SECONDS)
        auth = AuthRepository(firebaseAuth)
        settings = SettingsRepository(context)
        settings.setShareCode("CODE01")
        notifier = SharedScheduleNotifier(context, auth, settings)
        message = SharedScheduleMessage(auth.currentUid!!, "CODE01", "id", "Fixture change", "Fixture schedule")
        notifier.changeSession {}
    }

    @After
    fun tearDown() {
        if (::notifier.isInitialized) {
            notifier.changeSession {
                settings.clearShareCode()
                auth.signOut()
            }
        }
        if (::manager.isInitialized) manager.cancel("fcm-fixture-alarm", 704)
    }

    @Test
    fun currentSessionOnlyAndDistinctScheduleIds() {
        assertTrue(manager.areNotificationsEnabled())
        assertTrue(notifier.show(message))
        assertFalse(notifier.show(message.copy(recipientUid = "other")))
        assertFalse(notifier.show(message.copy(shareCode = "OTHER1")))
        awaitSharedCount(1)
        assertTrue(notifier.show(message.copy(title = "Updated")))
        awaitSharedCount(1)
        assertTrue(notifier.show(message.copy(scheduleId = "Aa")))
        assertTrue(notifier.show(message.copy(scheduleId = "BB")))
        awaitSharedCount(3)
    }

    @Test
    fun logoutClearsNewAndLegacySharedNotificationsButKeepsAlarm() {
        assertTrue(notifier.show(message))
        manager.createNotificationChannel(NotificationChannel("fcm-fixture-alarm", "Fixture alarm", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(
            NotificationChannel("fcm_fallback_notification_channel", "Legacy fixture", NotificationManager.IMPORTANCE_DEFAULT),
        )

        fun notification(channel: String) =
            Notification
                .Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Fixture")
                .build()
        manager.notify("fcm-fixture-alarm", 704, notification("fcm-fixture-alarm"))
        manager.notify(0, notification("schedule_channel"))
        manager.notify("FCM-Notification:fixture", 0, notification("fcm_fallback_notification_channel"))
        awaitCondition { manager.activeNotifications.size == 4 }
        notifier.changeSession {
            settings.clearShareCode()
            auth.signOut()
        }
        awaitCondition { manager.activeNotifications.size == 1 }
        assertEquals("fcm-fixture-alarm", manager.activeNotifications.single().tag)
        assertFalse(notifier.show(message))
        awaitSharedCount(0)
    }

    @Test
    fun codeReplacementAndUnregisterRemovePreviousNotifications() {
        assertTrue(notifier.show(message))
        awaitSharedCount(1)
        assertTrue(notifier.replaceShareCode(notifier.snapshot(), "NEW002"))
        awaitSharedCount(0)
        assertFalse(notifier.show(message))
        assertTrue(notifier.show(message.copy(shareCode = "NEW002")))
        awaitSharedCount(1)
        assertTrue(notifier.replaceShareCode(notifier.snapshot(), null))
        awaitSharedCount(0)
        assertFalse(notifier.show(message.copy(shareCode = "NEW002")))
    }

    @Test
    fun permissionDeniedDoesNotPost() {
        assumeTrue(
            "Run separately with POST_NOTIFICATIONS revoked",
            InstrumentationRegistry.getArguments().getString("sharedPermissionDenied") == "true",
        )
        assertFalse(manager.areNotificationsEnabled())
        assertFalse(notifier.show(message))
        awaitSharedCount(0)
    }

    private fun awaitSharedCount(count: Int) =
        awaitCondition {
            manager.activeNotifications.count { it.tag?.startsWith(SharedScheduleNotifier.TAG_PREFIX) == true } == count
        }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue(condition())
    }
}
