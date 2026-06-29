package com.smartprocurement.internal.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

// --- Entities ---

data class ShippingPhotoDto(
    val id: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val uploadedAt: String,
    val uploadedByUsername: String,
    val source: String
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val spec: String,
    val unit: String,
    val imageUrl: String,
    val origin: String,
    val minQty: Double,
    val stepQty: Double,
    var allowSubstitute: Boolean = true,
    val stockStatus: String, // "充足", "紧张", "已售罄"
    val price: Double,
    val category: String = "其他",
    val code: String = "",
    val imagePath: String = "",
    val packagingSpec: String = "",
    val stockQuantity: String = "0",
    val reservedQuantity: String = "0",
    val warningQuantity: String = "",
    val availableQuantity: String = "",
    val storageMethod: String = "",
    val shelfLife: String = "",
    val status: String = "正常供应",
    val isAvailable: Boolean = true,
    val substituteId: String = "",
    val remark: String = "",
    val isDeleted: Boolean = false,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val passwordHash: String,
    val passwordSalt: String,
    val realName: String,
    val phone: String,
    val department: String,
    val role: String,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val productId: String,
    val quantity: Double,
    val remarks: String = ""
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val displayOrderNo: String = "",
    val submitTime: String,
    val createdAt: String = "",
    val acceptedAt: String = "",
    val preparingAt: String = "",
    val shippedAt: String = "",
    val completedAt: String = "",
    val deliveryPoint: String,
    val status: String, // "待接单", "已接单", "备货中", "已发货", "已完成", "已取消"
    val requesterName: String,
    val department: String,
    val phone: String,
    val remarks: String = "",
    val shippingNote: String = "",
    val shippingPhotoCount: Int = 0,
    val shippingPhotosJson: String = "[]",
    val totalCents: Long = 0,
    val itemCount: Int = 0,
    val urgent: Boolean = false,
    val allowSubstitute: Boolean = true
) {
    val shippingPhotos: List<ShippingPhotoDto>
        get() = runCatching {
            val array = JSONArray(shippingPhotosJson.ifBlank { "[]" })
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ShippingPhotoDto(
                    id = item.optString("id"),
                    thumbnailUrl = item.optString("thumbnail_url"),
                    fullUrl = item.optString("full_url"),
                    uploadedAt = item.optString("uploaded_at").replace('T', ' ').take(16),
                    uploadedByUsername = item.optString("uploaded_by_username"),
                    source = item.optString("source", "camera")
                )
            }
        }.getOrDefault(emptyList())
}

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderId: String,
    val productId: String,
    val productName: String,
    val productSpec: String,
    val productUnit: String,
    val productImageUrl: String,
    val requestedQty: Double,
    val confirmedQty: Double,
    val deliveredQty: Double,
    val price: Double,
    val isSubstitute: Boolean = false,
    val originalProductName: String = "",
    val currentProductName: String = ""
)

// --- DAO ---

@Dao
interface SupplyDao {
    // Products
    @Query("SELECT * FROM products WHERE isDeleted = 0")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("UPDATE products SET isAvailable = :available, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProductAvailability(id: String, available: Boolean, status: String, updatedAt: Long)

    @Query("UPDATE products SET isDeleted = 1, isAvailable = 0, status = '已下架', updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteProduct(id: String, updatedAt: Long)

    @Query("UPDATE products SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreProduct(id: String, updatedAt: Long)

    // Users
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    // Cart Items
    @Query("SELECT * FROM cart_items")
    fun getCartItemsFlow(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items")
    suspend fun getCartItemsDirect(): List<CartItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(cartItem: CartItemEntity)

    @Delete
    suspend fun deleteCartItem(cartItem: CartItemEntity)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()

    // Orders
    @Query("SELECT * FROM orders ORDER BY submitTime DESC")
    fun getAllOrdersFlow(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    fun getOrderFlow(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM orders")
    suspend fun clearOrders()

    // Order Items
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getOrderItemsFlow(orderId: String): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItemsDirect(orderId: String): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items")
    suspend fun clearOrderItems()

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun clearOrderItems(orderId: String)
}

// --- Database ---

@Database(
    entities = [ProductEntity::class, UserEntity::class, CartItemEntity::class, OrderEntity::class, OrderItemEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun supplyDao(): SupplyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supply_procurement_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
