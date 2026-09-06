package com.example.slowclock.ui.addschedule

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.data.remote.repository.ScheduleRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

private const val MAX_TITLE_LENGTH = 100

/** 일정 추가·수정 화면. 저장이 끝나면 알람을 예약하고 [AddScheduleUiState.isSaved] 신호를 낸다. */
@HiltViewModel
class AddScheduleViewModel
    @Inject
    constructor(
        private val scheduleRepository: ScheduleRepository,
        private val alarmScheduler: AlarmScheduler,
        private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) : MviViewModel<AddScheduleIntent, AddScheduleUiState, AddScheduleReducerEvent>(savedStateHandle.restoreDraft()) {
        private val draftId =
            savedStateHandle.get<String>(DRAFT_ID) ?: UUID.randomUUID().toString().also { savedStateHandle[DRAFT_ID] = it }
        private var editLoadJob: Job? = null

        override fun onIntent(intent: AddScheduleIntent) {
            // 폼이 읽히거나 저장되는 중의 중복 입력·저장은 진행 중인 작업을 바꾸지 않는다.
            if (currentState.isLoading && (intent !is AddScheduleIntent.LoadForEdit || editLoadJob?.isActive != true)) return
            if (currentState.pendingAlarmSchedule != null &&
                intent != AddScheduleIntent.Retry && intent != AddScheduleIntent.ConsumeError
            ) {
                return
            }
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

                is AddScheduleIntent.UpdateTimeInput -> {
                    dispatch(AddScheduleReducerEvent.TimeInputChanged(intent.value, intent.isEnd))
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
                    val state = currentState
                    if (state.pendingAlarmSchedule != null) {
                        reserveSavedAlarm(state.pendingAlarmSchedule)
                    } else if (state.isEditMode && state.editingSchedule == null) {
                        state.editScheduleId?.let(::loadForEdit)
                    } else {
                        save()
                    }
                }

                AddScheduleIntent.ConsumeError -> {
                    if (currentState.pendingAlarmSchedule != null) {
                        dispatch(AddScheduleReducerEvent.Saved)
                    } else {
                        dispatch(AddScheduleReducerEvent.ErrorConsumed)
                    }
                }

                AddScheduleIntent.ConsumeSaved -> {
                    dispatch(AddScheduleReducerEvent.SavedConsumed)
                }
            }
            if (!currentState.isEditMode) savedStateHandle.saveDraft(currentState)
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
                    state.copy(selectedTime = event.time, startTimeInput = ScheduleTimeInput.from(event.time))
                }

                is AddScheduleReducerEvent.EndTimeChanged -> {
                    state.copy(endTime = event.time, endTimeInput = ScheduleTimeInput.from(event.time))
                }

                is AddScheduleReducerEvent.RecurringChanged -> {
                    state.copy(recurring = event.recurring)
                }

                is AddScheduleReducerEvent.RecurringTypeChanged -> {
                    state.copy(recurringType = event.type)
                }

                is AddScheduleReducerEvent.TimeInputChanged -> {
                    if (event.isEnd) {
                        state.copy(
                            endTimeInput = event.value,
                            endTime =
                                if (event.value.isEmpty) {
                                    null
                                } else {
                                    event.value.onDate(state.endTime ?: state.selectedTime)
                                        ?: state.endTime
                                },
                        )
                    } else {
                        state.copy(
                            startTimeInput = event.value,
                            selectedTime = event.value.onDate(state.selectedTime) ?: state.selectedTime,
                        )
                    }
                }

                is AddScheduleReducerEvent.EditLoading -> {
                    state.copy(
                        isLoading = true,
                        error = null,
                        isEditMode = true,
                        editingSchedule = null,
                        editScheduleId = event.scheduleId,
                        canRetry = false,
                    )
                }

                is AddScheduleReducerEvent.EditLoaded -> {
                    state.copy(
                        title = event.schedule.title,
                        description = event.schedule.description,
                        selectedTime = event.startTime,
                        endTime = event.endTime,
                        startTimeInput = ScheduleTimeInput.from(event.startTime),
                        endTimeInput = ScheduleTimeInput.from(event.endTime),
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
                    state.copy(isLoading = false, isSaved = true, pendingAlarmSchedule = null, error = null)
                }

                is AddScheduleReducerEvent.AlarmFailed -> {
                    state.copy(
                        isLoading = false,
                        pendingAlarmSchedule = event.schedule,
                        error = AppError.GeneralError("일정은 저장됐지만 알람을 예약하지 못했습니다. 다시 시도하면 알람만 예약합니다."),
                        canRetry = true,
                    )
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

        private fun reserveSavedAlarm(schedule: Schedule) {
            dispatch(AddScheduleReducerEvent.Saving)
            try {
                alarmScheduler.schedule(schedule)
                dispatch(AddScheduleReducerEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "일정 저장 후 알람 예약 실패", error)
                dispatch(AddScheduleReducerEvent.AlarmFailed(schedule))
            }
        }

        private fun loadForEdit(scheduleId: String) {
            if (currentState.editingSchedule?.id == scheduleId ||
                (currentState.editScheduleId == scheduleId && editLoadJob?.isActive == true)
            ) {
                return
            }
            editLoadJob?.cancel()
            dispatch(AddScheduleReducerEvent.EditLoading(scheduleId))
            editLoadJob =
                viewModelScope.launch {
                    val result = scheduleRepository.getScheduleById(scheduleId)
                    if (!isActive || currentState.editScheduleId != scheduleId) return@launch
                    when (result) {
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
                            dispatch(AddScheduleReducerEvent.Failed(result.error, canRetry = true))
                        }
                    }
                }
        }

        private fun save() {
            val state = currentState
            if (state.isLoading || state.isSaved) return
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
                        scheduleRepository.updateSchedule(schedule).map { schedule }
                    } else {
                        scheduleRepository.addSchedule(schedule)
                    }
                currentCoroutineContext().ensureActive()
                when (result) {
                    is ScheduleRepository.ScheduleResult.Success -> {
                        // 같은 ID 재시도에서도 서버가 돌려준 최신 일정으로 알람을 예약한다.
                        reserveSavedAlarm(result.data)
                    }

                    is ScheduleRepository.ScheduleResult.Error -> {
                        Log.e(TAG, "저장 실패: ${result.error.message}")
                        dispatch(AddScheduleReducerEvent.Failed(result.error, canRetry = result.error !is AppError.ScheduleConflictError))
                    }
                }
            }
        }

        private fun validationError(
            state: AddScheduleUiState,
            title: String,
        ): String? =
            when {
                state.isEditMode && state.editingSchedule == null -> {
                    "수정할 일정을 먼저 불러와 주세요"
                }

                !state.hasValidTimeInput -> {
                    "시간을 확인해 주세요 (시 0~23, 분 0~59)"
                }

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
            val base = if (isEditMode) requireNotNull(editingSchedule) else Schedule(id = draftId)
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
            const val DRAFT_ID = "new_schedule_id"
        }
    }

private fun Calendar.copyOf(): Calendar = Calendar.getInstance().apply { timeInMillis = this@copyOf.timeInMillis }
