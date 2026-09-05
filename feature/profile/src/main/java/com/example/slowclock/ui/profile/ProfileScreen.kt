package com.example.slowclock.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onUserMessageShown()
    }

    LaunchedEffect(uiState.shouldLeave) {
        if (uiState.shouldLeave) {
            viewModel.onLeaveHandled()
            onNavigateBack()
        }
    }

    ProfileContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onSignOut = viewModel::signOut,
        onDeleteAccountClick = viewModel::requestDeleteAccount,
        onDeleteAccountConfirm = viewModel::confirmDeleteAccount,
        onDeleteAccountDismiss = viewModel::dismissDeleteConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onDeleteAccountConfirm: () -> Unit,
    onDeleteAccountDismiss: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "내 정보",
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
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.isLoading -> {
                    Text("로딩 중...")
                }

                uiState.loadError != null -> {
                    Text(uiState.loadError, color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    ProfileBody(
                        uiState = uiState,
                        onSignOut = onSignOut,
                        onDeleteAccountClick = onDeleteAccountClick,
                    )
                }
            }
        }
    }

    if (uiState.isDeleteConfirmVisible) {
        DeleteAccountConfirmDialog(
            onConfirm = onDeleteAccountConfirm,
            onDismiss = onDeleteAccountDismiss,
        )
    }
}

@Composable
private fun ProfileBody(
    uiState: ProfileUiState,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Person,
            contentDescription = "프로필",
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = uiState.name.ifBlank { "이름 없음" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = uiState.email.ifBlank { "이메일 없음" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "내 공유 코드:",
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = uiState.shareCode.ifBlank { "-" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(56.dp))

        Button(
            onClick = onSignOut,
            enabled = !uiState.isDeleting,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            modifier = Modifier.size(width = 200.dp, height = 56.dp),
        ) {
            Text(
                text = "로그아웃",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "계정을 삭제하는 중입니다...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(
                onClick = onDeleteAccountClick,
                modifier = Modifier.size(width = 200.dp, height = 56.dp),
            ) {
                Text(
                    text = "계정 삭제",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "계정을 삭제할까요?",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = "일정, 가족 그룹, 알림 기록과 계정이 모두 지워지며 되돌릴 수 없습니다.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
    )
}
