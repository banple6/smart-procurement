package com.smartprocurement.internal

import com.smartprocurement.internal.data.CartItemEntity
import com.smartprocurement.internal.ui.cartBadgeCount
import org.junit.Assert.assertEquals
import org.junit.Test

class CartBadgeCounterTest {
    @Test
    fun badge_count_uses_item_types_not_total_quantity() {
        val cart = listOf(
            CartItemEntity(productId = "tomato", quantity = 3.0),
            CartItemEntity(productId = "potato", quantity = 5.0)
        )

        assertEquals(2, cartBadgeCount(cart))
    }
}
