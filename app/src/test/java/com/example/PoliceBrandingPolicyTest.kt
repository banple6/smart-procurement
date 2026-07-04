package com.smartprocurement.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class PoliceBrandingPolicyTest {
    private val root = File(System.getProperty("user.dir"))

    @Test
    fun brand_strings_use_police_internal_names() {
        val strings = File(root, "src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"app_name\">景荣鲜配</string>"))
        assertTrue(strings.contains("<string name=\"police_department_name\">XX公安局</string>"))
        assertTrue(strings.contains("<string name=\"system_full_name\">XX公安局后勤食材采购配送系统</string>"))
        assertTrue(strings.contains("<string name=\"internal_use_label\">公安内部使用</string>"))
    }

    @Test
    fun badge_resources_are_static_png_and_keep_original_ratio() {
        val originalRatio = 167.36 / 176.36
        listOf(
            "police_badge_large.png" to Pair(972, 1024),
            "police_badge.png" to Pair(486, 512),
            "police_badge_small.png" to Pair(243, 256),
        ).forEach { (name, expected) ->
            val image = ImageIO.read(File(root, "src/main/res/drawable-nodpi/$name"))
            assertEquals(expected.first, image.width)
            assertEquals(expected.second, image.height)
            val actualRatio = image.width.toDouble() / image.height.toDouble()
            assertTrue(kotlin.math.abs(actualRatio - originalRatio) < 0.002)
        }
    }

    @Test
    fun launcher_uses_police_badge_foreground_on_navy_background() {
        val adaptive = File(root, "src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val round = File(root, "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val background = File(root, "src/main/res/drawable/ic_launcher_background.xml").readText()

        assertTrue(adaptive.contains("@drawable/ic_launcher_police_foreground"))
        assertTrue(round.contains("@drawable/ic_launcher_police_foreground"))
        assertTrue(background.contains("#0B2864"))
    }

    @Test
    fun old_green_android_launcher_assets_are_removed() {
        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { dpi ->
            assertTrue(File(root, "src/main/res/mipmap-$dpi/ic_launcher.png").exists())
            assertTrue(File(root, "src/main/res/mipmap-$dpi/ic_launcher_round.png").exists())
            assertTrue(!File(root, "src/main/res/mipmap-$dpi/ic_launcher.webp").exists())
            assertTrue(!File(root, "src/main/res/mipmap-$dpi/ic_launcher_round.webp").exists())
        }
    }
}
