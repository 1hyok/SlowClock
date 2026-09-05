package com.example.slowclock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 역할별 토큰 배정. 화면은 이 토큰만 쓴다. 뜻 하나에 색 하나다(#109).
 * - primary(남색): 주요 동작, 앱 제목, 선택 상태
 * - secondary(주황): 지금 해야 할 일. 화면에서 가장 먼저 보여야 하는 자리에만 쓴다
 * - tertiary(초록): 끝낸 일
 * - error(빨강): 오류
 * - background 는 따뜻한 종이색, surface 는 흰색이다. 카드가 배경에서 저절로 갈린다
 *
 * 다크는 라이트의 역할을 뒤집지 않는다. 뒤집으면 연한 컨테이너가 진한 색 덩어리가 되어 화면을
 * 지배한다. 다크의 컨테이너는 배경에 가까운 어두운 톤이고 그 위 글자만 밝다.
 *
 * 시스템 dynamic color 는 쓰지 않는다. 기기마다 대비가 달라져 고령자 대상 대비 기준을 지킬 수 없다.
 */
private val LightColorScheme =
    lightColorScheme(
        primary = InkBlue,
        onPrimary = OnInkBlue,
        primaryContainer = InkBlueContainer,
        onPrimaryContainer = OnInkBlueContainer,
        secondary = EmberOrange,
        onSecondary = OnEmberOrange,
        secondaryContainer = EmberContainer,
        onSecondaryContainer = OnEmberContainer,
        tertiary = LeafGreen,
        onTertiary = OnLeafGreen,
        tertiaryContainer = LeafContainer,
        onTertiaryContainer = OnLeafContainer,
        error = RedError,
        onError = OnRedError,
        errorContainer = RedContainer,
        onErrorContainer = OnRedContainer,
        background = PaperBackground,
        onBackground = InkPrimary,
        surface = PaperSurface,
        onSurface = InkPrimary,
        surfaceVariant = PaperVariant,
        onSurfaceVariant = InkSecondary,
        surfaceContainerLowest = PaperContainerLowest,
        surfaceContainerLow = PaperContainerLow,
        surfaceContainer = PaperContainer,
        surfaceContainerHigh = PaperContainerHigh,
        surfaceContainerHighest = PaperContainerHighest,
        outline = InkOutline,
        outlineVariant = InkOutlineVariant,
        inverseSurface = NightSurface,
        inverseOnSurface = InkPrimaryDark,
        inversePrimary = InkBlueLight,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = InkBlueLight,
        onPrimary = OnInkBlueDark,
        primaryContainer = InkBlueContainerDark,
        onPrimaryContainer = OnInkBlueContainerDark,
        secondary = EmberLight,
        onSecondary = OnEmberDark,
        secondaryContainer = EmberContainerDark,
        onSecondaryContainer = OnEmberContainerDark,
        tertiary = LeafLight,
        onTertiary = OnLeafDark,
        tertiaryContainer = LeafContainerDark,
        onTertiaryContainer = OnLeafContainerDark,
        error = RedLight,
        onError = OnRedDark,
        errorContainer = RedContainerDark,
        onErrorContainer = OnRedContainerDark,
        background = NightBackground,
        onBackground = InkPrimaryDark,
        surface = NightSurface,
        onSurface = InkPrimaryDark,
        surfaceVariant = NightVariant,
        onSurfaceVariant = InkSecondaryDark,
        surfaceContainerLowest = NightContainerLowest,
        surfaceContainerLow = NightContainerLow,
        surfaceContainer = NightContainer,
        surfaceContainerHigh = NightContainerHigh,
        surfaceContainerHighest = NightContainerHighest,
        outline = InkOutlineDark,
        outlineVariant = InkOutlineVariantDark,
        inverseSurface = PaperSurface,
        inverseOnSurface = InkPrimary,
        inversePrimary = InkBlue,
    )

/**
 * 모서리 반경 한 벌. 종전에는 화면마다 12·16dp 가 섞여 있었다.
 * 넉넉한 반경이 화면을 부드럽게 만들고, 큰 덩어리일수록 더 둥글다.
 */
private val SlowClockShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
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
        shapes = SlowClockShapes,
        content = content,
    )
}
