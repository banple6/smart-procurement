package com.smartprocurement.internal.data

import com.smartprocurement.internal.BuildConfig
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
    val mustChangePassword: Boolean = false
)

data class RemoteLogin(
    val token: String,
    val expiresAt: Long,
    val user: RemoteUser
)

class ProcurementApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
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

    fun products(token: String): List<ProductEntity> {
        val array = requestArray("products", token = token)
        return List(array.length()) { index -> parseProduct(array.getJSONObject(index)) }
    }

    fun saveProduct(token: String, form: ProductEntity): ProductEntity {
        val json = JSONObject()
            .put("product_code", form.code.ifBlank { form.id.take(8) })
            .put("name", form.name)
            .put("category", form.category)
            .put("spec", form.spec)
            .put("unit", form.unit)
            .put("price_cents", (form.price * 100).toInt())
            .put("stock_quantity", form.stockQuantity.ifBlank { "0" })
            .put("min_order_quantity", form.minQty.toCleanString())
            .put("quantity_step", form.stepQty.toCleanString())
            .put("warning_quantity", form.warningQuantity.ifBlank { "0" })
            .put("origin", form.origin)
            .put("shelf_life", form.shelfLife)
            .put("storage_method", form.storageMethod)
            .put("description", form.remark)
            .put("supply_status", form.status.toApiStatus())
            .put("active", form.isAvailable)
        val path = if (form.id.isBlank() || form.id.length < 12) "admin/products" else "admin/products/${form.id}"
        val method = if (form.id.isBlank() || form.id.length < 12) "POST" else "PUT"
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
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(responseBody).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "服务器错误 ${response.code}" })
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
            stockQuantity = json.optString("stock_quantity", "0"),
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

    private fun Double.toCleanString(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
