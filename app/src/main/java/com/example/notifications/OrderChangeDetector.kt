package com.smartprocurement.internal.notifications

data class OrderSnapshot(
    val orderId: String,
    val version: Int,
    val status: String
)

data class ReconciledOrderChange(
    val eventId: String,
    val eventType: String,
    val orderId: String,
    val status: String
)

object OrderChangeDetector {
    fun detect(
        role: String,
        before: List<OrderSnapshot>,
        after: List<OrderSnapshot>
    ): List<ReconciledOrderChange> {
        if (before.isEmpty()) return emptyList()
        val oldById = before.associateBy { it.orderId }
        return when (role) {
            "admin" -> after
                .filter { it.orderId !in oldById && it.status == "待接单" }
                .map { it.toChange("ORDER_CREATED") }
            "unit_user" -> after
                .filter { current ->
                    val old = oldById[current.orderId]
                    old != null && (current.version > old.version || current.status != old.status)
                }
                .map { it.toChange("ORDER_STATUS_CHANGED") }
            else -> emptyList()
        }
    }

    private fun OrderSnapshot.toChange(type: String) = ReconciledOrderChange(
        eventId = "sync:$orderId:$version:$status",
        eventType = type,
        orderId = orderId,
        status = status
    )
}
