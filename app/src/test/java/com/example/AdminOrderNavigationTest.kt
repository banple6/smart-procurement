package com.smartprocurement.internal

import com.smartprocurement.internal.data.OrderAction
import com.smartprocurement.internal.data.RemoteOrderMapper
import com.smartprocurement.internal.ui.OrderMutationFailure
import com.smartprocurement.internal.ui.classifyOrderMutationFailure
import com.smartprocurement.internal.ui.resolveDeliveryBatchPreselection
import com.smartprocurement.internal.data.ApiRequestException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminOrderNavigationTest {
    @Test
    fun `admin actions follow accept then backend fast complete without batch navigation`() {
        assertEquals(OrderAction.ACCEPT, RemoteOrderMapper.actionForOrder("待接单", isAdmin = true))
        assertEquals(OrderAction.FAST_COMPLETE, RemoteOrderMapper.actionForOrder("备货中", isAdmin = true))
        assertEquals(OrderAction.FAST_COMPLETE, RemoteOrderMapper.actionForOrder("已接单", isAdmin = true))
        assertNull(RemoteOrderMapper.actionForOrder("已发货", isAdmin = true))
    }

    @Test
    fun `unit users can cancel pending orders but do not confirm receipt`() {
        assertEquals(OrderAction.CANCEL, RemoteOrderMapper.actionForOrder("待接单", isAdmin = false))
        assertNull(RemoteOrderMapper.actionForOrder("已发货", isAdmin = false))
    }

    @Test
    fun `stale conflicts and ambiguous network failures are refetched while business detail is preserved`() {
        assertEquals(
            OrderMutationFailure.STALE,
            classifyOrderMutationFailure(ApiRequestException(409, message = "订单状态已被其他操作员更新，页面已刷新"))
        )
        assertEquals(
            OrderMutationFailure.BUSINESS,
            classifyOrderMutationFailure(ApiRequestException(409, message = "该订单已经进入出库流程，请使用现有出库单继续处理"))
        )
        assertEquals(OrderMutationFailure.NETWORK_RESULT_UNKNOWN, classifyOrderMutationFailure(IOException("timeout")))
    }

    @Test
    fun `manual batch selection remains available without order action auto navigation`() {
        val result = resolveDeliveryBatchPreselection("A", listOf("A", "B"), setOf("B", "stale"))

        assertEquals(setOf("A", "B"), result.selectedOrderIds)
        assertTrue(!result.missingPreselectedOrder)
    }
}
