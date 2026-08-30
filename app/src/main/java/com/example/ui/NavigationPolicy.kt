package com.smartprocurement.internal.ui

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

fun canOpenScreen(role: String, screen: Screen): Boolean {
    return !isAdminOnlyScreen(screen) || role == "admin"
}
