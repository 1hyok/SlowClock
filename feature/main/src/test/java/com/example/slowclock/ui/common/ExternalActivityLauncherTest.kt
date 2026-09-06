package com.example.slowclock.ui.common

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActivityLauncherTest {
    @Test
    fun `기본 화면이 열리면 대체 화면은 열지 않는다`() {
        var calls = 0
        assertTrue(launchExternalActivity(open = { calls++ }, fallback = { error("unexpected fallback") }))
        assertEquals(1, calls)
    }

    @Test
    fun `화면이 없으면 대체 설정을 연다`() {
        var calls = 0
        assertTrue(launchExternalActivity(open = { throw ActivityNotFoundException() }, fallback = { calls++ }))
        assertEquals(1, calls)
    }

    @Test
    fun `모든 화면이 없거나 정책으로 막혀도 실패를 반환한다`() {
        assertFalse(launchExternalActivity(open = { throw ActivityNotFoundException() }))
        assertFalse(launchExternalActivity(open = { throw SecurityException() }))
        assertFalse(launchExternalActivity(open = { throw ActivityNotFoundException() }, fallback = { throw SecurityException() }))
    }
}
