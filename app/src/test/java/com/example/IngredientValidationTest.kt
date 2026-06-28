package com.example

import com.example.domain.validation.IngredientFormInput
import com.example.domain.validation.IngredientValidator
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
                availableQuantity = "-2",
                internalPrice = "-5"
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
        assertTrue(result.errors["availableQuantity"] == "今日可供数量不能小于 0")
        assertTrue(result.errors["internalPrice"] == "内部参考价不能小于 0")
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
                availableQuantity = "12.5",
                internalPrice = "4.50"
            )
        )

        assertTrue(result.errors.toString(), result.isValid)
    }
}
