package com.smartprocurement.internal

import androidx.compose.ui.graphics.toArgb
import com.smartprocurement.internal.ui.designsystem.PoliceBrandConfig
import com.smartprocurement.internal.ui.designsystem.PoliceColors
import com.smartprocurement.internal.ui.designsystem.PoliceDimens
import com.smartprocurement.internal.ui.designsystem.PoliceShapes
import com.smartprocurement.internal.ui.designsystem.PoliceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class PoliceBrandingPolicyTest {
    @Test
    fun police_brand_tokens_match_required_values() {
        assertEquals("景荣鲜配", PoliceBrandConfig.appName)
        assertEquals("XX公安局", PoliceBrandConfig.departmentName)
        assertEquals("XX公安局后勤食材采购配送系统", PoliceBrandConfig.systemName)
        assertEquals("公安内部使用", PoliceBrandConfig.internalUseLabel)

        assertEquals(0xFF152584.toInt(), PoliceColors.PoliceBadgeBlue.toArgb())
        assertEquals(0xFF0B2864.toInt(), PoliceColors.PoliceNavy.toArgb())
        assertEquals(0xFF123F82.toInt(), PoliceColors.PolicePrimary.toArgb())
        assertEquals(0xFF175AA6.toInt(), PoliceColors.PoliceActionBlue.toArgb())
        assertEquals(0xFFF3F5F8.toInt(), PoliceColors.PageBackground.toArgb())
        assertEquals(0xFF247548.toInt(), PoliceColors.StatusCompleted.toArgb())

        assertEquals(8f, PoliceShapes.CardRadius.value)
        assertEquals(52f, PoliceDimens.PrimaryButtonHeight.value)
        assertEquals(48f, PoliceDimens.MinTouchTarget.value)
    }

    @Test
    fun police_badge_assets_exist_and_keep_original_ratio() {
        val master = File("../design-assets/Police_Badge_of_China.svg")
        assertTrue("SVG master should be checked into design-assets", master.isFile)

        val normal = File("src/main/res/drawable-nodpi/police_badge.png")
        val large = File("src/main/res/drawable-nodpi/police_badge_large.png")
        val small = File("src/main/res/drawable-nodpi/police_badge_small.png")
        listOf(normal, large, small).forEach { file ->
            assertTrue("${file.path} should exist", file.isFile)
            val image = ImageIO.read(file)
            val ratio = image.width.toDouble() / image.height.toDouble()
            val expectedRatio = 167.36 / 176.36
            assertEquals(expectedRatio, ratio, 0.01)
        }
    }

    @Test
    fun launcher_icon_uses_business_foreground_without_police_badge() {
        val adaptiveIcon = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val roundIcon = File("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val background = File("src/main/res/drawable/ic_launcher_background.xml").readText()
        assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_foreground"))
        assertTrue(roundIcon.contains("@drawable/ic_launcher_foreground"))
        assertTrue(!adaptiveIcon.contains("police"))
        assertTrue(!roundIcon.contains("police"))
        assertTrue(background.contains("#0B2864"))
    }

    @Test
    fun police_badge_only_appears_on_android_login_not_splash_or_profile_headers() {
        val splash = File("src/main/java/com/example/ui/SupplyApp.kt").readText()
        val login = File("src/main/java/com/example/ui/AuthScreens.kt").readText()
        val components = File("src/main/java/com/example/ui/designsystem/PoliceComponents.kt").readText()

        assertTrue(!splash.contains("PoliceBadgeImage"))
        assertTrue(login.contains("PoliceBadgeImage(size = 56.dp"))
        assertTrue(!components.substringAfter("fun PoliceBrandHeader").substringBefore("@Composable\nfun PoliceIdentityHeader").contains("PoliceBadgeImage"))
        assertTrue(!components.substringAfter("fun PoliceIdentityHeader").substringBefore("@Composable\nfun PolicePrimaryButton").contains("PoliceBadgeImage"))
    }

    @Test
    fun status_colors_do_not_turn_business_states_into_brand_blue() {
        assertEquals("已完成", PoliceStatus.fromUiStatus("已完成").label)
        assertEquals(PoliceColors.StatusCompleted.toArgb(), PoliceStatus.fromUiStatus("已完成").contentColor.toArgb())
        assertEquals("备货中", PoliceStatus.fromUiStatus("备货中").label)
        assertEquals(PoliceColors.StatusPreparing.toArgb(), PoliceStatus.fromUiStatus("备货中").contentColor.toArgb())
    }
}
