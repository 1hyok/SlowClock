// app/src/main/java/com/example/slowclock/ui/theme/Type.kt
package com.example.slowclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============== 접근성 강화 타이포그래피 ==============

val Typography = Typography(
    // 대형 제목 (앱 이름, 주요 헤더)
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,          // 28sp → 32sp
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // 중형 제목 (화면 제목, 섹션 헤더)
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,          // 20sp → 24sp
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // 소형 제목 (카드 제목, 그룹 헤더)
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,          // 18sp → 20sp
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // 큰 본문 (주요 내용, 일정 제목)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,          // 16sp → 20sp (크게 증가)
        lineHeight = 28.sp,        // 24sp → 28sp
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),

    // 중간 본문 (설명 텍스트, 부가 정보)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,          // 14sp → 18sp (크게 증가)
        lineHeight = 26.sp,        // 20sp → 26sp
        letterSpacing = 0.25.sp,
        color = TextSecondary
    ),

    // 작은 본문 (시간, 메타데이터)
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,          // 12sp → 16sp (크게 증가)
        lineHeight = 24.sp,        // 16sp → 24sp
        letterSpacing = 0.4.sp,
        color = TextSecondary
    ),

    // 버튼 텍스트 (모든 버튼)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,          // 14sp → 18sp
        lineHeight = 24.sp,        // 20sp → 24sp
        letterSpacing = 0.1.sp,
        color = Surface
    ),

    // 작은 버튼 텍스트
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,          // 12sp → 16sp
        lineHeight = 20.sp,        // 16sp → 20sp
        letterSpacing = 0.5.sp,
        color = Surface
    ),

    // 아주 작은 라벨 (태그, 배지)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,          // 11sp → 14sp
        lineHeight = 18.sp,        // 16sp → 18sp
        letterSpacing = 0.5.sp,
        color = TextDisabled
    )
)