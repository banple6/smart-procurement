package com.smartprocurement.internal

import com.smartprocurement.internal.data.RemoteOrderMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
              "created_by": "53a99954-6f13-4947-a13b-3ee6d1c19f83",
              "created_by_username": "unit01",
              "created_at": "2026-06-27 17:12:00",
              "accepted_at": "2026-06-27 17:20:00",
              "preparing_at": "2026-06-27 17:35:00",
              "shipped_at": "2026-06-27 18:00:00",
              "completed_at": "",
              "shipping_note": "已核对数量",
              "shipping_photo_count": 1,
              "shipping_photos": [
                {
                  "id": "photo-1",
                  "thumbnail_url": "/api/v1/orders/remote-order-id/shipping-photos/photo-1?variant=thumbnail",
                  "full_url": "/api/v1/orders/remote-order-id/shipping-photos/photo-1?variant=full",
                  "uploaded_at": "2026-06-27 18:00:00",
                  "uploaded_by_username": "proc_admin",
                  "source": "camera"
                }
              ],
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
        assertEquals("已发货", mapped.order.status)
        assertEquals("东区一楼收货点", mapped.order.deliveryPoint)
        assertEquals("东区食堂", mapped.order.department)
        assertEquals("unit01", mapped.order.requesterName)
        assertEquals("上午配送", mapped.order.remarks)
        assertEquals("2026-06-27 17:12", mapped.order.createdAt)
        assertEquals("2026-06-27 17:20", mapped.order.acceptedAt)
        assertEquals("2026-06-27 17:35", mapped.order.preparingAt)
        assertEquals("2026-06-27 18:00", mapped.order.shippedAt)
        assertEquals("", mapped.order.completedAt)
        assertEquals("已核对数量", mapped.order.shippingNote)
        assertEquals(1, mapped.order.shippingPhotoCount)
        assertEquals("photo-1", mapped.order.shippingPhotos.single().id)
        assertEquals("/api/v1/orders/remote-order-id/shipping-photos/photo-1?variant=thumbnail", mapped.order.shippingPhotos.single().thumbnailUrl)
        assertEquals("proc_admin", mapped.order.shippingPhotos.single().uploadedByUsername)
        assertEquals("西红柿", mapped.items.single().productName)
        assertEquals("一级", mapped.items.single().productSpec)
        assertEquals(4.5, mapped.items.single().price, 0.001)
        assertEquals(2.5, mapped.items.single().requestedQty, 0.001)
    }

    @Test
    fun preparing_order_has_real_stage_times_without_fake_progress_text() {
        val json = JSONObject(
            """
            {
              "id": "internal-uuid",
              "order_no": "SP20260628-000001",
              "unit_name_snapshot": "东区食堂",
              "delivery_point_snapshot": "东区一楼收货点",
              "status": "preparing",
              "created_at": "2026-06-28 10:00:00",
              "accepted_at": "2026-06-28 10:05:00",
              "preparing_at": "2026-06-28 10:10:00",
              "shipped_at": "",
              "completed_at": "",
              "items": []
            }
            """.trimIndent()
        )

        val mapped = RemoteOrderMapper.mapOrder(json)

        assertEquals("SP20260628-000001", mapped.order.displayOrderNo)
        assertEquals("备货中", mapped.order.status)
        assertEquals("2026-06-28 10:00", mapped.order.createdAt)
        assertEquals("2026-06-28 10:05", mapped.order.acceptedAt)
        assertEquals("2026-06-28 10:10", mapped.order.preparingAt)
        assertEquals("", mapped.order.shippedAt)
        assertEquals("", mapped.order.completedAt)
    }

    @Test
    fun preparing_admin_action_does_not_use_generic_shipped_status() {
        assertNull(RemoteOrderMapper.apiStatusForNextUiAction("备货中", isAdmin = true))
        assertEquals("accepted", RemoteOrderMapper.apiStatusForNextUiAction("待接单", isAdmin = true))
        assertEquals("preparing", RemoteOrderMapper.apiStatusForNextUiAction("已接单", isAdmin = true))
        assertEquals("completed", RemoteOrderMapper.apiStatusForNextUiAction("已发货", isAdmin = true))
    }
}
