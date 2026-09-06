package com.example.slowclock.data.remote.repository

import android.content.Context
import android.util.Log
import com.example.slowclock.util.Recurrence
import com.example.slowclock.util.RecurrenceRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 걸어 둔 알람 하나의 기록. 재부팅 뒤 다시 걸 수 있는 최소한의 사실만 남긴다.
 *
 * Firestore 문서의 사본이 아니다. 복원 경로가 네트워크와 로그인을 타면 재부팅 직후에 못 걸고,
 * 그러면 그날 알람이 통째로 사라진다(#127).
 */
@Serializable
data class ScheduledAlarm(
    val id: String,
    val title: String,
    val description: String,
    /** 첫 회차의 시작 시각. 되풀이하는 일정은 여기서 규칙대로 세어 다음 회차를 얻는다. */
    val startMillis: Long,
    val endMillis: Long? = null,
    /** [Recurrence] 의 이름. 옛 기록에는 없으므로 기본값은 되풀이 없음이다. */
    val recurrence: String = Recurrence.NONE.name,
    /**
     * 지금 기기에 걸어 둔 회차의 시작 시각. 아직 아무것도 안 걸었으면 null.
     *
     * 이 값이 없으면 「지금 걸린 것이 어느 회차인가」 를 알 수 없다. 그러면 시작 알람이 울린
     * 직후 다음 회차를 앞당겨 걸면서, 아직 안 울린 그 회차의 종료 알람까지 지운다 — 자리가
     * 일정마다 시작 하나·종료 하나뿐이기 때문이다(#163).
     */
    val bookedStartMillis: Long? = null,
) {
    val rule: Recurrence
        get() = runCatching { Recurrence.valueOf(recurrence) }.getOrDefault(Recurrence.NONE)

    /** 시작에서 종료까지의 길이. 종료가 없으면 0 이다. */
    private val durationMillis: Long
        get() = endMillis?.let { it - startMillis } ?: 0L

    /**
     * 지금 걸어 두어야 할 회차의 시작 시각. 걸 것이 더 없으면 null.
     *
     * **걸어 둔 회차에 아직 울릴 것이 남아 있으면 그 회차를 그대로 지킨다.** 앞당겨 걸면
     * 남은 알람이 지워지기 때문이다(#163). 그래서 되풀이하는 일정은 종료 알람까지 울린 뒤에야
     * 다음 회차로 넘어간다.
     *
     * 경계는 알람을 거는 쪽과 같은 `> now` 다. 기준이 갈리면 장부에는 있는데 걸리지는 않는
     * 유령 기록이 쌓인다.
     */
    fun occurrenceToBook(nowMillis: Long): Long? {
        val booked = bookedStartMillis
        if (booked != null && (booked > nowMillis || booked + durationMillis > nowMillis)) {
            return booked
        }
        if (rule == Recurrence.NONE) {
            // 되풀이하지 않는 일정은 첫 회차가 전부다. 시작이 지났어도 종료가 남았으면 건다.
            return startMillis.takeIf { it > nowMillis || it + durationMillis > nowMillis }
        }
        // 아직 아무것도 안 걸었는데 진행 중인 회차가 있으면 그것부터 건다. 시작이 지난 뒤에
        // 저장하거나 재부팅한 경우다. 다음 회차부터 세면 오늘 종료 알람을 통째로 놓친다(#163).
        RecurrenceRule
            .occurrenceOn(startMillis, rule, nowMillis)
            ?.takeIf { it + durationMillis > nowMillis }
            ?.let { return it }
        return RecurrenceRule.nextOccurrenceAfter(startMillis, rule, nowMillis)
    }

    fun isLive(nowMillis: Long): Boolean = occurrenceToBook(nowMillis) != null

    /**
     * 걸어 둔 회차를 뺀 내용이 [other] 와 같은가.
     *
     * 서버 목록으로 이 기기의 알람을 맞출 때 쓴다. 같으면 다시 걸지 않고 지금 걸린 회차를
     * 지킨다. 매번 다시 걸면 앱을 열 때마다 방금 미뤄 둔 알람이 지워진다.
     */
    fun sameContentAs(other: ScheduledAlarm): Boolean = copy(bookedStartMillis = null) == other.copy(bookedStartMillis = null)
}

/**
 * 미뤄 둔 알람 하나의 기록.
 *
 * 다시 알림은 `AlarmManager` 에만 걸려 있어 재부팅과 앱 교체로 사라진다. 미룬 사람은 5분 뒤
 * 울릴 것이라고 믿고 있고, 그 믿음이 이 기능의 전부다. 그래서 걸어 둔 사실을 기기에 남긴다.
 *
 * [baseRequestCode] 가 이 기록의 열쇠다. 다시 알림은 원래 알람과 자리 번호가 같고 action 으로만
 * 갈리므로, 그 번호 하나가 「어느 알람을 미뤘는가」 를 가리킨다(#129).
 */
@Serializable
data class SnoozedAlarm(
    val baseRequestCode: Int,
    val scheduleId: String,
    val title: String,
    val description: String,
    val isFullScreen: Boolean,
    val snoozeCount: Int,
    /** 다시 울릴 시각. 이미 지났으면 복원에서 버린다. */
    val triggerAtMillis: Long,
)

/**
 * 기기 안에만 남는 「미뤄 둔 알람 장부」.
 *
 * [ScheduledAlarmRepository] 와 파일을 나눈다. 열쇠가 다르고(자리 번호 대 일정 id) 수명도 다르다
 * — 이쪽은 몇 분이면 없어질 기록이다. 한 파일에 섞으면 「전부 읽기」 가 둘을 갈라야 한다.
 */
@Singleton
class SnoozedAlarmRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val json = Json { ignoreUnknownKeys = true }

        fun save(alarm: SnoozedAlarm) {
            prefs.edit().putString(alarm.baseRequestCode.toString(), json.encodeToString(alarm)).apply()
        }

        fun remove(baseRequestCode: Int) {
            prefs.edit().remove(baseRequestCode.toString()).apply()
        }

        fun clear() {
            prefs.edit().clear().apply()
        }

        /** 장부 전체. 못 읽은 기록은 버린다. 하나가 깨졌다고 나머지까지 못 걸면 안 된다. */
        fun all(): List<SnoozedAlarm> =
            prefs.all.values.mapNotNull { raw ->
                (raw as? String)?.let { stored ->
                    runCatching { json.decodeFromString<SnoozedAlarm>(stored) }
                        .onFailure { Log.w(TAG, "깨진 다시 알림 기록을 버린다: ${it.message}") }
                        .getOrNull()
                }
            }

        private companion object {
            const val TAG = "SnoozedAlarmRepo"

            // scheduled_alarms 와 같은 이유로 백업에서 뺀다
            // (app/src/main/res/xml/data_extraction_rules.xml).
            const val PREFS_NAME = "snoozed_alarms"
        }
    }

/**
 * 기기 안에만 남는 「걸어 둔 알람 장부」.
 *
 * SharedPreferences 를 쓴다. 기록은 일정당 다섯 필드뿐이고 조회는 「전부 읽기」 하나라 쿼리도
 * 조인도 마이그레이션도 없다. 같은 성격의 자리를 [SettingsRepository] 가 이미 같은 방식으로
 * 들고 있어 수단을 둘로 가르지 않는다.
 *
 * DataStore 를 쓰지 않은 이유는 읽는 자리 때문이다. 복원은 BroadcastReceiver 안에서 몇 초 안에
 * 끝나야 하는데 DataStore 는 코루틴 전용이라 그 자리에서 메인 스레드를 막아야 한다. 부팅
 * 브로드캐스트에서 가장 피하고 싶은 모양이다.
 */
@Singleton
class ScheduledAlarmRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val json = Json { ignoreUnknownKeys = true }

        fun save(alarm: ScheduledAlarm) {
            prefs.edit().putString(alarm.id, json.encodeToString(alarm)).apply()
        }

        fun remove(scheduleId: String) {
            prefs.edit().remove(scheduleId).apply()
        }

        fun clear() {
            prefs.edit().clear().apply()
        }

        /** 장부 전체. 못 읽은 기록은 버린다. 하나가 깨졌다고 나머지 알람까지 못 걸면 안 된다. */
        fun all(): List<ScheduledAlarm> =
            prefs.all.values.mapNotNull { raw ->
                (raw as? String)?.let { stored ->
                    runCatching { json.decodeFromString<ScheduledAlarm>(stored) }
                        .onFailure { Log.w(TAG, "깨진 알람 기록을 버린다: ${it.message}") }
                        .getOrNull()
                }
            }

        private companion object {
            const val TAG = "ScheduledAlarmRepo"

            // settings 와 파일을 나눈다. 이 장부만 백업에서 빼야 하기 때문이다
            // (app/src/main/res/xml/data_extraction_rules.xml).
            const val PREFS_NAME = "scheduled_alarms"
        }
    }
