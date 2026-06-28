package com.example

import com.example.data.RemoteOrderMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteOrderMapperTest {
    @Test
    fun maps_remote_order_header_and_items_for_order_ui() {
        val json = JSONObject(
            """
            {
              "id": "remote-order-id",
              "order_no": "SP00000001",
              "unit_name_snapshot": "东区食堂",
              "delivery_point_snapshot": "东区一楼收货点",
              "note": "上午配送",
              "status": "shipped",
              "created_at": "2026-06-27 17:12:00",
              "items": [
                {
                  "product_id": "prod-tomato",
                  "product_name_snapshot": "西红柿",
                  "spec_snapshot": "一级",
                  "unit_snapshot": "公斤",
                  "price_cents_snapshot": 450,
                  "quantity": "2.5"
                }
              ]
            }
            """.trimIndent()
        )

        val mapped = RemoteOrderMapper.mapOrder(json)

        assertEquals("remote-order-id", mapped.order.orderId)
        assertEquals("SP00000001", mapped.order.displayOrderNo)
        assertEquals("配送中", mapped.order.status)
        assertEquals("东区一楼收货点", mapped.order.deliveryPoint)
        assertEquals("东区食堂", mapped.order.department)
        assertEquals("上午配送", mapped.order.remarks)
        assertEquals("西红柿", mapped.items.single().productName)
        assertEquals("一级", mapped.items.single().productSpec)
        assertEquals(4.5, mapped.items.single().price, 0.001)
        assertEquals(2.5, mapped.items.single().requestedQty, 0.001)
    }
}
