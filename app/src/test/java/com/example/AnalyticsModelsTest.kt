package com.smartprocurement.internal

import com.smartprocurement.internal.data.AnalyticsDateRange
import com.smartprocurement.internal.data.AnalyticsJsonParser
import com.smartprocurement.internal.data.AnalyticsPriceItem
import com.smartprocurement.internal.ui.Screen
import com.smartprocurement.internal.ui.analyticsInventoryRiskText
import com.smartprocurement.internal.ui.analyticsPriceChangeText
import com.smartprocurement.internal.ui.analyticsPriceBaselineText
import com.smartprocurement.internal.ui.analyticsPriceStatusText
import com.smartprocurement.internal.ui.canOpenScreen
import com.smartprocurement.internal.ui.charts.chartSelectionIndex
import com.smartprocurement.internal.ui.charts.barSelectionIndex
import com.smartprocurement.internal.ui.charts.chartLabelIndices
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate


@RunWith(RobolectricTestRunner::class)
class AnalyticsModelsTest {
    @Test
    fun `recent range is inclusive and stable`() {
        val range = AnalyticsDateRange.recent(7, LocalDate.of(2026, 8, 22))
        assertEquals("2026-08-16", range.startDate)
        assertEquals("2026-08-22", range.endDate)
        assertEquals(7, range.days)
    }

    @Test
    fun `overview parser preserves cents quantity strings and nullable comparison`() {
        val body = JSONObject(
            """{
              "summary":{"valid_order_count":2,"total_cents":1800,"unit_count":2,"product_count":1,"open_receipt_issues":0},
              "comparison":{"valid_order_count_percent":null,"total_cents_percent":50.0,"unit_count_percent":null,"product_count_percent":0.0},
              "trend":[{"date":"2026-08-21","order_count":2,"total_cents":1800}],
              "demand_rank":[{"product_id":"p1","product_name":"土豆","category":"蔬菜","unit":"公斤","quantity":"4.25"}]
            }"""
        )

        val result = AnalyticsJsonParser.overview(body)

        assertEquals(1800L, result.summary.totalCents)
        assertNull(result.comparison.validOrderCountPercent)
        assertEquals(50.0, result.comparison.totalCentsPercent!!, 0.0)
        assertEquals("4.25", result.demandRank.single().quantity)
    }

    @Test
    fun `price parser does not invent percentage for zero baseline`() {
        val result = AnalyticsJsonParser.prices(
            JSONObject(
                """{"items":[{"product_id":"p1","product_name":"苹果","category":"水果","unit":"箱",
                  "initial_price_cents":null,"current_price_cents":5000,"min_price_cents":5000,
                  "max_price_cents":5000,"change_percent":null,"is_new":true,"change_count":1}]}"""
            )
        ).single()

        assertTrue(result.isNew)
        assertNull(result.initialPriceCents)
        assertNull(result.changePercent)
        assertEquals(5000L, result.currentPriceCents)
    }

    @Test
    fun `price labels use is new instead of missing baseline`() {
        val existingWithoutBaseline = AnalyticsPriceItem(
            productId = "old",
            productName = "旧食材",
            category = "蔬菜",
            unit = "公斤",
            initialPriceCents = null,
            currentPriceCents = 700,
            minPriceCents = 700,
            maxPriceCents = 700,
            changePercent = null,
            isNew = false,
            changeCount = 1
        )
        val newProduct = existingWithoutBaseline.copy(productId = "new", productName = "新食材", isNew = true)

        assertEquals("暂无可比价格", analyticsPriceBaselineText(existingWithoutBaseline))
        assertEquals("暂无可比价格", analyticsPriceStatusText(existingWithoutBaseline))
        assertEquals("新食材", analyticsPriceBaselineText(newProduct))
        assertEquals("新食材", analyticsPriceStatusText(newProduct))
    }

    @Test
    fun `inventory parser preserves nullable estimate and structured risk`() {
        val result = AnalyticsJsonParser.inventory(
            JSONObject(
                """{"summary":{"product_count":1,"out_of_stock":0,"warning":1,"tight":0,"paused":0,"normal":0},
                  "items":[{"product_id":"p1","product_name":"土豆","category":"蔬菜","spec":"散装","unit":"公斤",
                  "stock_quantity":"500","reserved_quantity":"380","available_quantity":"120","warning_quantity":"150",
                  "average_daily_demand":"0","estimated_days_available":null,"supply_status":"normal","active":true,
                  "risk_level":"warning","risk_text":"低于预警"}]}"""
            )
        ).items.single()

        assertEquals("120", result.availableQuantity)
        assertEquals("warning", result.risk)
        assertNull(result.estimatedDaysAvailable)
        assertEquals("低于预警", analyticsInventoryRiskText(result.risk))
    }

    @Test
    fun `product parser preserves price summary and cents`() {
        val result = AnalyticsJsonParser.product(
            JSONObject(
                """{"product":{"id":"p1","name":"土豆","category":"蔬菜","spec":"散装","unit":"公斤","price_cents":600},
                  "inventory":{"stock_quantity":"10","reserved_quantity":"2","available_quantity":"8","warning_quantity":"3","supply_status":"normal"},
                  "period":{"order_count":2,"quantity":"4","amount_cents":1800,"unit_count":2,"unit":"公斤"},
                  "price":{"current_cents":600,"range_start_cents":400,"min_cents":400,"max_cents":600,"change_cents":200,"change_percent":50.0,"is_new":false},
                  "price_history":[],"demand_trend":[],"unit_rank":[]}"""
            )
        )

        assertEquals(600L, result.price.currentCents)
        assertEquals(400L, result.price.rangeStartCents)
        assertEquals(1800L, result.period.amountCents)
        assertEquals("+50.0%", analyticsPriceChangeText(result.price.changePercent))
    }

    @Test
    fun `chart data selection clamps and never selects another row for empty data`() {
        assertEquals(0, chartSelectionIndex(0f, 300f, 3))
        assertEquals(1, chartSelectionIndex(150f, 300f, 3))
        assertEquals(2, chartSelectionIndex(300f, 300f, 3))
        assertNull(chartSelectionIndex(10f, 300f, 0))
        assertEquals(0, chartSelectionIndex(100f, 300f, 1))
        assertEquals(0, barSelectionIndex(12f, 200f, 3, 10f, 190f))
        assertEquals(2, barSelectionIndex(189f, 200f, 3, 10f, 190f))
        assertNull(barSelectionIndex(20f, 200f, 0))
        assertEquals(listOf(0, 2, 4), chartLabelIndices(5))
        assertEquals(listOf(0), chartLabelIndices(1))
        assertTrue(chartLabelIndices(0).isEmpty())
    }

    @Test
    fun `analytics screens remain admin only`() {
        assertTrue(canOpenScreen("admin", Screen.Analytics))
        assertTrue(!canOpenScreen("unit_user", Screen.Analytics))
        assertTrue(!canOpenScreen("unit_user", Screen.ProductAnalytics("p1")))
    }
}
