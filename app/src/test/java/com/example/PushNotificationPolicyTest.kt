package com.smartprocurement.internal

import com.smartprocurement.internal.notifications.ProcessedEventIds
import com.smartprocurement.internal.notifications.PushEvent
import com.smartprocurement.internal.notifications.PushNotificationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PushNotificationPolicyTest {
    @Test
    fun android_13_requires_runtime_permission_but_android_12_does_not() {
        assertTrue(PushNotificationPolicy.requiresRuntimePermission(33))
        assertTrue(PushNotificationPolicy.requiresRuntimePermission(36))
        assertFalse(PushNotificationPolicy.requiresRuntimePermission(32))
    }

    @Test
    fun sdk_initializes_only_after_consent_login_and_app_key() {
        assertFalse(PushNotificationPolicy.canInitialize(consented = false, loggedIn = true, appKey = "key"))
        assertFalse(PushNotificationPolicy.canInitialize(consented = true, loggedIn = false, appKey = "key"))
        assertFalse(PushNotificationPolicy.canInitialize(consented = true, loggedIn = true, appKey = ""))
        assertTrue(PushNotificationPolicy.canInitialize(consented = true, loggedIn = true, appKey = "key"))
    }

    @Test
    fun role_event_and_channel_mapping_is_explicit() {
        val created = PushEvent("event-1", "ORDER_CREATED", "order-1")
        val changed = PushEvent("event-2", "ORDER_STATUS_CHANGED", "order-1", "shipped")

        assertTrue(PushNotificationPolicy.canNotify("admin", created))
        assertFalse(PushNotificationPolicy.canNotify("unit_user", created))
        assertTrue(PushNotificationPolicy.canNotify("unit_user", changed))
        assertFalse(PushNotificationPolicy.canNotify("admin", changed))
        assertEquals("new_orders", PushNotificationPolicy.channelId(created))
        assertEquals("order_updates", PushNotificationPolicy.channelId(changed))
        assertEquals("收到新的采购订单，点击查看。", PushNotificationPolicy.message(created))
        assertEquals("订单已发货，请及时确认收货。", PushNotificationPolicy.message(changed))
    }

    @Test
    fun admin_pending_order_reminder_uses_a_clear_bounded_badge() {
        assertEquals("", PushNotificationPolicy.pendingOrderBadge(0))
        assertEquals("3", PushNotificationPolicy.pendingOrderBadge(3))
        assertEquals("99+", PushNotificationPolicy.pendingOrderBadge(120))
        assertEquals("有 3 笔采购单等待接单", PushNotificationPolicy.pendingOrderReminder(3))
    }

    @Test
    fun processed_event_ids_are_unique_and_capped_at_200() {
        val processed = ProcessedEventIds()
        repeat(205) { processed.add("event-$it") }

        assertEquals(200, processed.values().size)
        assertFalse(processed.contains("event-0"))
        assertTrue(processed.contains("event-204"))
        assertFalse(processed.add("event-204"))
    }

    @Test
    fun delayed_registration_is_handed_to_a_retrying_worker_and_ui_is_not_left_connecting() {
        val worker = File("src/main/java/com/example/notifications/PushRegistrationWorker.kt")
        val receiver = File("src/main/java/com/example/notifications/OrderPushReceiver.kt").readText()
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(worker.exists())
        val workerSource = worker.readText()
        assertTrue(workerSource.contains("Result.retry()"))
        assertTrue(workerSource.contains("registerPushDevice"))
        assertTrue(receiver.contains("PushRegistrationWorker.schedule"))
        assertTrue(viewModel.contains("PushRegistrationWorker.schedule"))
        assertTrue(viewModel.contains("PushNotificationState.CONNECTION_FAILED"))
        assertTrue(viewModel.contains("连接失败，请点击重试"))
    }

    @Test
    fun jpush_required_common_service_and_receiver_action_are_declared() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val commonService = File("src/main/java/com/example/notifications/JPushCommonService.kt")

        assertTrue(commonService.exists())
        assertTrue(commonService.readText().contains("JCommonService"))
        assertTrue(manifest.contains(".notifications.JPushCommonService"))
        assertTrue(manifest.contains("cn.jiguang.user.service.action"))
        assertTrue(manifest.contains("cn.jpush.android.intent.RECEIVER_MESSAGE"))
    }
}
