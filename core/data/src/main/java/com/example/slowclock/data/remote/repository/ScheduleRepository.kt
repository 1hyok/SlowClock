// app/src/main/java/com/example/slowclock/data/remote/repository/ScheduleRepository.kt
package com.example.slowclock.data.remote.repository

import android.util.Log
import com.example.slowclock.data.FirestoreCollections
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.util.AppError
import com.example.slowclock.util.occurrenceOn
import com.example.slowclock.util.toAppError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
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

        /**
         * 사용자의 공유 코드를 읽는다. 던지는 예외는 부르는 쪽의 try 가 받는다.
         *
         * 코드가 비어 있으면 그 일정은 가족이 어떤 코드로도 읽을 수 없다. 보안 규칙의 공유
         * 읽기 조건이 `sharedCode != ""` 이기 때문이다. 그래서 비어 있는 것을 경고로 남긴다.
         */
        private suspend fun fetchShareCode(uid: String): String {
            val userShareCode =
                usersCollection
                    .document(uid)
                    .get()
                    .await()
                    .getString("shareCode")
                    .orEmpty()
            if (userShareCode.isBlank()) {
                Log.w("ScheduleRepo", "공유 코드가 비어 있어 이 일정은 가족에게 보이지 않는다")
            }
            // 앱 시작의 비동기 복구보다 저장이 먼저 실행돼도 등록부를 확인한 뒤 쓴다.
            firestore.ensureShareCodeOwner(uid, userShareCode)
            return userShareCode
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

            return try {
                // 공유 코드 읽기도 저장과 같은 try 안에 둔다. 이 읽기는 오프라인이거나 토큰이
                // 무효해지면 그대로 던지는데, 호출자인 ViewModel 은 viewModelScope 에서 부르고
                // 잡지 않는다. try 밖에 두면 저장 실패는 안내가 되고 이 읽기 실패만 앱을
                // 강제 종료시킨다(#133).
                val userShareCode = fetchShareCode(uid)
                val newSchedule =
                    schedule.copy(
                        userId = uid,
                        sharedCode = userShareCode,
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now(),
                    )

                val docRef = schedulesCollection.document()
                val scheduleWithId = newSchedule.copy(id = docRef.id)
                Log.d("ScheduleRepo", "일정 저장 시도: ${scheduleWithId.title}")

                docRef.set(scheduleWithId).await()
                Log.d("ScheduleRepo", "일정 저장 성공: ${docRef.id}")
                ScheduleResult.Success(docRef.id)
            } catch (e: ShareCodeOwnershipException) {
                ScheduleResult.Error(AppError.GeneralError(e.message.orEmpty()))
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

            // 편집 화면이 바꾼 필드만 쓴다. 읽은 뒤 바뀐 완료 기록·공유 코드·소유자를 되돌리거나,
            // 이미 삭제된 문서를 set 으로 다시 만들지 않는다.
            val changes =
                mapOf(
                    "title" to schedule.title,
                    "description" to schedule.description,
                    "startTime" to schedule.startTime,
                    "endTime" to schedule.endTime,
                    "recurring" to schedule.recurring,
                    "recurringType" to schedule.recurringType,
                    "updatedAt" to Timestamp.now(),
                )

            return try {
                firestore.ensureShareCodeOwner(uid, updatedSchedule.sharedCode)
                schedulesCollection
                    .document(schedule.id)
                    .update(changes)
                    .await()

                Log.d("ScheduleRepo", "일정 수정 성공: ${schedule.id}")
                ScheduleResult.Success(Unit)
            } catch (e: ShareCodeOwnershipException) {
                ScheduleResult.Error(AppError.GeneralError(e.message.orEmpty()))
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

        /**
         * 특정 날짜의 일정 실시간 구독. 로그인 전이면 빈 목록을 한 번 내고 끝난다.
         *
         * [today] 가 true 면 「오늘」 을 구독하는 것이고, 회차를 펼칠 날짜를 스냅샷이 올 때마다
         * 다시 읽는다. 붙잡아 두면 자정을 넘긴 뒤에도 어제 회차로 펼쳐지고, 그 회차 식별자가
         * 완료 기록의 열쇠라 잘못된 날짜가 서버에 남는다(#171). 날짜를 사용자가 고르는 화면은
         * false 로 두어 고른 날이 흔들리지 않게 한다.
         */
        fun observeSchedulesForDate(
            calendar: Calendar,
            today: Boolean = false,
        ): Flow<List<Schedule>> {
            val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
            val fixedDayMillis = calendar.timeInMillis
            return callbackFlow {
                val listener =
                    schedulesCollection
                        .whereEqualTo("userId", uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                close(error)
                                return@addSnapshotListener
                            }
                            val dayMillis = if (today) System.currentTimeMillis() else fixedDayMillis
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

        /**
         * 사용자가 만든 일정 전부. 알람을 이 기기에 다시 맞추는 데 쓴다.
         *
         * 오늘 것만으로는 모자란다. 알람 장부는 회차가 아니라 일정 단위로 남고, 며칠 뒤 한 번만
         * 있는 일정도 지금 걸어 두어야 그날 울린다(#176).
         *
         * 실패하면 null 이다. 빈 목록과 갈라야 한다 — 빈 목록으로 맞추면 걸려 있던 알람을 전부
         * 지우게 된다. 캐시는 일부 문서만 담을 수 있으므로 서버 응답으로만 맞춘다.
         */
        suspend fun getSchedulesOf(userId: String): List<Schedule>? =
            try {
                schedulesCollection
                    .whereEqualTo("userId", userId)
                    .get(Source.SERVER)
                    .await()
                    .mapNotNull { parseScheduleFromDocument(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "알람을 맞출 일정 목록 조회 실패", e)
                null
            }

        /**
         * 공유 코드 없이 저장된 내 일정에 코드를 채운다. 채운 건수를 낸다.
         *
         * 일정은 저장할 때의 공유 코드를 문서에 굳혀 넣는다. 신호가 약한 곳에서 처음 로그인하면
         * 코드가 비어 있고, 그 사이에 만든 일정은 `sharedCode` 가 빈 채로 남는다. 보안 규칙의
         * 공유 읽기가 `sharedCode != ""` 를 요구하므로, 나중에 코드를 만들어도 그 일정들은
         * 가족이 어떤 코드로도 읽지 못한다.
         *
         * 사용자 쪽에서는 코드를 만들어 가족에게 알려 줬는데 일정이 안 보인다. 화면 어디에도
         * 이유가 없고 되돌릴 길도 없다 — 「공유 코드 다시 만들기」(#134)는 사용자 문서만 고쳤다.
         * 그래서 코드를 보장하는 자리에서 이미 저장된 일정도 함께 맞춘다(#178).
         *
         * 실패해도 던지지 않는다. 코드를 만든 결과를 이 일이 뒤집으면 안 되고, 다음 로그인에서
         * 다시 맞추면 된다.
         */
        suspend fun fillMissingSharedCode(userId: String): Int =
            try {
                val shareCode = fetchShareCode(userId)
                if (shareCode.isBlank()) {
                    0
                } else {
                    val stale =
                        schedulesCollection
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("sharedCode", "")
                            .get()
                            .await()
                            .documents
                    stale.chunked(FirestoreCollections.BATCH_LIMIT).forEach { chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { document ->
                            batch.update(
                                document.reference,
                                mapOf("sharedCode" to shareCode, "updatedAt" to Timestamp.now()),
                            )
                        }
                        batch.commit().await()
                    }
                    if (stale.isNotEmpty()) Log.d("ScheduleRepo", "공유 코드를 채운 일정: ${stale.size}건")
                    stale.size
                }
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "빠진 공유 코드 채우기 실패", e)
                0
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
