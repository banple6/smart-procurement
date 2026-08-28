package com.smartprocurement.internal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ExternalActionType {
    SHIPPING_CAMERA,
    OUTBOUND_SHIPPING_CAMERA,
    BATCH_SUMMARY_EXPORT,
    BATCH_PICKING_EXPORT,
    BATCH_OUTBOUND_EXPORT,
    OUTBOUND_EXPORT,
    LEDGER_EXPORT,
    PREPARATION_EXPORT,
    DELIVERY_SHEETS_EXPORT,
    PRODUCT_IMPORT_TEMPLATE_EXPORT,
    PRICE_IMPORT_PICKER
}

val WorkbookExportTypes = setOf(
    ExternalActionType.BATCH_SUMMARY_EXPORT,
    ExternalActionType.BATCH_PICKING_EXPORT,
    ExternalActionType.BATCH_OUTBOUND_EXPORT,
    ExternalActionType.OUTBOUND_EXPORT,
    ExternalActionType.LEDGER_EXPORT,
    ExternalActionType.PREPARATION_EXPORT,
    ExternalActionType.DELIVERY_SHEETS_EXPORT
)

typealias WorkbookDocumentRequest = (ExternalActionType, String, String) -> Unit

data class PendingExternalAction(
    val type: ExternalActionType,
    val ownerId: String = "",
    val payload: String = ""
) {
    fun restoredScreen(): Screen = when (type) {
        ExternalActionType.SHIPPING_CAMERA -> Screen.ShippingProof(ownerId)
        ExternalActionType.OUTBOUND_SHIPPING_CAMERA -> Screen.OutboundShippingProof(ownerId)
        ExternalActionType.BATCH_SUMMARY_EXPORT,
        ExternalActionType.BATCH_PICKING_EXPORT,
        ExternalActionType.BATCH_OUTBOUND_EXPORT -> Screen.DeliveryBatchDetail(ownerId)
        ExternalActionType.OUTBOUND_EXPORT -> Screen.OutboundDetail(ownerId)
        ExternalActionType.LEDGER_EXPORT -> Screen.Ledger
        ExternalActionType.PREPARATION_EXPORT -> Screen.PreparationSummary
        ExternalActionType.DELIVERY_SHEETS_EXPORT -> Screen.DeliverySheets
        ExternalActionType.PRODUCT_IMPORT_TEMPLATE_EXPORT,
        ExternalActionType.PRICE_IMPORT_PICKER -> Screen.Home
    }
}

class ExternalActivityState(
    initialAction: PendingExternalAction? = null,
    private val persist: (PendingExternalAction?) -> Unit = {}
) {
    var pendingAction: PendingExternalAction? by mutableStateOf(initialAction)
        private set

    fun begin(action: PendingExternalAction) {
        pendingAction = action
        persist(action)
    }

    fun consume(type: ExternalActionType, ownerId: String = ""): PendingExternalAction? {
        val current = pendingAction ?: return null
        if (current.type != type || current.ownerId != ownerId) return null
        pendingAction = null
        persist(null)
        return current
    }

    fun clear() {
        pendingAction = null
        persist(null)
    }
}
