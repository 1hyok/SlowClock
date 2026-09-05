package com.example.slowclock.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(MainKey)
    // 추천 화면이 고른 제목. 일정 추가 entry 를 바꾸지 않고 전달하므로 그 화면의 ViewModel 이 유지된다.
    var recommendedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val currentRoute = backStack.lastOrNull()?.tabRoute() ?: ""

    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route -> tabKeys[route]?.let(backStack::showTab) },
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.popBack() },
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
                        )
                    }
                    entry<DoneKey> { DoneScreen() }
                    entry<TimelineKey> { TimelineScreen() }
                    entry<SettingsKey> { SettingsScreen() }
                    entry<AddScheduleKey> {
                        AddScheduleScreen(
                            initialTitle = recommendedTitle,
                            onNavigateBack = {
                                recommendedTitle = null
                                backStack.popBack()
                            },
                            onNavigateToRecommendation = { backStack.add(RecommendationKey) },
                        )
                    }
                    entry<EditScheduleKey> { key ->
                        AddScheduleScreen(
                            scheduleId = key.scheduleId,
                            onNavigateBack = { backStack.popBack() },
                            onNavigateToRecommendation = { backStack.add(RecommendationKey) },
                        )
                    }
                    entry<RecommendationKey> {
                        RecommendationScreen(
                            onSelectRecommendation = { title ->
                                recommendedTitle = title
                                backStack.popBack()
                            },
                        )
                    }
                    entry<ProfileKey> { ProfileScreen(onNavigateBack = { backStack.popBack() }) }
                    entry<ShareCodeKey> { SettingsScreenShareCode(onReturn = { backStack.popBack() }) }
                },
        )
    }
}
