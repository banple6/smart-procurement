package com.smartprocurement.internal

import com.smartprocurement.internal.domain.validation.CartProductSnapshot
import com.smartprocurement.internal.domain.validation.CartValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartValidationTest {
    @Test
    fun rejects_zero_stock_paused_off_shelf_deleted_and_below_minimum() {
        val zeroStock = product(availableQuantity = "0")
        assertFalse(CartValidator.canOrder(zeroStock, 1.0).isValid)
        assertEquals("库存不足，请减少数量", CartValidator.canOrder(zeroStock, 1.0).message)

        val zeroPrice = product(priceCents = 0)
        assertFalse(CartValidator.canOrder(zeroPrice, 1.0).isValid)
        assertEquals("价格未设置", CartValidator.canOrder(zeroPrice, 1.0).message)
//http://47.94.227.58/
        val paused = product(status = "暂停供应")
        assertFalse(CartValidator.canOrder(paused, 1.0).isValid)
        assertEquals("该食材暂停供应，不能加入清单", CartValidator.canOrder(paused, 1.0).message)

        val offShelf = product(isAvailable = false)
        assertFalse(CartValidator.canOrder(offShelf, 1.0).isValid)
        assertEquals("该食材已下架，不能加入清单", CartValidator.canOrder(offShelf, 1.0).message)

        val deleted = product(isDeleted = true)
        assertFalse(CartValidator.canOrder(deleted, 1.0).isValid)
        assertEquals("该食材已删除，不能加入清单", CartValidator.canOrder(deleted, 1.0).message)

        val belowMinimum = product(minQty = 2.0)
        assertFalse(CartValidator.canOrder(belowMinimum, 1.0).isValid)
        assertEquals("低于最小申领量", CartValidator.canOrder(belowMinimum, 1.0).message)
    }

    @Test
    fun rejects_quantities_above_available_stock_or_not_on_step() {
        val snapshot = product(availableQuantity = "5", minQty = 1.0, stepQty = 0.5)

        assertFalse(CartValidator.canOrder(snapshot, 5.5).isValid)
        assertEquals("库存不足，请减少数量", CartValidator.canOrder(snapshot, 5.5).message)

        assertFalse(CartValidator.canOrder(snapshot, 1.25).isValid)
        assertEquals("数量不符合申领步长", CartValidator.canOrder(snapshot, 1.25).message)

        assertTrue(CartValidator.canOrder(snapshot, 4.5).isValid)
    }

    private fun product(
        availableQuantity: String = "10",
        minQty: Double = 1.0,
        stepQty: Double = 1.0,
        priceCents: Long = 450,
        status: String = "正常供应",
        isAvailable: Boolean = true,
        isDeleted: Boolean = false
    ) = CartProductSnapshot(
        availableQuantity = availableQuantity,
        minQty = minQty,
        stepQty = stepQty,
        priceCents = priceCents,
        status = status,
        isAvailable = isAvailable,
        isDeleted = isDeleted
    )
}
