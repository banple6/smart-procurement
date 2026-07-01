package com.smartprocurement.internal.ui.theme

import androidx.compose.runtime.Composable
import com.smartprocurement.internal.ui.designsystem.GovernmentTheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    // Set default dynamicColor to false to preserve brand styles
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GovernmentTheme(content = content)
}
