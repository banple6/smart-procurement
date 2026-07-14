package com.smartprocurement.internal.domain.quantity

import java.math.BigDecimal

object QuantityFormatter {
    fun format(value: String, fallback: String = "0"): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return fallback
        return runCatching {
            val number = BigDecimal(trimmed)
            if (number.compareTo(BigDecimal.ZERO) == 0) {
                "0"
            } else {
                number.stripTrailingZeros().toPlainString()
            }
        }.getOrElse { trimmed }
    }
}
