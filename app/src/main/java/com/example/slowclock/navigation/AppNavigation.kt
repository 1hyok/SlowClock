package com.example.slowclock.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.slowclock.ui.addschedule.AddScheduleScreen
import com.example.slowclock.ui.common.components.BottomNavigationBar
import com.example.slowclock.ui.done.DoneScreen
import com.example.slowclock.ui.main.MainScreen
import com.example.slowclock.ui.profile.ProfileScreen
import com.example.slowclock.ui.recommendation.RecommendationScreen
import com.example.slowclock.ui.settings.SettingsScreen
import com.example.slowclock.ui.settings.SettingsScreenShareCode
import com.example.slowclock.ui.timeline.TimelineScreen
import kotlinx.serialization.Serializable

/**
 * 화면 키. Navigation 3 에서는 백스택을 앱이 소유하고, 화면(feature 모듈)은 네비게이션 콜백만 받는다.
 * 키는 백스택 저장·복원을 위해 직렬화 가능해야 한다.
 */
sealed interface SlowClockKey : NavKey

@Serializable
data object MainKey : SlowClockKey

@Serializable
data object DoneKey : SlowClockKey

@Serializable
data object TimelineKey : SlowClockKey

@Serializable
data object SettingsKey : SlowClockKey

@Serializable
data object ProfileKey : SlowClockKey

@Serializable
data object AddScheduleKey : SlowClockKey

@Serializable
data class EditScheduleKey(
    val scheduleId: String,
) : SlowClockKey

@Serializable
data object RecommendationKey : SlowClockKey

@Serializable
data object ShareCodeKey : SlowClockKey

/** 하단 탭. 탭 키는 백스택의 루트(메인) 바로 위에 하나만 둔다. */
private val tabKeys: Map<String, SlowClockKey> =
    mapOf(
        "main" to MainKey,
        "done" to DoneKey,
        "timeline" to TimelineKey,
        "settings" to SettingsKey,
    )

private fun NavKey.tabRoute(): String? = tabKeys.entries.firstOrNull { it.value == this }?.key

private fun NavBackStack<NavKey>.popBack() {
    if (size > 1) removeLastOrNull()
}

private fun NavBackStack<NavKey>.showTab(key: SlowClockKey) {
    while (size > 1) removeLastOrNull()
    if (key != MainKey) add(key)
}

private val recommendationSaver =
    listSaver<ScheduleRecommendation?, String>(
        save = { value -> value?.let { listOf(it.target, it.title) } ?: emptyList() },
        restore = { values -> if (values.size == 2) ScheduleRecommendation(values[0], values[1]) else null },
    )

@Composable
fun AppNavigation(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(MainKey)
    // 대상 entry 를 바꾸지 않고 결과만 전달하므로 입력 중인 ViewModel 이 유지된다.
    var recommendation by rememberSaveable(stateSaver = recommendationSaver) { mutableStateOf<ScheduleRecommendation?>(null) }
    val currentRoute = backStack.lastOrNull()?.tabRoute()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 탭 화면에서만 보인다. 상세 화면에 탭이 남아 있으면 그 자리에서 탭을 눌렀을 때
            // 어디로 가는지 알 수 없다.
            if (currentRoute != null) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> tabKeys[route]?.let(backStack::showTab) },
                )
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            onBack = {
                recommendation = recommendation?.afterLeaving(backStack.lastOrNull())
                backStack.popBack()
            },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<MainKey> {
                        MainScreen(
                            onAddSchedule = { backStack.add(AddScheduleKey) },
                            onEditSchedule = { scheduleId -> backStack.add(EditScheduleKey(scheduleId)) },
                            onNavigateToProfile = { backStack.add(ProfileKey) },
                            onNavigateToSettings = { backStack.add(ShareCodeKey) },
                            onSignIn = onSignIn,
                        )
                    }
                    entry<DoneKey> { DoneScreen() }
                    entry<TimelineKey> { TimelineScreen() }
                    entry<SettingsKey> { SettingsScreen() }
                    entry<AddScheduleKey> {
                        AddScheduleScreen(
                            initialTitle = recommendation?.titleFor(AddScheduleKey),
                            onInitialTitleConsume = { recommendation = null },
                            onNavigateBack = {
                                recommendation = null
                                backStack.popBack()
                            },
                            onNavigateToRecommendation = { backStack.add(RecommendationKey) },
                        )
                    }
                    entry<EditScheduleKey> { key ->
                        AddScheduleScreen(
                            scheduleId = key.scheduleId,
                            initialTitle = recommendation?.titleFor(key),
                            onInitialTitleConsume = { recommendation = null },
                            onNavigateBack = {
                                recommendation = null
                                backStack.popBack()
                            },
                            onNavigateToRecommendation = { backStack.add(RecommendationKey) },
                        )
                    }
                    entry<RecommendationKey> {
                        RecommendationScreen(
                            onSelectRecommendation = { title ->
                                recommendation = ScheduleRecommendation.selected(backStack, title)
                                backStack.popBack()
                            },
                        )
                    }
                    entry<ProfileKey> {
                        ProfileScreen(onNavigateBack = { backStack.popBack() }, onSignIn = onSignIn)
                    }
                    entry<ShareCodeKey> { SettingsScreenShareCode(onReturn = { backStack.popBack() }) }
                },
        )
    }
}
