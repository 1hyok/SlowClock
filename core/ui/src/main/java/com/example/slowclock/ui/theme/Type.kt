package com.example.slowclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============== 느린시계 글자 ==============
//
// 고령자가 읽는 화면이라 기본 크기를 Material 기본값보다 크게 잡는다. 크기만 키우면 화면이
// 평평해지므로 단계마다 굵기도 함께 바꿔 위아래를 만든다.
//
// 종전에는 display·title 단계를 정의하지 않아 화면이 쓰는 titleLarge·titleMedium 이 Material
// 기본값으로 떨어졌다. 그 결과 titleMedium(16sp)이 bodySmall(16sp)과 같아지고 bodyLarge(20sp)
// 보다 작아져, 제목이 본문보다 작게 나오는 자리가 생겼다(#109). 열세 단계를 모두 정의한다.
//
// 타이포그래피는 색을 갖지 않는다. 글자 색은 각 화면이 테마 토큰으로 정한다. 스타일에 색을 박아
// 두면 다크 모드에서 배경만 바뀌고 글자는 라이트 색으로 남는다(#90 에서 제거).
//
// 줄 높이는 글자 크기의 1.4배 안팎이다. 한글은 라틴 문자보다 세로로 꽉 차서 줄 간격이 좁으면
// 글줄이 붙어 보인다.

private val Sans = FontFamily.Default

val Typography =
    Typography(
        // 화면의 주인공. 지금 할 일의 시각처럼 한 화면에 하나만 쓴다.
        displayLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                lineHeight = 46.sp,
                letterSpacing = (-0.25).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        // 앱 이름과 화면 제목
        headlineLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.sp,
            ),
        // 구역 제목과 카드 제목
        titleLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        // 본문
        bodyLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.1.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        // 버튼과 라벨
        labelLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
            ),
    )
