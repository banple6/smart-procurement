package com.smartprocurement.internal.domain.validation

import com.smartprocurement.internal.domain.money.Money
import java.math.BigDecimal

enum class InventoryAdjustMode(val apiValue: String, val label: String) {
    SET("set", "设置调整后库存"),
    INCREASE("increase", "增加入库数量"),
    DECREASE("decrease", "减少库存数量")
}

data class PriceValidationResult(
    val isValid: Boolean,
    val message: String = "",
    val cents: Long = 0
)

data class InventoryValidationResult(
    val isValid: Boolean,
    val message: String = "",
    val afterQuantity: String = ""
)

object ProductQuickActionValidator {
    private val moneyPattern = Regex("^\\d+(\\.\\d{1,2})?$")
    private val quantityPattern = Regex("^\\d+(\\.\\d{1,3})?$")
    private val maxPriceCents = 99_999_999L

    fun validatePrice(priceText: String, reason: String, currentPriceCents: Long): PriceValidationResult {
        if (reason.trim().isBlank()) return PriceValidationResult(false, "请填写价格调整原因")
        val value = priceText.trim()
        if (value.isBlank()) return PriceValidationResult(false, "请输入新价格")
        if (!moneyPattern.matches(value)) return PriceValidationResult(false, "价格最多保留两位小数")
        val cents = runCatching { Money.yuanTextToCents(value) }.getOrNull()
            ?: return PriceValidationResult(false, "价格格式不正确")
        if (cents <= 0) return PriceValidationResult(false, "价格必须大于 0")
        if (cents > maxPriceCents) return PriceValidationResult(false, "价格不能超过 999999.99 元")
        if (cents == currentPriceCents) return PriceValidationResult(false, "新价格必须和当前价格不同")
        return PriceValidationResult(true, cents = cents)
    }

    fun validateInventory(
        mode: InventoryAdjustMode,
        quantityText: String,
        reason: String,
        currentStock: String,
        reservedStock: String
    ): InventoryValidationResult {
        if (reason.trim().isBlank()) return InventoryValidationResult(false, "请填写库存调整原因")
        val value = quantityText.trim()
        if (value.isBlank()) return InventoryValidationResult(false, "请输入库存数量")
        if (!quantityPattern.matches(value)) return InventoryValidationResult(false, "库存最多保留三位小数")
        val quantity = value.toDecimalOrNull() ?: return InventoryValidationResult(false, "库存数量格式不正确")
        val current = currentStock.toDecimalOrNull() ?: BigDecimal.ZERO
        val reserved = reservedStock.toDecimalOrNull() ?: BigDecimal.ZERO
        if (quantity < BigDecimal.ZERO) return InventoryValidationResult(false, "库存数量不能小于 0")
        val after = when (mode) {
            InventoryAdjustMode.SET -> quantity
            InventoryAdjustMode.INCREASE -> current + quantity
            InventoryAdjustMode.DECREASE -> current - quantity
        }
        if (after < BigDecimal.ZERO) return InventoryValidationResult(false, "库存数量不能小于 0")
        if (after < reserved) return InventoryValidationResult(false, "库存不能小于已预占库存")
        return InventoryValidationResult(true, afterQuantity = after.toPlainQuantity())
    }

    private fun String.toDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()

    private fun BigDecimal.toPlainQuantity(): String {
        val text = stripTrailingZeros().toPlainString()
        return if (text == "-0") "0" else text
    }
}
