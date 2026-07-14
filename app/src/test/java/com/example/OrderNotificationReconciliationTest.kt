package com.smartprocurement.internal

import com.smartprocurement.internal.notifications.OrderSnapshot
import com.smartprocurement.internal.notifications.OrderChangeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderNotificationReconciliationTest {
    @Test
    fun admin_detects_only_new_pending_orders() {
        val before = listOf(OrderSnapshot("old", 1, "已接单"))
        val after = before + OrderSnapshot("new", 1, "待接单") + OrderSnapshot("done", 1, "已完成")

        val changes = OrderChangeDetector.detect("admin", before, after)

        assertEquals(listOf("new"), changes.map { it.orderId })
        assertEquals("ORDER_CREATED", changes.single().eventType)
    }

    @Test
    fun unit_detects_status_or_version_changes_but_not_initial_cache_fill() {
        val initial = listOf(OrderSnapshot("order-1", 1, "待接单"))
        assertTrue(OrderChangeDetector.detect("unit_user", emptyList(), initial).isEmpty())

        val changed = listOf(OrderSnapshot("order-1", 2, "已接单"))
        val changes = OrderChangeDetector.detect("unit_user", initial, changed)

        assertEquals(1, changes.size)
        assertEquals("sync:order-1:2:已接单", changes.single().eventId)
        assertEquals("ORDER_STATUS_CHANGED", changes.single().eventType)
    }
}
