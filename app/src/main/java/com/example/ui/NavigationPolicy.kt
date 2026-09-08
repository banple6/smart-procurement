package com.smartprocurement.internal.ui

data class SessionCapabilities(
    val isAdmin: Boolean = false,
    val canManageAccounts: Boolean = false,
    val canIssueManagerInvites: Boolean = false,
    val canViewSystemStatus: Boolean = false,
    val canViewDetailedMetrics: Boolean = false,
    val canManageBackups: Boolean = false,
    val canRestoreBackups: Boolean = false
) {
    companion object {
        fun from(
            role: String,
            canManageAccounts: Boolean = false,
            canIssueManagerInvites: Boolean = false,
            canViewSystemStatus: Boolean = false,
            canViewDetailedMetrics: Boolean = false,
            canManageBackups: Boolean = false,
            canRestoreBackups: Boolean = false
        ): SessionCapabilities {
            val isAdmin = role == "admin"
            return SessionCapabilities(
                isAdmin = isAdmin,
                canManageAccounts = isAdmin && canManageAccounts,
                canIssueManagerInvites = isAdmin && canIssueManagerInvites,
                canViewSystemStatus = isAdmin && canViewSystemStatus,
                canViewDetailedMetrics = isAdmin && canViewDetailedMetrics,
                canManageBackups = isAdmin && canManageBackups,
                canRestoreBackups = isAdmin && canRestoreBackups
            )
        }
    }
}

fun isAdminOnlyScreen(screen: Screen): Boolean = when (screen) {
    Screen.UnitManagement,
    Screen.UnitQuotaManagement,
    is Screen.UnitQuotaDetail,
    Screen.AccountManagement,
    Screen.Ledger,
    Screen.InventoryRecords,
    Screen.SystemStatus,
    Screen.PreparationSummary,
    Screen.DeliverySheets,
    Screen.DeliveryBatches,
    is Screen.DeliveryBatchCreate,
    is Screen.DeliveryBatchDetail,
    Screen.PriceImports,
    is Screen.PriceImportDetail,
    Screen.Analytics,
    is Screen.ProductAnalytics,
    Screen.AddProduct,
    is Screen.EditProduct,
    Screen.DeletedProducts,
    is Screen.ShippingProof,
    Screen.Outbounds,
    is Screen.OutboundDetail,
    is Screen.OutboundShippingProof -> true
    else -> false
}

fun canOpenScreen(
    role: String,
    screen: Screen,
    capabilities: SessionCapabilities = SessionCapabilities.from(role)
): Boolean {
    if (!isAdminOnlyScreen(screen)) return true
    if (role != "admin" || !capabilities.isAdmin) return false
    return when (screen) {
        Screen.UnitManagement,
        Screen.AccountManagement -> capabilities.canManageAccounts
        Screen.SystemStatus -> capabilities.canViewSystemStatus
        else -> true
    }
}
