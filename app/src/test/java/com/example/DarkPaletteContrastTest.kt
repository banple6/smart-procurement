package com.smartprocurement.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.smartprocurement.internal.ui.theme.JrxpDarkColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class DarkPaletteContrastTest {
    @Test
    fun dark_palette_keeps_text_and_actions_readable() {
        assertContrast("background text", JrxpDarkColorScheme.background, JrxpDarkColorScheme.onBackground, 7.0)
        assertContrast("surface text", JrxpDarkColorScheme.surface, JrxpDarkColorScheme.onSurface, 7.0)
        assertContrast("secondary text", JrxpDarkColorScheme.surfaceVariant, JrxpDarkColorScheme.onSurfaceVariant, 4.5)
        assertContrast("primary action", JrxpDarkColorScheme.background, JrxpDarkColorScheme.primary, 4.5)
        assertContrast("primary container", JrxpDarkColorScheme.primaryContainer, JrxpDarkColorScheme.onPrimaryContainer, 4.5)
        assertContrast("error container", JrxpDarkColorScheme.errorContainer, JrxpDarkColorScheme.onErrorContainer, 4.5)
    }

    private fun assertContrast(label: String, background: Color, foreground: Color, minimum: Double) {
        val ratio = contrastRatio(background, foreground)
        assertTrue("$label contrast $ratio is below $minimum", ratio >= minimum)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        val red = channel((argb shr 16) and 0xFF)
        val green = channel((argb shr 8) and 0xFF)
        val blue = channel(argb and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
}
