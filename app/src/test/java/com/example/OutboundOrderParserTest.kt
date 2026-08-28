package com.smartprocurement.internal

import com.smartprocurement.internal.data.ProcurementApiClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboundOrderParserTest {
    @Test
    fun `maps outbound detail with snapshot line values and orders`() {
        val client = ProcurementApiClient("http://127.0.0.1/api/v1/")
        val outbound = client.parseOutboundOrder(
            JSONObject(
                """
                {
                  "id":"outbound-a", "outbound_no":"CK20260828-001",
                  "preparation_batch_id":"batch-a", "batch_no":"BH20260828-001",
                  "unit_id":"unit-a", "unit_name_snapshot":"第一食堂",
                  "delivery_point_snapshot":"东门收货点", "status":"pending",
                  "created_at":"2026-08-28 01:35:08", "version":3,
                  "order_count":1, "product_count":1, "total_cents":1200,
                  "orders":[{"id":"order-a","order_no":"SP001","status":"preparing","total_cents":1200,"delivery_point_snapshot":"东门收货点"}],
                  "lines":[{"product_id":"potato","category":"蔬菜","product_name":"土豆","spec":"散装","unit":"斤","quantity":"10","price_cents_snapshot":120,"subtotal_cents":1200}]
                }
                """.trimIndent()
            )
        )

        assertEquals("outbound-a", outbound.id)
        assertEquals("待发货", outboundStatus(outbound.status))
        assertEquals("第一食堂", outbound.unitName)
        assertEquals(1200L, outbound.totalCents)
        assertEquals("10", outbound.lines.single().quantity)
        assertEquals(120L, outbound.lines.single().priceCentsSnapshot)
        assertEquals("备货中", outbound.orders.single().status)
    }

    private fun outboundStatus(value: String) = when (value) {
        "pending" -> "待发货"
        else -> value
    }
}
