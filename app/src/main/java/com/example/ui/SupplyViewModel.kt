package com.smartprocurement.internal.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.data.*
import com.smartprocurement.internal.domain.validation.AuthValidator
import com.smartprocurement.internal.domain.validation.IngredientFormInput
import com.smartprocurement.internal.domain.validation.IngredientValidator
import com.smartprocurement.internal.domain.validation.LoginInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed interface Screen {
    object Splash : Screen
    object Login : Screen
    object DeviceAuth : Screen
    object Home : Screen
    object AddProduct : Screen
    data class EditProduct(val productId: String) : Screen
    object DeletedProducts : Screen
    data class ProductDetail(val productId: String) : Screen
    object Cart : Screen
    object DeliveryForm : Screen
    data class ConfirmDetails(
        val date: String,
        val timeRange: String,
        val location: String,
        val contact: String,
        val urgent: Boolean,
        val allowSubstitute: Boolean,
        val remarks: String
    ) : Screen
    data class SubmitSuccess(val orderId: String) : Screen
    object OrderList : Screen
    data class OrderDetails(val orderId: String) : Screen
}

data class IngredientFormState(
    val id: String = "",
    val name: String = "",
    val category: String = "蔬菜",
    val code: String = "",
    val imagePath: String = "",
    val spec: String = "",
    val unit: String = "公斤",
    val minOrderQuantity: String = "1",
    val quantityStep: String = "1",
    val packagingSpec: String = "",
    val stockQuantity: String = "0",
    val warningQuantity: String = "",
    val availableQuantity: String = "",
    val origin: String = "",
    val internalPrice: String = "",
    val storageMethod: String = "冷藏",
    val shelfLife: String = "",
    val status: String = "正常供应",
    val isAvailable: Boolean = true,
    val allowSubstitute: Boolean = true,
    val substituteId: String = "",
    val remark: String = ""
)

class SupplyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SupplyRepository
    private val sessionStore = SessionStore(application)
    private val imageStorage = IngredientImageStorage(application)
    private val apiClient = ProcurementApiClient()
    private var authToken by mutableStateOf("")

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.supplyDao()
        repository = SupplyRepository(dao)

        viewModelScope.launch {
            sessionStore.sessionFlow.first()?.let { session ->
                if (session.token.isNotBlank()) {
                    runCatching {
                        val remoteUser = withContext(Dispatchers.IO) { apiClient.me(session.token) }
                        authToken = session.token
                        applyRemoteUser(remoteUser)
                        refreshProducts()
                        refreshOrders()
                    }.onFailure {
                        sessionStore.clearSession()
                    }
                }
            }
        }
    }

    // --- State Flows from Repository ---
    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedProducts: StateFlow<List<ProductEntity>> = repository.deletedProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cartItems: StateFlow<List<CartItemEntity>> = repository.cartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOrders: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Router Navigation ---
    val navigationStack = mutableStateListOf<Screen>(Screen.Splash)

    var currentTab by mutableStateOf("home") // "home", "category", "cart", "orders", "profile"

    // --- Profile Info ---
    var currentUser by mutableStateOf<UserEntity?>(null)
        private set
    var userName by mutableStateOf("未登录")
    var userId by mutableStateOf("")
    var userDept by mutableStateOf("")
    val institution = "智慧后勤采购"
    var userRole by mutableStateOf("")
    var unitId by mutableStateOf("")
    var isDeviceAuthorized by mutableStateOf(false)
    var lastSyncText by mutableStateOf("")

    val loginErrors = mutableStateMapOf<String, String>()
    val ingredientErrors = mutableStateMapOf<String, String>()
    var isAuthLoading by mutableStateOf(false)
    var isSavingIngredient by mutableStateOf(false)
    var pendingCameraUri by mutableStateOf<Uri?>(null)

    // Alert dialog message helper
    var alertMessage by mutableStateOf<String?>(null)

    // --- Navigation Methods ---
    fun navigateTo(screen: Screen) {
        // Prevent duplicates on top of stack
        if (navigationStack.lastOrNull() != screen) {
            navigationStack.add(screen)
        }
    }

    fun navigateBack(): Boolean {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        return false
    }

    fun popToRootAndNavigate(screen: Screen) {
        navigationStack.clear()
        navigationStack.add(screen)
    }

    fun finishSplash() {
        if (currentUser != null) popToRootAndNavigate(Screen.Home) else popToRootAndNavigate(Screen.Login)
    }

    fun login(username: String, password: String, rememberLogin: Boolean) {
        val validation = AuthValidator.validateLogin(LoginInput(username, password))
        loginErrors.clear()
        loginErrors.putAll(validation.errors)
        if (!validation.isValid) return
        isAuthLoading = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { apiClient.login(username, password) } }
            isAuthLoading = false
            result.onSuccess { login ->
                authToken = login.token
                applyRemoteUser(login.user)
                if (rememberLogin) {
                    sessionStore.saveSession(
                        LoginSession(
                            userId = login.user.id,
                            rememberLogin = true,
                            token = login.token,
                            expiresAt = login.expiresAt,
                            username = login.user.username,
                            displayName = login.user.displayName,
                            role = login.user.role,
                            unitId = login.user.unitId
                        )
                    )
                }
                refreshProducts()
                refreshOrders()
                popToRootAndNavigate(Screen.Home)
            }.onFailure {
                loginErrors["password"] = it.message ?: "账号或密码错误"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val tokenToRevoke = authToken
            if (tokenToRevoke.isNotBlank()) {
                runCatching { withContext(Dispatchers.IO) { apiClient.logout(tokenToRevoke) } }
            }
            sessionStore.clearSession()
            repository.clearCart()
            repository.clearOrderCache()
            currentUser = null
            userName = "未登录"
            userId = ""
            userDept = ""
            userRole = ""
            unitId = ""
            authToken = ""
            currentTab = "home"
            popToRootAndNavigate(Screen.Login)
        }
    }

    private fun applyRemoteUser(user: RemoteUser) {
        currentUser = UserEntity(
            id = user.id,
            username = user.username,
            passwordHash = "",
            passwordSalt = "",
            realName = user.displayName,
            phone = "",
            department = if (user.role == "admin") "系统管理员" else "子单位账号",
            role = user.role
        )
        userName = user.displayName
        userId = user.username
        userDept = if (user.role == "admin") "系统管理员" else "子单位账号"
        userRole = user.role
        unitId = user.unitId
        isDeviceAuthorized = false
    }

    fun refreshProducts() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val products = withContext(Dispatchers.IO) { apiClient.products(authToken) }
                repository.replaceProducts(products)
                lastSyncText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }.onFailure {
                alertMessage = it.message ?: "商品同步失败"
            }
        }
    }

    fun refreshOrders() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val orders = withContext(Dispatchers.IO) {
                    apiClient.orders(authToken, currentUser?.role == "admin")
                }
                repository.replaceOrders(orders)
            }.onFailure {
                alertMessage = it.message ?: "订单同步失败"
            }
        }
    }

    fun canManageIngredients(): Boolean = currentUser?.role == "admin"
    fun canDeleteIngredients(): Boolean = currentUser?.role == "admin"
    fun canRestoreIngredients(): Boolean = currentUser?.role == "admin"

    fun createCameraUri(): Uri {
        val uri = imageStorage.createCameraUri()
        pendingCameraUri = uri
        return uri
    }

    fun persistIngredientImage(uri: Uri, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { imageStorage.saveImage(uri) }
            result.onSuccess(onSaved).onFailure {
                alertMessage = "图片读取失败，请重新选择或拍摄"
            }
        }
    }

    fun formStateFor(productId: String?): IngredientFormState {
        val product = productId?.let { allProducts.value.find { product -> product.id == it } }
        return product?.toFormState() ?: IngredientFormState()
    }

    fun saveIngredient(form: IngredientFormState, onSuccess: () -> Unit) {
        if (!canManageIngredients()) {
            alertMessage = "当前账号无权保存食材"
            return
        }
        val validation = IngredientValidator.validate(
            IngredientFormInput(
                name = form.name,
                category = form.category,
                spec = form.spec,
                unit = form.unit,
                minOrderQuantity = form.minOrderQuantity,
                quantityStep = form.quantityStep,
                stockQuantity = form.stockQuantity,
                warningQuantity = form.warningQuantity,
                availableQuantity = form.availableQuantity,
                internalPrice = form.internalPrice
            )
        )
        ingredientErrors.clear()
        ingredientErrors.putAll(validation.errors)
        if (!validation.isValid) return

        isSavingIngredient = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = if (form.id.isBlank()) null else repository.getProductById(form.id)
            val product = ProductEntity(
                id = form.id,
                name = form.name.trim(),
                spec = form.spec.trim(),
                unit = form.unit,
                imageUrl = form.imagePath,
                origin = form.origin.trim(),
                minQty = form.minOrderQuantity.toDoubleOrNull() ?: 1.0,
                stepQty = form.quantityStep.toDoubleOrNull() ?: 1.0,
                allowSubstitute = form.allowSubstitute,
                stockStatus = "充足",
                price = form.internalPrice.toDoubleOrNull() ?: 0.0,
                category = form.category,
                code = form.code.trim(),
                imagePath = form.imagePath,
                packagingSpec = form.packagingSpec.trim(),
                stockQuantity = form.stockQuantity,
                warningQuantity = form.warningQuantity,
                availableQuantity = form.availableQuantity,
                storageMethod = form.storageMethod,
                shelfLife = form.shelfLife.trim(),
                status = form.status,
                isAvailable = form.isAvailable,
                substituteId = form.substituteId,
                remark = form.remark.trim(),
                isDeleted = existing?.isDeleted ?: false,
                createdBy = existing?.createdBy ?: (currentUser?.id ?: ""),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val saved = apiClient.saveProduct(authToken, product)
                    if (form.imagePath.isNotBlank() && !form.imagePath.startsWith("http") && java.io.File(form.imagePath).exists()) {
                        apiClient.uploadProductImage(authToken, saved.id, form.imagePath)
                    }
                }
            }
            isSavingIngredient = false
            result.onSuccess {
                refreshProducts()
                alertMessage = if (existing == null) "食材已保存" else "食材修改已保存"
                onSuccess()
            }.onFailure {
                alertMessage = it.message ?: "保存失败"
            }
        }
    }

    fun setIngredientAvailable(productId: String, available: Boolean) {
        if (!canManageIngredients()) {
            alertMessage = "当前账号无权调整供应状态"
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.setProductStatus(authToken, productId, if (available) "normal" else "off_shelf", available)
                }
            }.onSuccess {
                refreshProducts()
                alertMessage = if (available) "食材已重新上架" else "食材已下架"
            }.onFailure {
                alertMessage = it.message ?: "调整供应状态失败"
            }
        }
    }

    fun softDeleteIngredient(productId: String) {
        if (!canDeleteIngredients()) {
            alertMessage = "当前账号无权删除食材"
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.deleteProduct(authToken, productId) } }
                .onSuccess {
                    refreshProducts()
                    alertMessage = "食材已删除"
                    navigateBack()
                }.onFailure {
                    alertMessage = it.message ?: "删除失败"
                }
        }
    }

    fun restoreIngredient(productId: String) {
        if (!canRestoreIngredients()) {
            alertMessage = "当前账号无权恢复食材"
            return
        }
        viewModelScope.launch {
            repository.restoreProduct(productId)
            alertMessage = "食材已恢复"
        }
    }

    // --- Cart Actions ---
    fun addToCart(productId: String, quantity: Double, remarks: String = "") {
        viewModelScope.launch {
            repository.addToCart(productId, quantity, remarks)
        }
    }

    fun updateCartQty(productId: String, quantity: Double) {
        viewModelScope.launch {
            repository.updateCartItemQuantity(productId, quantity)
        }
    }

    fun updateCartRemarks(productId: String, remarks: String) {
        viewModelScope.launch {
            repository.updateCartItemRemarks(productId, remarks)
        }
    }

    fun deleteCartItem(productId: String) {
        viewModelScope.launch {
            repository.deleteCartItem(productId)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            repository.clearCart()
        }
    }

    // --- Submit Order ---
    fun submitOrder(
        date: String,
        timeRange: String,
        location: String,
        contact: String,
        urgent: Boolean,
        allowSubstitute: Boolean,
        remarks: String
    ) {
        viewModelScope.launch {
            val cartList = repository.getCartItemsDirect()
            if (cartList.isEmpty()) return@launch
            if (authToken.isBlank()) {
                alertMessage = "请先登录后再提交订单"
                popToRootAndNavigate(Screen.Login)
                return@launch
            }

            val remoteResult = runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.createOrder(authToken, remarks, cartList.map { it.productId to it.quantity })
                }
            }
            if (remoteResult.isFailure) {
                alertMessage = remoteResult.exceptionOrNull()?.message ?: "订单提交失败"
                return@launch
            }
            val remoteOrder = RemoteOrderMapper.mapOrder(remoteResult.getOrThrow())
            repository.upsertOrder(remoteOrder)
            repository.clearCart()
            refreshProducts()
            popToRootAndNavigate(Screen.SubmitSuccess(remoteOrder.order.orderId))
        }
    }

    // --- Single Order / Items Queries ---
    fun getOrderFlow(orderId: String): kotlinx.coroutines.flow.Flow<OrderEntity?> = repository.getOrderFlow(orderId)
    fun getOrderItemsFlow(orderId: String): kotlinx.coroutines.flow.Flow<List<OrderItemEntity>> = repository.getOrderItemsFlow(orderId)

    fun nextOrderActionLabel(order: OrderEntity): String? {
        if (currentUser?.role == "admin") {
            return when (order.status) {
                "待接单" -> "接单"
                "已接单" -> "开始备货"
                "备货中" -> "确认发货"
                "已发货" -> "完成订单"
                else -> null
            }
        }
        return when (order.status) {
            "待接单" -> "取消订单"
            "已发货" -> "确认收货"
            else -> null
        }
    }

    fun performOrderAction(order: OrderEntity) {
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后操作订单"
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                val bundle = withContext(Dispatchers.IO) {
                    if (currentUser?.role == "admin") {
                        val nextStatus = RemoteOrderMapper.apiStatusForNextUiAction(order.status, isAdmin = true)
                            ?: throw IllegalStateException("当前状态不可推进")
                        apiClient.setAdminOrderStatus(authToken, order.orderId, nextStatus)
                    } else {
                        when (order.status) {
                            "待接单" -> apiClient.cancelOrder(authToken, order.orderId)
                            "已发货" -> apiClient.confirmReceipt(authToken, order.orderId)
                            else -> throw IllegalStateException("当前状态不可操作")
                        }
                    }
                }
                repository.upsertOrder(bundle)
                refreshProducts()
                bundle
            }
            result.onSuccess {
                alertMessage = "订单状态已更新"
            }.onFailure {
                alertMessage = it.message ?: "订单操作失败"
            }
        }
    }

    // --- Replacement Confirm ---
    fun acceptReplacement(orderId: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, "已接单")
            alertMessage = "已接单替换方案。订单已更新，正在通知已发货心。"
        }
    }

    fun rejectReplacement(orderId: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, "已完成")
            alertMessage = "该食材已取消申领，订单需求已更新。"
        }
    }

    private fun ProductEntity.toFormState(): IngredientFormState = IngredientFormState(
        id = id,
        name = name,
        category = category,
        code = code,
        imagePath = imagePath.ifBlank { imageUrl },
        spec = spec,
        unit = unit,
        minOrderQuantity = minQty.toCleanString(),
        quantityStep = stepQty.toCleanString(),
        packagingSpec = packagingSpec,
        stockQuantity = stockQuantity,
        warningQuantity = warningQuantity,
        availableQuantity = availableQuantity,
        origin = origin,
        internalPrice = if (price == 0.0) "" else price.toCleanString(),
        storageMethod = storageMethod.ifBlank { "冷藏" },
        shelfLife = shelfLife,
        status = status,
        isAvailable = isAvailable,
        allowSubstitute = allowSubstitute,
        substituteId = substituteId,
        remark = remark
    )

    private fun Double.toCleanString(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}
