package com.smartprocurement.internal

import com.smartprocurement.internal.data.OrderEntity
import com.smartprocurement.internal.ui.orderTimelineNodes
import com.smartprocurement.internal.ui.components.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderTimelineMapperTest {
    @Test
    fun pending_order_marks_submit_completed_waiting_acceptance_current_and_future_steps_pending() {
        val nodes = orderTimelineNodes(order(status = "待接单", createdAt = "2026-07-06 14:32"))

        assertEquals(listOf("订单已提交", "等待管理员接单", "开始备货", "已发货", "已完成"), nodes.map { it.label })
        assertEquals(listOf(StepState.COMPLETED, StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING, StepState.UPCOMING), nodes.map { it.state })
        assertEquals("2026-07-06 14:32", nodes[0].timestamp)
        assertNull(nodes[1].timestamp)
        assertNull(nodes[2].timestamp)
    }

    @Test
    fun completed_order_marks_all_normal_steps_completed_and_uses_missing_time_label_only_for_completed_nodes() {
        val nodes = orderTimelineNodes(
            order(
                status = "已完成",
                createdAt = "2026-07-06 14:32",
                acceptedAt = "",
                preparingAt = "",
                shippedAt = "",
                completedAt = "2026-07-06 15:40"
            )
        )

        assertEquals(listOf(StepState.COMPLETED, StepState.COMPLETED, StepState.COMPLETED, StepState.COMPLETED, StepState.COMPLETED), nodes.map { it.state })
        assertEquals("时间未记录", nodes[1].timestamp)
        assertEquals("时间未记录", nodes[2].timestamp)
        assertEquals("时间未记录", nodes[3].timestamp)
        assertEquals("2026-07-06 15:40", nodes[4].timestamp)
    }

    @Test
    fun cancelled_order_does_not_show_full_green_fulfillment_route() {
        val nodes = orderTimelineNodes(
            order(
                status = "已取消",
                createdAt = "2026-07-06 14:32",
                cancelledAt = ""
            )
        )

        assertEquals(listOf("订单已提交", "订单已取消"), nodes.map { it.label })
        assertEquals(listOf(StepState.COMPLETED, StepState.CANCELLED), nodes.map { it.state })
        assertEquals("取消时间未记录", nodes[1].timestamp)
    }

    private fun order(
        status: String,
        createdAt: String = "",
        acceptedAt: String = "",
        preparingAt: String = "",
        shippedAt: String = "",
        completedAt: String = "",
        cancelledAt: String = ""
    ) = OrderEntity(
        orderId = "order-1",
        displayOrderNo = "SP20260706-000004",
        submitTime = createdAt,
        createdAt = createdAt,
        acceptedAt = acceptedAt,
        preparingAt = preparingAt,
        shippedAt = shippedAt,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        deliveryPoint = "客户演示收货点",
        status = status,
        requesterName = "demo_unit01",
        department = "客户演示单位"
    )
}
