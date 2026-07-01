package com.smartprocurement.internal

import com.smartprocurement.internal.domain.validation.InventoryAdjustMode
import com.smartprocurement.internal.domain.validation.ProductQuickActionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductQuickActionsTest {
    @Test
    fun validates_quick_price_input_without_double_truncation() {
        assertFalse(ProductQuickActionValidator.validatePrice("4.50", "", currentPriceCents = 450).isValid)
        assertEquals("请填写价格调整原因", ProductQuickActionValidator.validatePrice("4.50", "", 450).message)

        assertFalse(ProductQuickActionValidator.validatePrice("4.50", "今日调价", currentPriceCents = 450).isValid)
        assertEquals("新价格必须和当前价格不同", ProductQuickActionValidator.validatePrice("4.50", "今日调价", 450).message)

        assertFalse(ProductQuickActionValidator.validatePrice("0", "今日调价", currentPriceCents = 450).isValid)
        assertEquals("价格必须大于 0", ProductQuickActionValidator.validatePrice("0", "今日调价", 450).message)

        assertFalse(ProductQuickActionValidator.validatePrice("5.123", "今日调价", currentPriceCents = 450).isValid)
        assertEquals("价格最多保留两位小数", ProductQuickActionValidator.validatePrice("5.123", "今日调价", 450).message)

        val valid = ProductQuickActionValidator.validatePrice("5.20", "今日调价", currentPriceCents = 450)
        assertTrue(valid.isValid)
        assertEquals(520L, valid.cents)
    }

    @Test
    fun calculates_inventory_adjustment_modes_and_rejects_invalid_changes() {
        val set = ProductQuickActionValidator.validateInventory(InventoryAdjustMode.SET, "50", "新一批到货", currentStock = "20", reservedStock = "2")
        assertTrue(set.isValid)
        assertEquals("50", set.afterQuantity)

        val increase = ProductQuickActionValidator.validateInventory(InventoryAdjustMode.INCREASE, "30", "新一批到货", currentStock = "20", reservedStock = "2")
        assertTrue(increase.isValid)
        assertEquals("50", increase.afterQuantity)

        val decrease = ProductQuickActionValidator.validateInventory(InventoryAdjustMode.DECREASE, "5", "盘点扣减", currentStock = "20", reservedStock = "2")
        assertTrue(decrease.isValid)
        assertEquals("15", decrease.afterQuantity)

        val noReason = ProductQuickActionValidator.validateInventory(InventoryAdjustMode.SET, "20", "", currentStock = "20", reservedStock = "2")
        assertFalse(noReason.isValid)
        assertEquals("请填写库存调整原因", noReason.message)

        val belowReserved = ProductQuickActionValidator.validateInventory(InventoryAdjustMode.SET, "1", "盘点设置", currentStock = "20", reservedStock = "2")
        assertFalse(belowReserved.isValid)
        assertEquals("库存不能小于已预占库存", belowReserved.message)
    }
}
