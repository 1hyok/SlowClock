package com.example.slowclock.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.common.components.SignInPrompt
import com.example.slowclock.ui.common.dialog.DeleteConfirmDialog
import com.example.slowclock.ui.main.components.CurrentTaskSection
import com.example.slowclock.ui.main.components.EmptyStateCard
import com.example.slowclock.ui.main.components.ScheduleDetailDialog
import com.example.slowclock.ui.main.components.SharedRemindersSection
import com.example.slowclock.ui.main.components.TodayScheduleSection
import com.example.slowclock.ui.mvi.ObserveSignal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    ObserveSignal(
        signal = state.openExactAlarmSettings,
        consumed = MainIntent.ConsumeExactAlarmSettingsRequest,
        onIntent = viewModel::onIntent,
    ) {
        context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
    MainContent(
        state = state,
        onIntent = viewModel::onIntent,
        onAddSchedule = onAddSchedule,
        onEditSchedule = onEditSchedule,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/** 메인 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다. */
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
) {
    val isSignedOut = state.isSignedInKnown && state.currentUserId.isBlank()
    val dateFormat = remember { SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN) }
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }

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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "느린시계",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "내 정보",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            if (isSignedOut) return@Scaffold
            FloatingActionButton(
                onClick = onAddSchedule,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "일정 추가",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(
                    text = dateFormat.format(Date()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isSignedOut) {
                item { SignInPrompt(onSignIn = onSignIn) }
                return@LazyColumn
            }

            state.currentSchedule?.let { schedule ->
                item {
                    CurrentTaskSection(
                        schedule = schedule,
                        onShowDetail = { onIntent(MainIntent.ShowDetail(schedule.id)) },
                    )
                }
            }

            item {
                TodayScheduleSection(
                    schedules = state.todaySchedules,
                    onToggleComplete = { onIntent(MainIntent.ToggleComplete(it)) },
                    onShowDetail = { onIntent(MainIntent.ShowDetail(it)) },
                )
            }

            if (state.sharedReminders.isNotEmpty()) {
                item {
                    SharedRemindersSection(
                        sharedReminders = state.sharedReminders,
                        currentUserUid = state.currentUserId.ifBlank { null },
                        timeFormat = timeFormat,
                        onToggleComplete = { onIntent(MainIntent.ToggleSharedReminderComplete(it)) },
                    )
                }
            }

            if (state.todaySchedules.isEmpty() && !state.isLoading) {
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
