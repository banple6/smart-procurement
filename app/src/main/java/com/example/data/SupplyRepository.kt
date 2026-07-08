package com.smartprocurement.internal.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class SupplyRepository(private val database: AppDatabase) {
    private val supplyDao = database.supplyDao()

    val allProducts: Flow<List<ProductEntity>> = supplyDao.getAllProductsFlow()
    val deletedProducts: Flow<List<ProductEntity>> = supplyDao.getDeletedProductsFlow()
    val cartItems: Flow<List<CartItemEntity>> = supplyDao.getCartItemsFlow()
    val allOrders: Flow<List<OrderEntity>> = supplyDao.getAllOrdersFlow()

    fun getOrderFlow(orderId: String): Flow<OrderEntity?> = supplyDao.getOrderFlow(orderId)
    fun getOrderItemsFlow(orderId: String): Flow<List<OrderItemEntity>> = supplyDao.getOrderItemsFlow(orderId)

    suspend fun getProductById(id: String): ProductEntity? = supplyDao.getProductById(id)
    suspend fun updateProduct(product: ProductEntity) = supplyDao.updateProduct(product)
    suspend fun replaceProducts(products: List<ProductEntity>) {
        database.withTransaction {
            supplyDao.clearProducts()
            if (products.isNotEmpty()) supplyDao.insertProducts(products)
        }
    }
    suspend fun saveProduct(product: ProductEntity) = supplyDao.insertProduct(product.withComputedSupplyStatus())
    suspend fun setProductAvailable(id: String, available: Boolean) {
        supplyDao.updateProductAvailability(
            id = id,
            available = available,
            status = if (available) "正常供应" else "已下架",
            updatedAt = System.currentTimeMillis()
        )
    }
    suspend fun softDeleteProduct(id: String) = supplyDao.softDeleteProduct(id, System.currentTimeMillis())
    suspend fun restoreProduct(id: String) = supplyDao.restoreProduct(id, System.currentTimeMillis())

    suspend fun getUserById(id: String): UserEntity? = supplyDao.getUserById(id)
    suspend fun getUserByUsername(username: String): UserEntity? = supplyDao.getUserByUsername(username)

    suspend fun getCartItemsDirect(): List<CartItemEntity> = supplyDao.getCartItemsDirect()
    suspend fun addToCart(productId: String, quantity: Double, remarks: String = "") {
        val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
        if (existing != null) {
            // Overwrite or add quantity
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        } else {
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        }
    }
    suspend fun updateCartItemQuantity(productId: String, quantity: Double) {
        if (quantity <= 0) {
            supplyDao.deleteCartItem(CartItemEntity(productId, 0.0))
        } else {
            val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
            val remarks = existing?.remarks ?: ""
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        }
    }
    suspend fun updateCartItemRemarks(productId: String, remarks: String) {
        val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
        val qty = existing?.quantity ?: 1.0
        supplyDao.insertCartItem(CartItemEntity(productId, qty, remarks))
    }
    suspend fun deleteCartItem(productId: String) {
        supplyDao.deleteCartItem(CartItemEntity(productId, 0.0))
    }
    suspend fun clearCart() = supplyDao.clearCart()

    suspend fun insertOrder(order: OrderEntity) = supplyDao.insertOrder(order)
    suspend fun insertOrderItems(items: List<OrderItemEntity>) = supplyDao.insertOrderItems(items)
    suspend fun replaceOrders(orderBundles: List<RemoteOrderBundle>) {
        if (orderBundles.isNotEmpty()) {
            database.withTransaction {
                orderBundles.forEach { bundle ->
                    val existing = supplyDao.getOrderById(bundle.order.orderId)
                    if (existing == null || bundle.order.isSameOrNewerThan(existing)) {
                        supplyDao.insertOrder(bundle.order)
                        supplyDao.clearOrderItems(bundle.order.orderId)
                        if (bundle.items.isNotEmpty()) supplyDao.insertOrderItems(bundle.items)
                    }
                }
            }
        }
    }
    suspend fun clearOrderCache() {
        supplyDao.clearOrderItems()
        supplyDao.clearOrders()
    }
    suspend fun upsertOrder(orderBundle: RemoteOrderBundle) {
        database.withTransaction {
            val existing = supplyDao.getOrderById(orderBundle.order.orderId)
            if (existing == null || orderBundle.order.isSameOrNewerThan(existing)) {
                supplyDao.insertOrder(orderBundle.order)
                supplyDao.clearOrderItems(orderBundle.order.orderId)
                if (orderBundle.items.isNotEmpty()) supplyDao.insertOrderItems(orderBundle.items)
            }
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        val order = supplyDao.getOrderById(orderId)
        if (order != null) {
            supplyDao.insertOrder(order.copy(status = status))
        }
    }

    private fun ProductEntity.withComputedSupplyStatus(): ProductEntity {
        val stock = stockQuantity.toBigDecimalOrNullCompat()
        val warning = warningQuantity.toBigDecimalOrNullCompat()
        val computedStatus = when {
            isDeleted -> "已下架"
            !isAvailable -> "已下架"
            stock != null && warning != null && stock < warning -> "库存紧张"
            status == "暂停供应" -> "暂停供应"
            else -> "正常供应"
        }
        return copy(
            status = computedStatus,
            stockStatus = if (computedStatus == "库存紧张") "紧张" else if (stock == BigDecimal.ZERO) "已售罄" else "充足",
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun String.toBigDecimalOrNullCompat(): BigDecimal? = runCatching {
        if (isBlank()) null else BigDecimal(trim())
    }.getOrNull()

    private fun OrderEntity.isSameOrNewerThan(existing: OrderEntity): Boolean {
        if (version != existing.version) return version > existing.version
        if (remoteUpdatedAt.isNotBlank() && existing.remoteUpdatedAt.isNotBlank()) {
            return remoteUpdatedAt >= existing.remoteUpdatedAt
        }
        return true
    }
}
