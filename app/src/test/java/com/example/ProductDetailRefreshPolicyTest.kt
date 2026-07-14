package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductDetailRefreshPolicyTest {
    @Test
    fun product_detail_supports_pull_refresh_and_price_uses_yuan_suffix() {
        val source = File("src/main/java/com/example/ui/HomeAndDetail.kt").readText()

        assertTrue(source.contains("PullToRefreshBox("))
        assertTrue(source.contains("onRefresh = { viewModel.refreshProducts() }"))
        assertTrue(source.contains("isRefreshing = viewModel.isRefreshingProducts"))
        assertTrue(source.contains("suffix = \"元\""))
    }
}
