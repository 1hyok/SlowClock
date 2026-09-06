// app/src/main/java/com/example/slowclock/data/remote/repository/ScheduleRepository.kt
package com.example.slowclock.data.remote.repository

import android.util.Log
import com.example.slowclock.data.FirestoreCollections
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.example.slowclock.util.Recurrence
import com.example.slowclock.util.RecurrenceRule
import com.example.slowclock.util.toAppError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * Schedule 컬렉션에 대한 저장소 클래스 (정리된 버전)
 */
class ScheduleRepository
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        private val firestore: FirebaseFirestore,
    ) {
        private val schedulesCollection = firestore.collection(FirestoreCollections.SCHEDULES)
        private val usersCollection = firestore.collection(FirestoreCollections.USERS)

        // 결과 타입 정의
        sealed class ScheduleResult<out T> {
            data class Success<T>(
                val data: T,
            ) : ScheduleResult<T>()

            data class Error(
                val error: AppError,
            ) : ScheduleResult<Nothing>()
        }

        // 🔥 수동 파싱 함수 - toObject() 대신 이걸 써야 해
        private fun parseScheduleFromDocument(doc: com.google.firebase.firestore.DocumentSnapshot): Schedule? {
            return try {
                val data = doc.data ?: return null

                // 각 필드를 개별적으로 파싱해서 확실하게 가져옴
                val id = doc.id
                val userId = data["userId"] as? String ?: ""
                val familyGroupId = data["familyGroupId"] as? String ?: ""
                val sharedCode = data["sharedCode"] as? String ?: ""
                val title = data["title"] as? String ?: ""
                val description = data["description"] as? String ?: ""
                val startTime = data["startTime"] as? Timestamp ?: Timestamp.now()
                val endTime = data["endTime"] as? Timestamp
                val recurringType = data["recurringType"] as? String
                val completedDates =
                    (data["completedDates"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()
                val updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now()

                // Boolean 필드들을 명시적으로 변환
                val completed =
                    when (val completed = data["completed"]) {
                        is Boolean -> {
                            completed
                        }

                        is String -> {
                            completed.toBoolean()
                        }

                        else -> {
                            Log.w(
                                "ScheduleRepo",
                                "completed 필드 파싱 실패: $completed (타입: ${completed?.javaClass})",
                            )
                            false
                        }
                    }

                val recurring =
                    when (val recurring = data["recurring"]) {
                        is Boolean -> recurring
                        is String -> recurring.toBoolean()
                        else -> false
                    }

                val skipped =
                    when (val skipped = data["skipped"]) {
                        is Boolean -> skipped
                        is String -> skipped.toBoolean()
                        else -> false
                    }

                Log.d("ScheduleRepo", "파싱된 일정: $title, 완료상태: $completed (원본: ${data["completed"]})")

                Schedule(
                    id = id,
                    userId = userId,
                    familyGroupId = familyGroupId,
                    sharedCode = sharedCode,
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    completed = completed,
                    recurring = recurring,
                    recurringType = recurringType,
                    completedDates = completedDates,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 파싱 실패: ${doc.id}", e)
                null
            }
        }

        /**
         * 문서 하나를 [dayMillis] 가 속한 날의 회차로 펼친다. 그날에 없으면 null.
         *
         * 반복 일정은 문서가 하나지만 날마다 다시 온다. 시작·종료 시각을 그날로 옮기고, 완료
         * 여부는 그 회차의 것만 본다. 이 펼침이 없으면 반복 일정이 만든 날 하루만 보이고
         * 다음 날부터 목록에서도 알람에서도 사라진다(#130).
         */
        private fun Schedule.occurrenceOn(dayMillis: Long): Schedule? {
            val recurrence = Recurrence.of(recurring, recurringType)
            val start =
                RecurrenceRule.occurrenceOn(
                    baseMillis = startTime.toDate().time,
                    recurrence = recurrence,
                    dayMillis = dayMillis,
                ) ?: return null
            val key = RecurrenceRule.occurrenceKey(start)
            // 종료 시각은 시작에서 떨어진 만큼 그대로 옮긴다. 자정을 넘는 일정도 길이가 유지된다.
            val shifted = start - startTime.toDate().time
            return copy(
                startTime = Timestamp(Date(start)),
                endTime = endTime?.let { Timestamp(Date(it.toDate().time + shifted)) },
                completed = if (recurrence == Recurrence.NONE) completed else completedDates.contains(key),
                occurrenceDate = key,
            )
        }

        // 특정 날짜의 data 가져오기
        suspend fun getSchedulesForDate(calendar: Calendar): ScheduleResult<List<Schedule>> {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Log.e("ScheduleRepo", "사용자 로그인 안됨")
                return ScheduleResult.Error(AppError.AuthError)
            }

            val startOfDay = calendar.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)
            startOfDay.set(Calendar.MILLISECOND, 0)

            val endOfDay = calendar.clone() as Calendar
            endOfDay.set(Calendar.HOUR_OF_DAY, 23)
            endOfDay.set(Calendar.MINUTE, 59)
            endOfDay.set(Calendar.SECOND, 59)
            endOfDay.set(Calendar.MILLISECOND, 999)

            return try {
                val documents =
                    schedulesCollection
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()

                val allSchedules = documents.mapNotNull { parseScheduleFromDocument(it) }

                val selectedDateSchedules =
                    allSchedules
                        .mapNotNull { it.occurrenceOn(calendar.timeInMillis) }
                        .sortedBy { it.startTime }

                ScheduleResult.Success(selectedDateSchedules)
            } catch (e: Exception) {
                ScheduleResult.Error(e.toAppError())
            }
        }

        // 일정 추가
        suspend fun addSchedule(schedule: Schedule): ScheduleResult<String> {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Log.e("ScheduleRepo", "사용자 로그인 안됨")
                return ScheduleResult.Error(AppError.AuthError)
            }

            // 데이터 검증
            if (schedule.title.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            // Fetch user's shareCode
            val userDoc =
                usersCollection
                    .document(uid)
                    .get()
                    .await()
            val userShareCode = userDoc.getString("shareCode") ?: ""
            Log.d("ScheduleRepo", "Fetched userShareCode: '$userShareCode'")
            if (userShareCode.isBlank()) {
                Log.w("ScheduleRepo", "User's shareCode is blank! Schedule will be saved without a sharedCode.")
            }

            val newSchedule =
                schedule.copy(
                    userId = uid,
                    sharedCode = userShareCode,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now(),
                )

            return try {
                val docRef = schedulesCollection.document()
                val scheduleWithId = newSchedule.copy(id = docRef.id)
                Log.d("ScheduleRepo", "일정 저장 시도: ${scheduleWithId.title}")

                docRef.set(scheduleWithId).await()
                Log.d("ScheduleRepo", "일정 저장 성공: ${docRef.id}")
                ScheduleResult.Success(docRef.id)
            } catch (e: FirebaseFirestoreException) {
                Log.e("ScheduleRepo", "Firestore 저장 에러: ${e.code}", e)
                val error =
                    when (e.code) {
                        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NetworkError
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.TimeoutError
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionError
                        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.StorageFullError
                        else -> AppError.SaveError
                    }
                ScheduleResult.Error(error)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 저장 실패", e)
                ScheduleResult.Error(AppError.SaveError)
            }
        }

        /**
         * 일정 완료 상태 변경.
         *
         * 반복 일정은 회차마다 따로 남긴다([occurrenceDate] 가 그 회차의 날짜다). `completed`
         * 는 문서에 하나뿐이라 반복 일정에 쓰면 한 번 완료한 뒤 영영 완료로 남는다(#130).
         */
        suspend fun markScheduleAsCompleted(
            scheduleId: String,
            completed: Boolean = true,
            occurrenceDate: String = "",
        ): ScheduleResult<Unit> {
            if (scheduleId.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            return try {
                Log.d("ScheduleRepo", "완료 상태 변경 시도: $scheduleId -> $completed ($occurrenceDate)")

                val change =
                    if (occurrenceDate.isBlank()) {
                        mapOf<String, Any>("completed" to completed)
                    } else {
                        // 회차 하나만 더하거나 뺀다. 다른 기기가 같은 문서의 다른 회차를 건드려도
                        // 서로 덮어쓰지 않는다.
                        val op =
                            if (completed) {
                                FieldValue.arrayUnion(occurrenceDate)
                            } else {
                                FieldValue.arrayRemove(occurrenceDate)
                            }
                        mapOf("completedDates" to op)
                    }

                schedulesCollection
                    .document(scheduleId)
                    .update(change + ("updatedAt" to Timestamp.now()))
                    .await()

                Log.d("ScheduleRepo", "완료 상태 변경 성공: $scheduleId -> $completed")
                ScheduleResult.Success(Unit)
            } catch (e: FirebaseFirestoreException) {
                Log.e("ScheduleRepo", "완료 상태 변경 실패: ${e.code}", e)
                val error =
                    when (e.code) {
                        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFoundError
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionError
                        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NetworkError
                        else -> AppError.GeneralError("상태 변경에 실패했습니다")
                    }
                ScheduleResult.Error(error)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "완료 상태 변경 중 예상치 못한 에러", e)
                ScheduleResult.Error(e.toAppError())
            }
        }

        suspend fun updateSchedule(schedule: Schedule): ScheduleResult<Unit> {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                return ScheduleResult.Error(AppError.AuthError)
            }

            if (schedule.id.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            if (schedule.title.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            // Always preserve sharedCode and all fields from the original schedule
            val updatedSchedule =
                schedule.copy(
                    userId = uid, // 현재 사용자 ID로 강제 설정
                    updatedAt = Timestamp.now(),
                    sharedCode = schedule.sharedCode, // ensure sharedCode is preserved
                )

            return try {
                schedulesCollection
                    .document(schedule.id)
                    .set(updatedSchedule)
                    .await()

                Log.d("ScheduleRepo", "일정 수정 성공: ${schedule.id}")
                ScheduleResult.Success(Unit)
            } catch (e: FirebaseFirestoreException) {
                Log.e("ScheduleRepo", "일정 수정 실패: ${e.code}", e)
                val error =
                    when (e.code) {
                        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFoundError
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionError
                        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NetworkError
                        else -> AppError.SaveError
                    }
                ScheduleResult.Error(error)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 수정 중 예상치 못한 에러", e)
                ScheduleResult.Error(AppError.SaveError)
            }
        }

        // 일정 삭제
        suspend fun deleteSchedule(scheduleId: String): ScheduleResult<Unit> {
            if (scheduleId.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            return try {
                schedulesCollection
                    .document(scheduleId)
                    .delete()
                    .await()

                Log.d("ScheduleRepo", "일정 삭제 성공: $scheduleId")
                ScheduleResult.Success(Unit)
            } catch (e: FirebaseFirestoreException) {
                Log.e("ScheduleRepo", "일정 삭제 실패: ${e.code}", e)
                val error =
                    when (e.code) {
                        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFoundError
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionError
                        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NetworkError
                        else -> AppError.GeneralError("삭제에 실패했습니다")
                    }
                ScheduleResult.Error(error)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 삭제 중 예상치 못한 에러", e)
                ScheduleResult.Error(e.toAppError())
            }
        }

        // ID로 일정 가져오기 (편집용)
        suspend fun getScheduleById(scheduleId: String): ScheduleResult<Schedule> {
            if (scheduleId.isBlank()) {
                return ScheduleResult.Error(AppError.InvalidDataError)
            }

            return try {
                val document = schedulesCollection.document(scheduleId).get().await()

                if (!document.exists()) {
                    return ScheduleResult.Error(AppError.NotFoundError)
                }

                val schedule = parseScheduleFromDocument(document)
                if (schedule != null) {
                    ScheduleResult.Success(schedule)
                } else {
                    ScheduleResult.Error(AppError.InvalidDataError)
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e("ScheduleRepo", "일정 조회 실패: ${e.code}", e)
                val error =
                    when (e.code) {
                        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NetworkError
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionError
                        else -> AppError.NotFoundError
                    }
                ScheduleResult.Error(error)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 조회 중 예상치 못한 에러", e)
                ScheduleResult.Error(e.toAppError())
            }
        }

        // 특정 날짜의 일정 실시간 구독. 로그인 전이면 빈 목록을 한 번 내고 끝난다.
        fun observeSchedulesForDate(calendar: Calendar): Flow<List<Schedule>> {
            val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
            val dayMillis = calendar.timeInMillis
            return callbackFlow {
                val listener =
                    schedulesCollection
                        .whereEqualTo("userId", uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                close(error)
                                return@addSnapshotListener
                            }
                            val schedules =
                                snapshot
                                    ?.documents
                                    ?.mapNotNull { parseScheduleFromDocument(it) }
                                    ?.mapNotNull { it.occurrenceOn(dayMillis) }
                                    ?.sortedBy { it.startTime }
                                    ?: emptyList()
                            trySend(schedules)
                        }
                awaitClose { listener.remove() }
            }
        }

        /**
         * 공유 코드로 일정(리마인더) 목록을 실시간 구독한다.
         *
         * 로그인 전이면 빈 목록을 한 번 내고 끝난다. 보안 규칙의 schedules 읽기가 전부
         * `isSignedIn()` 을 요구하므로, 가드가 없으면 로그인 전에 건 리스너가
         * PERMISSION_DENIED 로 닫히고 그 흐름은 다시 붙지 않는다(#134).
         *
         * 반복 일정은 오늘 회차로 펼쳐 낸다. 화면이 오늘 것만 거르므로, 펼치지 않으면 가족의
         * 반복 일정은 만든 날 하루만 보인다(#130).
         */
        fun observeSchedulesBySharedCode(sharedCode: String): Flow<List<Schedule>> {
            auth.currentUser?.uid ?: return flowOf(emptyList())
            return callbackFlow {
                val listener =
                    schedulesCollection
                        .whereEqualTo("sharedCode", sharedCode)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                close(error)
                                return@addSnapshotListener
                            }
                            val today = Calendar.getInstance().timeInMillis
                            val schedules =
                                snapshot
                                    ?.documents
                                    ?.mapNotNull { parseScheduleFromDocument(it) }
                                    ?.mapNotNull { it.occurrenceOn(today) }
                                    ?: emptyList()
                            trySend(schedules)
                        }
                awaitClose { listener.remove() }
            }
        }

        // 계정 삭제용: 사용자가 만든 일정 전부 삭제
        suspend fun deleteAllSchedulesOf(userId: String): Boolean =
            try {
                val documents =
                    schedulesCollection
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                documents.documents.chunked(FirestoreCollections.BATCH_LIMIT).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                true
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "사용자 일정 일괄 삭제 실패", e)
                false
            }
    }
