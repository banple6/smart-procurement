package com.smartprocurement.internal.domain.validation

import java.math.BigDecimal

data class CartProductSnapshot(
    val availableQuantity: String,
    val minQty: Double,
    val stepQty: Double,
    val priceCents: Long,
    val status: String,
    val isAvailable: Boolean,
    val isDeleted: Boolean
)

data class CartValidationResult(
    val isValid: Boolean,
    val message: String = ""
)

object CartValidator {
    fun canOrder(product: CartProductSnapshot, quantity: Double): CartValidationResult {
        if (product.isDeleted) return CartValidationResult(false, "该食材已删除，不能加入清单")
        if (!product.isAvailable || product.status == "已下架") return CartValidationResult(false, "该食材已下架，不能加入清单")
        if (product.status == "暂停供应") return CartValidationResult(false, "该食材暂停供应，不能加入清单")
        if (product.priceCents <= 0) return CartValidationResult(false, "价格未设置")

        val requested = BigDecimal.valueOf(quantity)
        val available = product.availableQuantity.ifBlank { "0" }.toDecimalOrZero()
        val minimum = BigDecimal.valueOf(product.minQty)
        val step = BigDecimal.valueOf(product.stepQty)
        if (available <= BigDecimal.ZERO || requested > available) return CartValidationResult(false, "库存不足，请减少数量")
        if (requested < minimum) return CartValidationResult(false, "低于最小申领量")
        if (step <= BigDecimal.ZERO) return CartValidationResult(false, "数量步长配置错误")
        if ((requested.subtract(minimum)).remainder(step).compareTo(BigDecimal.ZERO) != 0) {
            return CartValidationResult(false, "数量不符合申领步长")
        }
        return CartValidationResult(true)
    }

    private fun String.toDecimalOrZero(): BigDecimal = runCatching { BigDecimal(trim()) }.getOrDefault(BigDecimal.ZERO)
}
