package com.example.slowclock.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.slowclock.data.model.ThemeMode
import com.example.slowclock.ui.common.components.ScreenHeader
import com.example.slowclock.ui.mvi.ObserveSignal

/** 메디컬타임즈 의료 뉴스 목록. 기사는 앱이 읽거나 저장하지 않고 브라우저로만 연다. */
internal const val MEDICAL_NEWS_URL = "https://www.medicaltimes.com/Main/News/List.html?MainCate=6&SubCate=79"

/** 정보 화면(stateful). */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 시스템 설정 화면은 결과를 돌려주지 않는다. 허용하고 돌아왔을 때 안내가 사라지려면
    // 이 화면이 다시 보이는 순간 권한을 다시 읽어야 한다(#128).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(SettingsIntent.RefreshAlarmPermission)
    }
    ObserveSignal(
        signal = state.openFullScreenAlarmSettings,
        consumed = SettingsIntent.ConsumeFullScreenAlarmSettingsRequest,
        onIntent = viewModel::onIntent,
    ) {
        context.openFullScreenAlarmSettings()
    }
    SettingsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * 전체 화면 알람 설정 화면을 연다.
 *
 * `package:` 를 붙여야 이 앱의 항목이 바로 열린다. 그 화면이 없는 기기에서는 앱 알림 설정으로
 * 내려간다. 둘 다 실패해도 앱이 죽지 않게 잡는다.
 */
private fun Context.openFullScreenAlarmSettings() {
    val direct =
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            "package:$packageName".toUri(),
        )
    val fallback =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(direct) }
        .recoverCatching { startActivity(fallback) }
        .onFailure { Log.w("SettingsScreen", "전체 화면 알람 설정 화면을 열지 못했다", it) }
}

/** 정보 화면(stateless). 프리뷰·스크린샷 테스트 진입점이다. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // 카드 셋이 기본 글자 배율에서도 360x800dp 를 넘긴다. 스크롤이 없으면 맨 아래
                // 카드가 하단 탭 뒤로 잘려 들어가고 손가락으로 끌어도 올라오지 않는다(#166).
                // 같은 실패를 내 정보 화면(#135)과 완료 화면이 이미 같은 방법으로 고쳤다.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "정보")
        if (state.showFullScreenAlarmNotice) {
            FullScreenAlarmCard(onOpenSettings = { onIntent(SettingsIntent.OpenFullScreenAlarmSettings) })
        }
        ThemeModeCard(selected = state.themeMode, onSelect = { onIntent(SettingsIntent.SelectThemeMode(it)) })
        MedicalNewsCard()
    }
}

/** 화면 밝기 선택. 기기 설정을 따르는 것이 기본이고, 원하면 밝게·어둡게로 고정한다. */
@Composable
private fun ThemeModeCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ThemeMode.SYSTEM to "기기 설정",
            ThemeMode.LIGHT to "밝게",
            ThemeMode.DARK to "어둡게",
        )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "화면 밝기",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 가로로 나눈 세 칸은 글자 크기를 키우면 반드시 잘린다. 세로로 편다(#107).
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (mode, label) ->
                    ThemeModeRow(
                        label = label,
                        selected = selected == mode,
                        onSelect = { onSelect(mode) },
                    )
                }
            }
        }
    }
}

/** 화면 밝기 선택 한 줄. 줄 전체가 눌리고, 글자가 커지면 줄도 같이 커진다. */
@Composable
private fun ThemeModeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .selectable(
                    selected = selected,
                    onClick = onSelect,
                    role = Role.RadioButton,
                ).heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 전체 화면 알람 권한 안내. 허용돼 있으면 이 카드는 아예 그려지지 않는다(#128).
 *
 * 권한이 없어도 소리와 진동은 서비스가 그대로 낸다(#122). 그래서 첫 실행을 막는 팝업이 아니라
 * 정보 화면의 카드로 둔다. 정시 알람 안내(#83)와 첫 진입에서 겹치면 팝업이 두 번 뜬다.
 */
@Composable
private fun FullScreenAlarmCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "알람을 화면 가득 띄우기",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "켜 두면 알람 시각에 화면이 저절로 켜지면서 무엇을 할 시간인지 " +
                            "큰 글씨로 보여 줍니다. 지금은 소리와 알림만 나갑니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun MedicalNewsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, MEDICAL_NEWS_URL.toUri()))
                },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "의료 정보 보기 (메디컬타임즈)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "브라우저에서 열립니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
