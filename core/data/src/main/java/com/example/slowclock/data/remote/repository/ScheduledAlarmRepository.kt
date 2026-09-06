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
) {
    val rule: Recurrence
        get() = runCatching { Recurrence.valueOf(recurrence) }.getOrDefault(Recurrence.NONE)

    /**
     * [nowMillis] 뒤에 울릴 첫 시각. 더 없으면 null.
     *
     * 되풀이하지 않는 일정은 시작이나 종료 중 아직 안 온 것이 있을 때만 값이 있다. 되풀이하는
     * 일정은 언제 물어도 값이 있다 — 끝이 없기 때문이다.
     *
     * 경계는 알람을 거는 쪽과 같은 `> now` 다. 기준이 갈리면 장부에는 있는데 걸리지는 않는
     * 유령 기록이 쌓인다.
     */
    fun nextTriggerAfter(nowMillis: Long): Long? {
        if (rule == Recurrence.NONE) {
            return listOfNotNull(startMillis, endMillis).filter { it > nowMillis }.minOrNull()
        }
        return RecurrenceRule.nextOccurrenceAfter(startMillis, rule, nowMillis)
    }

    fun isLive(nowMillis: Long): Boolean = nextTriggerAfter(nowMillis) != null
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
