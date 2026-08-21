package com.smartprocurement.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.smartprocurement.internal.ui.theme.MyApplicationTheme
import com.smartprocurement.internal.ui.thinkingorb.ThinkingOrb
import com.smartprocurement.internal.ui.thinkingorb.ThinkingOrbState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ThinkingOrbScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun thinking_orbs_static_showcase_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.background(Color(0xFF102A56)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ThinkingOrbState.entries.forEach { state ->
                        ThinkingOrb(state, 64.dp, debugTimeSeconds = .6)
                        ThinkingOrb(state, 20.dp, debugTimeSeconds = .6)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/thinking-orbs-static.png")
    }
}
