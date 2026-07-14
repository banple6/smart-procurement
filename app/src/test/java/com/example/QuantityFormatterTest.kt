package com.smartprocurement.internal

import com.smartprocurement.internal.domain.quantity.QuantityFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityFormatterTest {
    @Test
    fun scientific_notation_is_rendered_as_plain_decimal() {
        assertEquals("20", QuantityFormatter.format("2E+1"))
        assertEquals("200", QuantityFormatter.format("2E+2"))
        assertEquals("20.5", QuantityFormatter.format("20.500"))
        assertEquals("0", QuantityFormatter.format("-0.000"))
    }

    @Test
    fun invalid_server_value_is_preserved_for_diagnostics() {
        assertEquals("--", QuantityFormatter.format("--"))
        assertEquals("0", QuantityFormatter.format("   "))
    }
}
