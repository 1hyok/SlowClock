package com.example.slowclock.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.slowclock.util.AppError

/**
 * 오류 안내. 무엇이 잘못됐는지와 다시 해 볼 수 있는지를 한 카드에 담는다.
 *
 * 아이콘은 이모지가 아니라 테마 아이콘이다. 이모지는 기기 글꼴마다 크기와 모양이 달라 화면이
 * 기기마다 다르게 보이고, 화면 낭독기가 그림 이름을 그대로 읽는다(#109).
 */
@Composable
fun ErrorCard(
    error: AppError,
    modifier: Modifier = Modifier,
    canRetry: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector =
                    when (error) {
                        is AppError.OnlineWriteError, is AppError.NetworkError -> Icons.Outlined.WifiOff
                        is AppError.TimeoutError -> Icons.Outlined.Schedule
                        is AppError.AuthError -> Icons.Outlined.Lock
                        is AppError.PermissionError -> Icons.Outlined.Block
                        is AppError.InvalidDataError -> Icons.Outlined.EditNote
                        is AppError.NotFoundError -> Icons.Outlined.SearchOff
                        is AppError.ScheduleConflictError, is AppError.SaveError -> Icons.Outlined.SaveAs
                        is AppError.StorageFullError -> Icons.Outlined.Storage
                        is AppError.GeneralError -> Icons.Outlined.WarningAmber
                    },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    when (error) {
                        is AppError.OnlineWriteError, is AppError.NetworkError -> "연결 문제"
                        is AppError.TimeoutError -> "시간 초과"
                        is AppError.AuthError -> "로그인 필요"
                        is AppError.PermissionError -> "권한 없음"
                        is AppError.InvalidDataError -> "입력 오류"
                        is AppError.NotFoundError -> "찾을 수 없음"
                        is AppError.ScheduleConflictError -> "일정 확인 필요"
                        is AppError.SaveError -> "저장 실패"
                        is AppError.StorageFullError -> "저장공간 부족"
                        is AppError.GeneralError -> "알 수 없는 오류"
                    },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            if (onDismiss != null || (canRetry && onRetry != null)) {
                Spacer(modifier = Modifier.height(20.dp))

                val actionMinWidth = 144.dp * LocalDensity.current.fontScale
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onDismiss != null) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.widthIn(min = actionMinWidth).weight(1f).heightIn(min = 56.dp),
                        ) {
                            Text(text = "닫기", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (canRetry && onRetry != null) {
                        Button(
                            onClick = onRetry,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.widthIn(min = actionMinWidth).weight(1f).heightIn(min = 56.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "다시 시도", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
