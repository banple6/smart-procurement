package com.smartprocurement.internal.notifications

enum class PushDeepLinkDestination {
    LOGIN,
    CHANGE_PASSWORD,
    DENIED,
    ORDER_DETAIL
}

object PushDeepLinkPolicy {
    fun destination(
        loggedIn: Boolean,
        mustChangePassword: Boolean,
        orderAuthorized: Boolean
    ): PushDeepLinkDestination = when {
        !loggedIn -> PushDeepLinkDestination.LOGIN
        mustChangePassword -> PushDeepLinkDestination.CHANGE_PASSWORD
        !orderAuthorized -> PushDeepLinkDestination.DENIED
        else -> PushDeepLinkDestination.ORDER_DETAIL
    }

    fun fromExtras(extras: Map<String, String>): PushEvent? {
        val eventId = extras["event_id"].orEmpty()
        val eventType = extras["event_type"].orEmpty()
        val orderId = extras["order_id"].orEmpty()
        if (eventId.isBlank() || orderId.isBlank()) return null
        if (eventType !in setOf("ORDER_CREATED", "ORDER_STATUS_CHANGED")) return null
        return PushEvent(eventId = eventId, eventType = eventType, orderId = orderId)
    }
}
