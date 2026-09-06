package com.example.slowclock

import android.app.PendingIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.slowclock.ui.alarm.AlarmTriggerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** OS가 extras를 신원에서 제외해도 앞 회차 버튼이 다음 회차로 바뀌지 않는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class AlarmCommandIdentityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dismissAndSnoozePendingIntentsKeepTheirOwnOccurrence() {
        val first = AlarmTriggerService.dismissIntent(context, 42, "first-occurrence")
        val second = AlarmTriggerService.dismissIntent(context, 42, "second-occurrence")
        val snooze = AlarmTriggerService.snoozeIntent(context, 42, "second-occurrence")
        assertFalse(first.filterEquals(second))
        assertFalse(second.filterEquals(snooze))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val firstPending = PendingIntent.getService(context, 0, first, flags)
        val secondPending = PendingIntent.getService(context, 0, second, flags)
        try {
            assertNotEquals(firstPending, secondPending)
            assertEquals("first-occurrence", first.getStringExtra("alarmCommandToken"))
            assertEquals("second-occurrence", second.getStringExtra("alarmCommandToken"))
        } finally {
            firstPending.cancel()
            secondPending.cancel()
        }
    }

    @Test
    fun receiverToServiceIntentPreservesSnoozeCounts() {
        for (count in 0..2) {
            val intent = AlarmTriggerService.ringIntent(context, "약", "", true, "test-schedule", 42, count)
            assertEquals(count, intent.getIntExtra(AlarmTriggerService.EXTRA_SNOOZE_COUNT, -1))
            assertEquals(42, intent.getIntExtra(AlarmTriggerService.EXTRA_REQUEST_CODE, -1))
            assertEquals("test-schedule", intent.getStringExtra(AlarmTriggerService.EXTRA_SCHEDULE_ID))
        }
    }
}
