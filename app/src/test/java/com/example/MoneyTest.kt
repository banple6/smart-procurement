package com.smartprocurement.internal

import com.smartprocurement.internal.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun converts_yuan_input_to_cents_with_big_decimal_rounding() {
        assertEquals(450L, Money.yuanTextToCents("4.50"))
        assertEquals(1L, Money.yuanTextToCents("0.01"))
        assertEquals(101L, Money.yuanTextToCents("1.005"))
    }

    @Test
    fun formats_cents_as_chinese_yuan_amount() {
        assertEquals("¥0.00", Money.formatCents(0))
        assertEquals("¥4.50", Money.formatCents(450))
        assertEquals("¥12.03", Money.formatCents(1203))
    }
}
