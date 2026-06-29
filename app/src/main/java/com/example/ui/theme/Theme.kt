package com.smartprocurement.internal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = LogiPrimary,
    primaryContainer = LogiPrimaryContainer,
    onPrimaryContainer = LogiOnPrimaryContainer,
    secondary = LogiSecondary,
    secondaryContainer = LogiSecondaryContainer,
    onSecondaryContainer = LogiOnSecondaryContainer,
    tertiary = LogiTertiary,
    background = LogiBackground,
    surface = LogiSurface,
    surfaceVariant = LogiSurfaceVariant,
    onPrimary = LogiOnPrimary,
    onSecondary = LogiOnSecondary,
    onBackground = LogiOnSurface,
    onSurface = LogiOnSurface,
    onSurfaceVariant = LogiOnSurfaceVariant,
    outline = LogiOutline,
    outlineVariant = LogiOutlineVariant,
    error = LogiError,
    errorContainer = LogiErrorContainer,
    onErrorContainer = LogiOnErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    // Set default dynamicColor to false to preserve brand styles
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
