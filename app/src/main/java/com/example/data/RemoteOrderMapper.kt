package com.smartprocurement.internal.data

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

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
            val requestedQty = item.optString("requested_quantity", item.optString("quantity", "0")).toDoubleOrNull() ?: 0.0
            val actualQty = item.optString("actual_quantity", item.optString("quantity", "0")).toDoubleOrNull() ?: requestedQty
            OrderItemEntity(
                remoteItemId = item.optString("id"),
                orderId = orderId,
                productId = item.optString("product_id"),
                productName = item.optString("product_name_snapshot", "未知食材"),
                productSpec = item.optString("spec_snapshot", "默认规格"),
                productUnit = item.optString("unit_snapshot", ""),
                productImageUrl = item.optString("image_path"),
                requestedQty = requestedQty,
                actualQty = actualQty,
                adjustmentReason = item.optString("adjustment_reason"),
                adjusted = item.optBooleanCompat("adjusted"),
                confirmedQty = actualQty,
                deliveredQty = if (status == "已完成") actualQty else 0.0,
                price = item.optInt("price_cents_snapshot", 0) / 100.0,
                isSubstitute = false
            )
        }
        return RemoteOrderBundle(
            order = OrderEntity(
                orderId = orderId,
                displayOrderNo = json.optString("order_no").ifBlank { "未生成订单号" },
                submitTime = json.optString("created_at").toUiTime(),
                createdAt = json.optString("created_at").toUiTime(),
                serverUpdatedAt = json.optString("updated_at"),
                acceptedAt = json.optString("accepted_at").toUiTime(),
                preparingAt = json.optString("preparing_at").toUiTime(),
                shippedAt = json.optString("shipped_at").toUiTime(),
                completedAt = json.optString("completed_at").toUiTime(),
                deliveryPoint = json.optString("delivery_point_snapshot"),
                status = status,
                requesterName = json.optString("created_by_username"),
                department = json.optString("unit_name_snapshot"),
                phone = "",
                remarks = json.optString("note"),
                shippingNote = json.optString("shipping_note"),
                shippingPhotoCount = json.optInt("shipping_photo_count", 0),
                shippingPhotosJson = json.optJSONArray("shipping_photos")?.toString() ?: "[]",
                openReceiptIssueCount = json.optInt("open_receipt_issue_count", 0),
                receiptIssuesJson = json.optJSONArray("receipt_issues")?.toString() ?: "[]",
                hasAdjustments = json.optBooleanCompat("has_adjustments"),
                totalCents = json.optLong("total_cents", items.sumOf { item ->
                    BigDecimal.valueOf(item.price)
                        .multiply(BigDecimal.valueOf(item.actualQty))
                        .multiply(BigDecimal(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact()
                }),
                itemCount = json.optInt("item_count", items.size),
                urgent = false,
                allowSubstitute = true
            ),
            items = items
        )
    }

    fun apiStatusForNextUiAction(currentStatus: String, isAdmin: Boolean): String? {
        if (!isAdmin) return null
        return when (currentStatus) {
            "待接单" -> "accepted"
            "已接单" -> "preparing"
            "已发货" -> "completed"
            else -> null
        }
    }

    private fun String.toUiOrderStatus(): String = when (this) {
        "accepted" -> "已接单"
        "preparing" -> "备货中"
        "shipped" -> "已发货"
        "completed" -> "已完成"
        "cancelled" -> "已取消"
        else -> "待接单"
    }

    private fun String.toUiTime(): String {
        if (isBlank()) return ""
        return replace('T', ' ').take(16)
    }

    private fun JSONObject.optBooleanCompat(name: String): Boolean {
        val value = opt(name)
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }

}
