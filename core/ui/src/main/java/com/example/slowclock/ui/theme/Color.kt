package com.example.slowclock.ui.theme

import androidx.compose.ui.graphics.Color

// ============== 접근성 우선 색상 시스템 ==============
// 고령자 대상이라 본문 대비를 WCAG AA(4.5:1) 이상으로 잡는다. 라이트와 다크는 역할을 뒤집지 않고
// 각자 계조를 따로 둔다. 화면은 이 값을 직접 쓰지 않고 Theme.kt 의 역할 토큰만 쓴다.

// --- 라이트: 파랑(주요 동작) ---
val BluePrimary = Color(0xFF1A73E8)
val BlueOnPrimary = Color(0xFFFFFFFF)
val BlueContainer = Color(0xFFD7E6FF)
val OnBlueContainer = Color(0xFF06285C)

// --- 라이트: 진한 파랑(제목·타임라인 선) ---
val BlueDeep = Color(0xFF3A5CCC)
val BlueContainerSoft = Color(0xFFE1E8FF)
val OnBlueContainerSoft = Color(0xFF101C4D)

// --- 라이트: 초록(완료) ---
val GreenSuccess = Color(0xFF00A152)
val GreenContainer = Color(0xFFCDEFD9)
val OnGreenContainer = Color(0xFF06351C)

// --- 라이트: 빨강(오류·미완료) ---
val RedError = Color(0xFFB3261E)
val RedContainer = Color(0xFFF9DEDC)
val OnRedContainer = Color(0xFF410E0B)

// --- 라이트: 표면 계조 ---
val Surface = Color(0xFFFFFFFF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F8FB)
val SurfaceContainer = Color(0xFFF1F3F7)
val SurfaceContainerHigh = Color(0xFFEBEEF3)
val SurfaceContainerHighest = Color(0xFFE5E8EE)
val SurfaceVariantLight = Color(0xFFE7E9EE)
val TextPrimary = Color(0xFF1A1C1E)
val TextSecondary = Color(0xFF43474E)
val Outline = Color(0xFF73777F)
val OutlineVariant = Color(0xFFC3C7CF)

// --- 다크: 파랑 ---
val BlueLight = Color(0xFFA9C7FF)
val OnBlueDark = Color(0xFF00305F)
val BlueContainerDark = Color(0xFF1B3C6B)
val OnBlueContainerDark = Color(0xFFD7E6FF)

// --- 다크: 진한 파랑 자리(제목) ---
val BlueSoftDark = Color(0xFFB9C6FF)
val OnBlueSoftDark = Color(0xFF1B2C72)
val BlueContainerSoftDark = Color(0xFF2A3C86)
val OnBlueContainerSoftDark = Color(0xFFE1E8FF)

// --- 다크: 초록 ---
val GreenLight = Color(0xFF7DD8A0)
val OnGreenDark = Color(0xFF00391D)
val GreenContainerDark = Color(0xFF14432A)
val OnGreenContainerDark = Color(0xFFCDEFD9)

// --- 다크: 빨강 ---
val RedLight = Color(0xFFF2B8B5)
val OnRedDark = Color(0xFF601410)
val RedContainerDark = Color(0xFF8C1D18)
val OnRedContainerDark = Color(0xFFF9DEDC)

// --- 다크: 표면 계조 ---
val SurfaceDark = Color(0xFF101418)
val SurfaceContainerLowestDark = Color(0xFF0B0F13)
val SurfaceContainerLowDark = Color(0xFF171B1F)
val SurfaceContainerDark = Color(0xFF1B1F24)
val SurfaceContainerHighDark = Color(0xFF262A2F)
val SurfaceContainerHighestDark = Color(0xFF31353A)
val SurfaceVariantDark = Color(0xFF43474E)
val TextPrimaryDark = Color(0xFFE2E2E6)
val TextSecondaryDark = Color(0xFFC3C7CF)
val OutlineDark = Color(0xFF8D9199)
val OutlineVariantDark = Color(0xFF43474E)
