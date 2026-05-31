package com.example.slowclock.ui.addschedule

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slowclock.core.alarm.ScheduleAlarmHelper
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.notification.requestExactAlarmPermissionIfNeeded
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AddScheduleUiState(
    val title: String = "",
    val description: String = "",
    val selectedTime: Calendar = Calendar.getInstance(),
    val endTime: Calendar? = null,
    val recurring: Boolean = false,
    val recurringType: String = "daily",
    val showTimePicker: Boolean = false,
    val showEndTimePicker: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: AppError? = null,
    val canSave: Boolean = false,
    val canRetry: Boolean = false,
    val isEditMode: Boolean = false,
    val editingScheduleId: String = "",
    val editingSchedule: Schedule? = null, // <-- add this
)

@HiltViewModel
class AddScheduleViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddScheduleUiState())
        val uiState: StateFlow<AddScheduleUiState> = _uiState.asStateFlow()

        fun updateTitle(title: String) {
            _uiState.value =
                _uiState.value.copy(
                    title = title,
                    canSave = title.trim().isNotBlank(),
                    error = null,
                )
        }

        fun updateDescription(description: String) {
            _uiState.value = _uiState.value.copy(description = description)
        }

        fun updateTime(time: Calendar) {
            val newTime =
                Calendar.getInstance().apply {
                    timeInMillis = time.timeInMillis
                }
            _uiState.value = _uiState.value.copy(selectedTime = newTime)
        }

        fun updateEndTime(time: Calendar?) {
            val newTime =
                time?.let {
                    Calendar.getInstance().apply { timeInMillis = it.timeInMillis }
                }
            _uiState.value = _uiState.value.copy(endTime = newTime)
        }

        fun updateRecurring(recurring: Boolean) {
            _uiState.value = _uiState.value.copy(recurring = recurring)
        }

        fun updateRecurringType(type: String) {
            _uiState.value = _uiState.value.copy(recurringType = type)
        }

        fun showTimePicker(show: Boolean) {
            _uiState.value = _uiState.value.copy(showTimePicker = show)
        }

        fun showEndTimePicker(show: Boolean) {
            _uiState.value = _uiState.value.copy(showEndTimePicker = show)
        }

        fun loadScheduleForEdit(scheduleId: String) {
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        error = null,
                        isEditMode = true,
                        editingScheduleId = scheduleId,
                    )

                when (val result = scheduleRepository.getScheduleById(scheduleId)) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        val schedule = result.data
                        val startCal =
                            Calendar.getInstance().apply {
                                time = schedule.startTime.toDate()
                            }
                        val endCal =
                            schedule.endTime?.let { endTime ->
                                Calendar.getInstance().apply {
                                    time = endTime.toDate()
                                }
                            }

                        _uiState.value =
                            _uiState.value.copy(
                                title = schedule.title,
                                description = schedule.description,
                                selectedTime = startCal,
                                endTime = endCal,
                                recurring = schedule.recurring,
                                recurringType = schedule.recurringType ?: "daily",
                                isLoading = false,
                                canSave = schedule.title.isNotBlank(),
                                editingSchedule = schedule, // <-- store full schedule
                            )
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
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

        fun saveSchedule(context: Context) {
            val currentTitle = _uiState.value.title.trim()

            // Validation (outside coroutine)
            when {
                currentTitle.isBlank() -> {
                    _uiState.value =
                        _uiState.value.copy(
                            error = AppError.GeneralError("할 일을 입력해주세요"),
                        )
                    return
                }

                currentTitle.length > 100 -> {
                    _uiState.value =
                        _uiState.value.copy(
                            error = AppError.GeneralError("제목이 너무 깁니다 (최대 100자)"),
                        )
                    return
                }

                _uiState.value.endTime?.let { end ->
                    end.timeInMillis <= _uiState.value.selectedTime.timeInMillis
                } == true -> {
                    _uiState.value =
                        _uiState.value.copy(
                            error = AppError.GeneralError("종료 시간은 시작 시간보다 늦어야 합니다"),
                        )
                    return
                }
            }

            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        error = null,
                        canRetry = false,
                    )

                try {
                    Log.d("AddSchedule", "일정 저장 시작: $currentTitle")
                    Log.d("AddSchedule", "현재 사용자: ${FirebaseAuth.getInstance().currentUser?.uid}")

                    val schedule =
                        if (_uiState.value.isEditMode) {
                            // Use original schedule as base, update only changed fields
                            val original = _uiState.value.editingSchedule!!
                            original.copy(
                                title = currentTitle,
                                description = _uiState.value.description.trim(),
                                startTime = Timestamp(_uiState.value.selectedTime.time),
                                endTime = _uiState.value.endTime?.let { Timestamp(it.time) },
                                recurring = _uiState.value.recurring,
                                recurringType = if (_uiState.value.recurring) _uiState.value.recurringType else null,
                            )
                        } else {
                            Schedule(
                                title = currentTitle,
                                description = _uiState.value.description.trim(),
                                startTime = Timestamp(_uiState.value.selectedTime.time),
                                endTime = _uiState.value.endTime?.let { Timestamp(it.time) },
                                recurring = _uiState.value.recurring,
                                recurringType = if (_uiState.value.recurring) _uiState.value.recurringType else null,
                            )
                        }

                    val result =
                        if (_uiState.value.isEditMode) {
                            Log.d("AddSchedule", "일정 수정 모드")
                            scheduleRepository.updateSchedule(schedule)
                        } else {
                            Log.d("AddSchedule", "일정 추가 모드")
                            scheduleRepository.addSchedule(schedule)
                        }

                    when (result) {
                        is ScheduleRepository.ScheduleResult.Success -> {
                            Log.d("AddSchedule", "저장 성공")
                            requestExactAlarmPermissionIfNeeded(context)
                            try {
                                ScheduleAlarmHelper.scheduleAlarm(context, schedule)
                                Log.d("AddSchedule", "알람 예약 성공")
                            } catch (e: Exception) {
                                Log.e("AddSchedule", "알람 예약 실패", e)
                            }
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    isSuccess = true,
                                )
                        }

                        is ScheduleRepository.ScheduleResult.Error -> {
                            Log.e("AddSchedule", "저장 실패: ${result.error.message}")
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    error = result.error,
                                    canRetry = true,
                                )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddSchedule", "예상치 못한 저장 실패", e)
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = AppError.GeneralError("일정 저장 중 문제가 발생했습니다"),
                            canRetry = true,
                        )
                }
            }
        }

        fun clearError() {
            _uiState.value = _uiState.value.copy(error = null, canRetry = false)
        }

        fun retryLastAction(context: Context) {
            clearError()
            saveSchedule(context)
        }
    }
