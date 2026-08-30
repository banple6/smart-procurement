package com.smartprocurement.internal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossClientSyncPolicyTest {
    @Test
    fun push_is_only_an_invalidation_signal_not_a_room_write() {
        val receiver = File("src/main/java/com/example/notifications/OrderPushReceiver.kt").readText()
        val worker = File("src/main/java/com/example/notifications/OrderSyncWorker.kt").readText()

        assertTrue(receiver.contains("onNotifyMessageArrived"))
        assertTrue(receiver.contains("OrderSyncWorker.scheduleImmediate"))
        assertFalse(receiver.contains("SupplyRepository"))
        assertTrue(worker.contains("ExistingWorkPolicy.KEEP"))
    }

    @Test
    fun foreground_and_network_recovery_refresh_server_backed_data() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(viewModel.contains("CRITICAL_FOREGROUND_REFRESH_MILLIS = 10_000L"))
        assertTrue(viewModel.contains("BACKGROUND_FOREGROUND_REFRESH_MILLIS = 60_000L"))
        assertTrue(viewModel.contains("delay(foregroundRefreshIntervalMillis())"))
        assertTrue(viewModel.contains("onAppResumed()"))
        assertTrue(viewModel.contains("onAppPaused()"))
        assertTrue(viewModel.contains("registerDefaultNetworkCallback(networkCallback)"))
        assertTrue(viewModel.contains("refreshForegroundScreen()"))
        assertTrue(viewModel.contains("if (currentUser?.role == \"unit_user\") refreshUnitQuota()"))
        assertTrue(viewModel.contains("is Screen.UnitQuotaManagement -> refreshAdminQuotaUnits()"))
        assertTrue(viewModel.contains("is Screen.UnitQuotaDetail -> refreshAdminQuotaDetail(screen.unitId)"))
        assertTrue(viewModel.contains("private fun refreshCurrentMainTab()"))
        assertTrue(viewModel.contains("\"dashboard\" -> refreshDashboard()"))
        assertTrue(viewModel.contains("\"orders\" -> refreshOrders()"))
    }

    @Test
    fun foreground_sync_starts_when_authentication_finishes_after_resume() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(viewModel.contains("private fun startForegroundSyncIfAppIsActive()"))
        assertTrue(viewModel.contains("if (appInForeground) startForegroundSync()"))
        // Both saved-session recovery and an interactive login must arm the same loop.
        assertTrue(viewModel.split("startForegroundSyncIfAppIsActive()").size - 1 >= 3)
    }

    @Test
    fun critical_writes_are_not_queued_while_offline() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(viewModel.contains("fun requireNetworkForWrite(): Boolean"))
        assertTrue(viewModel.contains("当前网络不可用，请联网后重试。"))
        assertTrue(viewModel.contains("fun performOrderAction"))
        assertTrue(viewModel.contains("fun submitShippingProof"))
        assertTrue(viewModel.contains("fun createDeliveryBatch"))
    }
}
