package com.smartprocurement.internal

import androidx.compose.ui.graphics.toArgb
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDimens
import com.smartprocurement.internal.ui.designsystem.GovernmentShapes
import com.smartprocurement.internal.ui.designsystem.GovernmentStatus
import com.smartprocurement.internal.ui.designsystem.GovernmentThemeDefaults
import com.smartprocurement.internal.ui.designsystem.GovernmentTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GovernmentDesignSystemTest {
    @Test
    fun government_design_tokens_match_required_values() {
        assertEquals("三公鲜配", GovernmentThemeDefaults.appName)
        assertTrue(GovernmentThemeDefaults.forceLightTheme)

        assertEquals(0xFF123F82.toInt(), GovernmentColors.GovernmentBlue.toArgb())
        assertEquals(0xFFF3F5F8.toInt(), GovernmentColors.PageBackground.toArgb())
        assertEquals(0xFF172233.toInt(), GovernmentColors.TextPrimary.toArgb())
        assertEquals(0xFFC62828.toInt(), GovernmentColors.StatusError.toArgb())

        assertEquals(22f, GovernmentTypography.PageTitleSize.value)
        assertEquals(16f, GovernmentTypography.BodySize.value)
        assertEquals(14f, GovernmentTypography.SecondaryBodySize.value)

        assertEquals(4f, GovernmentShapes.SmallRadius.value)
        assertEquals(8f, GovernmentShapes.MediumRadius.value)
        assertEquals(10f, GovernmentShapes.LargeRadius.value)
        assertEquals(52f, GovernmentDimens.PrimaryButtonHeight.value)
        assertEquals(48f, GovernmentDimens.MinTouchTarget.value)
    }

    @Test
    fun status_styles_include_visible_chinese_text() {
        val statuses = listOf("pending", "accepted", "preparing", "shipped", "completed", "cancelled")
        statuses.forEach { status ->
            val style = GovernmentStatus.fromApiStatus(status)
            assertFalse(style.label.isBlank())
            assertTrue(style.label.any { it.code > 127 })
        }
        assertEquals("待接单", GovernmentStatus.fromUiStatus("待接单").label)
        assertEquals("库存紧张", GovernmentStatus.fromUiStatus("库存紧张").label)
    }
}
