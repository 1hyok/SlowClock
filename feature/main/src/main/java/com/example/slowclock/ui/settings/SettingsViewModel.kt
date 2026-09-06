package com.example.slowclock.ui.settings

import androidx.lifecycle.viewModelScope
import com.example.slowclock.core.alarm.AlarmScheduler
import com.example.slowclock.data.remote.repository.SettingsRepository
import com.example.slowclock.ui.mvi.MviViewModel
import com.example.slowclock.util.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 정보 화면. 화면 테마와 전체 화면 알람 권한 안내를 다룬다. 테마는 기기에만 남고, 저장소가
 * 내는 흐름을 다시 받아 상태로 옮긴다. 그래서 다른 화면에서 값이 바뀌어도 이 화면이 따라간다.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val alarmScheduler: AlarmScheduler,
    ) : MviViewModel<SettingsIntent, SettingsUiState, SettingsReducerEvent>(SettingsUiState()) {
        init {
            viewModelScope.launch {
                settingsRepository.observeThemeMode().collect { mode ->
                    dispatch(SettingsReducerEvent.ThemeModeLoaded(mode))
                }
            }
            refreshFullScreenAlarmPermission()
        }

        override fun onIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.SelectThemeMode -> {
                    settingsRepository.setThemeMode(intent.mode)
                }

                SettingsIntent.RefreshAlarmPermission -> {
                    refreshFullScreenAlarmPermission()
                }

                SettingsIntent.OpenFullScreenAlarmSettings -> {
                    dispatch(SettingsReducerEvent.FullScreenAlarmSettingsRequested)
                }

                SettingsIntent.ConsumeFullScreenAlarmSettingsRequest -> {
                    dispatch(SettingsReducerEvent.FullScreenAlarmSettingsRequestConsumed)
                }

                SettingsIntent.OpenMedicalNews -> {
                    dispatch(SettingsReducerEvent.MedicalNewsRequested)
                }

                SettingsIntent.ConsumeMedicalNewsRequest -> {
                    dispatch(SettingsReducerEvent.MedicalNewsRequestConsumed)
                }

                SettingsIntent.MedicalNewsUnavailable -> {
                    dispatch(SettingsReducerEvent.MedicalNewsFailed)
                }

                SettingsIntent.ConsumeError -> {
                    dispatch(SettingsReducerEvent.ErrorConsumed)
                }
            }
        }

        /**
         * 권한 조회는 부수효과다. reduce 안에서 읽으면 상태 갱신이 재시도될 때 두 번 돈다.
         * 여기서 읽고 결과만 이벤트로 넘긴다.
         */
        private fun refreshFullScreenAlarmPermission() {
            dispatch(SettingsReducerEvent.FullScreenAlarmPermissionChecked(alarmScheduler.canUseFullScreenAlarm()))
        }

        override fun reduce(
            state: SettingsUiState,
            event: SettingsReducerEvent,
        ): SettingsUiState =
            when (event) {
                is SettingsReducerEvent.ThemeModeLoaded -> {
                    state.copy(themeMode = event.mode)
                }

                // 「봤음」 표식을 두지 않는다. 권한 상태가 곧 진실이라, 사용자가 나중에 회수하면
                // 안내가 다시 뜬다(#128).
                is SettingsReducerEvent.FullScreenAlarmPermissionChecked -> {
                    state.copy(showFullScreenAlarmNotice = !event.granted)
                }

                SettingsReducerEvent.FullScreenAlarmSettingsRequested -> {
                    state.copy(openFullScreenAlarmSettings = Unit)
                }

                SettingsReducerEvent.FullScreenAlarmSettingsRequestConsumed -> {
                    state.copy(openFullScreenAlarmSettings = null)
                }

                SettingsReducerEvent.MedicalNewsRequested -> {
                    state.copy(openMedicalNews = Unit, error = null)
                }

                SettingsReducerEvent.MedicalNewsRequestConsumed -> {
                    state.copy(openMedicalNews = null)
                }

                SettingsReducerEvent.MedicalNewsFailed -> {
                    state.copy(
                        error = AppError.GeneralError("브라우저를 열 수 없습니다. 기기에 브라우저가 설치되어 있고 사용 가능한지 확인해주세요"),
                    )
                }

                SettingsReducerEvent.ErrorConsumed -> {
                    state.copy(error = null)
                }
            }
    }
