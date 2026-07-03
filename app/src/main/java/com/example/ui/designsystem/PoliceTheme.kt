package com.smartprocurement.internal.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

object PoliceBrandConfig {
    const val appName = "景荣鲜配"
    const val departmentName = "XX公安局"
    const val systemShortName = "后勤食材采购配送系统"
    const val systemName = "XX公安局后勤食材采购配送系统"
    const val internalUseLabel = "公安内部使用"
    const val logisticsSubtitle = "XX公安局后勤保障"
    const val forceLightTheme = true
}

private val PoliceLightColorScheme = lightColorScheme(
    primary = PoliceColors.PolicePrimary,
    onPrimary = PoliceColors.TextOnBlue,
    primaryContainer = PoliceColors.PoliceLight,
    onPrimaryContainer = PoliceColors.PoliceNavy,
    secondary = PoliceColors.PoliceActionBlue,
    onSecondary = PoliceColors.TextOnBlue,
    secondaryContainer = PoliceColors.PoliceLight,
    onSecondaryContainer = PoliceColors.PoliceNavy,
    background = PoliceColors.PageBackground,
    onBackground = PoliceColors.TextPrimary,
    surface = PoliceColors.SurfaceWhite,
    onSurface = PoliceColors.TextPrimary,
    surfaceVariant = PoliceColors.SurfaceMuted,
    onSurfaceVariant = PoliceColors.TextSecondary,
    outline = PoliceColors.BorderColor,
    outlineVariant = PoliceColors.DividerColor,
    error = PoliceColors.StatusError,
    errorContainer = PoliceColors.StatusErrorBackground,
    onErrorContainer = PoliceColors.StatusError
)

@Composable
fun PoliceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PoliceLightColorScheme,
        typography = PoliceTypography.Material,
        content = content
    )
}
