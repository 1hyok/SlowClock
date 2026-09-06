package com.example.slowclock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.feature.main.R
import com.example.slowclock.ui.mvi.ObserveSignal

/** 공유 코드 입력 화면(stateful). 저장이 끝나면 [onReturn] 으로 돌아간다. */
@Composable
fun SettingsScreenShareCode(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveSignal(
        signal = state.isSaved.takeIf { it },
        consumed = ShareCodeIntent.ConsumeSaved,
        onIntent = viewModel::onIntent,
    ) { onReturn() }

    ShareCodeContent(state = state, onIntent = viewModel::onIntent, onReturn = onReturn, modifier = modifier)
}

/** 공유 코드 입력 화면(stateless). */
@Composable
internal fun ShareCodeContent(
    state: ShareCodeUiState,
    onIntent: (ShareCodeIntent) -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("공유 일정을 볼 공유 코드를 입력하세요", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = { onIntent(ShareCodeIntent.UpdateInput(it)) },
            label = { Text("공유 코드") },
            singleLine = true,
            enabled = !state.isSaving,
        )
        if (state.hasRegisteredCode) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "공유 일정을 그만 보려면 코드를 모두 지우고 저장하세요.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onIntent(ShareCodeIntent.Save) },
            enabled = state.canSave,
        ) {
            Text(
                when {
                    state.isSaving -> "저장 중..."
                    state.input.isBlank() && state.hasRegisteredCode -> "공유 해제하고 돌아가기"
                    else -> "저장하고 돌아가기"
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onReturn,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.share_code_back_without_saving), textAlign = TextAlign.Center)
        }
        // 등록이 곧 읽기 권한이라, 실패를 알리지 않으면 가족 일정이 왜 비어 있는지 알 수 없다(#174).
        if (state.saveError != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.saveError,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
