package com.smartprocurement.internal

import com.smartprocurement.internal.ui.ExternalActionType
import com.smartprocurement.internal.ui.ExternalActivityState
import com.smartprocurement.internal.ui.PendingExternalAction
import com.smartprocurement.internal.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActivityStateTest {
    @Test
    fun `camera cancel clears pending action and preserves shipping destination`() {
        var persisted: PendingExternalAction? = null
        val state = ExternalActivityState { persisted = it }
        val action = PendingExternalAction(
            ExternalActionType.SHIPPING_CAMERA,
            ownerId = "order-a",
            payload = "/tmp/photo.jpg"
        )

        state.begin(action)
        assertEquals(Screen.ShippingProof("order-a"), state.pendingAction?.restoredScreen())
        assertEquals(action, state.consume(ExternalActionType.SHIPPING_CAMERA, "order-a"))
        assertNull(state.pendingAction)
        assertNull(persisted)
    }

    @Test
    fun `activity recreation restores destination and consumes result once`() {
        var persisted: PendingExternalAction? = null
        ExternalActivityState(persist = { persisted = it }).begin(
            PendingExternalAction(ExternalActionType.BATCH_OUTBOUND_EXPORT, ownerId = "batch-a")
        )

        val recreated = ExternalActivityState(initialAction = persisted)

        assertEquals(Screen.DeliveryBatchDetail("batch-a"), recreated.pendingAction?.restoredScreen())
        assertTrue(recreated.consume(ExternalActionType.BATCH_OUTBOUND_EXPORT, "batch-a") != null)
        assertNull(recreated.consume(ExternalActionType.BATCH_OUTBOUND_EXPORT, "batch-a"))
    }

    @Test
    fun `mismatched callback cannot consume another external action`() {
        val state = ExternalActivityState(
            PendingExternalAction(ExternalActionType.LEDGER_EXPORT)
        )

        assertNull(state.consume(ExternalActionType.BATCH_PICKING_EXPORT, "batch-a"))
        assertEquals(ExternalActionType.LEDGER_EXPORT, state.pendingAction?.type)
    }
}
