package com.example.slowclock

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.slowclock.data.model.Schedule
import com.example.slowclock.ui.common.components.ErrorCard
import com.example.slowclock.ui.common.components.ScheduleRow
import com.example.slowclock.ui.main.components.ScheduleDetailDialog
import com.example.slowclock.ui.theme.SlowClockTheme
import com.example.slowclock.util.AppError
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

class UiRecoveryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nonRetryableErrorCanBeDismissed() {
        var dismisses = 0
        compose.setContent {
            SlowClockTheme(darkTheme = false) {
                ErrorCard(error = AppError.PermissionError, canRetry = false, onDismiss = { dismisses++ })
            }
        }
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("닫기").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, dismisses) }
    }

    @Test
    fun nonClickableRowIsNotDisabledAndItsCompletionActionWorks() {
        var toggles = 0
        compose.setContent {
            SlowClockTheme(darkTheme = false) {
                ScheduleRow(title = "혈압약 먹기", time = Date(0), completed = false, onToggleComplete = { toggles++ })
            }
        }
        compose.onNodeWithText("혈압약 먹기").assertIsEnabled().assertHasNoClickAction()
        compose.onNodeWithContentDescription("완료로 표시").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
    }

    @Test
    fun longDetailAtLargeFontCanScrollToActions() {
        var deletes = 0
        var dismisses = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SlowClockTheme(darkTheme = false) {
                    ScheduleDetailDialog(
                        schedule =
                            Schedule(
                                id = "detail",
                                title = "혈압약 먹기",
                                description = List(30) { "식사 후 물과 함께 복용해주세요." }.joinToString("\n"),
                                startTime = Timestamp(Date(0)),
                            ),
                        onDismiss = { dismisses++ },
                        onDelete = { deletes++ },
                    )
                }
            }
        }
        compose
            .onNodeWithText("삭제")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(1, deletes)
            assertEquals(1, dismisses)
        }
    }
}
