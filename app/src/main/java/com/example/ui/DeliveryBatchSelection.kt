package com.smartprocurement.internal.ui

internal data class DeliveryBatchPreselection(
    val selectedOrderIds: Set<String>,
    val missingPreselectedOrder: Boolean
)

internal fun resolveDeliveryBatchPreselection(
    preselectOrderId: String?,
    eligibleOrderIds: List<String>,
    currentSelection: Set<String> = emptySet()
): DeliveryBatchPreselection {
    val eligible = eligibleOrderIds.toSet()
    val selected = currentSelection.intersect(eligible).toMutableSet()
    if (preselectOrderId.isNullOrBlank()) return DeliveryBatchPreselection(selected, false)
    if (preselectOrderId !in eligible) return DeliveryBatchPreselection(selected, true)
    selected += preselectOrderId
    return DeliveryBatchPreselection(selected, false)
}
