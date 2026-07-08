package com.smartprocurement.internal.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

object PoliceBrandConfig {
    const val appName = "三公鲜配"
    const val departmentName = "单位信息未配置"
    const val systemShortName = "食材申领与配送平台"
    const val systemName = "单位食材申领与配送协同平台"
    const val internalUseLabel = "内部授权使用"
    const val logisticsSubtitle = "三公鲜配管理端"
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
