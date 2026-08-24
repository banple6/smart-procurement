package com.smartprocurement.internal

import com.smartprocurement.internal.ui.AnalyticsFilterDraft
import com.smartprocurement.internal.ui.AnalyticsRequestTracker
import com.smartprocurement.internal.ui.AnalyticsSection
import com.smartprocurement.internal.ui.adminMainTabIds
import com.smartprocurement.internal.ui.analyticsFilterFields
import com.smartprocurement.internal.ui.normalizedMainTab
import com.smartprocurement.internal.ui.productAnalyticsNavigationTarget
import com.smartprocurement.internal.ui.unitMainTabIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsUiStateTest {
    @Test
    fun `analytics sections can load concurrently and only latest generation wins`() {
        val tracker = AnalyticsRequestTracker()
        val overviewFirst = tracker.begin(AnalyticsSection.OVERVIEW)
        val priceFirst = tracker.begin(AnalyticsSection.PRICE)
        val overviewSecond = tracker.begin(AnalyticsSection.OVERVIEW)

        assertFalse(tracker.isLatest(AnalyticsSection.OVERVIEW, overviewFirst))
        assertTrue(tracker.isLatest(AnalyticsSection.OVERVIEW, overviewSecond))
        assertTrue(tracker.isLatest(AnalyticsSection.PRICE, priceFirst))
    }

    @Test
    fun `inventory filter exposes only client risk switch`() {
        assertEquals(setOf("category", "inventoryRiskOnly"), analyticsFilterFields("inventory"))
        assertFalse("unitId" in analyticsFilterFields("inventory"))
        assertTrue("category" in analyticsFilterFields("inventory"))
    }

    @Test
    fun `filter draft changes do not mutate applied value until caller applies it`() {
        val applied = AnalyticsFilterDraft(category = "蔬菜")
        val draft = applied.copy(category = "水果", inventoryRiskOnly = true)

        assertEquals("蔬菜", applied.category)
        assertEquals("水果", draft.category)
        assertTrue(draft.inventoryRiskOnly)
    }

    @Test
    fun `admin and unit navigation remain role specific`() {
        assertEquals(listOf("dashboard", "ingredients", "orders", "analytics", "profile"), adminMainTabIds())
        assertEquals(listOf("home", "cart", "orders", "profile"), unitMainTabIds())
        assertEquals("dashboard", normalizedMainTab("admin", "cart"))
        assertEquals("home", normalizedMainTab("unit_user", "analytics"))
    }

    @Test
    fun `product analysis target is available before data loading`() {
        val target = productAnalyticsNavigationTarget("p-1", "大白菜")
        assertEquals("p-1", target.productId)
        assertEquals("大白菜", target.productName)
    }
}
