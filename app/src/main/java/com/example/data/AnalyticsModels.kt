package com.smartprocurement.internal.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId


data class AnalyticsDateRange(
    val startDate: String,
    val endDate: String,
    val days: Int
) {
    companion object {
        fun recent(days: Int, today: LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))): AnalyticsDateRange {
            require(days in 1..365)
            return AnalyticsDateRange(today.minusDays(days.toLong() - 1).toString(), today.toString(), days)
        }
    }
}

data class AnalyticsSummary(
    val validOrderCount: Int = 0,
    val totalCents: Long = 0,
    val unitCount: Int = 0,
    val productCount: Int = 0,
    val inventoryAlertCount: Int = 0,
    val openReceiptIssues: Int = 0
)

data class AnalyticsComparison(
    val validOrderCountPercent: Double? = null,
    val totalCentsPercent: Double? = null,
    val unitCountPercent: Double? = null,
    val productCountPercent: Double? = null
)

data class AnalyticsTrendPoint(val date: String, val orderCount: Int, val totalCents: Long)

data class AnalyticsDemandItem(
    val productId: String,
    val productName: String,
    val category: String,
    val unit: String,
    val quantity: String
)

data class AnalyticsOverview(
    val summary: AnalyticsSummary = AnalyticsSummary(),
    val comparison: AnalyticsComparison = AnalyticsComparison(),
    val trend: List<AnalyticsTrendPoint> = emptyList(),
    val demandRank: List<AnalyticsDemandItem> = emptyList()
)

data class AnalyticsUnitItem(
    val unitId: String,
    val unitName: String,
    val orderCount: Int,
    val totalCents: Long,
    val productCount: Int,
    val openReceiptIssues: Int
)

data class AnalyticsPriceItem(
    val productId: String,
    val productName: String,
    val category: String,
    val unit: String,
    val initialPriceCents: Long?,
    val currentPriceCents: Long,
    val minPriceCents: Long,
    val maxPriceCents: Long,
    val changePercent: Double?,
    val isNew: Boolean,
    val changeCount: Int
)

data class AnalyticsInventorySummary(
    val productCount: Int = 0,
    val outOfStock: Int = 0,
    val warning: Int = 0,
    val tight: Int = 0,
    val paused: Int = 0,
    val normal: Int = 0
)

data class AnalyticsInventoryItem(
    val productId: String,
    val productName: String,
    val category: String,
    val spec: String,
    val unit: String,
    val stockQuantity: String,
    val reservedQuantity: String,
    val availableQuantity: String,
    val warningQuantity: String,
    val averageDailyDemand: String,
    val estimatedDaysAvailable: String?,
    val supplyStatus: String,
    val active: Boolean,
    val risk: String
)

data class AnalyticsInventory(
    val summary: AnalyticsInventorySummary = AnalyticsInventorySummary(),
    val items: List<AnalyticsInventoryItem> = emptyList()
)

data class AnalyticsProductInfo(
    val id: String,
    val name: String,
    val category: String,
    val spec: String,
    val unit: String,
    val priceCents: Long
)

data class AnalyticsProductInventory(
    val stockQuantity: String,
    val reservedQuantity: String,
    val availableQuantity: String,
    val warningQuantity: String,
    val supplyStatus: String
)

data class AnalyticsProductPeriod(
    val orderCount: Int,
    val quantity: String,
    val amountCents: Long,
    val unitCount: Int,
    val unit: String
)

data class AnalyticsProductPrice(
    val currentCents: Long,
    val rangeStartCents: Long?,
    val minCents: Long,
    val maxCents: Long,
    val changeCents: Long?,
    val changePercent: Double?,
    val isNew: Boolean
)

data class AnalyticsPriceEvent(val createdAt: String, val oldPriceCents: Long?, val newPriceCents: Long)
data class AnalyticsProductDemandPoint(val date: String, val quantity: String, val subtotalCents: Long)
data class AnalyticsProductUnitItem(val unitId: String, val unitName: String, val quantity: String, val unit: String)

data class AnalyticsProductDetail(
    val product: AnalyticsProductInfo,
    val inventory: AnalyticsProductInventory,
    val period: AnalyticsProductPeriod,
    val price: AnalyticsProductPrice,
    val priceHistory: List<AnalyticsPriceEvent>,
    val demandTrend: List<AnalyticsProductDemandPoint>,
    val unitRank: List<AnalyticsProductUnitItem>
)

object AnalyticsJsonParser {
    fun overview(json: JSONObject): AnalyticsOverview {
        val summary = json.optJSONObject("summary") ?: JSONObject()
        val comparison = json.optJSONObject("comparison") ?: JSONObject()
        return AnalyticsOverview(
            summary = AnalyticsSummary(
                validOrderCount = summary.optInt("valid_order_count"),
                totalCents = summary.optLong("total_cents"),
                unitCount = summary.optInt("unit_count"),
                productCount = summary.optInt("product_count"),
                inventoryAlertCount = summary.optInt("inventory_alert_count"),
                openReceiptIssues = summary.optInt("open_receipt_issues")
            ),
            comparison = AnalyticsComparison(
                validOrderCountPercent = comparison.optNullableDouble("valid_order_count_percent"),
                totalCentsPercent = comparison.optNullableDouble("total_cents_percent"),
                unitCountPercent = comparison.optNullableDouble("unit_count_percent"),
                productCountPercent = comparison.optNullableDouble("product_count_percent")
            ),
            trend = json.optJSONArray("trend").toObjectList { item ->
                AnalyticsTrendPoint(item.optString("date"), item.optInt("order_count"), item.optLong("total_cents"))
            },
            demandRank = json.optJSONArray("demand_rank").toObjectList { item ->
                AnalyticsDemandItem(
                    item.optString("product_id"), item.optString("product_name"), item.optString("category"),
                    item.optString("unit"), item.optString("quantity", "0")
                )
            }
        )
    }

    fun units(json: JSONObject): List<AnalyticsUnitItem> = json.optJSONArray("items").toObjectList { item ->
        AnalyticsUnitItem(
            item.optString("unit_id"), item.optString("unit_name"), item.optInt("order_count"),
            item.optLong("total_cents"), item.optInt("product_count"), item.optInt("open_receipt_issues")
        )
    }

    fun prices(json: JSONObject): List<AnalyticsPriceItem> = json.optJSONArray("items").toObjectList { item ->
        AnalyticsPriceItem(
            productId = item.optString("product_id"),
            productName = item.optString("product_name"),
            category = item.optString("category"),
            unit = item.optString("unit"),
            initialPriceCents = item.optNullableLong("initial_price_cents"),
            currentPriceCents = item.optLong("current_price_cents"),
            minPriceCents = item.optLong("min_price_cents"),
            maxPriceCents = item.optLong("max_price_cents"),
            changePercent = item.optNullableDouble("change_percent"),
            isNew = item.optBoolean("is_new"),
            changeCount = item.optInt("change_count")
        )
    }

    fun inventory(json: JSONObject): AnalyticsInventory {
        val summary = json.optJSONObject("summary") ?: JSONObject()
        return AnalyticsInventory(
            summary = AnalyticsInventorySummary(
                productCount = summary.optInt("product_count"),
                outOfStock = summary.optInt("out_of_stock"),
                warning = summary.optInt("warning"),
                tight = summary.optInt("tight"),
                paused = summary.optInt("paused"),
                normal = summary.optInt("normal")
            ),
            items = json.optJSONArray("items").toObjectList { item ->
                AnalyticsInventoryItem(
                    productId = item.optString("product_id"), productName = item.optString("product_name"),
                    category = item.optString("category"), spec = item.optString("spec"), unit = item.optString("unit"),
                    stockQuantity = item.optString("stock_quantity", "0"), reservedQuantity = item.optString("reserved_quantity", "0"),
                    availableQuantity = item.optString("available_quantity", "0"), warningQuantity = item.optString("warning_quantity", "0"),
                    averageDailyDemand = item.optString("average_daily_demand", "0"),
                    estimatedDaysAvailable = item.optNullableString("estimated_days_available"),
                    supplyStatus = item.optString("supply_status"), active = item.optBoolean("active"),
                    risk = item.optString("risk_level", item.optString("risk"))
                )
            }
        )
    }

    fun product(json: JSONObject): AnalyticsProductDetail {
        val product = json.getJSONObject("product")
        val inventory = json.optJSONObject("inventory") ?: JSONObject()
        val period = json.optJSONObject("period") ?: JSONObject()
        val price = json.optJSONObject("price") ?: JSONObject()
        return AnalyticsProductDetail(
            product = AnalyticsProductInfo(
                product.optString("id"), product.optString("name"), product.optString("category"),
                product.optString("spec"), product.optString("unit"), product.optLong("price_cents")
            ),
            inventory = AnalyticsProductInventory(
                inventory.optString("stock_quantity", "0"), inventory.optString("reserved_quantity", "0"),
                inventory.optString("available_quantity", "0"), inventory.optString("warning_quantity", "0"),
                inventory.optString("supply_status")
            ),
            period = AnalyticsProductPeriod(
                period.optInt("order_count"), period.optString("quantity", "0"), period.optLong("amount_cents"),
                period.optInt("unit_count"), period.optString("unit")
            ),
            price = AnalyticsProductPrice(
                currentCents = price.optLong("current_cents", product.optLong("price_cents")),
                rangeStartCents = price.optNullableLong("range_start_cents"),
                minCents = price.optLong("min_cents", product.optLong("price_cents")),
                maxCents = price.optLong("max_cents", product.optLong("price_cents")),
                changeCents = price.optNullableLong("change_cents"),
                changePercent = price.optNullableDouble("change_percent"),
                isNew = price.optBoolean("is_new")
            ),
            priceHistory = json.optJSONArray("price_history").toObjectList { item ->
                AnalyticsPriceEvent(item.optString("created_at"), item.optNullableLong("old_price_cents"), item.optLong("new_price_cents"))
            },
            demandTrend = json.optJSONArray("demand_trend").toObjectList { item ->
                AnalyticsProductDemandPoint(item.optString("date"), item.optString("quantity", "0"), item.optLong("subtotal_cents"))
            },
            unitRank = json.optJSONArray("unit_rank").toObjectList { item ->
                AnalyticsProductUnitItem(item.optString("unit_id"), item.optString("unit_name"), item.optString("quantity", "0"), item.optString("unit"))
            }
        )
    }
}

private inline fun <T> org.json.JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return List(length()) { index -> transform(getJSONObject(index)) }
}

private fun JSONObject.optNullableDouble(name: String): Double? = if (isNull(name) || !has(name)) null else optDouble(name)
private fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name) || !has(name)) null else optString(name)
