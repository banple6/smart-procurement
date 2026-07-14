package com.smartprocurement.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DarkThemePolicyTest {
    @Test
    fun app_shell_uses_theme_aware_system_and_navigation_surfaces() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        val appShell = File("src/main/java/com/example/ui/SupplyApp.kt").readText()
        val profile = File("src/main/java/com/example/ui/Screens.kt").readText()

        assertFalse(activity.contains("SystemBarStyle.light"))
        assertFalse(appShell.contains("containerColor = Color.White"))
        assertFalse(appShell.contains("containerColor = JrxpColors.PureSurface"))
        assertFalse(profile.contains(".background(JrxpColors.PureSurface)"))
    }

    @Test
    fun legacy_design_system_supports_dark_mode() {
        val policeTheme = File("src/main/java/com/example/ui/designsystem/PoliceTheme.kt").readText()
        val defaults = File("src/main/java/com/example/ui/designsystem/GovernmentTheme.kt").readText()

        assertTrue(policeTheme.contains("darkColorScheme"))
        assertTrue(policeTheme.contains("isSystemInDarkTheme"))
        assertTrue(defaults.contains("const val forceLightTheme = false"))
    }

    @Test
    fun high_traffic_screens_do_not_pin_light_backgrounds_or_text() {
        val files = listOf(
            "src/main/java/com/example/ui/HomeAndDetail.kt",
            "src/main/java/com/example/ui/CartAndOrder.kt",
            "src/main/java/com/example/ui/ExtraScreens.kt",
            "src/main/java/com/example/ui/WebLoginScreens.kt",
        )
        val forbidden = listOf(
            "JrxpColors.PureSurface",
            "JrxpColors.InkPrimary",
            "JrxpColors.InkSecondary",
            "GovernmentColors.PageBackground",
            "GovernmentColors.SurfaceWhite",
            "GovernmentColors.TextPrimary",
            "GovernmentColors.TextSecondary",
        )

        files.forEach { path ->
            val source = File(path).readText()
            forbidden.forEach { token ->
                assertFalse("$path still pins $token", source.contains(token))
            }
        }
    }
}
