package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LightThemePolicyTest {
    @Test
    fun android_theme_disables_force_dark() {
        val themeXml = File("src/main/res/values/themes.xml").readText()

        assertTrue(themeXml.contains("forceDarkAllowed"))
        assertTrue(themeXml.contains("false"))
    }
}
