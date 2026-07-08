package com.smartprocurement.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class PoliceBrandingPolicyTest {
    private val root = File(System.getProperty("user.dir"))

    @Test
    fun brand_strings_use_neutral_product_names() {
        val strings = File(root, "src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"app_name\">三公鲜配</string>"))
        assertTrue(strings.contains("<string name=\"police_department_name\">单位信息未配置</string>"))
        assertTrue(strings.contains("<string name=\"system_full_name\">单位食材申领与配送协同平台</string>"))
        assertTrue(strings.contains("<string name=\"internal_use_label\">内部授权使用</string>"))
        assertTrue(!strings.contains("\u0058\u0058\u516C\u5B89\u5C40"))
        assertTrue(!strings.contains("\u516C\u5B89\u5185\u90E8"))
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
    fun launcher_uses_business_foreground_on_navy_background_without_police_badge() {
        val adaptive = File(root, "src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val round = File(root, "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val background = File(root, "src/main/res/drawable/ic_launcher_background.xml").readText()
        val foreground = File(root, "src/main/res/drawable/ic_launcher_foreground.xml").readText()

        assertTrue(adaptive.contains("@drawable/ic_launcher_foreground"))
        assertTrue(round.contains("@drawable/ic_launcher_foreground"))
        assertTrue(!adaptive.contains("police"))
        assertTrue(!round.contains("police"))
        assertTrue(background.contains("#0B2864"))
        assertTrue(foreground.contains("delivery-box-leaf"))
        assertTrue(!foreground.contains("gradient"))
        assertTrue(!foreground.contains("aapt:attr"))
    }

    @Test
    fun police_badge_is_not_used_by_opening_screens() {
        val uiFiles = File(root, "src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        val badgeReferences = uiFiles
            .filter { it.readText().contains("PoliceBadgeImage(") }
            .map { it.relativeTo(File(root, "src/main/java")).path }
        val openingReferences = uiFiles
            .filter { it.readText().contains("PoliceOpeningBadge(") }
            .map { it.relativeTo(File(root, "src/main/java")).path }

        assertEquals(listOf("com/example/ui/designsystem/PoliceComponents.kt"), badgeReferences.sorted())
        assertEquals(
            listOf(
                "com/example/ui/AuthScreens.kt",
                "com/example/ui/SupplyApp.kt",
                "com/example/ui/designsystem/PoliceComponents.kt",
            ),
            openingReferences.sorted()
        )
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

    @Test
    fun app_has_public_beta_help_tutorial_entry() {
        val screens = File(root, "src/main/java/com/example/ui/Screens.kt").readText()
        val extra = File(root, "src/main/java/com/example/ui/ExtraScreens.kt").readText()
        val viewModel = File(root, "src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(screens.contains("帮助与教程"))
        assertTrue(extra.contains("HelpTutorialScreen"))
        assertTrue(extra.contains("管理员公测上线操作指引"))
        assertTrue(extra.contains("子单位食材申领操作指引"))
        assertTrue(viewModel.contains("navigateTo(Screen.OnboardingGuide)"))
    }
}
