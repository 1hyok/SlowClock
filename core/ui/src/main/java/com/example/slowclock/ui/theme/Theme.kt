package com.example.slowclock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 역할별 토큰 배정. 화면은 이 토큰만 쓴다.
 * - primary: 주요 동작·강조(파랑), primaryContainer: 파란 카드 배경
 * - secondary: 제목·타임라인 선, tertiary: 완료 상태(초록)
 * - error: 오류·미완료(빨강)
 * - surfaceContainer 계열: 카드 배경. 라이트는 흰 배경 위 회색 계조, 다크는 배경보다 밝은 계조다.
 *
 * 다크는 라이트의 역할을 뒤집지 않는다. 뒤집으면 연한 컨테이너가 진한 색 덩어리가 되어 화면을
 * 지배한다. 다크의 컨테이너는 배경에 가까운 어두운 톤이고 그 위 글자만 밝다.
 *
 * 시스템 dynamic color 는 쓰지 않는다. 기기마다 대비가 달라져 고령자 대상 대비 기준을 지킬 수 없다.
 */
private val LightColorScheme =
    lightColorScheme(
        primary = BluePrimary,
        onPrimary = BlueOnPrimary,
        primaryContainer = BlueContainer,
        onPrimaryContainer = OnBlueContainer,
        secondary = BlueDeep,
        onSecondary = BlueOnPrimary,
        secondaryContainer = BlueContainerSoft,
        onSecondaryContainer = OnBlueContainerSoft,
        tertiary = GreenSuccess,
        onTertiary = BlueOnPrimary,
        tertiaryContainer = GreenContainer,
        onTertiaryContainer = OnGreenContainer,
        error = RedError,
        onError = BlueOnPrimary,
        errorContainer = RedContainer,
        onErrorContainer = OnRedContainer,
        background = Surface,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = SurfaceContainerLowest,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        outline = Outline,
        outlineVariant = OutlineVariant,
        inverseSurface = SurfaceDark,
        inverseOnSurface = TextPrimaryDark,
        inversePrimary = BlueLight,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = BlueLight,
        onPrimary = OnBlueDark,
        primaryContainer = BlueContainerDark,
        onPrimaryContainer = OnBlueContainerDark,
        secondary = BlueSoftDark,
        onSecondary = OnBlueSoftDark,
        secondaryContainer = BlueContainerSoftDark,
        onSecondaryContainer = OnBlueContainerSoftDark,
        tertiary = GreenLight,
        onTertiary = OnGreenDark,
        tertiaryContainer = GreenContainerDark,
        onTertiaryContainer = OnGreenContainerDark,
        error = RedLight,
        onError = OnRedDark,
        errorContainer = RedContainerDark,
        onErrorContainer = OnRedContainerDark,
        background = SurfaceDark,
        onBackground = TextPrimaryDark,
        surface = SurfaceDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = TextSecondaryDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        inverseSurface = Surface,
        inverseOnSurface = TextPrimary,
        inversePrimary = BluePrimary,
    )

@Composable
fun SlowClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 상태 표시줄 아이콘 색은 창이 정한다. 앱만 어둡게 두면 기기가 밝은 모드일 때 어두운 아이콘이
    // 어두운 배경 위에 남아 읽히지 않는다. 그래서 테마가 정해질 때 함께 맞춘다.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        SideEffect {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
