package com.example.data

import org.json.JSONObject

data class RemoteOrderBundle(
    val order: OrderEntity,
    val items: List<OrderItemEntity>
)

object RemoteOrderMapper {
    fun mapOrder(json: JSONObject): RemoteOrderBundle {
        val orderId = json.getString("id")
        val status = json.optString("status", "pending").toUiOrderStatus()
        val itemsArray = json.optJSONArray("items")
        val items = List(itemsArray?.length() ?: 0) { index ->
            val item = itemsArray!!.getJSONObject(index)
            OrderItemEntity(
                orderId = orderId,
                productId = item.optString("product_id"),
                productName = item.optString("product_name_snapshot", "未知食材"),
                productSpec = item.optString("spec_snapshot", "默认规格"),
                productUnit = item.optString("unit_snapshot", ""),
                productImageUrl = item.optString("image_path"),
                requestedQty = item.optString("quantity", "0").toDoubleOrNull() ?: 0.0,
                confirmedQty = item.optString("quantity", "0").toDoubleOrNull() ?: 0.0,
                deliveredQty = if (status == "已完成") item.optString("quantity", "0").toDoubleOrNull() ?: 0.0 else 0.0,
                price = item.optInt("price_cents_snapshot", 0) / 100.0,
                isSubstitute = false
            )
        }
        return RemoteOrderBundle(
            order = OrderEntity(
                orderId = orderId,
                displayOrderNo = json.optString("order_no").ifBlank { orderId },
                submitTime = json.optString("created_at").toUiTime(),
                deliveryPoint = json.optString("delivery_point_snapshot"),
                status = status,
                requesterName = json.optString("created_by"),
                department = json.optString("unit_name_snapshot"),
                phone = "",
                remarks = json.optString("note"),
                urgent = false,
                allowSubstitute = true,
                estimatedDelivery = json.optString("updated_at").toUiTime(),
                progressPercent = status.toProgressPercent(),
                progressText = status.toProgressText()
            ),
            items = items
        )
    }

    fun apiStatusForNextUiAction(currentStatus: String, isAdmin: Boolean): String? {
        if (!isAdmin) return null
        return when (currentStatus) {
            "待确认" -> "accepted"
            "已确认" -> "preparing"
            "分拣中" -> "shipped"
            "配送中" -> "completed"
            else -> null
        }
    }

    private fun String.toUiOrderStatus(): String = when (this) {
        "accepted" -> "已确认"
        "preparing" -> "分拣中"
        "shipped" -> "配送中"
        "completed" -> "已完成"
        "cancelled" -> "已取消"
        else -> "待确认"
    }

    private fun String.toUiTime(): String {
        if (isBlank()) return ""
        return replace('T', ' ').take(16)
    }

    private fun String.toProgressPercent(): Float = when (this) {
        "待确认" -> 0.2f
        "已确认" -> 0.35f
        "分拣中" -> 0.65f
        "配送中" -> 0.85f
        "已完成" -> 1f
        else -> 0f
    }

    private fun String.toProgressText(): String = when (this) {
        "待确认" -> "等待管理员确认"
        "已确认" -> "管理员已确认"
        "分拣中" -> "仓储正在分拣"
        "配送中" -> "配送途中"
        "已完成" -> "已完成签收"
        "已取消" -> "订单已取消"
        else -> ""
    }
}
