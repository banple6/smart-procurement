package com.smartprocurement.internal.ui

enum class AnalyticsSection(val tabId: String) {
    OVERVIEW("overview"),
    PRICE("price"),
    INVENTORY("inventory"),
    UNITS("units"),
    PRODUCT("product");

    companion object {
        fun fromTab(tabId: String): AnalyticsSection = entries.firstOrNull { it.tabId == tabId } ?: OVERVIEW
    }
}

sealed interface AnalyticsLoadState {
    data object Idle : AnalyticsLoadState
    data class Loading(val keepsContent: Boolean) : AnalyticsLoadState
    data class Ready(val refreshedAtMillis: Long) : AnalyticsLoadState
    data class Error(val message: String, val keepsContent: Boolean) : AnalyticsLoadState
}

data class AnalyticsFilterDraft(
    val unitId: String = "",
    val category: String = "",
    val inventoryRiskOnly: Boolean = false
)

internal fun analyticsFilterFields(tabId: String): Set<String> = when (tabId) {
    "inventory" -> setOf("category", "inventoryRiskOnly")
    "price" -> setOf("category")
    "units" -> setOf("category")
    else -> setOf("unitId", "category")
}

internal class AnalyticsRequestTracker {
    private val generations = mutableMapOf<AnalyticsSection, Long>()

    fun begin(section: AnalyticsSection): Long {
        val next = (generations[section] ?: 0L) + 1L
        generations[section] = next
        return next
    }

    fun isLatest(section: AnalyticsSection, generation: Long): Boolean = generations[section] == generation
}

internal fun adminMainTabIds(): List<String> = listOf("dashboard", "ingredients", "orders", "analytics", "profile")

internal fun unitMainTabIds(): List<String> = listOf("home", "cart", "orders", "profile")

internal fun normalizedMainTab(role: String, currentTab: String): String {
    val allowed = if (role == "admin") adminMainTabIds() else unitMainTabIds()
    return currentTab.takeIf(allowed::contains) ?: allowed.first()
}

internal fun productAnalyticsNavigationTarget(productId: String, productName: String = ""): Screen.ProductAnalytics =
    Screen.ProductAnalytics(productId = productId, productName = productName)
