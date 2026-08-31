package com.smartprocurement.internal.data

enum class CartItemState {
    VALID,
    STALE,
}

/** A cart row derived from the current catalog without changing the user's saved cart. */
data class CartLine(
    val cartItem: CartItemEntity,
    val product: ProductEntity?,
    val state: CartItemState,
    val displayName: String,
) {
    val canSubmit: Boolean get() = state == CartItemState.VALID
    val message: String get() = "该食材已更新或停止供应，请重新选择"
}

object CartReconciler {
    fun reconcile(
        cartItems: List<CartItemEntity>,
        activeProducts: List<ProductEntity>,
        knownProducts: List<ProductEntity> = activeProducts,
    ): List<CartLine> {
        val activeById = activeProducts
            .filter(::isOrderableCatalogProduct)
            .associateBy { it.id }
        val knownById = knownProducts.associateBy { it.id }
        return cartItems.map { cartItem ->
            val product = activeById[cartItem.productId]
            if (product == null) {
                CartLine(
                    cartItem = cartItem,
                    product = null,
                    state = CartItemState.STALE,
                    displayName = knownById[cartItem.productId]?.name?.ifBlank { "某项历史食材" } ?: "某项历史食材",
                )
            } else {
                CartLine(
                    cartItem = cartItem,
                    product = product,
                    state = CartItemState.VALID,
                    displayName = product.name,
                )
            }
        }
    }

    private fun isOrderableCatalogProduct(product: ProductEntity): Boolean =
        !product.isDeleted && product.isAvailable && product.status !in setOf("已下架", "暂停供应")
}
