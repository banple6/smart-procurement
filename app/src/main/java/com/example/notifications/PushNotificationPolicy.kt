package com.smartprocurement.internal.notifications

data class PushEvent(
    val eventId: String,
    val eventType: String,
    val orderId: String,
    val status: String = ""
)

object PushNotificationPolicy {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 33

    fun canInitialize(consented: Boolean, loggedIn: Boolean, appKey: String): Boolean =
        consented && loggedIn && appKey.isNotBlank()

    fun canNotify(role: String, event: PushEvent): Boolean = when (event.eventType) {
        "ORDER_CREATED" -> role == "admin"
        "ORDER_STATUS_CHANGED" -> role == "unit_user"
        else -> false
    }

    fun channelId(event: PushEvent): String =
        if (event.eventType == "ORDER_CREATED") "new_orders" else "order_updates"

    fun message(event: PushEvent): String = when {
        event.eventType == "ORDER_CREATED" -> "收到新的采购订单，点击查看。"
        event.status == "accepted" -> "订单已接单。"
        event.status == "preparing" -> "订单正在备货。"
        event.status == "shipped" -> "订单已发货，请及时确认收货。"
        event.status == "completed" -> "订单已完成。"
        event.status == "cancelled" -> "订单已取消。"
        else -> "订单状态已更新。"
    }

    fun pendingOrderBadge(count: Int): String = when {
        count <= 0 -> ""
        count > 99 -> "99+"
        else -> count.toString()
    }

    fun pendingOrderReminder(count: Int): String = "有 ${count.coerceAtLeast(0)} 笔采购单等待接单"
}

class ProcessedEventIds(initial: Collection<String> = emptyList()) {
    private val values = LinkedHashSet<String>()

    init {
        initial.toList().takeLast(MAX_SIZE).forEach(values::add)
    }

    fun add(eventId: String): Boolean {
        if (eventId.isBlank() || values.contains(eventId)) return false
        values.add(eventId)
        while (values.size > MAX_SIZE) {
            values.remove(values.first())
        }
        return true
    }

    fun contains(eventId: String): Boolean = values.contains(eventId)

    fun values(): List<String> = values.toList()

    companion object {
        const val MAX_SIZE = 200
    }
}
