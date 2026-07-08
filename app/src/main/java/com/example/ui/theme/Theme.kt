package com.smartprocurement.internal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════
// 三公鲜配主题系统
// ══════════════════════════════════════════════════════════════

private val JrxpLightColorScheme = lightColorScheme(
    primary = JrxpColors.CommandNavy,
    onPrimary = Color.White,
    primaryContainer = JrxpColors.PaleBlue,
    onPrimaryContainer = JrxpColors.CommandNavy,
    secondary = JrxpColors.DutyBlue,
    onSecondary = Color.White,
    secondaryContainer = JrxpColors.PaleBlueBg,
    onSecondaryContainer = JrxpColors.DutyBlue,
    tertiary = JrxpColors.LedgerGold,
    onTertiary = Color.White,
    background = JrxpColors.LedgerPaper,
    onBackground = JrxpColors.InkPrimary,
    surface = JrxpColors.ReadingSurface,
    onSurface = JrxpColors.InkPrimary,
    surfaceVariant = JrxpColors.MutedSurface,
    onSurfaceVariant = JrxpColors.InkSecondary,
    outline = JrxpColors.RuleLine,
    outlineVariant = JrxpColors.SoftDivider,
    error = JrxpColors.CriticalRed,
    errorContainer = JrxpColors.CriticalRedBg,
    onErrorContainer = JrxpColors.CriticalRed,
    surfaceContainerLow = JrxpColors.LedgerPaper,
    surfaceContainer = JrxpColors.MutedSurface,
    surfaceContainerHigh = JrxpColors.PureSurface,
)

private val JrxpDarkColorScheme = darkColorScheme(
    primary = JrxpColors.DispatchBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = JrxpColors.PaleBlue,
    secondary = JrxpColors.DispatchBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3448),
    onSecondaryContainer = JrxpColors.PaleBlue,
    tertiary = JrxpColors.LedgerGold,
    onTertiary = Color.White,
    background = JrxpColors.DarkBackground,
    onBackground = JrxpColors.DarkInkPrimary,
    surface = JrxpColors.DarkSurface,
    onSurface = JrxpColors.DarkInkPrimary,
    surfaceVariant = JrxpColors.DarkSurfaceHigh,
    onSurfaceVariant = JrxpColors.DarkInkSecondary,
    outline = Color(0xFF3D444C),
    outlineVariant = Color(0xFF2E343A),
    error = Color(0xFFD4534B),
    errorContainer = Color(0xFF3D2020),
    onErrorContainer = Color(0xFFD4534B),
    surfaceContainerLow = JrxpColors.DarkBackground,
    surfaceContainer = JrxpColors.DarkSurface,
    surfaceContainerHigh = JrxpColors.DarkSurfaceHigh,
)

// ── 扩展颜色（Material3 不直接支持的语义色） ────────────────
@Immutable
data class JrxpExtendedColors(
    val commandNavy: Color,
    val dutyBlue: Color,
    val dispatchBlue: Color,
    val paleBlue: Color,
    val ledgerPaper: Color,
    val readingSurface: Color,
    val mutedSurface: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val ruleLine: Color,
    val softDivider: Color,
    val focusedBorder: Color,
    val supplyGreen: Color,
    val warningAmber: Color,
    val criticalRed: Color,
    val cancelledGray: Color,
    val ledgerGold: Color,
    val supplyGreenBg: Color,
    val warningAmberBg: Color,
    val criticalRedBg: Color,
    val paleBlueBg: Color,
)

val LightExtendedColors = JrxpExtendedColors(
    commandNavy = JrxpColors.CommandNavy,
    dutyBlue = JrxpColors.DutyBlue,
    dispatchBlue = JrxpColors.DispatchBlue,
    paleBlue = JrxpColors.PaleBlue,
    ledgerPaper = JrxpColors.LedgerPaper,
    readingSurface = JrxpColors.ReadingSurface,
    mutedSurface = JrxpColors.MutedSurface,
    inkPrimary = JrxpColors.InkPrimary,
    inkSecondary = JrxpColors.InkSecondary,
    inkTertiary = JrxpColors.InkTertiary,
    ruleLine = JrxpColors.RuleLine,
    softDivider = JrxpColors.SoftDivider,
    focusedBorder = JrxpColors.FocusedBorder,
    supplyGreen = JrxpColors.SupplyGreen,
    warningAmber = JrxpColors.WarningAmber,
    criticalRed = JrxpColors.CriticalRed,
    cancelledGray = JrxpColors.CancelledGray,
    ledgerGold = JrxpColors.LedgerGold,
    supplyGreenBg = JrxpColors.SupplyGreenBg,
    warningAmberBg = JrxpColors.WarningAmberBg,
    criticalRedBg = JrxpColors.CriticalRedBg,
    paleBlueBg = JrxpColors.PaleBlueBg,
)

val DarkExtendedColors = JrxpExtendedColors(
    commandNavy = JrxpColors.DispatchBlue,
    dutyBlue = Color(0xFF4A8BC2),
    dispatchBlue = Color(0xFF6AA4D4),
    paleBlue = Color(0xFF1E3448),
    ledgerPaper = JrxpColors.DarkBackground,
    readingSurface = JrxpColors.DarkSurface,
    mutedSurface = JrxpColors.DarkSurfaceHigh,
    inkPrimary = JrxpColors.DarkInkPrimary,
    inkSecondary = JrxpColors.DarkInkSecondary,
    inkTertiary = Color(0xFF787E86),
    ruleLine = Color(0xFF3D444C),
    softDivider = Color(0xFF2E343A),
    focusedBorder = Color(0xFF5E84A8),
    supplyGreen = Color(0xFF5CAA7F),
    warningAmber = Color(0xFFC9923E),
    criticalRed = Color(0xFFD4534B),
    cancelledGray = Color(0xFF8A929A),
    ledgerGold = Color(0xFFC9A05C),
    supplyGreenBg = Color(0xFF1A2E22),
    warningAmberBg = Color(0xFF2E2418),
    criticalRedBg = Color(0xFF3D2020),
    paleBlueBg = Color(0xFF1A2838),
)

val LocalJrxpColors = staticCompositionLocalOf { LightExtendedColors }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JrxpDarkColorScheme else JrxpLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalJrxpColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JrxpTypography,
            content = content
        )
    }
}

// 方便访问扩展颜色的扩展属性
object JrxpTheme {
    val colors: JrxpExtendedColors
        @Composable get() = LocalJrxpColors.current
}
