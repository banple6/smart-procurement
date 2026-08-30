package com.smartprocurement.internal.data

import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.quantity.QuantityFormatter
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
import java.net.URLEncoder
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
    val user: RemoteUser,
    val status: String = "active",
    val pendingApproval: Boolean = false,
    val message: String = ""
)

data class UnitQuota(
    val enabled: Boolean = false,
    val quotaMonth: String = "",
    val defaultMonthlyQuotaCents: Long = 0,
    val baseQuotaCents: Long = 0,
    val openingBalanceCents: Long = 0,
    val adjustmentCents: Long = 0,
    val availableCents: Long = 0,
    val usedThisMonthCents: Long = 0,
    val version: Int = 0,
    val updatedAt: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): UnitQuota = UnitQuota(
            enabled = json.optBoolean("enabled", false),
            quotaMonth = json.optString("quota_month"),
            defaultMonthlyQuotaCents = json.optLong("default_monthly_quota_cents", 0),
            baseQuotaCents = json.optLong("base_quota_cents", 0),
            openingBalanceCents = json.optLong("opening_balance_cents", 0),
            adjustmentCents = json.optLong("adjustment_cents", 0),
            availableCents = json.optLong("available_cents", 0),
            usedThisMonthCents = json.optLong("used_this_month_cents", 0),
            version = json.optInt("version", 0),
            updatedAt = json.optString("display_updated_at").ifBlank { json.optString("updated_at") }
        )
    }
}

data class UnitQuotaLedgerRow(
    val id: String,
    val eventType: String,
    val deltaCents: Long,
    val balanceAfterCents: Long,
    val orderNo: String,
    val note: String,
    val createdAt: String
)

data class RemoteOrderPage(
    val items: List<RemoteOrderBundle>,
    val total: Int,
    val nextCursor: String,
    val hasMore: Boolean
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
    val openReceiptIssues: Int = 0,
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
    val lastOrderAt: String = "",
    val quota: UnitQuota = UnitQuota()
)

class ApiRequestException(
    val statusCode: Int,
    val errorCode: String? = null,
    message: String
) : IllegalStateException(message)

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

data class InventoryAdjustRemoteResult(
    val product: ProductEntity,
    val beforeStockQuantity: String,
    val afterStockQuantity: String,
    val reservedQuantity: String,
    val availableQuantity: String
)

data class CutoffInfo(
    val businessDate: String = "",
    val enabled: Boolean = false,
    val cutoffTime: String = "",
    val isClosed: Boolean = false,
    val remainingSeconds: Long = 0
)

data class PreparationSummaryItem(
    val productName: String,
    val spec: String,
    val unit: String,
    val requestedQuantity: String,
    val actualQuantity: String,
    val unitCount: Int,
    val orderCount: Int
)

data class DeliverySheetUnit(
    val unitName: String,
    val deliveryPoint: String,
    val orderCount: Int,
    val itemCount: Int
)

data class DeliveryBatchOrder(
    val id: String,
    val orderNo: String,
    val unitId: String,
    val unitName: String,
    val deliveryPoint: String,
    val status: String,
    val totalCents: Long,
    val createdAt: String,
    val version: Int = 1
)

data class DeliveryBatch(
    val id: String,
    val batchNo: String,
    val name: String,
    val note: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Int,
    val orderCount: Int,
    val unitCount: Int,
    val orders: List<DeliveryBatchOrder> = emptyList()
)

data class BatchSummaryItem(
    val productId: String,
    val productName: String,
    val category: String,
    val spec: String,
    val unit: String,
    val requestedQuantity: String,
    val actualQuantity: String,
    val subtotalCents: Long
)

data class BatchUnitSummary(
    val unitId: String,
    val unitName: String,
    val deliveryPoint: String,
    val orderCount: Int,
    val totalCents: Long,
    val items: List<BatchSummaryItem>
)

data class BatchUnitBreakdown(
    val unitId: String,
    val unitName: String,
    val requestedQuantity: String,
    val actualQuantity: String
)

data class BatchProductSummary(
    val productId: String,
    val productName: String,
    val category: String,
    val spec: String,
    val unit: String,
    val requestedQuantity: String,
    val actualQuantity: String,
    val subtotalCents: Long,
    val unitCount: Int,
    val unitBreakdown: List<BatchUnitBreakdown>
)

data class DeliveryBatchSummary(
    val batch: DeliveryBatch,
    val orderCount: Int,
    val unitCount: Int,
    val productCount: Int,
    val totalCents: Long,
    val byUnit: List<BatchUnitSummary>,
    val byProduct: List<BatchProductSummary>
)

data class OutboundOrderLine(
    val productId: String,
    val category: String,
    val productName: String,
    val spec: String,
    val unit: String,
    val quantity: String,
    val priceCentsSnapshot: Long,
    val subtotalCents: Long
)

data class OutboundOrderRef(
    val id: String,
    val orderNo: String,
    val status: String,
    val totalCents: Long,
    val deliveryPoint: String
)

data class OutboundOrder(
    val id: String,
    val outboundNo: String,
    val preparationBatchId: String,
    val batchNo: String,
    val unitId: String,
    val unitName: String,
    val deliveryPoint: String,
    val status: String,
    val createdAt: String,
    val shippedAt: String,
    val version: Int,
    val orderCount: Int,
    val productCount: Int,
    val totalCents: Long,
    val orders: List<OutboundOrderRef> = emptyList(),
    val lines: List<OutboundOrderLine> = emptyList()
)

data class SystemResources(
    val scope: String = "container",
    val cpuPercent: Double = 0.0,
    val memoryUsedBytes: Long = 0,
    val memoryTotalBytes: Long = 0,
    val diskUsedBytes: Long = 0,
    val diskTotalBytes: Long = 0,
    val diskFreeBytes: Long = 0
)

data class SystemPerformance(
    val requestCount5m: Int = 0,
    val averageLatencyMs: Int = 0,
    val p95LatencyMs: Int = 0,
    val errorCount5m: Int = 0,
    val errorRatePercent: Double = 0.0,
    val sqliteLockCount24h: Int = 0
)

data class SystemSessions(
    val activeAppSessions: Int = 0,
    val activeWebSessions: Int = 0
)

data class SystemStorage(
    val databaseBytes: Long = 0,
    val productImagesBytes: Long = 0,
    val shippingPhotosBytes: Long = 0,
    val receiptIssuePhotosBytes: Long = 0,
    val backupsBytes: Long = 0
)

data class SystemServices(
    val api: String = "unknown",
    val database: String = "unknown",
    val uploads: String = "unknown",
    val backup: String = "unknown",
    val web: String = "unknown",
    val sms: String = "disabled"
)

data class SystemCapacity(
    val status: String = "sufficient",
    val summary: String = "",
    val disclaimer: String = "",
    val apiP95LatencyMs: Int = 0,
    val errorRatePercent: Double = 0.0,
    val sqliteLockCount24h: Int = 0
)

data class LatestBackup(
    val status: String = "missing",
    val createdAt: String = "",
    val sizeBytes: Long = 0,
    val verified: Boolean = false,
    val offsiteSynced: Boolean = false,
    val appVersion: String = "",
    val databaseVersion: String = ""
)

data class SystemAlert(
    val level: String = "info",
    val title: String = "",
    val occurredAt: String = "",
    val impact: String = "",
    val suggestion: String = ""
)

data class SystemOverview(
    val overallStatus: String = "healthy",
    val checkedAt: String = "",
    val uptimeSeconds: Long = 0,
    val resources: SystemResources = SystemResources(),
    val performance: SystemPerformance = SystemPerformance(),
    val sessions: SystemSessions = SystemSessions(),
    val storage: SystemStorage = SystemStorage(),
    val services: SystemServices = SystemServices(),
    val capacity: SystemCapacity = SystemCapacity(),
    val latestBackup: LatestBackup = LatestBackup(),
    val alerts: List<SystemAlert> = emptyList(),
    val todayOrders: Int = 0,
    val pendingOrders: Int = 0,
    val preparingOrders: Int = 0,
    val shippedOrders: Int = 0,
    val activeUnits: Int = 0,
    val activeUsers: Int = 0,
    val products: Int = 0,
    val openReceiptIssues: Int = 0,
    val latestBackupStatus: String = "",
    val latestBackupTime: String = ""
)

data class WebLoginBrowserInfo(
    val browserName: String = "",
    val browserOs: String = "",
    val browserIp: String = "",
    val deviceName: String = ""
) {
    val name: String get() = browserName
    val os: String get() = browserOs
    val ip: String get() = browserIp
}

data class WebLoginScanResult(
    val loginToken: String,
    val websiteName: String = "三公鲜配管理平台",
    val websiteHost: String = "",
    val browser: WebLoginBrowserInfo,
    val targetRole: String = "",
    val username: String = "",
    val displayName: String = "",
    val unitName: String = "",
    val createdAt: String = "",
    val expiresAt: String = ""
) {
    val deviceName: String get() = browser.deviceName
}

data class WebSessionRecord(
    val id: String,
    val browserName: String = "",
    val browserOs: String = "",
    val browserIp: String = "",
    val deviceName: String = "",
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val active: Boolean = true
)

data class InviteInspectResult(
    val token: String,
    val valid: Boolean = false,
    val inviteType: String = "",
    val roleLabel: String = "",
    val issuerName: String = "",
    val issuerOrg: String = "",
    val unitCode: String = "",
    val unitName: String = "",
    val deliveryPoint: String = "",
    val phoneRequired: Boolean = false,
    val expiresAt: String = "",
    val remainingUses: Int = 0
)

data class PriceImportDefaults(
    val category: String = "其他",
    val spec: String = "散装",
    val stockQuantity: String = "0",
    val supplyStatus: String = "paused",
    val fallbackUnit: String = "",
    val active: Boolean = true
)

/** A one-row override for a new product discovered in an Excel import. */
data class PriceImportNewProductPatch(
    val productCode: String,
    val category: String,
    val spec: String,
    val unit: String,
    val stockQuantity: String,
    val supplyStatus: String,
    val active: Boolean = true
)

data class PriceImportMetrics(
    val parsedRows: Int = 0,
    val existingProductRows: Int = 0,
    val newProductRows: Int = 0,
    val needsReviewRows: Int = 0,
    val exceptionRows: Int = 0,
    val readyRows: Int = 0,
    val ignoredRows: Int = 0
)

data class PriceImportRow(
    val id: String,
    val sourceProductName: String,
    val sourceProductCode: String = "",
    val sourceSpec: String = "",
    val sourceUnit: String = "",
    val proposedProductCode: String = "",
    val proposedCategory: String = "",
    val proposedSpec: String = "散装",
    val proposedUnit: String = "",
    val proposedStockQuantity: String = "0",
    val proposedSupplyStatus: String = "paused",
    val operationType: String,
    val validationStatus: String,
    val matchedProductId: String = "",
    val matchedProductName: String = "",
    val systemUnit: String = "",
    val currentPriceCents: Long? = null,
    val proposedPriceCents: Long? = null,
    val warning: String = "",
    val conversionFactor: String = "1"
)

data class PriceImportBatch(
    val id: String,
    val status: String,
    val sourceFilename: String,
    val createdAt: String = "",
    val selectedSheetName: String = "",
    val llmCalled: Boolean = false,
    val metrics: PriceImportMetrics = PriceImportMetrics(),
    val newProductDefaults: PriceImportDefaults = PriceImportDefaults(),
    val rows: List<PriceImportRow> = emptyList()
)

/** The workbook preview returned by the server for administrator-confirmed field mapping. */
data class PriceImportSheetPreview(
    val name: String,
    val headerCandidate: Int? = null,
    val preview: List<List<String>> = emptyList()
)

data class PriceImportStructure(
    val batchId: String,
    val sheets: List<PriceImportSheetPreview> = emptyList()
)

private const val INVITE_QR_PREFIX = "jingrongxianpei://invite?token="

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
            .put("username", username.trim())
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

    fun registerPushDevice(
        token: String,
        registrationId: String,
        installationId: String,
        appVersion: String
    ) {
        val body = JSONObject()
            .put("registration_id", registrationId)
            .put("installation_id", installationId)
            .put("platform", "android")
            .put("app_version", appVersion)
            .toString()
            .toRequestBody(JSON)
        request("push/devices/register", token = token, method = "POST", body = body)
    }

    fun unbindPushDevice(token: String, installationId: String) {
        val encoded = URLEncoder.encode(installationId, Charsets.UTF_8.name())
        request("push/devices/current?installation_id=$encoded", token = token, method = "DELETE")
    }

    fun markPushOpened(token: String, eventId: String) {
        val encoded = URLEncoder.encode(eventId, Charsets.UTF_8.name())
        request("push/events/$encoded/opened", token = token, method = "POST")
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
            .apply { if (form.id.isNotBlank()) put("expected_version", form.version) }
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

    fun setProductStatus(token: String, product: ProductEntity, status: String, active: Boolean): ProductEntity {
        val body = JSONObject()
            .put("supply_status", status)
            .put("active", active)
            .put("expected_version", product.version)
            .toString()
            .toRequestBody(JSON)
        return parseProduct(request("admin/products/${product.id}/status", token = token, method = "PATCH", body = body))
    }

    fun updateProductPrice(token: String, product: ProductEntity, priceCents: Long, reason: String): ProductEntity {
        val body = JSONObject()
            .put("price_cents", priceCents)
            .put("reason", reason)
            .put("expected_version", product.version)
            .toString()
            .toRequestBody(JSON)
        return parseProduct(request("admin/products/${product.id}/price", token = token, method = "PATCH", body = body))
    }

    fun adjustProductInventory(token: String, product: ProductEntity, mode: String, quantity: String, reason: String): InventoryAdjustRemoteResult {
        val currentStock = product.stockQuantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val delta = quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val nextStock = when (mode) {
            "increase" -> currentStock + delta
            "decrease" -> (currentStock - delta).max(BigDecimal.ZERO)
            else -> delta
        }.stripTrailingZeros().toPlainString()
        val body = JSONObject()
            .put("stock_quantity", nextStock)
            .put("detail", reason)
            .put("expected_version", product.version)
            .toString()
            .toRequestBody(JSON)
        val parsed = parseProduct(request("admin/products/${product.id}/stock", token = token, method = "PATCH", body = body))
        return InventoryAdjustRemoteResult(
            product = parsed,
            beforeStockQuantity = product.stockQuantity,
            afterStockQuantity = parsed.stockQuantity,
            reservedQuantity = parsed.reservedQuantity,
            availableQuantity = parsed.availableQuantity
        )
    }

    fun deleteProduct(token: String, productId: String) {
        request("admin/products/$productId", token = token, method = "DELETE")
    }

    fun deleteProductsBatch(token: String, productIds: List<String>): Int {
        val body = JSONObject()
            .put("ids", JSONArray(productIds))
            .put("confirmed", true)
            .toString()
            .toRequestBody(JSON)
        return request("admin/products/batch", token = token, method = "DELETE", body = body)
            .optInt("archived_count", 0)
    }

    fun downloadProductImportTemplate(token: String): ByteArray =
        executeBytes("admin/products/import-template.xlsx", token)

    fun restoreProduct(token: String, productId: String): ProductEntity {
        return parseProduct(request("admin/products/$productId/restore", token = token, method = "POST"))
    }

    fun priceImportBatches(token: String): List<PriceImportBatch> {
        val items = request("admin/price-imports", token = token).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index -> parsePriceImportBatch(items.getJSONObject(index)) }
    }

    fun uploadPriceImport(token: String, file: File): PriceImportBatch {
        val mediaType = when (file.extension.lowercase()) {
            "csv" -> "text/csv"
            "xls" -> "application/vnd.ms-excel"
            else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }.toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .build()
        return parsePriceImportBatch(request("admin/price-imports", token = token, method = "POST", body = body))
    }

    fun analyzePriceImport(token: String, batchId: String): PriceImportBatch {
        return parsePriceImportBatch(
            request(
                "admin/price-imports/$batchId/analyze",
                token = token,
                method = "POST",
                body = "{}".toRequestBody(JSON)
            )
        )
    }

    fun priceImportStructure(token: String, batchId: String): PriceImportStructure {
        val result = request("admin/price-imports/$batchId/structure", token = token)
        val sheets = result.optJSONArray("sheets") ?: JSONArray()
        return PriceImportStructure(
            batchId = result.optString("batch_id", batchId),
            sheets = List(sheets.length()) { index ->
                val sheet = sheets.getJSONObject(index)
                val preview = sheet.optJSONArray("preview") ?: JSONArray()
                PriceImportSheetPreview(
                    name = sheet.optString("name"),
                    headerCandidate = if (sheet.isNull("header_candidate")) null else sheet.optInt("header_candidate"),
                    preview = List(preview.length()) { rowIndex ->
                        val row = preview.optJSONArray(rowIndex) ?: JSONArray()
                        List(row.length()) { columnIndex -> row.optString(columnIndex) }
                    }
                )
            }
        )
    }

    fun reanalyzePriceImport(
        token: String,
        batchId: String,
        sheetName: String,
        headerRow: Int,
        mapping: Map<String, String>
    ): PriceImportBatch {
        val mappingJson = JSONObject().apply {
            mapping.filterValues { it.isNotBlank() }.forEach { (field, column) -> put(field, column) }
        }
        val body = JSONObject()
            .put("sheet_name", sheetName)
            .put("header_row", headerRow)
            .put("mapping", mappingJson)
            .toString()
            .toRequestBody(JSON)
        return parsePriceImportBatch(
            request("admin/price-imports/$batchId/analyze", token = token, method = "POST", body = body)
        )
    }

    fun priceImportDetail(token: String, batchId: String): PriceImportBatch =
        parsePriceImportBatch(request("admin/price-imports/$batchId", token = token))

    fun updatePriceImportDefaults(token: String, batchId: String, defaults: PriceImportDefaults): PriceImportBatch {
        val body = JSONObject()
            .put("category", defaults.category)
            .put("spec", defaults.spec)
            .put("stock_quantity", defaults.stockQuantity)
            .put("supply_status", defaults.supplyStatus)
            .put("fallback_unit", defaults.fallbackUnit)
            .put("active", defaults.active)
            .toString()
            .toRequestBody(JSON)
        return parsePriceImportBatch(request("admin/price-imports/$batchId/new-product-defaults", token = token, method = "PATCH", body = body))
    }

    fun selectPriceImportProduct(token: String, batchId: String, rowId: String, productId: String): PriceImportBatch {
        val body = JSONObject().put("matched_product_id", productId).toString().toRequestBody(JSON)
        return parsePriceImportBatch(request("admin/price-imports/$batchId/rows/$rowId", token = token, method = "PATCH", body = body))
    }

    fun ignorePriceImportRow(token: String, batchId: String, rowId: String): PriceImportBatch {
        val body = JSONObject().put("ignore", true).toString().toRequestBody(JSON)
        return parsePriceImportBatch(request("admin/price-imports/$batchId/rows/$rowId", token = token, method = "PATCH", body = body))
    }

    fun updatePriceImportNewProduct(
        token: String,
        batchId: String,
        rowId: String,
        patch: PriceImportNewProductPatch
    ): PriceImportBatch {
        val body = JSONObject()
            .put("product_code", patch.productCode)
            .put("category", patch.category)
            .put("spec", patch.spec)
            .put("unit", patch.unit)
            .put("stock_quantity", patch.stockQuantity)
            .put("supply_status", patch.supplyStatus)
            .put("active", patch.active)
            .toString()
            .toRequestBody(JSON)
        return parsePriceImportBatch(
            request("admin/price-imports/$batchId/rows/$rowId", token = token, method = "PATCH", body = body)
        )
    }

    fun applyPriceImport(token: String, batchId: String): PriceImportBatch {
        return parsePriceImportBatch(
            request(
                "admin/price-imports/$batchId/apply",
                token = token,
                method = "POST",
                body = JSONObject().put("confirmed", true).toString().toRequestBody(JSON)
            )
        )
    }

    fun createOrder(token: String, note: String, items: List<Pair<String, Double>>): JSONObject {
        val requestId = IdempotencyKeys.newKey()
        val array = JSONArray()
        items.forEach { (productId, quantity) ->
            array.put(JSONObject().put("product_id", productId).put("quantity", quantity.toCleanString()))
        }
        val body = JSONObject()
            .put("client_request_id", requestId)
            .put("note", note)
            .put("items", array)
            .toString()
            .toRequestBody(JSON)
        return request("orders", token = token, method = "POST", body = body, extraHeaders = IdempotencyKeys.header(requestId))
    }

    fun currentUnitQuota(token: String): UnitQuota {
        return UnitQuota.fromJson(request("quota/current", token = token))
    }

    fun orders(token: String, isAdmin: Boolean): List<RemoteOrderBundle> =
        orderPage(token = token, isAdmin = isAdmin, limit = 100).items

    fun orderPage(
        token: String,
        isAdmin: Boolean,
        status: String = "",
        dateFrom: String = "",
        dateTo: String = "",
        queryText: String = "",
        cursor: String = "",
        limit: Int = 20
    ): RemoteOrderPage {
        val params = mutableListOf("include_items=true", "limit=${limit.coerceIn(1, 100)}")
        if (status.isNotBlank() && status != "全部") params += "status=${encode(status.toApiOrderStatus())}"
        if (dateFrom.isNotBlank()) params += "date_from=${encode(dateFrom)}"
        if (dateTo.isNotBlank()) params += "date_to=${encode(dateTo)}"
        if (queryText.isNotBlank()) params += "query=${encode(queryText.trim())}"
        if (cursor.isNotBlank()) params += "cursor=${encode(cursor)}"
        val endpoint = if (isAdmin) "admin/orders" else "orders"
        val path = "$endpoint?${params.joinToString("&")}"
        val json = request(path, token = token)
        val array = json.getJSONArray("items")
        val items = List(array.length()) { index ->
            RemoteOrderMapper.mapOrder(array.getJSONObject(index))
        }
        return RemoteOrderPage(
            items = items,
            total = json.optInt("total", items.size),
            nextCursor = json.optString("next_cursor"),
            hasMore = json.optBoolean("has_more", false)
        )
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
            openReceiptIssues = json.optInt("open_receipt_issues", 0),
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
                    quantity = QuantityFormatter.format(item.optString("quantity"))
                )
            }
        )
    }

    fun analyticsOverview(
        token: String,
        startDate: String,
        endDate: String,
        unitId: String = "",
        category: String = ""
    ): AnalyticsOverview = AnalyticsJsonParser.overview(
        request("admin/analytics/overview?${analyticsQuery(startDate, endDate, unitId, category)}", token = token)
    )

    fun analyticsUnits(
        token: String,
        startDate: String,
        endDate: String,
        category: String = "",
        sort: String = "amount"
    ): List<AnalyticsUnitItem> = AnalyticsJsonParser.units(
        request("admin/analytics/units?${analyticsQuery(startDate, endDate, category = category)}&sort=${URLEncoder.encode(sort, Charsets.UTF_8.name())}", token = token)
    )

    fun analyticsPrices(
        token: String,
        startDate: String,
        endDate: String,
        category: String = ""
    ): List<AnalyticsPriceItem> = AnalyticsJsonParser.prices(
        request("admin/analytics/prices?${analyticsQuery(startDate, endDate, category = category)}", token = token)
    )

    fun analyticsInventory(token: String): AnalyticsInventory = AnalyticsJsonParser.inventory(
        request("admin/analytics/inventory", token = token)
    )

    fun analyticsProduct(
        token: String,
        productId: String,
        startDate: String,
        endDate: String
    ): AnalyticsProductDetail = AnalyticsJsonParser.product(
        request(
            "admin/analytics/products/${URLEncoder.encode(productId, Charsets.UTF_8.name())}?" +
                analyticsQuery(startDate, endDate),
            token = token
        )
    )

    private fun analyticsQuery(
        startDate: String,
        endDate: String,
        unitId: String = "",
        category: String = ""
    ): String = buildList {
        add("start_date=${URLEncoder.encode(startDate, Charsets.UTF_8.name())}")
        add("end_date=${URLEncoder.encode(endDate, Charsets.UTF_8.name())}")
        if (unitId.isNotBlank()) add("unit_id=${URLEncoder.encode(unitId, Charsets.UTF_8.name())}")
        if (category.isNotBlank()) add("category=${URLEncoder.encode(category, Charsets.UTF_8.name())}")
    }.joinToString("&")

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
                lastOrderAt = item.optString("last_order_at").replace('T', ' ').take(16),
                quota = UnitQuota.fromJson(item.optJSONObject("quota") ?: JSONObject())
            )
        }
    }

    fun unitQuota(token: String, unitId: String): UnitQuota =
        UnitQuota.fromJson(request("admin/units/$unitId/quota", token = token))

    fun updateUnitQuota(
        token: String,
        unitId: String,
        enabled: Boolean,
        defaultMonthlyQuotaCents: Long,
        expectedVersion: Int
    ): UnitQuota {
        val body = JSONObject()
            .put("enabled", enabled)
            .put("default_monthly_quota_cents", defaultMonthlyQuotaCents)
            .put("expected_version", expectedVersion)
            .toString()
            .toRequestBody(JSON)
        return UnitQuota.fromJson(
            request("admin/units/$unitId/quota", token = token, method = "PUT", body = body)
        )
    }

    fun adjustUnitQuota(
        token: String,
        unitId: String,
        deltaCents: Long,
        reason: String,
        expectedVersion: Int
    ): UnitQuota {
        val body = JSONObject()
            .put("delta_cents", deltaCents)
            .put("reason", reason.trim())
            .put("expected_version", expectedVersion)
            .toString()
            .toRequestBody(JSON)
        return UnitQuota.fromJson(
            request("admin/units/$unitId/quota/adjustments", token = token, method = "POST", body = body)
        )
    }

    fun unitQuotaLedger(token: String, unitId: String): List<UnitQuotaLedgerRow> {
        val array = request("admin/units/$unitId/quota/ledger", token = token).optJSONArray("items") ?: JSONArray()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            UnitQuotaLedgerRow(
                id = item.optString("id"),
                eventType = item.optString("event_type"),
                deltaCents = item.optLong("delta_cents", 0),
                balanceAfterCents = item.optLong("balance_after_cents", 0),
                orderNo = item.optString("order_no"),
                note = item.optString("note"),
                createdAt = item.optString("display_created_at")
                    .ifBlank { item.optString("created_at").replace('T', ' ').take(16) }
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
                quantity = QuantityFormatter.format(item.optString("quantity")),
                subtotalCents = item.optLong("subtotal_cents", 0),
                totalCents = item.optLong("total_cents", 0)
            )
        }
    }

    fun exportLedger(token: String): ByteArray {
        return executeBytes("admin/ledger/export.xlsx", token)
    }

    fun cutoff(token: String): CutoffInfo {
        val json = request("procurement/cutoff", token = token)
        return CutoffInfo(
            businessDate = json.optString("business_date"),
            enabled = json.optBooleanCompat("enabled"),
            cutoffTime = json.optString("cutoff_time"),
            isClosed = json.optBooleanCompat("is_closed"),
            remainingSeconds = json.optLong("remaining_seconds", 0)
        )
    }

    fun preparationSummary(token: String): List<PreparationSummaryItem> {
        val array = request("admin/preparation-summary", token = token).optJSONArray("items") ?: JSONArray()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            PreparationSummaryItem(
                productName = item.optString("product_name"),
                spec = item.optString("spec"),
                unit = item.optString("unit"),
                requestedQuantity = QuantityFormatter.format(item.optString("requested_quantity")),
                actualQuantity = QuantityFormatter.format(item.optString("actual_quantity", item.optString("requested_quantity"))),
                unitCount = item.optInt("unit_count", 0),
                orderCount = item.optInt("order_count", 0)
            )
        }
    }

    fun deliverySheets(token: String): List<DeliverySheetUnit> {
        val units = request("admin/delivery-sheets", token = token).optJSONArray("units") ?: JSONArray()
        return List(units.length()) { index ->
            val unit = units.getJSONObject(index)
            val orders = unit.optJSONArray("orders")
            var itemCount = 0
            for (orderIndex in 0 until (orders?.length() ?: 0)) {
                itemCount += orders!!.getJSONObject(orderIndex).optJSONArray("items")?.length() ?: 0
            }
            DeliverySheetUnit(
                unitName = unit.optString("unit_name"),
                deliveryPoint = unit.optString("delivery_point"),
                orderCount = orders?.length() ?: unit.optInt("order_count", 0),
                itemCount = itemCount.takeIf { it > 0 } ?: unit.optInt("item_count", 0)
            )
        }
    }

    fun exportPreparationSummary(token: String): ByteArray = executeBytes("admin/preparation-summary/export.xlsx", token)

    fun exportDeliverySheets(token: String): ByteArray = executeBytes("admin/delivery-sheets/export.xlsx", token)

    fun deliveryBatches(token: String): List<DeliveryBatch> {
        val items = request("admin/batches", token = token).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index -> parseDeliveryBatch(items.getJSONObject(index)) }
    }

    fun eligibleDeliveryBatchOrders(token: String): List<DeliveryBatchOrder> {
        val items = request("admin/batches/eligible-orders", token = token).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index -> parseDeliveryBatchOrder(items.getJSONObject(index)) }
    }

    fun createDeliveryBatch(
        token: String,
        name: String,
        note: String,
        orderIds: List<String>
    ): DeliveryBatch {
        val body = JSONObject()
            .put("name", name.trim())
            .put("note", note.trim())
            .put("order_ids", JSONArray(orderIds))
            .toString()
            .toRequestBody(JSON)
        return parseDeliveryBatch(request("admin/batches", token = token, method = "POST", body = body))
    }

    fun deliveryBatchDetail(token: String, batchId: String): DeliveryBatch =
        parseDeliveryBatch(request("admin/batches/$batchId", token = token))

    fun deliveryBatchSummary(token: String, batchId: String): DeliveryBatchSummary =
        parseDeliveryBatchSummary(request("admin/batches/$batchId/summary", token = token))

    fun closeDeliveryBatch(token: String, batch: DeliveryBatch): DeliveryBatch {
        val body = JSONObject()
            .put("status", "closed")
            .put("expected_version", batch.version)
            .toString()
            .toRequestBody(JSON)
        return parseDeliveryBatch(
            request("admin/batches/${batch.id}/status", token = token, method = "PATCH", body = body)
        )
    }

    fun exportDeliveryBatchSummary(token: String, batchId: String): ByteArray =
        executeBytes("admin/batches/$batchId/summary.xlsx", token)

    fun exportDeliveryBatchPickingList(token: String, batchId: String): ByteArray =
        executeBytes("admin/batches/$batchId/picking-list.xlsx", token)

    fun exportDeliveryBatchOutbound(token: String, batchId: String): ByteArray =
        executeBytes("admin/batches/$batchId/outbound.xlsx", token)

    fun generateOutboundOrders(token: String, batchId: String): List<OutboundOrder> {
        val items = request("admin/outbounds/from-batch/$batchId", token = token, method = "POST")
            .optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index -> parseOutboundOrder(items.getJSONObject(index)) }
    }

    fun outboundOrders(token: String): List<OutboundOrder> {
        val items = request("admin/outbounds", token = token).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index -> parseOutboundOrder(items.getJSONObject(index)) }
    }

    fun outboundOrderDetail(token: String, outboundId: String): OutboundOrder =
        parseOutboundOrder(request("admin/outbounds/$outboundId", token = token))

    fun exportOutboundOrder(token: String, outboundId: String): ByteArray =
        executeBytes("admin/outbounds/$outboundId/export.xlsx", token)

    fun shipOutboundOrder(
        token: String,
        outboundId: String,
        photoFiles: List<File>,
        note: String,
        clientRequestId: String
    ): OutboundOrder {
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
        return parseOutboundOrder(
            request("admin/outbounds/$outboundId/ship", token = token, method = "POST", body = body)
        )
    }

    fun systemOverview(token: String): SystemOverview {
        val json = request("admin/system/overview?detail=true", token = token)
        val resources = json.optJSONObject("resources") ?: JSONObject()
        val performance = json.optJSONObject("performance") ?: JSONObject()
        val sessions = json.optJSONObject("sessions") ?: JSONObject()
        val storage = json.optJSONObject("storage") ?: JSONObject()
        val services = json.optJSONObject("services") ?: JSONObject()
        val capacity = json.optJSONObject("capacity") ?: JSONObject()
        val latest = json.optJSONObject("latest_backup") ?: JSONObject()
        val alerts = json.optJSONArray("alerts") ?: JSONArray()
        return SystemOverview(
            overallStatus = json.optString("overall_status", "healthy"),
            checkedAt = json.optString("checked_at").replace('T', ' ').take(19),
            uptimeSeconds = json.optLong("uptime_seconds", 0),
            resources = SystemResources(
                scope = resources.optString("scope", "container"),
                cpuPercent = resources.optDouble("cpu_percent", 0.0),
                memoryUsedBytes = resources.optLong("memory_used_bytes", 0),
                memoryTotalBytes = resources.optLong("memory_total_bytes", 0),
                diskUsedBytes = resources.optLong("disk_used_bytes", 0),
                diskTotalBytes = resources.optLong("disk_total_bytes", 0),
                diskFreeBytes = resources.optLong("disk_free_bytes", 0)
            ),
            performance = SystemPerformance(
                requestCount5m = performance.optInt("request_count_5m", 0),
                averageLatencyMs = performance.optInt("average_latency_ms", 0),
                p95LatencyMs = performance.optInt("p95_latency_ms", 0),
                errorCount5m = performance.optInt("error_count_5m", 0),
                errorRatePercent = performance.optDouble("error_rate_percent", 0.0),
                sqliteLockCount24h = performance.optInt("sqlite_lock_count_24h", 0)
            ),
            sessions = SystemSessions(
                activeAppSessions = sessions.optInt("active_app_sessions", 0),
                activeWebSessions = sessions.optInt("active_web_sessions", 0)
            ),
            storage = SystemStorage(
                databaseBytes = storage.optLong("database_bytes", 0),
                productImagesBytes = storage.optLong("product_images_bytes", 0),
                shippingPhotosBytes = storage.optLong("shipping_photos_bytes", 0),
                receiptIssuePhotosBytes = storage.optLong("receipt_issue_photos_bytes", 0),
                backupsBytes = storage.optLong("backups_bytes", 0)
            ),
            services = SystemServices(
                api = services.optString("api", "unknown"),
                database = services.optString("database", "unknown"),
                uploads = services.optString("uploads", "unknown"),
                backup = services.optString("backup", "unknown"),
                web = services.optString("web", "unknown"),
                sms = services.optString("sms", "disabled")
            ),
            capacity = SystemCapacity(
                status = capacity.optString("status", "sufficient"),
                summary = capacity.optString("summary"),
                disclaimer = capacity.optString("disclaimer"),
                apiP95LatencyMs = capacity.optInt("api_p95_latency_ms", performance.optInt("p95_latency_ms", 0)),
                errorRatePercent = capacity.optDouble("error_rate_percent", performance.optDouble("error_rate_percent", 0.0)),
                sqliteLockCount24h = capacity.optInt("sqlite_lock_count_24h", performance.optInt("sqlite_lock_count_24h", 0))
            ),
            latestBackup = LatestBackup(
                status = latest.optString("status", "missing"),
                createdAt = latest.optString("created_at").replace('T', ' ').take(19),
                sizeBytes = latest.optLong("size_bytes", 0),
                verified = latest.optBooleanCompat("verified"),
                offsiteSynced = latest.optBooleanCompat("offsite_synced"),
                appVersion = latest.optString("app_version"),
                databaseVersion = latest.optString("database_version")
            ),
            alerts = List(alerts.length()) { index ->
                val item = alerts.getJSONObject(index)
                SystemAlert(
                    level = item.optString("level", item.optString("severity", "info")),
                    title = item.optString("title"),
                    occurredAt = item.optString("occurred_at").replace('T', ' ').take(19),
                    impact = item.optString("impact", item.optString("message")),
                    suggestion = item.optString("suggestion")
                )
            },
            todayOrders = json.optInt("today_orders", 0),
            pendingOrders = json.optInt("pending_orders", 0),
            preparingOrders = json.optInt("preparing_orders", 0),
            shippedOrders = json.optInt("shipped_orders", 0),
            activeUnits = json.optInt("active_units", 0),
            activeUsers = json.optInt("active_users", 0),
            products = json.optInt("products", 0),
            openReceiptIssues = json.optInt("open_receipt_issues", 0),
            latestBackupStatus = json.optString("latest_backup_status"),
            latestBackupTime = json.optString("latest_backup_time")
        )
    }

    fun inspectInvite(inviteToken: String): InviteInspectResult {
        val body = JSONObject()
            .put("invite_token", inviteToken)
            .toString()
            .toRequestBody(JSON)
        val json = request("auth/invites/inspect", method = "POST", body = body)
        return InviteInspectResult(
            token = inviteToken,
            valid = json.optBooleanCompat("valid"),
            inviteType = json.optString("invite_type"),
            roleLabel = json.optString("role_label", json.optString("display_role")),
            issuerName = json.optString("issuer_name_masked", json.optString("issuer_name")),
            issuerOrg = json.optString("issuer_org"),
            unitCode = json.optString("unit_code"),
            unitName = json.optString("unit_name"),
            deliveryPoint = json.optString("delivery_point"),
            phoneRequired = json.optBooleanCompat("phone_required"),
            expiresAt = json.optString("expires_at").replace('T', ' ').take(19),
            remainingUses = json.optInt("remaining_uses", 0)
        )
    }

    fun sendRegisterPhoneCode(phone: String, inviteToken: String): JSONObject {
        val body = JSONObject()
            .put("phone", phone)
            .put("purpose", "register")
            .put("invite_token", inviteToken)
            .toString()
            .toRequestBody(JSON)
        return request("auth/phone/send-code", method = "POST", body = body)
    }

    fun verifyRegisterPhoneCode(phone: String, code: String, inviteToken: String): String {
        val body = JSONObject()
            .put("phone", phone)
            .put("code", code)
            .put("purpose", "register")
            .put("invite_token", inviteToken)
            .toString()
            .toRequestBody(JSON)
        return request("auth/phone/verify-code", method = "POST", body = body).optString("phone_verification_ticket")
    }

    fun registerWithInvite(
        inviteToken: String,
        username: String,
        displayName: String,
        password: String,
        phone: String,
        phoneVerificationTicket: String
    ): RemoteLogin {
        val body = JSONObject()
            .put("invite_token", inviteToken)
            .put("username", username)
            .put("display_name", displayName)
            .put("password", password)
            .put("phone", phone)
            .put("phone_verification_ticket", phoneVerificationTicket)
            .toString()
            .toRequestBody(JSON)
        val json = request("auth/register-with-invite", method = "POST", body = body)
        if (json.optString("status") == "pending_approval") {
            return RemoteLogin(
                token = "",
                expiresAt = 0,
                user = RemoteUser(id = "", username = username, displayName = displayName, role = "", unitId = ""),
                status = "pending_approval",
                pendingApproval = true,
                message = json.optString("message", "管理者权限需要系统管理员审批")
            )
        }
        return RemoteLogin(
            token = json.getString("token"),
            expiresAt = json.getLong("expires_at"),
            user = parseUser(json.getJSONObject("user"))
        )
    }

    fun scanWebLoginQr(token: String, rawValue: String): WebLoginScanResult {
        val body = JSONObject()
            .put("qr_token", rawValue)
            .put("device_name", android.os.Build.MODEL.orEmpty())
            .put("app_version", BuildConfig.VERSION_NAME)
            .toString()
            .toRequestBody(JSON)
        val json = request("mobile/web-auth/qr/scan", token = token, method = "POST", body = body)
        val browser = json.optJSONObject("browser") ?: json
        val user = json.optJSONObject("user") ?: JSONObject()
        return WebLoginScanResult(
            loginToken = json.optString("login_token", json.optString("challenge_id", json.optString("token"))),
            websiteName = json.optString("website_name", "三公鲜配管理平台"),
            websiteHost = json.optString("website_host"),
            browser = WebLoginBrowserInfo(
                browserName = json.optString("browser_name", browser.optString("browser_name", browser.optString("browser"))),
                browserOs = json.optString("operating_system", browser.optString("browser_os", browser.optString("os"))),
                browserIp = json.optString("ip_display", browser.optString("browser_ip", browser.optString("ip"))),
                deviceName = json.optString("device_name", browser.optString("device_name", browser.optString("device")))
            ),
            targetRole = json.optString("target_role", user.optString("role")),
            username = user.optString("username"),
            displayName = user.optString("display_name"),
            unitName = user.optString("unit_name"),
            createdAt = json.optString("created_at"),
            expiresAt = json.optString("expires_at")
        )
    }

    fun approveWebLogin(token: String, loginToken: String) {
        request("mobile/web-auth/qr/$loginToken/approve", token = token, method = "POST")
    }

    fun rejectWebLogin(token: String, loginToken: String) {
        request("mobile/web-auth/qr/$loginToken/reject", token = token, method = "POST")
    }

    fun webSessions(token: String): List<WebSessionRecord> {
        val json = request("mobile/web-sessions", token = token)
        val array = json.optJSONArray("items") ?: json.optJSONArray("sessions") ?: JSONArray()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            WebSessionRecord(
                id = item.optString("id", item.optString("session_id")),
                browserName = item.optString("browser_name", item.optString("browser")),
                browserOs = item.optString("browser_os", item.optString("os")),
                browserIp = item.optString("browser_ip", item.optString("ip")),
                deviceName = item.optString("device_name", item.optString("device")),
                createdAt = item.optString("created_at").replace('T', ' ').take(16),
                lastSeenAt = item.optString("last_seen_at", item.optString("created_at")).replace('T', ' ').take(16),
                active = item.optBooleanCompat("active")
            )
        }
    }

    fun revokeWebSession(token: String, sessionId: String) {
        request("mobile/web-sessions/$sessionId", token = token, method = "DELETE")
    }

    fun revokeAllWebSessions(token: String) {
        request("mobile/web-sessions/revoke-all", token = token, method = "POST")
    }

    fun checkAppUpdate(token: String, channel: String): AppUpdateCheckResult {
        val path = "app-update/check?channel=$channel&current_version_code=${BuildConfig.VERSION_CODE}&package_name=${BuildConfig.APPLICATION_ID}&android_api_level=${android.os.Build.VERSION.SDK_INT}&installation_id=android-${BuildConfig.VERSION_CODE}-local"
        val json = request(path, token = token)
        val release = json.optJSONObject("release")?.let(::parseAppUpdateRelease)
        return AppUpdateCheckResult(
            updateAvailable = json.optBooleanCompat("update_available"),
            mandatory = json.optBooleanCompat("mandatory"),
            currentVersionBlocked = json.optBooleanCompat("current_version_blocked"),
            release = release
        )
    }

    fun downloadAppRelease(token: String, release: AppUpdateRelease, onProgress: (Int) -> Unit = {}): ByteArray {
        val ticket = release.downloadTicket
        return executeBytes("app-update/releases/${release.releaseId}/download?download_ticket=$ticket", token, onProgress)
    }

    fun orderDetail(token: String, orderId: String, isAdmin: Boolean): RemoteOrderBundle {
        val path = if (isAdmin) "admin/orders/$orderId" else "orders/$orderId"
        return RemoteOrderMapper.mapOrder(request(path, token = token))
    }

    fun setAdminOrderStatus(token: String, order: OrderEntity, status: String): RemoteOrderBundle {
        val body = JSONObject()
            .put("status", status)
            .put("expected_status", order.status.toApiOrderStatus())
            .put("expected_version", order.version)
            .toString()
            .toRequestBody(JSON)
        return RemoteOrderMapper.mapOrder(
            request("admin/orders/${order.orderId}/status", token = token, method = "PATCH", body = body)
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

    fun cancelOrder(token: String, orderId: String, reason: String): RemoteOrderBundle {
        val body = JSONObject()
            .put("reason", reason.trim())
            .toString()
            .toRequestBody(JSON)
        return RemoteOrderMapper.mapOrder(
            request("orders/$orderId/cancel", token = token, method = "POST", body = body)
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

    private fun request(path: String, token: String = "", method: String = "GET", body: okhttp3.RequestBody? = null, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        return JSONObject(execute(path, token, method, body, extraHeaders))
    }

    private fun execute(path: String, token: String = "", method: String = "GET", body: okhttp3.RequestBody? = null, extraHeaders: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        val request = when (method) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody()).build()
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody()).build()
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody()).build()
            "DELETE" -> builder.delete(body ?: ByteArray(0).toRequestBody()).build()
            else -> builder.get().build()
        }
        val callClient = if (body is MultipartBody) uploadClient else client
        callClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val payload = runCatching { JSONObject(responseBody) }.getOrNull()
                val detail = payload?.opt("detail")
                val detailObject = detail as? JSONObject
                val message = when (detail) {
                    is JSONObject -> detail.optString("message")
                    else -> detail?.toString().orEmpty()
                }
                throw ApiRequestException(
                    statusCode = response.code,
                    errorCode = detailObject?.optString("code")?.ifBlank { null },
                    message = message.ifBlank { response.code.toChineseError() }.toChineseDetail(response.code)
                )
            }
            return responseBody
        }
    }

    private fun executeBytes(path: String, token: String = "", onProgress: (Int) -> Unit = {}): ByteArray {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        client.newCall(builder.get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(response.code.toChineseError())
            }
            val body = response.body ?: return ByteArray(0)
            val total = body.contentLength()
            if (total <= 0) {
                onProgress(15)
                return body.bytes()
            }
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var readTotal = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    readTotal += read
                    onProgress(((readTotal * 60 / total).toInt()).coerceIn(1, 60))
                }
            }
            return out.toByteArray()
        }
    }

    private fun parseDeliveryBatchOrder(json: JSONObject): DeliveryBatchOrder = DeliveryBatchOrder(
        id = json.optString("id"),
        orderNo = json.optString("order_no"),
        unitId = json.optString("unit_id"),
        unitName = json.optString("unit_name_snapshot", json.optString("unit_name")),
        deliveryPoint = json.optString("delivery_point_snapshot", json.optString("delivery_point")),
        status = json.optString("status").toUiOrderStatus(),
        totalCents = json.optLong("total_cents", 0),
        createdAt = json.optString("created_at").replace('T', ' ').take(16),
        version = json.optInt("version", 1)
    )

    private fun parseDeliveryBatch(json: JSONObject): DeliveryBatch {
        val orderArray = json.optJSONArray("orders") ?: JSONArray()
        val orders = List(orderArray.length()) { index -> parseDeliveryBatchOrder(orderArray.getJSONObject(index)) }
        return DeliveryBatch(
            id = json.optString("id"),
            batchNo = json.optString("batch_no"),
            name = json.optString("name"),
            note = json.optString("note"),
            status = json.optString("status", "open"),
            createdAt = json.optString("created_at").replace('T', ' ').take(16),
            updatedAt = json.optString("updated_at").replace('T', ' ').take(16),
            version = json.optInt("version", 1),
            orderCount = json.optInt("order_count", orders.size),
            unitCount = json.optInt("unit_count", orders.map { it.unitId }.filter { it.isNotBlank() }.distinct().size),
            orders = orders
        )
    }

    private fun parseBatchSummaryItem(json: JSONObject): BatchSummaryItem = BatchSummaryItem(
        productId = json.optString("product_id"),
        productName = json.optString("product_name"),
        category = json.optString("category"),
        spec = json.optString("spec"),
        unit = json.optString("unit"),
        requestedQuantity = QuantityFormatter.format(json.optString("requested_quantity")),
        actualQuantity = QuantityFormatter.format(json.optString("actual_quantity", json.optString("quantity"))),
        subtotalCents = json.optLong("subtotal_cents", 0)
    )

    private fun parseDeliveryBatchSummary(json: JSONObject): DeliveryBatchSummary {
        val units = json.optJSONArray("by_unit") ?: JSONArray()
        val products = json.optJSONArray("by_product") ?: JSONArray()
        return DeliveryBatchSummary(
            batch = parseDeliveryBatch(json.optJSONObject("batch") ?: JSONObject()),
            orderCount = json.optInt("order_count", 0),
            unitCount = json.optInt("unit_count", 0),
            productCount = json.optInt("product_count", 0),
            totalCents = json.optLong("total_cents", 0),
            byUnit = List(units.length()) { index ->
                val unit = units.getJSONObject(index)
                val items = unit.optJSONArray("items") ?: JSONArray()
                BatchUnitSummary(
                    unitId = unit.optString("unit_id"),
                    unitName = unit.optString("unit_name"),
                    deliveryPoint = unit.optString("delivery_point"),
                    orderCount = unit.optInt("order_count", 0),
                    totalCents = unit.optLong("total_cents", 0),
                    items = List(items.length()) { itemIndex -> parseBatchSummaryItem(items.getJSONObject(itemIndex)) }
                )
            },
            byProduct = List(products.length()) { index ->
                val product = products.getJSONObject(index)
                val breakdown = product.optJSONArray("unit_breakdown") ?: JSONArray()
                BatchProductSummary(
                    productId = product.optString("product_id"),
                    productName = product.optString("product_name"),
                    category = product.optString("category"),
                    spec = product.optString("spec"),
                    unit = product.optString("unit"),
                    requestedQuantity = QuantityFormatter.format(product.optString("requested_quantity")),
                    actualQuantity = QuantityFormatter.format(product.optString("actual_quantity", product.optString("total_quantity"))),
                    subtotalCents = product.optLong("subtotal_cents", 0),
                    unitCount = product.optInt("unit_count", breakdown.length()),
                    unitBreakdown = List(breakdown.length()) { breakdownIndex ->
                        val value = breakdown.getJSONObject(breakdownIndex)
                        BatchUnitBreakdown(
                            unitId = value.optString("unit_id"),
                            unitName = value.optString("unit_name"),
                            requestedQuantity = QuantityFormatter.format(value.optString("requested_quantity")),
                            actualQuantity = QuantityFormatter.format(value.optString("actual_quantity", value.optString("quantity")))
                        )
                    }
                )
            }
        )
    }

    internal fun parseOutboundOrder(json: JSONObject): OutboundOrder {
        val orders = json.optJSONArray("orders") ?: JSONArray()
        val lines = json.optJSONArray("lines") ?: JSONArray()
        return OutboundOrder(
            id = json.optString("id"),
            outboundNo = json.optString("outbound_no"),
            preparationBatchId = json.optString("preparation_batch_id"),
            batchNo = json.optString("batch_no"),
            unitId = json.optString("unit_id"),
            unitName = json.optString("unit_name_snapshot"),
            deliveryPoint = json.optString("delivery_point_snapshot"),
            status = json.optString("status", "pending"),
            createdAt = json.optString("created_at").replace('T', ' ').take(16),
            shippedAt = json.optString("shipped_at").replace('T', ' ').take(16),
            version = json.optInt("version", 1),
            orderCount = json.optInt("order_count", orders.length()),
            productCount = json.optInt("product_count", lines.length()),
            totalCents = json.optLong("total_cents", 0),
            orders = List(orders.length()) { index ->
                val item = orders.getJSONObject(index)
                OutboundOrderRef(
                    id = item.optString("id"),
                    orderNo = item.optString("order_no"),
                    status = item.optString("status").toUiOrderStatus(),
                    totalCents = item.optLong("total_cents", 0),
                    deliveryPoint = item.optString("delivery_point_snapshot")
                )
            },
            lines = List(lines.length()) { index ->
                val item = lines.getJSONObject(index)
                OutboundOrderLine(
                    productId = item.optString("product_id"),
                    category = item.optString("category"),
                    productName = item.optString("product_name"),
                    spec = item.optString("spec"),
                    unit = item.optString("unit"),
                    quantity = QuantityFormatter.format(item.optString("quantity")),
                    priceCentsSnapshot = item.optLong("price_cents_snapshot", 0),
                    subtotalCents = item.optLong("subtotal_cents", 0)
                )
            }
        )
    }

    private fun parseAppUpdateRelease(json: JSONObject): AppUpdateRelease {
        val notes = json.optJSONArray("release_notes") ?: JSONArray()
        return AppUpdateRelease(
            releaseId = json.optString("release_id", json.optString("id")),
            packageName = json.optString("package_name"),
            versionCode = json.optInt("version_code", 0),
            versionName = json.optString("version_name"),
            channel = json.optString("channel"),
            minimumSupportedVersionCode = json.optInt("minimum_supported_version_code", 0),
            updateType = json.optString("update_type", "optional"),
            mandatory = json.optBooleanCompat("mandatory"),
            title = json.optString("title"),
            releaseNotes = List(notes.length()) { index -> notes.optString(index) },
            apkSha256 = json.optString("apk_sha256"),
            signerSha256 = json.optString("signer_sha256"),
            sizeBytes = json.optLong("size_bytes", json.optLong("apk_size_bytes", 0)),
            minSdk = json.optInt("min_sdk", 0),
            downloadUrl = json.optString("download_url"),
            downloadTicket = json.optString("download_ticket"),
            manifestSignature = json.optString("manifest_signature"),
            manifestPublicKey = json.optString("manifest_public_key"),
            manifestKeyId = json.optString("manifest_key_id"),
            manifestSignatureAlgorithm = json.optString("manifest_signature_algorithm")
        )
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
            stockQuantity = QuantityFormatter.format(json.optString("stock_quantity", "0")),
            reservedQuantity = QuantityFormatter.format(json.optString("reserved_quantity", "0")),
            warningQuantity = QuantityFormatter.format(json.optString("warning_quantity", "0")),
            availableQuantity = QuantityFormatter.format(json.optString("available_quantity", "0")),
            storageMethod = json.optString("storage_method"),
            shelfLife = json.optString("shelf_life"),
            status = status.toUiStatus(active),
            isAvailable = active,
            remark = json.optString("description"),
            isDeleted = json.optBoolean("is_deleted", false),
            createdBy = json.optString("created_by"),
            remoteUpdatedAt = json.optString("updated_at"),
            version = json.optInt("version", 1),
        )
    }

    private fun parsePriceImportBatch(json: JSONObject): PriceImportBatch {
        val defaults = json.optJSONObject("new_product_defaults") ?: JSONObject()
        val metrics = json.optJSONObject("metrics") ?: JSONObject()
        val rows = json.optJSONArray("rows") ?: JSONArray()
        return PriceImportBatch(
            id = json.optString("id"),
            status = json.optString("status"),
            sourceFilename = json.optString("source_filename"),
            createdAt = json.optString("created_at"),
            selectedSheetName = json.optString("selected_sheet_name"),
            llmCalled = json.optBoolean("llm_called", false),
            metrics = PriceImportMetrics(
                parsedRows = metrics.optInt("parsed_rows"),
                existingProductRows = metrics.optInt("existing_product_rows"),
                newProductRows = metrics.optInt("new_product_rows"),
                needsReviewRows = metrics.optInt("needs_review_rows"),
                exceptionRows = metrics.optInt("exception_rows"),
                readyRows = metrics.optInt("ready_rows"),
                ignoredRows = metrics.optInt("ignored_rows")
            ),
            newProductDefaults = PriceImportDefaults(
                category = defaults.optString("category", "其他"),
                spec = defaults.optString("spec", "散装"),
                stockQuantity = defaults.optString("stock_quantity", "0"),
                supplyStatus = defaults.optString("supply_status", "paused"),
                fallbackUnit = defaults.optString("fallback_unit"),
                active = defaults.optBoolean("active", true)
            ),
            rows = List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                PriceImportRow(
                    id = row.optString("id"),
                    sourceProductName = row.optString("source_product_name"),
                    sourceProductCode = row.optString("source_product_code"),
                    sourceSpec = row.optString("source_spec"),
                    sourceUnit = row.optString("source_unit"),
                    proposedProductCode = row.optString("proposed_product_code"),
                    proposedCategory = row.optString("proposed_category"),
                    proposedSpec = row.optString("proposed_spec", "散装"),
                    proposedUnit = row.optString("proposed_unit"),
                    proposedStockQuantity = row.optString("proposed_stock_quantity", "0"),
                    proposedSupplyStatus = row.optString("proposed_supply_status", "paused"),
                    operationType = row.optString("operation_type"),
                    validationStatus = row.optString("validation_status"),
                    matchedProductId = row.optString("matched_product_id"),
                    matchedProductName = row.optString("matched_product_name"),
                    systemUnit = row.optString("system_unit"),
                    currentPriceCents = if (row.isNull("current_price_cents")) null else row.optLong("current_price_cents"),
                    proposedPriceCents = if (row.isNull("proposed_price_cents")) null else row.optLong("proposed_price_cents"),
                    warning = row.optString("warning"),
                    conversionFactor = row.optString("conversion_factor", "1")
                )
            }
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
        "voided" -> "已作废"
        else -> "待接单"
    }

    private fun String.toApiOrderStatus(): String = when (this) {
        "已接单" -> "accepted"
        "备货中" -> "preparing"
        "已发货" -> "shipped"
        "已完成" -> "completed"
        "已取消" -> "cancelled"
        "已作废" -> "voided"
        else -> "pending"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

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
        contains("QUOTA_INSUFFICIENT", ignoreCase = true) -> "本月可用采购额度不足，请调整申领数量后重试。"
        else -> this
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
