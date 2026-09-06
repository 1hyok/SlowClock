package com.example.slowclock.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorTypeTest {
    @Test
    fun `분류하지 못한 예외는 내부 메시지 대신 기본 안내를 낸다`() {
        assertEquals(AppError.GeneralError().message, IllegalStateException("INTERNAL transport closed").toAppError().message)
        assertEquals(AppError.GeneralError().message, RuntimeException().toAppError().message)
    }

    @Test
    fun `알려진 오류와 앱이 작성한 안내는 유지한다`() {
        assertEquals(AppError.NetworkError, RuntimeException("NETWORK unavailable").toAppError())
        assertEquals(AppError.TimeoutError, RuntimeException("timeout").toAppError())
        assertEquals("일정 제목을 입력해주세요", AppError.GeneralError("일정 제목을 입력해주세요").message)
    }
}
