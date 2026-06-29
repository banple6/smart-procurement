package com.smartprocurement.internal.data

import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.domain.money.Money
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RemoteUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val unitId: String,
    val unitCode: String = "",
    val unitName: String = "",
    val defaultDeliveryPoint: String = "",
    val mustChangePassword: Boolean = false
)

data class RemoteLogin(
    val token: String,
    val expiresAt: Long,
    val user: RemoteUser
)

data class DashboardOrder(
    val id: String,
    val orderNo: String,
    val unitName: String,
    val status: String,
    val totalCents: Long,
    val createdAt: String
)

data class DemandRankItem(
    val name: String,
    val unit: String,
    val quantity: String
)

data class AdminDashboard(
    val todayOrders: Int = 0,
    val pending: Int = 0,
    val preparing: Int = 0,
    val shipped: Int = 0,
    val todayTotalCents: Long = 0,
    val tightInventory: Int = 0,
    val recentOrders: List<DashboardOrder> = emptyList(),
    val demandRank: List<DemandRankItem> = emptyList()
)

data class RemoteUnit(
    val id: String,
    val unitCode: String,
    val unitName: String,
    val defaultDeliveryPoint: String,
    val active: Boolean,
    val accountCount: Int = 0,
    val orderCount: Int = 0,
    val lastOrderAt: String = ""
)

data class RemoteAdminUser(
    val id: String,
    val username: String,
    val displayName: String,
    val unitId: String,
    val unitName: String,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val lastLoginAt: String = ""
)

data class LedgerRow(
    val orderNo: String,
    val unitName: String,
    val deliveryPoint: String,
    val createdAt: String,
    val status: String,
    val productName: String,
    val productSpec: String,
    val productUnit: String,
    val quantity: String,
    val subtotalCents: Long,
    val totalCents: Long
)

class ProcurementApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val uploadClient: OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun login(username: String, password: String): RemoteLogin {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
            .toRequestBody(JSON)
        val json = request("auth/login", method = "POST", body = body)
        return RemoteLogin(
            token = json.getString("token"),
            expiresAt = json.getLong("expires_at"),
            user = parseUser(json.getJSONObject("user"))
        )
    }

    fun me(token: String): RemoteUser = parseUser(request("auth/me", token = token))

    fun logout(token: String) {
        request("auth/logout", token = token, method = "POST")
    }

    fun changePassword(token: String, oldPassword: String, newPassword: String) {
        val body = JSONObject()
            .put("old_password", oldPassword)
            .put("new_password", newPassword)
            .toString()
            .toRequestBody(JSON)
        request("auth/change-password", token = token, method = "POST", body = body)
    }

    fun products(token: String): List<ProductEntity> {
        val array = requestArray("products", token = token)
        return List(array.length()) { index -> parseProduct(array.getJSONObject(index)) }
    }

    fun saveProduct(token: String, form: ProductEntity): ProductEntity {
        val json = JSONObject()
            .put("product_code", form.code.ifBlank { "P${System.currentTimeMillis()}" })
            .put("name", form.name)
            .put("category", form.category)
            .put("spec", form.spec)
            .put("unit", form.unit)
            .put("price_cents", Money.yuanDoubleToCents(form.price))
            .put("stock_quantity", form.stockQuantity.ifBlank { "0" })
            .put("min_order_quantity", form.minQty.toCleanString())
            .put("quantity_step", form.stepQty.toCleanString())
            .put("warning_quantity", form.warningQuantity.ifBlank { "0" })
            .put("origin", form.origin)
            .put("supplier", form.packagingSpec)
            .put("shelf_life", form.shelfLife)
            .put("storage_method", form.storageMethod)
            .put("description", form.remark)
            .put("supply_status", form.status.toApiStatus())
            .put("active", form.isAvailable)
        val path = if (form.id.isBlank()) "admin/products" else "admin/products/${form.id}"
        val method = if (form.id.isBlank()) "POST" else "PUT"
        return parseProduct(request(path, token = token, method = method, body = json.toString().toRequestBody(JSON)))
    }

    fun uploadProductImage(token: String, productId: String, filePath: String): String {
        val file = File(filePath)
        val type = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }.toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(type))
            .build()
        return request("admin/products/$productId/image", token = token, method = "POST", body = body).getString("image_path")
    }

    fun setProductStatus(token: String, productId: String, status: String, active: Boolean): ProductEntity {
        val body = JSONObject()
            .put("supply_status", status)
            .put("active", active)
            .toString()
            .toRequestBody(JSON)
        return parseProduct(request("admin/products/$productId/status", token = token, method = "PATCH", body = body))
    }

    fun deleteProduct(token: String, productId: String) {
        request("admin/products/$productId", token = token, method = "DELETE")
    }

    fun restoreProduct(token: String, productId: String): ProductEntity {
        return parseProduct(request("admin/products/$productId/restore", token = token, method = "POST"))
    }

    fun createOrder(token: String, note: String, items: List<Pair<String, Double>>): JSONObject {
        val array = JSONArray()
        items.forEach { (productId, quantity) ->
            array.put(JSONObject().put("product_id", productId).put("quantity", quantity.toCleanString()))
        }
        val body = JSONObject()
            .put("client_request_id", UUID.randomUUID().toString())
            .put("note", note)
            .put("items", array)
            .toString()
            .toRequestBody(JSON)
        return request("orders", token = token, method = "POST", body = body)
    }

    fun orders(token: String, isAdmin: Boolean): List<RemoteOrderBundle> {
        val path = if (isAdmin) "admin/orders?include_items=true" else "orders?include_items=true"
        val json = request(path, token = token)
        val array = json.getJSONArray("items")
        return List(array.length()) { index ->
            RemoteOrderMapper.mapOrder(array.getJSONObject(index))
        }
    }

    fun dashboard(token: String): AdminDashboard {
        val json = request("admin/dashboard", token = token)
        val recent = json.optJSONArray("recent_orders")
        val rank = json.optJSONArray("demand_rank")
        return AdminDashboard(
            todayOrders = json.optInt("today_orders", 0),
            pending = json.optInt("pending", 0),
            preparing = json.optInt("preparing", 0),
            shipped = json.optInt("shipped", 0),
            todayTotalCents = json.optLong("today_total_cents", 0),
            tightInventory = json.optInt("tight_inventory", 0),
            recentOrders = List(recent?.length() ?: 0) { index ->
                val item = recent!!.getJSONObject(index)
                DashboardOrder(
                    id = item.optString("id"),
                    orderNo = item.optString("order_no"),
                    unitName = item.optString("unit_name_snapshot"),
                    status = item.optString("status").toUiOrderStatus(),
                    totalCents = item.optLong("total_cents", 0),
                    createdAt = item.optString("created_at").replace('T', ' ').take(16)
                )
            },
            demandRank = List(rank?.length() ?: 0) { index ->
                val item = rank!!.getJSONObject(index)
                DemandRankItem(
                    name = item.optString("name"),
                    unit = item.optString("unit"),
                    quantity = item.optString("quantity")
                )
            }
        )
    }

    fun units(token: String): List<RemoteUnit> {
        val array = requestArray("admin/units", token = token)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RemoteUnit(
                id = item.getString("id"),
                unitCode = item.optString("unit_code"),
                unitName = item.optString("unit_name"),
                defaultDeliveryPoint = item.optString("default_delivery_point"),
                active = item.optBooleanCompat("active"),
                accountCount = item.optInt("account_count", 0),
                orderCount = item.optInt("order_count", 0),
                lastOrderAt = item.optString("last_order_at").replace('T', ' ').take(16)
            )
        }
    }

    fun saveUnit(token: String, id: String, code: String, name: String, deliveryPoint: String): RemoteUnit {
        val body = JSONObject()
            .put("unit_code", code)
            .put("unit_name", name)
            .put("default_delivery_point", deliveryPoint)
            .toString()
            .toRequestBody(JSON)
        val json = if (id.isBlank()) {
            request("admin/units", token = token, method = "POST", body = body)
        } else {
            request("admin/units/$id", token = token, method = "PUT", body = body)
        }
        return RemoteUnit(json.getString("id"), json.optString("unit_code"), json.optString("unit_name"), json.optString("default_delivery_point"), json.optBooleanCompat("active"))
    }

    fun setUnitStatus(token: String, id: String, active: Boolean): RemoteUnit {
        val body = JSONObject().put("active", active).toString().toRequestBody(JSON)
        val json = request("admin/units/$id/status", token = token, method = "PATCH", body = body)
        return RemoteUnit(json.getString("id"), json.optString("unit_code"), json.optString("unit_name"), json.optString("default_delivery_point"), json.optBooleanCompat("active"))
    }

    fun users(token: String): List<RemoteAdminUser> {
        val array = requestArray("admin/users", token = token)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RemoteAdminUser(
                id = item.getString("id"),
                username = item.optString("username"),
                displayName = item.optString("display_name"),
                unitId = item.optString("unit_id"),
                unitName = item.optString("unit_name"),
                active = item.optBooleanCompat("active"),
                mustChangePassword = item.optBooleanCompat("must_change_password"),
                lastLoginAt = item.optString("last_login_at").replace('T', ' ').take(16)
            )
        }
    }

    fun createUnitUser(token: String, username: String, displayName: String, unitId: String, password: String): RemoteAdminUser {
        val body = JSONObject()
            .put("username", username)
            .put("display_name", displayName)
            .put("unit_id", unitId)
            .put("password", password)
            .put("role", "unit_user")
            .put("must_change_password", true)
            .toString()
            .toRequestBody(JSON)
        val json = request("admin/users", token = token, method = "POST", body = body)
        return RemoteAdminUser(json.getString("id"), json.optString("username"), json.optString("display_name"), json.optString("unit_id"), "", json.optBooleanCompat("active"), json.optBooleanCompat("must_change_password"))
    }

    fun setUserStatus(token: String, id: String, active: Boolean): RemoteAdminUser {
        val body = JSONObject().put("active", active).toString().toRequestBody(JSON)
        val json = request("admin/users/$id/status", token = token, method = "PATCH", body = body)
        return RemoteAdminUser(json.getString("id"), json.optString("username"), json.optString("display_name"), json.optString("unit_id"), json.optString("unit_name"), json.optBooleanCompat("active"), json.optBooleanCompat("must_change_password"))
    }

    fun resetPassword(token: String, id: String, newPassword: String) {
        val body = JSONObject()
            .put("new_password", newPassword)
            .put("must_change_password", true)
            .toString()
            .toRequestBody(JSON)
        request("admin/users/$id/reset-password", token = token, method = "POST", body = body)
    }

    fun ledger(token: String): List<LedgerRow> {
        val array = requestArray("admin/ledger", token = token)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            LedgerRow(
                orderNo = item.optString("order_no"),
                unitName = item.optString("unit_name_snapshot"),
                deliveryPoint = item.optString("delivery_point_snapshot"),
                createdAt = item.optString("created_at").replace('T', ' ').take(16),
                status = item.optString("status").toUiOrderStatus(),
                productName = item.optString("product_name_snapshot"),
                productSpec = item.optString("spec_snapshot"),
                productUnit = item.optString("unit_snapshot"),
                quantity = item.optString("quantity"),
                subtotalCents = item.optLong("subtotal_cents", 0),
                totalCents = item.optLong("total_cents", 0)
            )
        }
    }

    fun exportLedger(token: String): ByteArray {
        return executeBytes("admin/ledger/export.xlsx", token)
    }

    fun orderDetail(token: String, orderId: String, isAdmin: Boolean): RemoteOrderBundle {
        val path = if (isAdmin) "admin/orders/$orderId" else "orders/$orderId"
        return RemoteOrderMapper.mapOrder(request(path, token = token))
    }

    fun setAdminOrderStatus(token: String, orderId: String, status: String): RemoteOrderBundle {
        val body = JSONObject()
            .put("status", status)
            .toString()
            .toRequestBody(JSON)
        return RemoteOrderMapper.mapOrder(
            request("admin/orders/$orderId/status", token = token, method = "PATCH", body = body)
        )
    }

    fun shipOrder(token: String, orderId: String, photoFiles: List<File>, note: String, clientRequestId: String): RemoteOrderBundle {
        val imageType = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("note", note)
            .addFormDataPart("client_request_id", clientRequestId)
            .apply {
                photoFiles.forEachIndexed { index, file ->
                    addFormDataPart("photos", "shipping_${index + 1}.jpg", file.asRequestBody(imageType))
                }
            }
            .build()
        return RemoteOrderMapper.mapOrder(
            request("admin/orders/$orderId/ship", token = token, method = "POST", body = body)
        )
    }

    fun cancelOrder(token: String, orderId: String): RemoteOrderBundle {
        return RemoteOrderMapper.mapOrder(
            request("orders/$orderId/cancel", token = token, method = "POST")
        )
    }

    fun confirmReceipt(token: String, orderId: String): RemoteOrderBundle {
        return RemoteOrderMapper.mapOrder(
            request("orders/$orderId/confirm-receipt", token = token, method = "POST")
        )
    }

    private fun requestArray(path: String, token: String = ""): JSONArray {
        val text = execute(path, token)
        return JSONArray(text)
    }

    private fun request(path: String, token: String = "", method: String = "GET", body: okhttp3.RequestBody? = null): JSONObject {
        return JSONObject(execute(path, token, method, body))
    }

    private fun execute(path: String, token: String = "", method: String = "GET", body: okhttp3.RequestBody? = null): String {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val request = when (method) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody()).build()
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody()).build()
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody()).build()
            "DELETE" -> builder.delete().build()
            else -> builder.get().build()
        }
        val callClient = if (body is MultipartBody) uploadClient else client
        callClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(responseBody).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { response.code.toChineseError() }.toChineseDetail(response.code))
            }
            return responseBody
        }
    }

    private fun executeBytes(path: String, token: String = ""): ByteArray {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        client.newCall(builder.get().build()).execute().use { response ->
            val responseBody = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw IllegalStateException(response.code.toChineseError())
            }
            return responseBody
        }
    }

    private fun parseUser(json: JSONObject): RemoteUser = RemoteUser(
        id = json.getString("id"),
        username = json.getString("username"),
        displayName = json.getString("display_name"),
        role = json.getString("role"),
        unitId = json.optString("unit_id"),
        unitCode = json.optString("unit_code"),
        unitName = json.optString("unit_name"),
        defaultDeliveryPoint = json.optString("default_delivery_point"),
        mustChangePassword = json.optBoolean("must_change_password", false)
    )

    private fun parseProduct(json: JSONObject): ProductEntity {
        val status = json.optString("supply_status", "normal")
        val active = json.optBoolean("active", true)
        return ProductEntity(
            id = json.getString("id"),
            name = json.getString("name"),
            spec = json.getString("spec"),
            unit = json.getString("unit"),
            imageUrl = json.optString("image_path").toAbsoluteImageUrl(),
            origin = json.optString("origin"),
            minQty = json.optString("min_order_quantity", "1").toDoubleOrNull() ?: 1.0,
            stepQty = json.optString("quantity_step", "1").toDoubleOrNull() ?: 1.0,
            allowSubstitute = true,
            stockStatus = if (status == "tight") "紧张" else "充足",
            price = json.optInt("price_cents", 0) / 100.0,
            category = json.optString("category", "其他"),
            code = json.optString("product_code"),
            imagePath = json.optString("image_path").toAbsoluteImageUrl(),
            packagingSpec = json.optString("supplier"),
            stockQuantity = json.optString("stock_quantity", "0"),
            reservedQuantity = json.optString("reserved_quantity", "0"),
            warningQuantity = json.optString("warning_quantity", "0"),
            availableQuantity = json.optString("available_quantity", "0"),
            storageMethod = json.optString("storage_method"),
            shelfLife = json.optString("shelf_life"),
            status = status.toUiStatus(active),
            isAvailable = active,
            remark = json.optString("description"),
            isDeleted = json.optBoolean("is_deleted", false),
            createdBy = json.optString("created_by"),
        )
    }

    private fun String.toAbsoluteImageUrl(): String {
        if (isBlank() || startsWith("http")) return this
        return BuildConfig.API_BASE_URL.substringBefore("/api/v1/").trimEnd('/') + this
    }

    private fun String.toUiStatus(active: Boolean): String = when {
        !active || this == "off_shelf" -> "已下架"
        this == "tight" -> "库存紧张"
        this == "paused" -> "暂停供应"
        else -> "正常供应"
    }

    private fun String.toApiStatus(): String = when (this) {
        "库存紧张" -> "tight"
        "暂停供应" -> "paused"
        "已下架" -> "off_shelf"
        else -> "normal"
    }

    private fun String.toUiOrderStatus(): String = when (this) {
        "accepted" -> "已接单"
        "preparing" -> "备货中"
        "shipped" -> "已发货"
        "completed" -> "已完成"
        "cancelled" -> "已取消"
        else -> "待接单"
    }

    private fun Double.toCleanString(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

    private fun JSONObject.optBooleanCompat(name: String): Boolean {
        val value = opt(name)
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun Int.toChineseError(): String = when (this) {
        401 -> "登录已过期，请重新登录"
        403 -> "账号已停用，请联系管理员"
        409 -> "订单状态已变化，请刷新后重试"
        else -> "网络连接失败，请稍后重试"
    }

    private fun String.toChineseDetail(code: Int): String = when {
        isBlank() -> code.toChineseError()
        contains("Unit unavailable", ignoreCase = true) -> "所属单位已停用，请联系管理员"
        contains("User disabled", ignoreCase = true) -> "账号已停用，请联系管理员"
        contains("Illegal status transition", ignoreCase = true) -> "订单状态已变化，请刷新后重试"
        contains("Only pending orders", ignoreCase = true) -> "订单状态已变化，请刷新后重试"
        contains("Only shipped orders", ignoreCase = true) -> "订单状态已变化，请刷新后重试"
        contains("Order not found", ignoreCase = true) -> "订单不存在或无权查看"
        contains("Order requires items", ignoreCase = true) -> "请先选择食材"
        contains("Product not found", ignoreCase = true) -> "食材不存在"
        contains("Invalid supply status", ignoreCase = true) -> "供应状态不正确"
        contains("No fields", ignoreCase = true) -> "请填写需要保存的内容"
        contains("Invalid role", ignoreCase = true) -> "账号角色不正确"
        contains("unit_id required", ignoreCase = true) -> "请选择所属单位"
        contains("Image too large", ignoreCase = true) -> "图片过大，请重新选择"
        contains("Unsupported image type", ignoreCase = true) -> "图片格式不支持"
        contains("Invalid image file", ignoreCase = true) -> "图片文件无效"
        else -> this
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
