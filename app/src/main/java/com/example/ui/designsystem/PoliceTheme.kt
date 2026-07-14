package com.smartprocurement.internal.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

object PoliceBrandConfig {
    const val appName = "三公鲜配"
    const val departmentName = "单位信息未配置"
    const val systemShortName = "食材申领与配送平台"
    const val systemName = "单位食材申领与配送协同平台"
    const val internalUseLabel = "内部授权使用"
    const val logisticsSubtitle = "三公鲜配管理端"
    const val forceLightTheme = false
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

private val PoliceDarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF78B7EA),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF062844),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF153B5B),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD2E9FC),
    secondary = androidx.compose.ui.graphics.Color(0xFF9CC9ED),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF0A2B45),
    background = androidx.compose.ui.graphics.Color(0xFF11161B),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE8EDF2),
    surface = androidx.compose.ui.graphics.Color(0xFF191F25),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8EDF2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF222A31),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB7C0CA),
    outline = androidx.compose.ui.graphics.Color(0xFF53606C),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF303943),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF3D2020),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
)

@Composable
fun PoliceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) PoliceDarkColorScheme else PoliceLightColorScheme,
        typography = PoliceTypography.Material,
        content = content
    )
}
