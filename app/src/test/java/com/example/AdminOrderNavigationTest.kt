package com.smartprocurement.internal

import com.smartprocurement.internal.ui.AdminUiEvent
import com.smartprocurement.internal.ui.AdminUiEventQueue
import com.smartprocurement.internal.ui.adminOrderActionSuccessEvent
import com.smartprocurement.internal.ui.resolveDeliveryBatchPreselection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminOrderNavigationTest {
    @Test
    fun `successful accept emits batch create navigation once`() = runTest {
        val event = adminOrderActionSuccessEvent(
            isAdmin = true,
            previousStatus = "待接单",
            updatedStatus = "备货中",
            orderId = "order-a"
        )
        val queue = AdminUiEventQueue()

        queue.emit(requireNotNull(event))

        assertEquals(AdminUiEvent.NavigateToBatchCreate("order-a"), queue.events.first())
        assertNull(withTimeoutOrNull(1) { queue.events.first() })
    }

    @Test
    fun `failed or unconfirmed accept does not navigate`() {
        assertNull(
            adminOrderActionSuccessEvent(
                isAdmin = true,
                previousStatus = "待接单",
                updatedStatus = "待接单",
                orderId = "order-a"
            )
        )
        assertNull(
            adminOrderActionSuccessEvent(
                isAdmin = false,
                previousStatus = "待接单",
                updatedStatus = "备货中",
                orderId = "order-a"
            )
        )
    }

    @Test
    fun `preselect chooses only requested eligible order`() {
        val result = resolveDeliveryBatchPreselection(
            preselectOrderId = "A",
            eligibleOrderIds = listOf("A", "B", "C")
        )

        assertEquals(setOf("A"), result.selectedOrderIds)
        assertFalse(result.missingPreselectedOrder)
    }

    @Test
    fun `missing preselect keeps valid existing choices and reports failure`() {
        val result = resolveDeliveryBatchPreselection(
            preselectOrderId = "X",
            eligibleOrderIds = listOf("A", "B", "C"),
            currentSelection = setOf("B", "stale")
        )

        assertEquals(setOf("B"), result.selectedOrderIds)
        assertTrue(result.missingPreselectedOrder)
    }
}
