package com.smartprocurement.internal.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface AdminUiEvent {
    data class NavigateToBatchCreate(val orderId: String) : AdminUiEvent
}

internal fun adminOrderActionSuccessEvent(
    isAdmin: Boolean,
    previousStatus: String,
    updatedStatus: String,
    orderId: String
): AdminUiEvent? {
    if (!isAdmin || previousStatus != "待接单") return null
    if (updatedStatus !in setOf("已接单", "备货中")) return null
    return AdminUiEvent.NavigateToBatchCreate(orderId)
}

internal class AdminUiEventQueue {
    private val channel = Channel<AdminUiEvent>(capacity = Channel.BUFFERED)

    val events: Flow<AdminUiEvent> = channel.receiveAsFlow()

    fun emit(event: AdminUiEvent) {
        channel.trySend(event)
    }
}

internal data class DeliveryBatchPreselectionResult(
    val selectedOrderIds: Set<String>,
    val missingPreselectedOrder: Boolean
)

internal fun resolveDeliveryBatchPreselection(
    preselectOrderId: String?,
    eligibleOrderIds: Collection<String>,
    currentSelection: Set<String> = emptySet()
): DeliveryBatchPreselectionResult {
    val eligible = eligibleOrderIds.toSet()
    val retainedSelection = currentSelection.intersect(eligible)
    if (preselectOrderId.isNullOrBlank()) {
        return DeliveryBatchPreselectionResult(retainedSelection, missingPreselectedOrder = false)
    }
    if (preselectOrderId !in eligible) {
        return DeliveryBatchPreselectionResult(retainedSelection, missingPreselectedOrder = true)
    }
    return DeliveryBatchPreselectionResult(retainedSelection + preselectOrderId, missingPreselectedOrder = false)
}
