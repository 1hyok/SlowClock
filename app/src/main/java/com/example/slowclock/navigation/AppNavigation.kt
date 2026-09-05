// app/src/main/java/com/example/slowclock/navigation/AppNavigation.kt
package com.example.slowclock.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.slowclock.ui.addschedule.AddScheduleScreen
import com.example.slowclock.ui.common.components.BottomNavigationBar
import com.example.slowclock.ui.done.DoneScreen
import com.example.slowclock.ui.main.MainScreen
import com.example.slowclock.ui.profile.ProfileScreen
import com.example.slowclock.ui.recommendation.RecommendationScreen
import com.example.slowclock.ui.settings.SettingsScreen
import com.example.slowclock.ui.settings.SettingsScreenShareCode
import com.example.slowclock.ui.timeline.TimelineScreen

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(
        initial = navController.currentBackStackEntry,
    )

    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute?.destination?.route ?: "main",
                onNavigate = { navController.navigate(it) },
            )
        },
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("main") {
                MainScreen(
                    onAddSchedule = {
                        navController.navigate("add_schedule")
                    },
                    onEditSchedule = { scheduleId ->
                        navController.navigate("edit_schedule/$scheduleId")
                    },
                    onNavigateToProfile = {
                        navController.navigate("profile")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings_share_code")
                    },
                )
            }

            composable("done") { DoneScreen() }
            composable("timeline") { TimelineScreen() }
            composable("settings") { SettingsScreen() }

            composable(
                route = "add_schedule",
            ) {
                val initialTitle =
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.get<String>("initial_title")

                AddScheduleScreen(
                    initialTitle = initialTitle,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecommendation = {
                        navController.navigate("recommendation")
                    },
                )
            }

            composable("edit_schedule/{scheduleId}") { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: ""
                AddScheduleScreen(
                    scheduleId = scheduleId,
                    onNavigateBack = {
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRecommendation = {
                        navController.navigate("recommendation")
                    },
                )
            }

            composable("profile") {
                ProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable("recommendation") {
                RecommendationScreen(
                    onSelectRecommendation = { title ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("initial_title", title)
                        navController.popBackStack()
                    },
                )
            }

            composable("settings_share_code") {
                SettingsScreenShareCode(
                    onReturn = { navController.popBackStack() },
                )
            }
        }
    }
}
