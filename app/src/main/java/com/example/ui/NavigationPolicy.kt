package com.smartprocurement.internal.ui

fun isAdminOnlyScreen(screen: Screen): Boolean = when (screen) {
    Screen.UnitManagement,
    Screen.AccountManagement,
    Screen.Ledger,
    Screen.InventoryRecords,
    Screen.SystemStatus,
    Screen.PreparationSummary,
    Screen.DeliverySheets,
    Screen.AddProduct,
    is Screen.EditProduct,
    Screen.DeletedProducts,
    is Screen.ShippingProof -> true
    else -> false
}

fun canOpenScreen(role: String, screen: Screen): Boolean {
    return !isAdminOnlyScreen(screen) || role == "admin"
}
