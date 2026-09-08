package com.smartprocurement.internal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundDomainAlignmentTest {
    @Test
    fun `android completes only legacy pending outbounds through the authoritative endpoint`() {
        val api = File("src/main/java/com/example/data/ProcurementApiClient.kt").readText()
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()
        val detail = File("src/main/java/com/example/ui/OutboundScreens.kt")
            .readText()
            .substringBefore("fun OutboundShippingProofScreen")
        val batches = File("src/main/java/com/example/ui/DeliveryBatchScreens.kt").readText()

        assertTrue(api.contains("admin/outbounds/\${outbound.id}/complete"))
        assertTrue(api.contains(".put(\"expected_version\", outbound.version)"))
        assertTrue(api.contains(".put(\"client_request_id\", requestId)"))
        assertTrue(detail.contains("完成出库单"))
        assertTrue(detail.contains("无需上传照片"))
        assertFalse(detail.contains("拍照并确认发货"))
        assertFalse(batches.contains("按单位生成出库单"))
        assertFalse(viewModel.contains("apiClient.generateOutboundOrders"))
    }

    @Test
    fun `completion reconciliation only refetches server authority`() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(viewModel.contains("refreshOrders()"))
        assertTrue(viewModel.contains("refreshProducts()"))
        assertTrue(viewModel.contains("apiClient.outboundOrderDetail(authToken, outboundId) to apiClient.outboundOrders(authToken)"))
        assertFalse(viewModel.contains("stock -="))
        assertFalse(viewModel.contains("quota -="))
    }
}
