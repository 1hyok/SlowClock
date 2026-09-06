package com.example.slowclock.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleRecommendationTest {
    @Test
    fun `추가 화면에서 받은 추천은 추가 화면에만 전달한다`() {
        val result = ScheduleRecommendation.selected(listOf(MainKey, AddScheduleKey, RecommendationKey), "약 먹기")!!

        assertEquals("약 먹기", result.titleFor(AddScheduleKey))
        assertNull(result.titleFor(EditScheduleKey("s1")))
    }

    @Test
    fun `편집 화면에서 받은 추천은 그 일정에만 전달한다`() {
        val editor = EditScheduleKey("s1")
        val result = ScheduleRecommendation.selected(listOf(MainKey, editor, RecommendationKey), "산책")!!

        assertEquals("산책", result.titleFor(editor))
        assertNull(result.titleFor(EditScheduleKey("s2")))
        assertNull(result.titleFor(AddScheduleKey))
    }

    @Test
    fun `대상 폼에서 시스템 뒤로가기를 하면 다음 폼에 추천이 남지 않는다`() {
        val result = ScheduleRecommendation.selected(listOf(MainKey, AddScheduleKey, RecommendationKey), "약 먹기")!!

        assertNull(result.afterLeaving(AddScheduleKey))
    }

    @Test
    fun `추천 화면을 취소하면 남아 있던 결과도 지운다`() {
        val result = ScheduleRecommendation.selected(listOf(MainKey, EditScheduleKey("s1"), RecommendationKey), "산책")!!

        assertNull(result.afterLeaving(RecommendationKey))
    }

    @Test
    fun `바로 아래에 일정 폼이 없으면 추천 결과를 전달하지 않는다`() {
        assertNull(ScheduleRecommendation.selected(listOf(MainKey, RecommendationKey), "약 먹기"))
        assertNull(ScheduleRecommendation.selected(listOf(MainKey, AddScheduleKey), "약 먹기"))
    }
}
