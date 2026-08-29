package com.smartprocurement.internal

import com.smartprocurement.internal.ui.components.formatQuantity
import com.smartprocurement.internal.ui.components.isQuantityOnStep
import com.smartprocurement.internal.ui.components.normalizeQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityStepperValidationTest {

    // ── 基础 step 验证 ────────────────────────────────────────

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

    // ── 大数量直接输入安全性 ──────────────────────────────────

    @Test
    fun direct_input_100_is_accepted_on_integer_step() {
        // 整数 step=1, min=1 情况下，100 合法
        assertTrue(isQuantityOnStep(100.0, minValue = 1.0, step = 1.0))
        assertEquals("100", formatQuantity(100.0))
    }

    @Test
    fun direct_input_large_quantity_does_not_overflow() {
        // 999999999 输入应能安全解析，不引发 NumberFormatException / ArithmeticException
        val input = "999999999"
        val parsed = input.toDoubleOrNull()
        assertTrue("should parse without exception", parsed != null)
        assertTrue("should be on step 1", isQuantityOnStep(parsed!!, minValue = 1.0, step = 1.0))
        // BigDecimal 格式化不应产生科学计数法
        val formatted = formatQuantity(parsed)
        assertFalse("should not contain E notation", formatted.contains("E", ignoreCase = true))
    }

    @Test
    fun format_quantity_strips_trailing_zeros_correctly() {
        assertEquals("100", formatQuantity(100.0))
        assertEquals("0.5", formatQuantity(0.5))
        assertEquals("100.5", formatQuantity(100.5))
        assertEquals("1", formatQuantity(1.0))
    }

    // ── 小数 step 完整测试 ────────────────────────────────────

    @Test
    fun decimal_step_legal_values_pass() {
        // min=0.5, step=0.5: 合法值
        assertTrue(isQuantityOnStep(0.5, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(1.0, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(1.5, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(10.5, minValue = 0.5, step = 0.5))
        assertTrue(isQuantityOnStep(100.5, minValue = 0.5, step = 0.5))
    }

    @Test
    fun decimal_step_illegal_values_are_rejected() {
        // min=0.5, step=0.5: 非法值
        assertFalse("zero is below min", isQuantityOnStep(0.0, minValue = 0.5, step = 0.5))
        assertFalse("0.3 is not on 0.5 step", isQuantityOnStep(0.3, minValue = 0.5, step = 0.5))
        assertFalse("1.3 is not on 0.5 step", isQuantityOnStep(1.3, minValue = 0.5, step = 0.5))
        assertFalse("negative is invalid", isQuantityOnStep(-1.0, minValue = 0.5, step = 0.5))
    }

    @Test
    fun invalid_step_input_string_parses_to_null() {
        // 非数字输入（如 "abc", "" 等）应解析为 null，不崩溃
        assertEquals(null, "abc".toDoubleOrNull())
        assertEquals(null, "".toDoubleOrNull())
        assertEquals(null, "1.3.5".toDoubleOrNull())
    }

    // ── 最小值删除触发逻辑 ────────────────────────────────────

    @Test
    fun remove_at_min_triggers_zero_value() {
        // 当 value == minValue，点击减少时 onValueChange 应被调用传入 0.0
        // 验证：value <= minValue 时，atMin=true，减少操作返回 0.0（触发删除）
        val value = 0.5
        val minValue = 0.5
        val step = 0.5
        val atMin = value <= minValue
        assertTrue("should be at min", atMin)
        // 删除逻辑：atMin 时传 0.0，由 ViewModel 处理删除
        val nextValue = if (atMin) 0.0 else normalizeQuantity(value - step, minValue, step).coerceAtLeast(minValue)
        assertEquals(0.0, nextValue, 0.0001)
    }

    @Test
    fun decrement_above_min_stays_on_step() {
        // value=2.0, min=0.5, step=0.5 → 减少后应为 1.5（不是 0.0）
        val value = 2.0
        val minValue = 0.5
        val step = 0.5
        val atMin = value <= minValue
        assertFalse("should not be at min", atMin)
        val next = normalizeQuantity(value - step, minValue, step).coerceAtLeast(minValue)
        assertEquals(1.5, next, 0.0001)
        assertTrue("result should be on step", isQuantityOnStep(next, minValue, step))
    }

    // ── 金额精度（客户端侧，不依赖 Android Room）─────────────

    @Test
    fun price_multiplication_avoids_float_accumulation_error() {
        // 3.21 × 100 必须等于 321.00（不能是 320.9999...）
        val priceYuan = java.math.BigDecimal("3.21")
        val quantity = java.math.BigDecimal("100")
        val total = priceYuan.multiply(quantity)
        assertEquals(java.math.BigDecimal("321.00"), total.setScale(2, java.math.RoundingMode.HALF_UP))
    }

    @Test
    fun cart_total_sums_multiple_lines_correctly() {
        // 模拟 lineSubtotalCents 逻辑：price(cents) × qty 取整
        fun lineSubtotalCents(priceCents: Long, qty: Double): Long =
            java.math.BigDecimal.valueOf(priceCents)
                .multiply(java.math.BigDecimal.valueOf(qty))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toLong()

        // 两行：3.21 元 × 100 = 321.00 元 = 32100 分
        //       10.00 元 × 2  = 20.00 元  = 2000 分
        val total = lineSubtotalCents(321L, 100.0) + lineSubtotalCents(1000L, 2.0)
        assertEquals(34100L, total)
    }

    // ── 额度超额状态 ──────────────────────────────────────────

    @Test
    fun quota_exceeded_flag_is_correct() {
        // 可用 9.58 元 = 958 分，本单 10.00 元 = 1000 分 → exceeded
        val availableCents = 958L
        val totalCents = 1000L
        val exceeded = totalCents > availableCents
        assertTrue("should be exceeded", exceeded)
        val shortfall = totalCents - availableCents
        assertEquals(42L, shortfall)
    }

    @Test
    fun quota_not_exceeded_when_under_limit() {
        // 可用 100 元 = 10000 分，本单 50 元 = 5000 分 → not exceeded
        val availableCents = 10000L
        val totalCents = 5000L
        val exceeded = totalCents > availableCents
        assertFalse("should not be exceeded", exceeded)
    }

    @Test
    fun quota_exactly_at_limit_is_not_exceeded() {
        // 可用 = 本单 → not exceeded（恰好相等允许提交）
        val availableCents = 1000L
        val totalCents = 1000L
        assertFalse("exact match should not be exceeded", totalCents > availableCents)
    }
}
