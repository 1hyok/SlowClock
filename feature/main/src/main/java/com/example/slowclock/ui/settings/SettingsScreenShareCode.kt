package com.example.slowclock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    ShareCodeContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** 공유 코드 입력 화면(stateless). */
@Composable
internal fun ShareCodeContent(
    state: ShareCodeUiState,
    onIntent: (ShareCodeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
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
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onIntent(ShareCodeIntent.Save) },
            enabled = state.canSave,
        ) {
            Text(if (state.isSaving) "저장 중..." else "저장하고 돌아가기")
        }
    }
}
