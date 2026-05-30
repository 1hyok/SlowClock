// app/src/main/java/com/example/slowclock/ui/main/MainViewModel.kt
package com.example.slowclock.ui.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.FirestoreDB
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.model.User
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.util.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class MainUiState(
    val todaySchedules: List<Schedule> = emptyList(),
    val sharedReminders: List<Schedule> = emptyList(), // 공유 일정
    val currentSchedule: Schedule? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AppError? = null, // String → AppError 변경
    val selectedScheduleForDetail: Schedule? = null,
    val canRetry: Boolean = false, // 재시도 가능 여부 추가
    val showDeleteConfirmDialog: Boolean = false,
    val scheduleToDelete: Schedule? = null,
    val sharedReminderOwners: Map<String, String> = emptyMap(), // userId -> name
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
    ) : ViewModel() {
        private var sharedRemindersJob: Job? = null

        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            loadSchedules(Calendar.getInstance())
        }

        fun loadSchedules(calendar: Calendar) {
            val scheduleDate = calendar.clone() as Calendar
            scheduleDate.set(Calendar.HOUR_OF_DAY, 0)
            scheduleDate.set(Calendar.MINUTE, 0)
            scheduleDate.set(Calendar.SECOND, 0)
            scheduleDate.set(Calendar.MILLISECOND, 0)
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        error = null,
                        canRetry = false,
                    )

                try {
                    when (val result = scheduleRepository.getSchedulesForDate(scheduleDate)) {
                        is ScheduleRepository.ScheduleResult.Success -> {
                            val schedules = result.data
                            val currentTime = System.currentTimeMillis()

                            val currentSchedule =
                                schedules
                                    .filter { !it.completed }
                                    .let { incompleteSchedules ->
                                        // 1단계: 현재 진행 중인 일정들
                                        val ongoingSchedules =
                                            incompleteSchedules.filter { schedule ->
                                                val startTime = schedule.startTime.toDate().time
                                                val endTime =
                                                    schedule.endTime?.toDate()?.time
                                                        ?: (startTime + 60 * 60 * 1000)
                                                currentTime >= startTime && currentTime <= endTime
                                            }

                                        if (ongoingSchedules.isNotEmpty()) {
                                            // 진행 중: 끝나는 시간 빠른 순
                                            ongoingSchedules.minByOrNull { schedule ->
                                                schedule.endTime?.toDate()?.time
                                                    ?: (schedule.startTime.toDate().time + 60 * 60 * 1000)
                                            }
                                        } else {
                                            // 진행 중 없음: 시작 시간 빠른 순 → 끝나는 시간 빠른 순
                                            incompleteSchedules
                                                .filter { it.startTime.toDate().time > currentTime }
                                                .sortedWith(
                                                    compareBy<Schedule> { it.startTime.toDate().time }
                                                        .thenBy { schedule ->
                                                            schedule.endTime?.toDate()?.time
                                                                ?: (schedule.startTime.toDate().time + 60 * 60 * 1000)
                                                        },
                                                ).firstOrNull()
                                        }
                                    }

                            _uiState.value =
                                MainUiState(
                                    todaySchedules = schedules,
                                    currentSchedule = currentSchedule,
                                    completedCount = schedules.count { it.completed },
                                    totalCount = schedules.size,
                                    isLoading = false,
                                )

                            Log.d("MainViewModel", "일정 로드 성공: ${schedules.size}개")
                        }

                        is ScheduleRepository.ScheduleResult.Error -> {
                            Log.e("MainViewModel", "일정 로드 실패: ${result.error.message}")
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    error = result.error,
                                    canRetry = true, // 재시도 가능
                                )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "예상치 못한 에러", e)
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = AppError.GeneralError("일정을 불러오는 중 문제가 발생했습니다"),
                            canRetry = true,
                        )
                }
            }
        }

        fun toggleScheduleComplete(scheduleId: String) {
            viewModelScope.launch {
                val schedule = _uiState.value.todaySchedules.find { it.id == scheduleId }
                schedule?.let {
                    // 낙관적 업데이트 (즉시 UI 변경)
                    val updatedSchedules =
                        _uiState.value.todaySchedules.map { s ->
                            if (s.id == scheduleId) s.copy(completed = !s.completed) else s
                        }

                    val currentTime = System.currentTimeMillis()
                    val currentSchedule =
                        updatedSchedules.firstOrNull { schedule ->
                            !schedule.completed &&
                                schedule.startTime.toDate().time <= currentTime &&
                                (schedule.endTime?.toDate()?.time ?: Long.MAX_VALUE) > currentTime
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            todaySchedules = updatedSchedules,
                            currentSchedule = currentSchedule,
                            completedCount = updatedSchedules.count { it.completed },
                        )

                    // 서버 업데이트
                    when (
                        val result =
                            scheduleRepository.markScheduleAsCompleted(scheduleId, !it.completed)
                    ) {
                        is ScheduleRepository.ScheduleResult.Success -> {
                            Log.d("MainViewModel", "완료 상태 변경 성공")
                        }

                        is ScheduleRepository.ScheduleResult.Error -> {
                            Log.e("MainViewModel", "완료 상태 변경 실패: ${result.error.message}")
                            // 실패 시 원래 상태로 복구
                            loadSchedules(Calendar.getInstance())

                            _uiState.value =
                                _uiState.value.copy(
                                    error = result.error,
                                    canRetry = false,
                                )
                        }
                    }
                }
            }
        }

        fun showScheduleDetail(scheduleId: String) {
            val schedule = _uiState.value.todaySchedules.find { it.id == scheduleId }
            _uiState.value = _uiState.value.copy(selectedScheduleForDetail = schedule)
        }

        fun hideScheduleDetail() {
            _uiState.value = _uiState.value.copy(selectedScheduleForDetail = null)
        }

        fun clearError() {
            _uiState.value = _uiState.value.copy(error = null, canRetry = false)
        }

        fun retryLastAction() {
            clearError()
            loadSchedules(Calendar.getInstance())
        }

        fun showDeleteConfirmDialog(scheduleId: String) {
            val schedule = _uiState.value.todaySchedules.find { it.id == scheduleId }
            _uiState.value =
                _uiState.value.copy(
                    showDeleteConfirmDialog = true,
                    scheduleToDelete = schedule,
                )
        }

        fun hideDeleteConfirmDialog() {
            _uiState.value =
                _uiState.value.copy(
                    showDeleteConfirmDialog = false,
                    scheduleToDelete = null,
                )
        }

        fun deleteSchedule(scheduleId: String) {
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        error = null,
                        showDeleteConfirmDialog = false,
                        scheduleToDelete = null,
                    )

                when (val result = scheduleRepository.deleteSchedule(scheduleId)) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        Log.d("MainViewModel", "일정 삭제 성공")
                        // 목록에서 제거
                        val updatedSchedules =
                            _uiState.value.todaySchedules.filter { it.id != scheduleId }
                        _uiState.value =
                            _uiState.value.copy(
                                todaySchedules = updatedSchedules,
                                totalCount = updatedSchedules.size,
                                completedCount = updatedSchedules.count { it.completed },
                                isLoading = false,
                            )
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        Log.e("MainViewModel", "일정 삭제 실패: ${result.error.message}")
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                error = result.error,
                                canRetry = true,
                            )
                    }
                }
            }
        }

        fun observeSharedReminders(shareCode: String?) {
            if (shareCode.isNullOrBlank()) return
            sharedRemindersJob?.cancel()
            sharedRemindersJob =
                viewModelScope.launch {
                    scheduleRepository.observeSchedulesBySharedCode(shareCode).collectLatest { reminders ->
                        // Filter reminders to only include those with startTime on today's date
                        val today = java.util.Calendar.getInstance()
                        val filteredReminders =
                            reminders.filter { schedule ->
                                val cal =
                                    java.util.Calendar
                                        .getInstance()
                                        .apply { time = schedule.startTime.toDate() }
                                cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                                    cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
                            }
                        _uiState.value = _uiState.value.copy(sharedReminders = filteredReminders)
                        // Optionally update owner names if needed
                        val userIds = filteredReminders.map { it.userId }.filter { it.isNotBlank() }.distinct()
                        val ownerMap = fetchUserNames(userIds)
                        _uiState.value = _uiState.value.copy(sharedReminderOwners = ownerMap)
                    }
                }
        }

        private suspend fun fetchUserNames(userIds: List<String>): Map<String, String> =
            withContext(Dispatchers.IO) {
                if (userIds.isEmpty()) return@withContext emptyMap()
                try {
                    val usersCollection = FirestoreDB.users
                    val chunks = userIds.chunked(10) // Firestore whereIn max 10
                    val result = mutableMapOf<String, String>()
                    for (chunk in chunks) {
                        val docs = usersCollection.whereIn("id", chunk).get().await()
                        for (doc in docs.documents) {
                            val user = doc.toObject(User::class.java)
                            if (user != null) {
                                result[user.id] = user.name
                            }
                        }
                    }
                    result
                } catch (_: Exception) {
                    emptyMap()
                }
            }

        fun toggleSharedReminderComplete(
            scheduleId: String,
            context: Context,
        ) {
            viewModelScope.launch {
                val schedule = _uiState.value.sharedReminders.find { it.id == scheduleId }
                schedule?.let {
                    // Optimistic update
                    val updatedReminders =
                        _uiState.value.sharedReminders.map { s ->
                            if (s.id == scheduleId) s.copy(completed = !s.completed) else s
                        }
                    _uiState.value = _uiState.value.copy(sharedReminders = updatedReminders)

                    // Update in Firestore
                    when (
                        val result =
                            scheduleRepository.markScheduleAsCompleted(scheduleId, !it.completed)
                    ) {
                        is ScheduleRepository.ScheduleResult.Success -> {
                            // Send FCM notification to shareCode members
                            if (it.sharedCode.isNotBlank()) {
                                val title = if (!it.completed) "일정이 완료됨" else "일정이 미완료로 변경됨"
                                val message =
                                    "${it.title} 일정이 ${if (!it.completed) "완료" else "미완료"} 처리되었습니다."
                                scheduleRepository.sendNotificationToShareCodeMembers(
                                    context,
                                    it.sharedCode,
                                    title,
                                    message,
                                )
                            }
                        }

                        is ScheduleRepository.ScheduleResult.Error -> {
                            // On error, reload shared reminders to revert
                            val shareCode = it.sharedCode
                            if (shareCode.isNotBlank()) {
                                observeSharedReminders(shareCode)
                            }
                            _uiState.value =
                                _uiState.value.copy(
                                    error = result.error,
                                    canRetry = false,
                                )
                        }
                    }
                }
            }
        }

        suspend fun notifyShareCodeMembersForSchedule(
            context: Context,
            schedule: Schedule,
            type: String,
        ) {
            if (schedule.sharedCode.isBlank()) return
            val (title, message) =
                when (type) {
                    "create" -> "새 일정이 추가되었습니다" to "${schedule.title} 일정이 추가되었습니다."
                    "edit" -> "일정이 수정되었습니다" to "${schedule.title} 일정이 수정되었습니다."
                    "delete" -> "일정이 삭제되었습니다" to "${schedule.title} 일정이 삭제되었습니다."
                    else -> return
                }
            scheduleRepository.sendNotificationToShareCodeMembers(context, schedule.sharedCode, title, message)
        }

        fun addSharedSchedule(
            schedule: Schedule,
            context: Context,
        ) = viewModelScope.launch {
            val result = scheduleRepository.addSchedule(schedule)
            if (result is ScheduleRepository.ScheduleResult.Success) {
                notifyShareCodeMembersForSchedule(context, schedule, "create")
            }
        }

        fun updateSharedSchedule(
            schedule: Schedule,
            context: Context,
        ) = viewModelScope.launch {
            val result = scheduleRepository.updateSchedule(schedule)
            if (result is ScheduleRepository.ScheduleResult.Success) {
                notifyShareCodeMembersForSchedule(context, schedule, "edit")
            }
        }

        fun deleteSharedSchedule(
            schedule: Schedule,
            context: Context,
        ) = viewModelScope.launch {
            val result = scheduleRepository.deleteSchedule(schedule.id)
            if (result is ScheduleRepository.ScheduleResult.Success) {
                notifyShareCodeMembersForSchedule(context, schedule, "delete")
            }
        }

        fun sendTestFcm(context: Context) {
            viewModelScope.launch {
                try {
                    val user =
                        com.google.firebase.auth.FirebaseAuth
                            .getInstance()
                            .currentUser
                    if (user != null) {
                        val db =
                            com.google.firebase.firestore.FirebaseFirestore
                                .getInstance()
                        val userDoc =
                            db
                                .collection("users")
                                .document(user.uid)
                                .get()
                                .await()
                        val fcmToken = userDoc.getString("fcmToken")
                        if (!fcmToken.isNullOrBlank()) {
                            com.example.slowclock.notification.GuardianNotifier.sendReminderToUser(
                                context,
                                fcmToken,
                                "FCM 테스트",
                                "이것은 테스트 메시지입니다.",
                            )
                        } else {
                            Log.e("TestFCM", "FCM 토큰이 없습니다.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TestFCM", "테스트 FCM 전송 실패", e)
                }
            }
        }

        // --- ShareCode Watcher Logic ---
        fun addShareCodeWatcher(
            context: Context,
            shareCode: String,
        ) {
            Log.d("ShareCodeWatcher", "addShareCodeWatcher called with shareCode=$shareCode")
            android.widget.Toast
                .makeText(context, "addShareCodeWatcher called", android.widget.Toast.LENGTH_SHORT)
                .show()
            val user =
                com.google.firebase.auth.FirebaseAuth
                    .getInstance()
                    .currentUser ?: return
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val db =
                    com.google.firebase.firestore.FirebaseFirestore
                        .getInstance()
                db
                    .collection("shareCodeWatchers")
                    .document(shareCode)
                    .collection("tokens")
                    .document(user.uid)
                    .set(mapOf("fcmToken" to token))
                    .addOnSuccessListener {
                        Log.d("ShareCodeWatcher", "Watcher added for shareCode=$shareCode, userId=${user.uid}, token=$token")
                        android.widget.Toast
                            .makeText(context, "Watcher added!", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }.addOnFailureListener { e ->
                        Log.e("ShareCodeWatcher", "Failed to add watcher for shareCode=$shareCode, userId=${user.uid}", e)
                        android.widget.Toast
                            .makeText(context, "Failed to add watcher: ${e.message}", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
            }
        }

        fun removeShareCodeWatcher(shareCode: String) {
            val user =
                com.google.firebase.auth.FirebaseAuth
                    .getInstance()
                    .currentUser ?: return
            val db =
                com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
            db
                .collection("shareCodeWatchers")
                .document(shareCode)
                .collection("tokens")
                .document(user.uid)
                .delete()
        }
        // --- End ShareCode Watcher Logic ---
    }
