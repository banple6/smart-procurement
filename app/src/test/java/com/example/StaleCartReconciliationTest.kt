package com.smartprocurement.internal

import com.smartprocurement.internal.data.CartItemEntity
import com.smartprocurement.internal.data.CartItemState
import com.smartprocurement.internal.data.CartReconciler
import com.smartprocurement.internal.data.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StaleCartReconciliationTest {
    @Test
    fun archived_product_id_stays_visible_as_stale_and_is_never_remapped_by_name() {
        val oldId = "OLD_ID"
        val newId = "NEW_ID"
        val lines = CartReconciler.reconcile(
            cartItems = listOf(CartItemEntity(productId = oldId, quantity = 2.0)),
            activeProducts = listOf(product(id = newId, name = "圆白菜")),
            knownProducts = listOf(product(id = oldId, name = "圆白菜", isDeleted = true, isAvailable = false))
        )

        assertEquals(1, lines.size)
        assertEquals(oldId, lines.single().cartItem.productId)
        assertEquals(CartItemState.STALE, lines.single().state)
        assertEquals("圆白菜", lines.single().displayName)
        assertNull(lines.single().product)
        assertFalse(lines.single().canSubmit)
        assertTrue(lines.single().message.contains("已更新或停止供应"))
    }

    @Test
    fun valid_and_stale_rows_are_both_exposed_and_only_valid_rows_count_toward_totals() {
        val valid = CartItemEntity(productId = "VALID", quantity = 2.0)
        val stale = CartItemEntity(productId = "STALE", quantity = 3.0)
        val lines = CartReconciler.reconcile(
            cartItems = listOf(valid, stale),
            activeProducts = listOf(product(id = valid.productId, price = 5.0)),
            knownProducts = listOf(product(id = stale.productId, name = "旧食材", isDeleted = true, isAvailable = false))
        )

        assertEquals(2, lines.size)
        assertEquals(1, lines.count { it.state == CartItemState.VALID })
        assertEquals(1, lines.count { it.state == CartItemState.STALE })
        assertEquals(listOf(valid), lines.filter { it.canSubmit }.map { it.cartItem })
        assertEquals(listOf(stale), lines.filterNot { it.canSubmit }.map { it.cartItem })
    }

    @Test
    fun missing_product_uses_fallback_name_without_hiding_the_cart_row() {
        val lines = CartReconciler.reconcile(
            cartItems = listOf(CartItemEntity(productId = "MISSING", quantity = 1.0)),
            activeProducts = emptyList(),
            knownProducts = emptyList()
        )

        assertEquals(1, lines.size)
        assertEquals(CartItemState.STALE, lines.single().state)
        assertEquals("某项历史食材", lines.single().displayName)
    }

    @Test
    fun stale_cart_ui_keeps_the_row_visible_excludes_it_from_totals_and_blocks_submit() {
        val source = File("src/main/java/com/example/ui/CartAndOrder.kt").readText()

        assertTrue(source.contains("items(cartLines, key = { it.cartItem.productId })"))
        assertTrue(source.contains("Text(line.message"))
        assertTrue(source.contains("失效食材未计入合计"))
        assertTrue(source.contains("staleLines.isEmpty()"))
        assertTrue(source.contains("TextButton(onClick = { viewModel.deleteCartItem(line.cartItem.productId) })"))
        assertFalse(source.contains("cartList.mapNotNull"))
    }

    @Test
    fun order_submission_refreshes_catalog_and_reconciles_server_product_conflicts() {
        val source = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(source.contains("val refreshResult = refreshProductsFromServer()"))
        assertTrue(source.contains("val cartLines = repository.getCartLinesDirect()"))
        assertTrue(source.contains("清单中有 \$staleCount 项食材已更新或停止供应"))
        assertTrue(source.contains("error.isProductCatalogConflict()"))
        assertTrue(source.contains("部分食材状态已发生变化，请处理清单后重新提交。"))
    }

    private fun product(
        id: String,
        name: String = "测试食材",
        price: Double = 3.0,
        isAvailable: Boolean = true,
        isDeleted: Boolean = false
    ) = ProductEntity(
        id = id,
        name = name,
        spec = "500g",
        unit = "斤",
        imageUrl = "",
        origin = "",
        minQty = 1.0,
        stepQty = 1.0,
        stockStatus = "充足",
        price = price,
        availableQuantity = "100",
        isAvailable = isAvailable,
        isDeleted = isDeleted,
        status = if (isAvailable) "正常供应" else "已下架"
    )
}
