package com.smartprocurement.internal

import com.smartprocurement.internal.ui.components.formatQuantity
import com.smartprocurement.internal.ui.components.isQuantityOnStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityStepperValidationTest {
    @Test
    fun accepts_decimal_quantities_on_the_configured_minimum_and_step() {
        assertTrue(isQuantityOnStep(0.5, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(2.0, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(1.25, minValue = 0.25, step = 0.25))
    }

    @Test
    fun rejects_values_below_the_minimum_or_between_steps_without_rounding() {
        assertFalse(isQuantityOnStep(0.25, minValue = 0.5, step = 0.5))
        assertFalse(isQuantityOnStep(1.25, minValue = 0.5, step = 0.5))
        assertEquals("0.5", formatQuantity(0.5))
    }
}
