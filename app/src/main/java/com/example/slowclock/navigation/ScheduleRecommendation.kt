package com.example.slowclock.navigation

import androidx.navigation3.runtime.NavKey

/** 추천 결과는 추천 화면 바로 아래에서 기다리는 일정 편집 화면 한 곳에만 전달한다. */
internal data class ScheduleRecommendation(
    val target: String,
    val title: String,
) {
    fun titleFor(editor: NavKey): String? = title.takeIf { editor.editorToken() == target }

    fun afterLeaving(screen: NavKey?): ScheduleRecommendation? =
        takeUnless { screen == RecommendationKey || screen?.editorToken() == target }

    companion object {
        fun selected(
            backStack: List<NavKey>,
            title: String,
        ): ScheduleRecommendation? {
            if (backStack.lastOrNull() != RecommendationKey) return null
            val target = backStack.getOrNull(backStack.lastIndex - 1)?.editorToken() ?: return null
            return ScheduleRecommendation(target, title)
        }
    }
}

private fun NavKey.editorToken(): String? =
    when (this) {
        AddScheduleKey -> "add"
        is EditScheduleKey -> "edit:$scheduleId"
        else -> null
    }
