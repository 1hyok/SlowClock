package com.example.slowclock.ui.addschedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.slowclock.ui.addschedule.components.RecommendationPlaceholder
import com.example.slowclock.ui.addschedule.components.RecurringSection
import com.example.slowclock.ui.addschedule.components.TimePickerSection
import com.example.slowclock.ui.addschedule.components.TitleInputSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleScreen(
    scheduleId: String? = null,
    initialTitle: String? = null,
    onNavigateBack: (Boolean) -> Unit,
    viewModel: AddScheduleViewModel = hiltViewModel(),
    onNavigateToRecommendation: () -> Unit,
) {
    val scrollState = remember { ScrollState(0) }
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode = !scheduleId.isNullOrBlank()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }

    LaunchedEffect(scheduleId) {
        if (!scheduleId.isNullOrBlank()) {
            viewModel.loadScheduleForEdit(scheduleId)
        }
    }
    LaunchedEffect(initialTitle) {
        if (!initialTitle.isNullOrBlank()) {
            viewModel.updateTitle(initialTitle)
        }
    }
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onNavigateBack(true)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "일정 수정" else "일정 추가",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(false) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.saveSchedule(context) },
                containerColor =
                    if (uiState.canSave) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "저장",
                    tint =
                        if (uiState.canSave) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // 일정 제목 입력 (분리된 컴포넌트)
            TitleInputSection(
                title = uiState.title,
                description = uiState.description,
                onTitleChange = viewModel::updateTitle,
                onDescriptionChange = viewModel::updateDescription,
            )

            // 시간 선택
            TimePickerSection(
                selectedTime = uiState.selectedTime,
                endTime = uiState.endTime,
                onTimeSelected = viewModel::updateTime,
                onEndTimeSelected = viewModel::updateEndTime,
            )

            // 반복 일정 설정 (분리된 컴포넌트)
            RecurringSection(
                recurring = uiState.recurring,
                recurringType = uiState.recurringType,
                onRecurringChange = viewModel::updateRecurring,
                onRecurringTypeChange = viewModel::updateRecurringType,
            )

            // 추천 기능 영역
            RecommendationPlaceholder(
                onNavigateToRecommendation = onNavigateToRecommendation,
            )

            // 에러 메시지
            if (uiState.error != null) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = uiState.error!!.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        if (uiState.canRetry) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.clearError() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("닫기")
                                }

                                Button(
                                    onClick = { viewModel.retryLastAction(context) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("다시 시도")
                                }
                            }
                        }
                    }
                }
            }

            // 로딩 상태
            if (uiState.isLoading) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "일정을 저장하는 중...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
