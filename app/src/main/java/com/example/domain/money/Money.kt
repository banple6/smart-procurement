package com.smartprocurement.internal.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

object Money {
    fun yuanTextToCents(value: String): Long {
        if (value.isBlank()) return 0L
        return BigDecimal(value.trim())
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun yuanDoubleToCents(value: Double): Long {
        return BigDecimal.valueOf(value)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun centsToYuan(cents: Long): Double = BigDecimal(cents)
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        .toDouble()

    fun formatCents(cents: Long): String {
        val amount = BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return "¥" + DecimalFormat("0.00").format(amount)
    }

    fun formatYuan(value: Double): String = formatCents(yuanDoubleToCents(value))
}
