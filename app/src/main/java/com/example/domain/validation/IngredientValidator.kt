package com.smartprocurement.internal.domain.validation

import java.math.BigDecimal

data class IngredientFormInput(
    val name: String,
    val category: String,
    val spec: String,
    val unit: String,
    val minOrderQuantity: String,
    val quantityStep: String,
    val stockQuantity: String,
    val warningQuantity: String,
    val availableQuantity: String,
    val internalPrice: String
)

object IngredientValidator {
    fun validate(input: IngredientFormInput): ValidationResult {
        val errors = linkedMapOf<String, String>()
        if (input.name.isBlank()) errors["name"] = "食材名称不能为空"
        if (input.category.isBlank()) errors["category"] = "食材分类不能为空"
        if (input.spec.isBlank()) errors["spec"] = "规格不能为空"
        if (input.unit.isBlank()) errors["unit"] = "计量单位不能为空"
        requirePositive(input.minOrderQuantity, "minOrderQuantity", "最小申领量必须大于 0", errors)
        requirePositive(input.quantityStep, "quantityStep", "数量步长必须大于 0", errors)
        requireNonNegative(input.stockQuantity, "stockQuantity", "当前库存不能小于 0", errors)
        requireNonNegative(input.warningQuantity, "warningQuantity", "库存预警值不能小于 0", errors, allowBlank = true)
        requireNonNegative(input.availableQuantity, "availableQuantity", "今日可供数量不能小于 0", errors, allowBlank = true)
        requireNonNegative(input.internalPrice, "internalPrice", "内部参考价不能小于 0", errors, allowBlank = true)
        val stock = input.stockQuantity.toDecimalOrNull()
        val available = input.availableQuantity.toDecimalOrNull()
        if (!errors.containsKey("availableQuantity") && stock != null && available != null && available > stock) {
            errors["availableQuantity"] = "今日可供数量不能大于当前库存"
        }
        return ValidationResult(errors)
    }

    private fun requirePositive(value: String, key: String, message: String, errors: MutableMap<String, String>) {
        val decimal = value.toDecimalOrNull()
        if (decimal == null || decimal <= BigDecimal.ZERO) errors[key] = message
    }

    private fun requireNonNegative(
        value: String,
        key: String,
        message: String,
        errors: MutableMap<String, String>,
        allowBlank: Boolean = false
    ) {
        if (allowBlank && value.isBlank()) return
        val decimal = value.toDecimalOrNull()
        if (decimal == null || decimal < BigDecimal.ZERO) errors[key] = message
    }

    private fun String.toDecimalOrNull(): BigDecimal? = runCatching {
        if (isBlank()) null else BigDecimal(trim())
    }.getOrNull()
}
