package com.example.slowclock.ui.addschedule

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val MAX_TITLE_LENGTH = 100

/** 일정 추가·수정 화면. 저장이 끝나면 알람을 예약하고 [AddScheduleUiState.isSaved] 신호를 낸다. */
@HiltViewModel
class AddScheduleViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
        private val alarmScheduler: AlarmScheduler,
    ) : MviViewModel<AddScheduleIntent, AddScheduleUiState, AddScheduleReducerEvent>(AddScheduleUiState()) {
        override fun onIntent(intent: AddScheduleIntent) {
            when (intent) {
                is AddScheduleIntent.UpdateTitle -> {
                    dispatch(AddScheduleReducerEvent.TitleChanged(intent.value))
                }

                is AddScheduleIntent.UpdateDescription -> {
                    dispatch(AddScheduleReducerEvent.DescriptionChanged(intent.value))
                }

                is AddScheduleIntent.UpdateTime -> {
                    dispatch(AddScheduleReducerEvent.TimeChanged(intent.time.copyOf()))
                }

                is AddScheduleIntent.UpdateEndTime -> {
                    dispatch(AddScheduleReducerEvent.EndTimeChanged(intent.time?.copyOf()))
                }

                is AddScheduleIntent.UpdateRecurring -> {
                    dispatch(AddScheduleReducerEvent.RecurringChanged(intent.recurring))
                }

                is AddScheduleIntent.UpdateRecurringType -> {
                    dispatch(AddScheduleReducerEvent.RecurringTypeChanged(intent.type))
                }

                is AddScheduleIntent.LoadForEdit -> {
                    loadForEdit(intent.scheduleId)
                }

                AddScheduleIntent.Save -> {
                    save()
                }

                AddScheduleIntent.Retry -> {
                    dispatch(AddScheduleReducerEvent.ErrorConsumed)
                    save()
                }

                AddScheduleIntent.ConsumeError -> {
                    dispatch(AddScheduleReducerEvent.ErrorConsumed)
                }

                AddScheduleIntent.ConsumeSaved -> {
                    dispatch(AddScheduleReducerEvent.SavedConsumed)
                }
            }
        }

        override fun reduce(
            state: AddScheduleUiState,
            event: AddScheduleReducerEvent,
        ): AddScheduleUiState =
            when (event) {
                is AddScheduleReducerEvent.TitleChanged -> {
                    state.copy(title = event.value, error = null)
                }

                is AddScheduleReducerEvent.DescriptionChanged -> {
                    state.copy(description = event.value)
                }

                is AddScheduleReducerEvent.TimeChanged -> {
                    state.copy(selectedTime = event.time)
                }

                is AddScheduleReducerEvent.EndTimeChanged -> {
                    state.copy(endTime = event.time)
                }

                is AddScheduleReducerEvent.RecurringChanged -> {
                    state.copy(recurring = event.recurring)
                }

                is AddScheduleReducerEvent.RecurringTypeChanged -> {
                    state.copy(recurringType = event.type)
                }

                AddScheduleReducerEvent.EditLoading -> {
                    state.copy(isLoading = true, error = null, isEditMode = true)
                }

                is AddScheduleReducerEvent.EditLoaded -> {
                    state.copy(
                        title = event.schedule.title,
                        description = event.schedule.description,
                        selectedTime = event.startTime,
                        endTime = event.endTime,
                        recurring = event.schedule.recurring,
                        recurringType = event.schedule.recurringType ?: "daily",
                        isLoading = false,
                        editingSchedule = event.schedule,
                    )
                }

                AddScheduleReducerEvent.Saving -> {
                    state.copy(isLoading = true, error = null, canRetry = false)
                }

                AddScheduleReducerEvent.Saved -> {
                    state.copy(isLoading = false, isSaved = true)
                }

                is AddScheduleReducerEvent.Failed -> {
                    state.copy(isLoading = false, error = event.error, canRetry = event.canRetry)
                }

                AddScheduleReducerEvent.ErrorConsumed -> {
                    state.copy(error = null, canRetry = false)
                }

                AddScheduleReducerEvent.SavedConsumed -> {
                    state.copy(isSaved = false)
                }
            }

        private fun loadForEdit(scheduleId: String) {
            dispatch(AddScheduleReducerEvent.EditLoading)
            viewModelScope.launch {
                when (val result = scheduleRepository.getScheduleById(scheduleId)) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        val schedule = result.data
                        dispatch(
                            AddScheduleReducerEvent.EditLoaded(
                                schedule = schedule,
                                startTime = Calendar.getInstance().apply { time = schedule.startTime.toDate() },
                                endTime = schedule.endTime?.let { end -> Calendar.getInstance().apply { time = end.toDate() } },
                            ),
                        )
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        dispatch(AddScheduleReducerEvent.Failed(result.error, canRetry = false))
                    }
                }
            }
        }

        private fun save() {
            val state = currentState
            val title = state.title.trim()
            validationError(state, title)?.let { message ->
                dispatch(AddScheduleReducerEvent.Failed(AppError.GeneralError(message), canRetry = false))
                return
            }
            dispatch(AddScheduleReducerEvent.Saving)

            val schedule = state.toSchedule(title)
            viewModelScope.launch {
                val result =
                    if (state.isEditMode) {
                        scheduleRepository.updateSchedule(schedule).map { schedule.id }
                    } else {
                        scheduleRepository.addSchedule(schedule)
                    }
                when (result) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        // 새 일정은 Firestore 가 준 ID 로 알람을 건다. 빈 ID 로 걸면 모든 새 일정이 같은 요청 코드를 쓴다.
                        runCatching { alarmScheduler.schedule(schedule.copy(id = result.data)) }
                            .onFailure { Log.e(TAG, "알람 예약 실패", it) }
                        dispatch(AddScheduleReducerEvent.Saved)
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        Log.e(TAG, "저장 실패: ${result.error.message}")
                        dispatch(AddScheduleReducerEvent.Failed(result.error, canRetry = true))
                    }
                }
            }
        }

        private fun validationError(
            state: AddScheduleUiState,
            title: String,
        ): String? =
            when {
                title.isBlank() -> {
                    "할 일을 입력해주세요"
                }

                title.length > MAX_TITLE_LENGTH -> {
                    "제목이 너무 깁니다 (최대 ${MAX_TITLE_LENGTH}자)"
                }

                state.endTime != null && state.endTime.timeInMillis <= state.selectedTime.timeInMillis -> {
                    "종료 시간은 시작 시간보다 늦어야 합니다"
                }

                else -> {
                    null
                }
            }

        private fun AddScheduleUiState.toSchedule(title: String): Schedule {
            val base = if (isEditMode) editingSchedule ?: Schedule() else Schedule()
            return base.copy(
                title = title,
                description = description.trim(),
                startTime = Timestamp(selectedTime.time),
                endTime = endTime?.let { Timestamp(it.time) },
                recurring = recurring,
                recurringType = if (recurring) recurringType else null,
            )
        }

        private fun <T, R> ScheduleRepository.ScheduleResult<T>.map(transform: (T) -> R): ScheduleRepository.ScheduleResult<R> =
            when (this) {
                is ScheduleRepository.ScheduleResult.Success -> ScheduleRepository.ScheduleResult.Success(transform(data))
                is ScheduleRepository.ScheduleResult.Error -> this
            }

        private companion object {
            const val TAG = "AddScheduleViewModel"
        }
    }

private fun Calendar.copyOf(): Calendar = Calendar.getInstance().apply { timeInMillis = this@copyOf.timeInMillis }
