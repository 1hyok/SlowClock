package com.example.slowclock.ui.addschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.ui.addschedule.components.RecommendationPlaceholder
import com.example.slowclock.ui.addschedule.components.RecurringSection
import com.example.slowclock.ui.addschedule.components.TimePickerSection
import com.example.slowclock.ui.addschedule.components.TitleInputSection
import com.example.slowclock.ui.mvi.ObserveSignal

/**
 * 일정 추가·수정 화면(stateful). [scheduleId] 가 있으면 수정 모드로 불러오고, [initialTitle] 은
 * 추천 화면에서 고른 제목이다. 저장이 끝나면 [onNavigateBack] 으로 돌아간다.
 */
@Composable
fun AddScheduleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRecommendation: () -> Unit,
    modifier: Modifier = Modifier,
    scheduleId: String? = null,
    initialTitle: String? = null,
    viewModel: AddScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(scheduleId) {
        if (!scheduleId.isNullOrBlank()) viewModel.onIntent(AddScheduleIntent.LoadForEdit(scheduleId))
    }
    LaunchedEffect(initialTitle) {
        if (!initialTitle.isNullOrBlank()) viewModel.onIntent(AddScheduleIntent.UpdateTitle(initialTitle))
    }
    ObserveSignal(
        signal = state.isSaved.takeIf { it },
        consumed = AddScheduleIntent.ConsumeSaved,
        onIntent = viewModel::onIntent,
    ) { onNavigateBack() }

    AddScheduleContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onNavigateToRecommendation = onNavigateToRecommendation,
        modifier = modifier,
    )
}

/** 일정 추가·수정 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddScheduleContent(
    state: AddScheduleUiState,
    onIntent: (AddScheduleIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToRecommendation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (state.isEditMode) "일정 수정" else "일정 추가",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        // 저장은 화면 아래 넓은 버튼이다. 종전의 떠 있는 원형 버튼은 내용 위에 겹쳐 가렸고
        // 무엇을 하는 버튼인지 글자가 없었다.
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = { onIntent(AddScheduleIntent.Save) },
                    enabled = state.canSave,
                    shape = RoundedCornerShape(16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(64.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (state.isEditMode) "수정 저장" else "일정 저장",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            TitleInputSection(
                title = state.title,
                description = state.description,
                onTitleChange = { onIntent(AddScheduleIntent.UpdateTitle(it)) },
                onDescriptionChange = { onIntent(AddScheduleIntent.UpdateDescription(it)) },
            )

            TimePickerSection(
                selectedTime = state.selectedTime,
                endTime = state.endTime,
                onTimeSelect = { onIntent(AddScheduleIntent.UpdateTime(it)) },
                onEndTimeSelect = { onIntent(AddScheduleIntent.UpdateEndTime(it)) },
            )

            RecurringSection(
                recurring = state.recurring,
                recurringType = state.recurringType,
                onRecurringChange = { onIntent(AddScheduleIntent.UpdateRecurring(it)) },
                onRecurringTypeChange = { onIntent(AddScheduleIntent.UpdateRecurringType(it)) },
            )

            RecommendationPlaceholder(
                onNavigateToRecommendation = onNavigateToRecommendation,
            )

            state.error?.let { error ->
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = error.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        if (state.canRetry) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onIntent(AddScheduleIntent.ConsumeError) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("닫기")
                                }

                                Button(
                                    onClick = { onIntent(AddScheduleIntent.Retry) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("다시 시도")
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
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
                            text = if (state.isEditMode && state.editingSchedule == null) "일정을 불러오는 중..." else "일정을 저장하는 중...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
