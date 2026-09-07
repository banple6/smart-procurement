package com.smartprocurement.internal

import com.smartprocurement.internal.data.CartItemEntity
import com.smartprocurement.internal.data.CartReconciler
import com.smartprocurement.internal.data.ProcurementApiClient
import com.smartprocurement.internal.data.ProductEntity
import com.smartprocurement.internal.domain.product.ALL_PRODUCT_CATEGORIES
import com.smartprocurement.internal.domain.product.matchesProductCategory
import com.smartprocurement.internal.domain.product.productCategoryFilters
import com.smartprocurement.internal.domain.product.selectedProductCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DynamicProductCategoryCompatibilityTest {
    @Test
    fun parses_and_filters_future_categories_without_a_hardcoded_catalog() {
        val client = ProcurementApiClient("http://127.0.0.1/api/v1/")
        val products = listOf(
            client.parseProduct(productJson("鸡丁", "肉类")),
            client.parseProduct(productJson("冻虾", "冻货")),
            client.parseProduct(productJson("鸡蛋", "鸡蛋")),
            client.parseProduct(productJson("鲜牛奶", "牛奶")),
        )
        val filters = productCategoryFilters(listOf(" 蔬菜 ") + products.map { it.category } + listOf("冻货", "", "  "))

        assertEquals(ALL_PRODUCT_CATEGORIES, filters.first())
        assertEquals(listOf(ALL_PRODUCT_CATEGORIES, "冻货", "牛奶", "肉类", "蔬菜", "鸡蛋"), filters)
        assertTrue(products.all { matchesProductCategory(it.category, it.category) })
        assertFalse(matchesProductCategory(products[1].category, "牛奶"))
        assertTrue(matchesProductCategory(products[1].category, ALL_PRODUCT_CATEGORIES))
    }

    @Test
    fun refresh_rebuilds_categories_and_recovers_a_removed_selection() {
        val before = productCategoryFilters(listOf("蔬菜", "蛋奶"))
        val after = productCategoryFilters(listOf("蔬菜", "肉类", "冻货"))

        assertEquals(listOf(ALL_PRODUCT_CATEGORIES, "蔬菜", "蛋奶"), before)
        assertEquals(listOf(ALL_PRODUCT_CATEGORIES, "冻货", "肉类", "蔬菜"), after)
        assertEquals(ALL_PRODUCT_CATEGORIES, selectedProductCategory("蛋奶", after))
        assertEquals("冻货", selectedProductCategory("冻货", after))
    }

    @Test
    fun unknown_category_product_remains_valid_in_cart() {
        val frozen = product("冻虾", "冻货")
        val line = CartReconciler.reconcile(
            cartItems = listOf(CartItemEntity(productId = frozen.id, quantity = 1.0)),
            activeProducts = listOf(frozen),
            knownProducts = listOf(frozen),
        ).single()

        assertTrue(line.canSubmit)
        assertEquals("冻货", line.product?.category)
    }

    @Test
    fun order_payload_remains_product_id_and_quantity_only() {
        val source = File("src/main/java/com/example/data/ProcurementApiClient.kt").readText()
        val method = source.substring(source.indexOf("fun createOrder("), source.indexOf("fun currentUnitQuota("))

        assertTrue(method.contains("put(\"product_id\", productId).put(\"quantity\", quantity.toCleanString())"))
        assertFalse(method.contains("put(\"category\""))
    }

    private fun productJson(name: String, category: String) = JSONObject()
        .put("id", "product-$category")
        .put("name", name)
        .put("spec", "散装")
        .put("unit", "斤")
        .put("category", category)

    private fun product(name: String, category: String) = ProductEntity(
        id = "product-$category",
        name = name,
        spec = "散装",
        unit = "斤",
        imageUrl = "",
        origin = "",
        minQty = 1.0,
        stepQty = 1.0,
        stockStatus = "充足",
        price = 10.0,
        category = category,
        availableQuantity = "10",
    )
}
