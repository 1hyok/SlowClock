package com.example.slowclock.ui.theme

import androidx.compose.ui.graphics.Color

// ============== 느린 시계 색 ==============
// 고령자 대상이라 본문 대비를 WCAG AA(4.5:1) 이상으로 잡는다. 라이트와 다크는 역할을 뒤집지 않고
// 각자 계조를 따로 둔다. 화면은 이 값을 직접 쓰지 않고 Theme.kt 의 역할 토큰만 쓴다.
//
// 뜻마다 색을 하나씩 준다. 같은 색이 두 뜻을 맡으면 화면에서 구별되지 않는다(#109).
// - 남색: 앱의 기본색. 주요 동작과 제목.
// - 주황: 지금 해야 할 일. 화면에서 가장 먼저 보여야 하는 자리에만 쓴다.
// - 초록: 끝낸 일.
// - 빨강: 오류.
//
// 배경은 흰색이 아니라 따뜻한 종이색이다. 카드를 흰색으로 두면 배경과 카드가 저절로 갈린다.
// 종전에는 배경도 카드도 회색 계열이라 카드 경계가 흐렸다.

// --- 라이트: 남색(기본 동작) ---
val InkBlue = Color(0xFF1B4965)
val OnInkBlue = Color(0xFFFFFFFF)
val InkBlueContainer = Color(0xFFD3E4F0)
val OnInkBlueContainer = Color(0xFF0A2233)

// --- 라이트: 주황(지금 할 일) ---
val EmberOrange = Color(0xFFB4460F)
val OnEmberOrange = Color(0xFFFFFFFF)
val EmberContainer = Color(0xFFFCE4D6)
val OnEmberContainer = Color(0xFF4A1B03)

// --- 라이트: 초록(끝낸 일) ---
val LeafGreen = Color(0xFF1F6B3D)
val OnLeafGreen = Color(0xFFFFFFFF)
val LeafContainer = Color(0xFFD5EBDC)
val OnLeafContainer = Color(0xFF0A2916)

// --- 라이트: 빨강(오류) ---
val RedError = Color(0xFFB3261E)
val OnRedError = Color(0xFFFFFFFF)
val RedContainer = Color(0xFFF9DEDC)
val OnRedContainer = Color(0xFF410E0B)

// --- 라이트: 종이와 잉크 ---
val PaperBackground = Color(0xFFFAF6F0)
val PaperSurface = Color(0xFFFFFFFF)
val PaperContainerLowest = Color(0xFFFFFFFF)
val PaperContainerLow = Color(0xFFFCF9F5)
val PaperContainer = Color(0xFFF4EEE6)
val PaperContainerHigh = Color(0xFFEDE6DC)
val PaperContainerHighest = Color(0xFFE5DDD1)
val PaperVariant = Color(0xFFEDE6DC)
val InkPrimary = Color(0xFF1C1B19)
val InkSecondary = Color(0xFF4A4741)
val InkOutline = Color(0xFF7C776E)
val InkOutlineVariant = Color(0xFFD8D1C6)

// --- 다크: 남색 ---
val InkBlueLight = Color(0xFF9CCBE8)
val OnInkBlueDark = Color(0xFF092334)
val InkBlueContainerDark = Color(0xFF17394F)
val OnInkBlueContainerDark = Color(0xFFD3E4F0)

// --- 다크: 주황 ---
val EmberLight = Color(0xFFFFB08A)
val OnEmberDark = Color(0xFF4A1B03)
val EmberContainerDark = Color(0xFF4A2413)
val OnEmberContainerDark = Color(0xFFFCE4D6)

// --- 다크: 초록 ---
val LeafLight = Color(0xFF8CD3A5)
val OnLeafDark = Color(0xFF0A2916)
val LeafContainerDark = Color(0xFF1B4029)
val OnLeafContainerDark = Color(0xFFD5EBDC)

// --- 다크: 빨강 ---
val RedLight = Color(0xFFF2B8B5)
val OnRedDark = Color(0xFF601410)
val RedContainerDark = Color(0xFF8C1D18)
val OnRedContainerDark = Color(0xFFF9DEDC)

// --- 다크: 종이와 잉크 ---
val NightBackground = Color(0xFF14120F)
val NightSurface = Color(0xFF14120F)
val NightContainerLowest = Color(0xFF0D0C0A)
val NightContainerLow = Color(0xFF1C1A16)
val NightContainer = Color(0xFF22201B)
val NightContainerHigh = Color(0xFF2D2A24)
val NightContainerHighest = Color(0xFF39352E)
val NightVariant = Color(0xFF4A4741)
val InkPrimaryDark = Color(0xFFF0EBE3)
val InkSecondaryDark = Color(0xFFCFC8BD)
val InkOutlineDark = Color(0xFF968F84)
val InkOutlineVariantDark = Color(0xFF4A4741)
