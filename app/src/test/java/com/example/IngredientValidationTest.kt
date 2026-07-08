package com.smartprocurement.internal

import com.smartprocurement.internal.domain.validation.IngredientFormInput
import com.smartprocurement.internal.domain.validation.IngredientValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientValidationTest {
    @Test
    fun ingredient_validation_reports_field_level_errors() {
        val result = IngredientValidator.validate(
            IngredientFormInput(
                name = "",
                category = "",
                spec = "",
                unit = "",
                minOrderQuantity = "0",
                quantityStep = "-1",
                stockQuantity = "-3",
                warningQuantity = "-1",
                internalPrice = "-5",
                supplyStatus = "正常供应",
                active = true
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.errors["name"] == "食材名称不能为空")
        assertTrue(result.errors["category"] == "食材分类不能为空")
        assertTrue(result.errors["spec"] == "规格不能为空")
        assertTrue(result.errors["unit"] == "计量单位不能为空")
        assertTrue(result.errors["stockQuantity"] == "当前库存不能小于 0")
        assertTrue(result.errors["warningQuantity"] == "库存预警值不能小于 0")
        assertTrue(result.errors["minOrderQuantity"] == "最小申领量必须大于 0")
        assertTrue(result.errors["quantityStep"] == "数量步长必须大于 0")
        assertTrue(result.errors["internalPrice"] == "单价不能小于 0")
    }

    @Test
    fun ingredient_validation_accepts_decimal_quantities() {
        val result = IngredientValidator.validate(
            IngredientFormInput(
                name = "西红柿",
                category = "蔬菜",
                spec = "普通大红款",
                unit = "公斤",
                minOrderQuantity = "1",
                quantityStep = "0.5",
                stockQuantity = "20.5",
                warningQuantity = "5",
                internalPrice = "4.50",
                supplyStatus = "正常供应",
                active = true
            )
        )

        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun price_is_required_only_when_active_and_supply_is_normal_or_tight() {
        val activeNormal = IngredientValidator.validate(validInput(internalPrice = "", supplyStatus = "正常供应", active = true))
        assertFalse(activeNormal.isValid)
        assertTrue(activeNormal.errors["internalPrice"] == "正常供应并上架时，单价必须大于 0")

        val activeTight = IngredientValidator.validate(validInput(internalPrice = "0", supplyStatus = "库存紧张", active = true))
        assertFalse(activeTight.isValid)
        assertTrue(activeTight.errors["internalPrice"] == "正常供应并上架时，单价必须大于 0")

        assertTrue(IngredientValidator.validate(validInput(internalPrice = "", supplyStatus = "暂停供应", active = true)).isValid)
        assertTrue(IngredientValidator.validate(validInput(internalPrice = "", supplyStatus = "正常供应", active = false)).isValid)
    }

    private fun validInput(
        internalPrice: String,
        supplyStatus: String,
        active: Boolean
    ) = IngredientFormInput(
        name = "西红柿",
        category = "蔬菜",
        spec = "散装",
        unit = "公斤",
        minOrderQuantity = "1",
        quantityStep = "1",
        stockQuantity = "20",
        warningQuantity = "0",
        internalPrice = internalPrice,
        supplyStatus = supplyStatus,
        active = active
    )
}
