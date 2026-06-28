package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LogiPrimary,
    primaryContainer = LogiPrimaryContainer,
    onPrimaryContainer = LogiOnPrimaryContainer,
    secondary = LogiSecondary,
    secondaryContainer = LogiSecondaryContainer,
    onSecondaryContainer = LogiOnSecondaryContainer,
    tertiary = LogiTertiary,
    background = LogiOnSurface,
    surface = LogiOnSurface,
    surfaceVariant = LogiSurfaceVariant,
    onPrimary = LogiOnPrimary,
    onSecondary = LogiOnSecondary,
    onBackground = LogiBackground,
    onSurface = LogiBackground,
    onSurfaceVariant = LogiOnSurfaceVariant,
    outline = LogiOutline,
    outlineVariant = LogiOutlineVariant,
    error = LogiError,
    errorContainer = LogiErrorContainer
)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set default dynamicColor to false to preserve brand styles
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
