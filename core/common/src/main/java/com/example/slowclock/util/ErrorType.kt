// app/src/main/java/com/example/slowclock/util/ErrorType.kt
package com.example.slowclock.util

sealed class AppError(val message: String) {
    // 네트워크 관련 에러
    object NetworkError : AppError("인터넷 연결을 확인해주세요")
    object TimeoutError : AppError("요청 시간이 초과되었습니다. 다시 시도해주세요")

    // 인증 관련 에러
    object AuthError : AppError("로그인이 필요합니다")
    object PermissionError : AppError("권한이 없습니다")

    // 데이터 관련 에러
    object InvalidDataError : AppError("잘못된 데이터입니다")
    object NotFoundError : AppError("요청한 정보를 찾을 수 없습니다")

    // 저장 관련 에러
    object SaveError : AppError("저장에 실패했습니다. 다시 시도해주세요")
    object StorageFullError : AppError("저장 공간이 부족합니다")

    // 일반 에러 (Error → GeneralError로 변경)
    class GeneralError(customMessage: String? = null) : AppError(
        customMessage ?: "알 수 없는 오류가 발생했습니다"
    )
}

// 에러 타입 변환 유틸리티
fun Throwable.toAppError(): AppError {
    return when {
        message?.contains("network", ignoreCase = true) == true -> AppError.NetworkError
        message?.contains("timeout", ignoreCase = true) == true -> AppError.TimeoutError
        message?.contains("permission", ignoreCase = true) == true -> AppError.PermissionError
        message?.contains("not found", ignoreCase = true) == true -> AppError.NotFoundError
        message?.contains("authentication", ignoreCase = true) == true -> AppError.AuthError
        message?.contains("storage", ignoreCase = true) == true -> AppError.StorageFullError
        else -> AppError.GeneralError(message)
    }
}