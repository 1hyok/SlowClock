package com.example.slowclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 역할별 토큰 배정. 화면은 이 토큰만 쓴다.
 * - primary: 주요 동작·테두리(파랑), primaryContainer: 공유 일정 같은 파란 카드 배경
 * - secondary: 제목·타임라인 선(진한 파랑)
 * - tertiary: 완료 상태(초록), tertiaryContainer: 완료 카드·빈 상태 배경
 * - error: 미완료·오류(빨강)
 * - surfaceVariant: 카드 묶음 배경, onSurfaceVariant: 보조 텍스트
 */
private val LightColorScheme =
    lightColorScheme(
        primary = BluePrimary,
        onPrimary = Surface,
        primaryContainer = BlueContainer,
        onPrimaryContainer = OnBlueContainer,
        secondary = BlueDeep,
        onSecondary = Surface,
        secondaryContainer = BlueContainerSoft,
        onSecondaryContainer = OnBlueContainer,
        tertiary = GreenSuccess,
        onTertiary = Surface,
        tertiaryContainer = GreenContainer,
        onTertiaryContainer = OnGreenContainer,
        error = RedError,
        onError = Surface,
        background = Surface,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = TextSecondary,
        outline = Outline,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = BlueLight,
        onPrimary = OnBlueContainer,
        primaryContainer = OnBlueContainer,
        onPrimaryContainer = BlueContainer,
        secondary = BlueLight,
        onSecondary = OnBlueContainer,
        secondaryContainer = OnBlueContainer,
        onSecondaryContainer = BlueContainerSoft,
        tertiary = GreenLight,
        onTertiary = OnGreenContainer,
        tertiaryContainer = OnGreenContainer,
        onTertiaryContainer = GreenContainer,
        error = RedLight,
        onError = RedError,
        background = SurfaceDark,
        onBackground = TextPrimaryDark,
        surface = SurfaceDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = TextSecondaryDark,
        outline = TextDisabled,
    )

@Composable
fun SlowClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 접근성을 위해 false로 설정
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColorScheme
            }

            else -> {
                LightColorScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
