package com.smartprocurement.internal.ui.designsystem

import androidx.compose.ui.graphics.Color

data class GovernmentStatusStyle(
    val label: String,
    val contentColor: Color,
    val containerColor: Color
)

object GovernmentStatus {
    fun fromApiStatus(status: String): GovernmentStatusStyle = when (status) {
        "accepted" -> accepted()
        "preparing" -> preparing()
        "shipped" -> shipped()
        "completed" -> completed()
        "cancelled" -> cancelled()
        "tight" -> warning("库存紧张")
        "paused" -> warning("暂停供应")
        "off_shelf" -> cancelled("已下架")
        "normal" -> completed("正常供应")
        else -> pending()
    }

    fun fromUiStatus(status: String): GovernmentStatusStyle = when (status) {
        "已接单" -> accepted()
        "备货中" -> preparing()
        "已发货" -> shipped()
        "已完成" -> completed()
        "已取消" -> cancelled()
        "库存紧张" -> warning("库存紧张")
        "暂停供应" -> warning("暂停供应")
        "已下架" -> cancelled("已下架")
        "库存不足" -> error("库存不足")
        "正常供应" -> completed("正常供应")
        "启用" -> completed("启用")
        "停用" -> cancelled("停用")
        else -> pending()
    }

    private fun pending(label: String = "待接单") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusPending,
        containerColor = GovernmentColors.GovernmentBlueLight
    )

    private fun accepted(label: String = "已接单") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusAccepted,
        containerColor = GovernmentColors.StatusSuccessBackground
    )

    private fun preparing(label: String = "备货中") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusPreparing,
        containerColor = GovernmentColors.StatusWarningBackground
    )

    private fun shipped(label: String = "已发货") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusShipped,
        containerColor = GovernmentColors.GovernmentBlueLight
    )

    private fun completed(label: String = "已完成") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusCompleted,
        containerColor = GovernmentColors.StatusSuccessBackground
    )

    private fun cancelled(label: String = "已取消") = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusCancelled,
        containerColor = GovernmentColors.SurfaceMuted
    )

    private fun warning(label: String) = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusPreparing,
        containerColor = GovernmentColors.StatusWarningBackground
    )

    private fun error(label: String) = GovernmentStatusStyle(
        label = label,
        contentColor = GovernmentColors.StatusError,
        containerColor = GovernmentColors.StatusErrorBackground
    )
}
