package com.smartprocurement.internal.ui

import com.smartprocurement.internal.data.OrderEntity
import com.smartprocurement.internal.ui.components.StatusStep
import com.smartprocurement.internal.ui.components.StepState

fun orderTimelineNodes(order: OrderEntity): List<StatusStep> {
    val created = order.createdAt.ifBlank { order.submitTime }
    if (order.status == "已取消") {
        return listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("订单已取消", StepState.CANCELLED, order.cancelledAt.ifBlank { "取消时间未记录" })
        )
    }

    return when (order.status) {
        "已完成" -> listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("管理员已接单", StepState.COMPLETED, completedTime(order.acceptedAt)),
            StatusStep("开始备货", StepState.COMPLETED, completedTime(order.preparingAt)),
            StatusStep("已发货", StepState.COMPLETED, completedTime(order.shippedAt)),
            StatusStep("已完成", StepState.COMPLETED, completedTime(order.completedAt))
        )
        "已发货" -> listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("管理员已接单", StepState.COMPLETED, completedTime(order.acceptedAt)),
            StatusStep("开始备货", StepState.COMPLETED, completedTime(order.preparingAt)),
            StatusStep("已发货", StepState.COMPLETED, completedTime(order.shippedAt)),
            StatusStep("等待确认完成", StepState.CURRENT)
        )
        "备货中" -> listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("管理员已接单", StepState.COMPLETED, completedTime(order.acceptedAt)),
            StatusStep("开始备货", StepState.CURRENT, order.preparingAt.ifBlank { null }),
            StatusStep("已发货", StepState.UPCOMING),
            StatusStep("已完成", StepState.UPCOMING)
        )
        "已接单" -> listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("管理员已接单", StepState.COMPLETED, completedTime(order.acceptedAt)),
            StatusStep("等待开始备货", StepState.CURRENT),
            StatusStep("已发货", StepState.UPCOMING),
            StatusStep("已完成", StepState.UPCOMING)
        )
        else -> listOf(
            StatusStep("订单已提交", StepState.COMPLETED, completedTime(created)),
            StatusStep("等待管理员接单", StepState.CURRENT),
            StatusStep("开始备货", StepState.UPCOMING),
            StatusStep("已发货", StepState.UPCOMING),
            StatusStep("已完成", StepState.UPCOMING)
        )
    }
}

private fun completedTime(value: String): String = value.ifBlank { "时间未记录" }
