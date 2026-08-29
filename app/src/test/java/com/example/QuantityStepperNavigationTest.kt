package com.smartprocurement.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.smartprocurement.internal.ui.components.QuantityStepper
import com.smartprocurement.internal.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuantityStepperNavigationTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun direct_quantity_input_is_committed_when_the_screen_changes() {
        var visible by mutableStateOf(true)
        var committedValue = 0.0

        composeTestRule.setContent {
            MyApplicationTheme {
                if (visible) {
                    QuantityStepper(
                        value = 0.0,
                        unit = "斤",
                        minValue = 1.0,
                        step = 1.0,
                        onValueChange = { committedValue = it }
                    )
                }
            }
        }

        val quantityInput = composeTestRule.onNode(hasSetTextAction())
        quantityInput.performTextClearance()
        quantityInput.performTextInput("100")
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.waitForIdle()

        assertEquals(100.0, committedValue, 0.0)
    }
}
