package com.smartprocurement.internal

import com.smartprocurement.internal.notifications.PushDeepLinkDestination
import com.smartprocurement.internal.notifications.PushDeepLinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PushDeepLinkPolicyTest {
    @Test
    fun deep_link_never_bypasses_login_password_or_server_authorization() {
        assertEquals(
            PushDeepLinkDestination.LOGIN,
            PushDeepLinkPolicy.destination(loggedIn = false, mustChangePassword = false, orderAuthorized = false)
        )
        assertEquals(
            PushDeepLinkDestination.CHANGE_PASSWORD,
            PushDeepLinkPolicy.destination(loggedIn = true, mustChangePassword = true, orderAuthorized = false)
        )
        assertEquals(
            PushDeepLinkDestination.DENIED,
            PushDeepLinkPolicy.destination(loggedIn = true, mustChangePassword = false, orderAuthorized = false)
        )
        assertEquals(
            PushDeepLinkDestination.ORDER_DETAIL,
            PushDeepLinkPolicy.destination(loggedIn = true, mustChangePassword = false, orderAuthorized = true)
        )
    }

    @Test
    fun only_expected_push_extras_are_accepted() {
        val valid = PushDeepLinkPolicy.fromExtras(
            mapOf("event_id" to "event-1", "event_type" to "ORDER_CREATED", "order_id" to "order-1")
        )
        assertEquals("event-1", valid?.eventId)
        assertEquals("order-1", valid?.orderId)
        assertEquals(null, PushDeepLinkPolicy.fromExtras(mapOf("event_id" to "event-1", "role" to "admin")))
        assertEquals(null, PushDeepLinkPolicy.fromExtras(mapOf("event_type" to "UNKNOWN", "order_id" to "order-1")))
    }
}
