package com.example.slowclock.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.common.ScheduleLoadingIndicator
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.common.components.SignInPrompt
import com.example.slowclock.ui.common.components.rememberDayText
import com.example.slowclock.ui.common.dialog.DeleteConfirmDialog
import com.example.slowclock.ui.common.launchExternalActivity
import com.example.slowclock.ui.main.components.DayHeader
import com.example.slowclock.ui.main.components.EmptyStateCard
import com.example.slowclock.ui.main.components.NowCard
import com.example.slowclock.ui.main.components.ScheduleDetailDialog
import com.example.slowclock.ui.main.components.SharedRemindersSection
import com.example.slowclock.ui.main.components.TodayScheduleSection
import com.example.slowclock.ui.mvi.ObserveSignal
import kotlinx.coroutines.launch
import java.util.Date

/** 메인 화면(stateful). 네비게이션 콜백만 받고 나머지는 [MainIntent] 로 보낸다. */
@Composable
fun MainScreen(
    onAddSchedule: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    ObserveSignal(
        signal = state.userMessage,
        consumed = MainIntent.ConsumeUserMessage,
        onIntent = viewModel::onIntent,
    ) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    // 앱을 켜 둔 채 자정을 넘기면 구독이 어제 회차를 보고 있다(#171).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(MainIntent.ScreenResumed) }
    ObserveSignal(
        signal = state.openExactAlarmSettings,
        consumed = MainIntent.ConsumeExactAlarmSettingsRequest,
        onIntent = viewModel::onIntent,
    ) {
        val opened =
            launchExternalActivity(
                open = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                },
                fallback = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                },
            )
        if (!opened) viewModel.onIntent(MainIntent.ExactAlarmSettingsUnavailable)
    }
    MainContent(
        state = state,
        onIntent = viewModel::onIntent,
        onAddSchedule = onAddSchedule,
        onEditSchedule = onEditSchedule,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onSignIn = onSignIn,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    )
}

/**
 * 메인 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다.
 *
 * 위에서 아래로 날짜와 진행 상황, 지금 할 일, 오늘의 일정, 공유 일정 순이다. 앱을 켠 사람이
 * 가장 먼저 알고 싶은 것을 가장 위에 가장 크게 둔다(#109).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainContent(
    state: MainUiState,
    onIntent: (MainIntent) -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    // 날짜 머리글의 기준이 되는 날. 미리보기와 스크린샷 테스트는 고정된 날을 넣어, 그린 날에
    // 따라 그림이 달라지지 않게 한다. 시계를 읽으면 baseline 이 하루 만에 어긋난다(#143).
    today: Date = Date(),
    snackbarHost: @Composable () -> Unit = {},
) {
    val isSignedOut = state.isSignedInKnown && state.currentUserId.isBlank()
    val dateText = rememberDayText(today)
    // 지금 할 일은 위에 크게 나오므로 아래 목록에서 뺀다. 같은 일정이 한 화면에 두 번 보이면
    // 두 개인지 하나인지 알 수 없다(#109).
    val listedSchedules =
        remember(state.todaySchedules, state.currentSchedule) {
            state.todaySchedules.filterNot { it.id == state.currentSchedule?.id }
        }

    state.selectedScheduleForDetail?.let { schedule ->
        ScheduleDetailDialog(
            schedule = schedule,
            onDismiss = { onIntent(MainIntent.HideDetail) },
            onEdit = {
                onIntent(MainIntent.HideDetail)
                onEditSchedule(schedule.id)
            },
            onDelete = {
                onIntent(MainIntent.HideDetail)
                onIntent(MainIntent.RequestDelete(schedule.id))
            },
        )
    }

    state.scheduleToDelete?.let { schedule ->
        DeleteConfirmDialog(
            schedule = schedule,
            onConfirm = { onIntent(MainIntent.ConfirmDelete) },
            onDismiss = { onIntent(MainIntent.DismissDelete) },
        )
    }

    if (state.showExactAlarmNotice) {
        ExactAlarmNoticeDialog(
            onOpenSettings = { onIntent(MainIntent.OpenExactAlarmSettings) },
            onDismiss = { onIntent(MainIntent.DismissExactAlarmNotice) },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "느린 시계",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = "내 정보",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        floatingActionButton = {
            if (isSignedOut) return@Scaffold
            // 동그란 「+」 하나로는 무엇이 더해지는지 알 수 없다. 글자를 함께 둔다(#109).
            ExtendedFloatingActionButton(
                onClick = onAddSchedule,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(30.dp)) },
                text = { Text(text = "일정 추가", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            // 아래 여백은 떠 있는 추가 버튼이 마지막 항목을 가리지 않게 둔다. 글꼴을 키우면 버튼도
            // 커져 더 많이 가린다.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DayHeader(
                    dateText = dateText,
                    completedCount = state.completedCount,
                    totalCount = state.totalCount,
                )
            }

            if (isSignedOut) {
                item { SignInPrompt(onSignIn = onSignIn) }
                return@LazyColumn
            }

            state.currentSchedule?.let { schedule ->
                item {
                    NowCard(
                        schedule = schedule,
                        onShowDetail = { onIntent(MainIntent.ShowDetail(schedule.id)) },
                        onComplete = { onIntent(MainIntent.ToggleComplete(schedule.id)) },
                    )
                }
            }

            if (listedSchedules.isNotEmpty()) {
                item {
                    TodayScheduleSection(
                        schedules = listedSchedules,
                        onToggleComplete = { onIntent(MainIntent.ToggleComplete(it)) },
                        onShowDetail = { onIntent(MainIntent.ShowDetail(it)) },
                    )
                }
            }

            if (state.sharedReminders.isNotEmpty()) {
                item {
                    SharedRemindersSection(
                        sharedReminders = state.sharedReminders,
                        currentUserUid = state.currentUserId.ifBlank { null },
                        ownerNames = state.sharedReminderOwners,
                        onToggleComplete = { onIntent(MainIntent.ToggleSharedReminderComplete(it)) },
                    )
                }
            }

            if (!state.isSignedInKnown || state.isLoading) {
                item { ScheduleLoadingIndicator() }
            }
            if (state.pendingDelete != null) {
                item { ScheduleLoadingIndicator(message = "일정을 삭제하고 있습니다") }
            }
            if (state.isSignedInKnown && state.todaySchedules.isEmpty() && !state.isLoading && state.error == null &&
                state.pendingDelete == null
            ) {
                item { EmptyStateCard() }
            }

            state.error?.let { error ->
                item {
                    ErrorCard(
                        error = error,
                        canRetry = state.canRetry,
                        onRetry = { onIntent(MainIntent.Retry) },
                        onDismiss = { onIntent(MainIntent.ConsumeError) },
                    )
                }
            }
        }
    }
}

/**
 * 정확한 알람 권한 안내. Android 12 부터 시스템 설정에서 따로 허용해야 정시에 알람이 울린다.
 * 앱이 먼저 이유를 설명하고, 사용자가 「설정 열기」 를 누를 때만 시스템 설정으로 보낸다(#83).
 */
@Composable
private fun ExactAlarmNoticeDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(text = "알람을 정시에 울리려면", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                text = "이 기기는 정시 알람을 앱마다 따로 허용합니다. 허용하지 않으면 알람이 몇 분 늦게 울릴 수 있습니다.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = "설정 열기", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "나중에", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
