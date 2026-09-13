package com.example.slowclock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.slowclock.ui.recommendation.RecommendationScreen
import com.example.slowclock.ui.theme.SlowClockTheme
import com.example.slowclock.ui.timeline.TimelineDatePickerDialog
import org.hamcrest.Matchers.endsWith
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class WindowAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun healthNoticeDoesNotPreventScrollingToStudentRecommendations() {
        compose.setContent {
            SlowClockTheme {
                RecommendationScreen(onSelectRecommendation = {}, onNavigateBack = {})
            }
        }
        compose.onNodeWithText("학생").performClick()
        // LazyColumn 은 보이지 않는 줄을 아직 만들지 않는다. performScrollTo 는 이미 만들어진
        // 줄만 찾으므로 화면이 짧거나 글자 배율이 높으면 노드를 못 찾고 실패한다. 목록을
        // 스크롤해 가며 찾는 performScrollToNode 를 쓴다.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("자습"))
        compose.onNodeWithText("자습").assertIsDisplayed()
    }

    @Test
    fun calendarIsRemovedWithItsScreenWithoutCallingDisposedCallbacks() {
        var mounted by mutableStateOf(true)
        var dismisses = 0
        compose.setContent {
            SlowClockTheme {
                if (mounted) {
                    TimelineDatePickerDialog(Calendar.getInstance(), { _, _, _ -> }, { dismisses++ })
                }
            }
        }
        onView(withClassName(endsWith("DatePicker"))).check(matches(isDisplayed()))
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        onView(withClassName(endsWith("DatePicker"))).check(doesNotExist())
        compose.runOnIdle { assertEquals(0, dismisses) }
    }

    @Test
    fun calendarUsesCurrentCallbackWithoutOpeningAnotherWindow() {
        var generation by mutableIntStateOf(0)
        var selectedGeneration = -1
        var dismisses = 0
        compose.setContent {
            val current = generation
            TimelineDatePickerDialog(
                Calendar.getInstance(),
                { _, _, _ -> selectedGeneration = current },
                { dismisses++ },
            )
        }
        compose.runOnIdle { generation = 1 }
        onView(withId(android.R.id.button1)).perform(click())
        compose.runOnIdle {
            assertEquals(1, selectedGeneration)
            assertEquals(1, dismisses)
        }
    }
}
