package com.example.slowclock.ui.theme

import androidx.compose.ui.graphics.Color

// 템플릿 색상. 테마는 더 쓰지 않지만 토큰은 남겨 둔다.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ============== 접근성 강화 색상 시스템 ==============
// 화면들이 직접 쓰던 값을 토큰으로 올렸다. 고령자 대상이라 대비를 우선한다.

// 파랑: 주요 동작·테두리·강조 (WCAG AA, 흰 배경 대비 4.5:1 이상)
val BluePrimary = Color(0xFF1A73E8)
val BlueDeep = Color(0xFF3A5CCC)
val BlueContainer = Color(0xFFE3F2FD)
val BlueContainerSoft = Color(0xFFEAF1FF)
val OnBlueContainer = Color(0xFF0D47A1)
val BlueLight = Color(0xFF8AB4F8)

// 초록: 완료 상태
val GreenSuccess = Color(0xFF00A152)
val GreenDeep = Color(0xFF388E3C)
val GreenContainer = Color(0xFFE6F4EA)
val OnGreenContainer = Color(0xFF1B5E20)
val GreenLight = Color(0xFF81C995)

// 빨강: 오류·미완료
val RedError = Color(0xFFB3261E)
val RedLight = Color(0xFFF2B8B5)

// 텍스트 색상 (고대비)
val TextPrimary = Color(0xFF212121) // 검은색에 가까운 회색
val TextSecondary = Color(0xFF424242) // 진한 회색
val TextDisabled = Color(0xFF757575) // 비활성 텍스트

// 배경 색상
val Surface = Color(0xFFFFFFFF) // 순백색
val SurfaceVariantLight = Color(0xFFF8F9FB) // 카드 묶음 배경
val Outline = Color(0xFF757575)

// 다크 테마 배경
val SurfaceDark = Color(0xFF121212)
val SurfaceVariantDark = Color(0xFF2A2A2A)
val TextPrimaryDark = Color(0xFFF5F5F5)
val TextSecondaryDark = Color(0xFFCCCCCC)
