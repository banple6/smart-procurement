package com.smartprocurement.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.smartprocurement.internal.ui.components.JrxpPrimaryButton
import com.smartprocurement.internal.ui.components.MenuActionRow
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentStatusLabel
import com.smartprocurement.internal.ui.designsystem.PoliceIdentityHeader
import com.smartprocurement.internal.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-night-xxhdpi", sdk = [36])
class DarkThemeScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shared_admin_surfaces_render_in_dark_mode() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PoliceIdentityHeader(
                        title = "系统管理员",
                        line1 = "账号：sangong_admin01",
                        line2 = "三公鲜配管理端"
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GovernmentCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("订单概览", fontWeight = FontWeight.Bold)
                                GovernmentDataRow("今日订单", "12")
                                GovernmentDataRow("今日金额", "¥1,268.00")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GovernmentStatusLabel("待接单")
                                    GovernmentStatusLabel("备货中")
                                    GovernmentStatusLabel("已完成")
                                }
                            }
                        }
                        GovernmentCard {
                            Column {
                                MenuActionRow(Icons.Default.Home, "子单位管理") {}
                                MenuActionRow(Icons.Default.Person, "账号管理") {}
                                MenuActionRow(Icons.Default.Notifications, "订单通知", trailingText = "已开启") {}
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        JrxpPrimaryButton(text = "查看待接单订单", onClick = {})
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/dark-theme-components.png"
        )
    }
}
