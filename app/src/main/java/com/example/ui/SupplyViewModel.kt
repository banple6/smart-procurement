package com.smartprocurement.internal.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.data.*
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.validation.AuthValidator
import com.smartprocurement.internal.domain.validation.CartProductSnapshot
import com.smartprocurement.internal.domain.validation.CartValidator
import com.smartprocurement.internal.domain.validation.IngredientFormInput
import com.smartprocurement.internal.domain.validation.IngredientValidator
import com.smartprocurement.internal.domain.validation.InventoryAdjustMode
import com.smartprocurement.internal.domain.validation.LoginInput
import com.smartprocurement.internal.domain.validation.PasswordChangeInput
import com.smartprocurement.internal.domain.validation.PasswordGenerator
import com.smartprocurement.internal.domain.validation.ProductQuickActionValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed interface Screen {
    object Splash : Screen
    object Login : Screen
    object ChangePassword : Screen
    object Home : Screen
    object AddProduct : Screen
    data class EditProduct(val productId: String) : Screen
    object DeletedProducts : Screen
    data class ProductDetail(val productId: String) : Screen
    object Cart : Screen
    data class SubmitSuccess(val orderId: String) : Screen
    object OrderList : Screen
    data class OrderDetails(val orderId: String) : Screen
    data class ShippingProof(val orderId: String) : Screen
    object UnitManagement : Screen
    object AccountManagement : Screen
    object Ledger : Screen
    object InventoryRecords : Screen
    object PreparationSummary : Screen
    object DeliverySheets : Screen
    object WebQrScanner : Screen
    object WebLoginConfirm : Screen
    object WebSessions : Screen
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

data class OneTimeCredentialNotice(
    val unitName: String,
    val username: String,
    val displayName: String,
    val password: String,
    val type: String
)

fun extractWebLoginToken(rawValue: String): String {
    val trimmed = rawValue.trim()
    if (!trimmed.startsWith("jingrongxianpei://web-login?", ignoreCase = true)) return ""
    val query = trimmed.substringAfter('?', missingDelimiterValue = "")
    return query
        .split('&')
        .firstOrNull { it.startsWith("token=") }
        ?.substringAfter('=')
        .orEmpty()
}

sealed interface ProductSaveState {
    object Idle : ProductSaveState
    object SavingProduct : ProductSaveState
    object UploadingImage : ProductSaveState
    object Success : ProductSaveState
    data class ProductSavedImageFailed(val productId: String, val imagePath: String) : ProductSaveState
    data class Error(val message: String) : ProductSaveState
}

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
                        if (remoteUser.mustChangePassword) {
                            popToRootAndNavigate(Screen.ChangePassword)
                        } else {
                            refreshProducts()
                            refreshOrders()
                            refreshDashboard()
                            refreshCutoff()
                            refreshBadges()
                        }
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
    var currentUnitName by mutableStateOf("")
    var currentUnitCode by mutableStateOf("")
    var defaultDeliveryPoint by mutableStateOf("")
    var userRole by mutableStateOf("")
    var unitId by mutableStateOf("")
    var mustChangePassword by mutableStateOf(false)
    var lastSyncText by mutableStateOf("")

    val loginErrors = mutableStateMapOf<String, String>()
    val passwordErrors = mutableStateMapOf<String, String>()
    val ingredientErrors = mutableStateMapOf<String, String>()
    var isAuthLoading by mutableStateOf(false)
    var isChangingPassword by mutableStateOf(false)
    var isSubmittingOrder by mutableStateOf(false)
    var activeOrderActionId by mutableStateOf("")
    var activeShippingUploadId by mutableStateOf("")
    var activePriceProductId by mutableStateOf("")
    var activeInventoryProductId by mutableStateOf("")
    var activeStatusProductId by mutableStateOf("")
    var isSavingIngredient by mutableStateOf(false)
    var productSaveState by mutableStateOf<ProductSaveState>(ProductSaveState.Idle)
    var dashboard by mutableStateOf(AdminDashboard())
    var adminUnits by mutableStateOf<List<RemoteUnit>>(emptyList())
    var adminUsers by mutableStateOf<List<RemoteAdminUser>>(emptyList())
    var ledgerRows by mutableStateOf<List<LedgerRow>>(emptyList())
    var cutoffInfo by mutableStateOf<CutoffInfo?>(null)
    var preparationSummaryItems by mutableStateOf<List<PreparationSummaryItem>>(emptyList())
    var deliverySheetUnits by mutableStateOf<List<DeliverySheetUnit>>(emptyList())
    var notificationBadges by mutableStateOf(NotificationBadges())
    var pendingCameraUri by mutableStateOf<Uri?>(null)
    var pendingWebLogin by mutableStateOf<WebLoginScanResult?>(null)
    var webLoginActionLoading by mutableStateOf(false)
    var webSessionRecords by mutableStateOf<List<WebSessionRecord>>(emptyList())

    // Alert dialog message helper
    var alertMessage by mutableStateOf<String?>(null)
    var snackbarMessage by mutableStateOf<String?>(null)
    var oneTimeCredentialNotice by mutableStateOf<OneTimeCredentialNotice?>(null)

    // --- Navigation Methods ---
    fun navigateTo(screen: Screen) {
        if (!canOpenScreen(userRole, screen)) {
            snackbarMessage = "当前账号无此操作权限"
            currentTab = "home"
            popToRootAndNavigate(Screen.Home)
            return
        }
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
        when {
            currentUser == null -> popToRootAndNavigate(Screen.Login)
            mustChangePassword -> popToRootAndNavigate(Screen.ChangePassword)
            else -> popToRootAndNavigate(Screen.Home)
        }
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
                            unitId = login.user.unitId,
                            unitCode = login.user.unitCode,
                            unitName = login.user.unitName,
                            defaultDeliveryPoint = login.user.defaultDeliveryPoint,
                            mustChangePassword = login.user.mustChangePassword
                        )
                    )
                }
                if (login.user.mustChangePassword) {
                    popToRootAndNavigate(Screen.ChangePassword)
                } else {
                    refreshProducts()
                    refreshOrders()
                    refreshDashboard()
                    refreshCutoff()
                    refreshBadges()
                    popToRootAndNavigate(Screen.Home)
                }
            }.onFailure {
                loginErrors["password"] = it.toUserMessage("账号或密码错误")
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        val validation = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = userId, oldPassword = oldPassword, newPassword = newPassword, confirmPassword = confirmPassword)
        )
        passwordErrors.clear()
        passwordErrors.putAll(validation.errors)
        if (!validation.isValid) return
        if (authToken.isBlank()) {
            alertMessage = "登录已过期，请重新登录"
            popToRootAndNavigate(Screen.Login)
            return
        }
        isChangingPassword = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { apiClient.changePassword(authToken, oldPassword, newPassword) }
            }
            isChangingPassword = false
            result.onSuccess {
                sessionStore.clearSession()
                repository.clearCart()
                repository.clearOrderCache()
                authToken = ""
                currentUser = null
                mustChangePassword = false
                alertMessage = "密码已修改，请使用新密码重新登录"
                popToRootAndNavigate(Screen.Login)
            }.onFailure {
                passwordErrors["newPassword"] = it.toUserMessage("密码修改失败")
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
            currentUnitName = ""
            currentUnitCode = ""
            defaultDeliveryPoint = ""
            userRole = ""
            unitId = ""
            mustChangePassword = false
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
            department = if (user.role == "admin") "系统管理员" else user.unitName,
            role = user.role
        )
        userName = user.displayName
        userId = user.username
        userDept = if (user.role == "admin") "系统管理员" else user.unitName
        currentUnitName = user.unitName
        currentUnitCode = user.unitCode
        defaultDeliveryPoint = user.defaultDeliveryPoint
        userRole = user.role
        unitId = user.unitId
        mustChangePassword = user.mustChangePassword
    }

    fun refreshProducts() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val products = withContext(Dispatchers.IO) { apiClient.products(authToken) }
                repository.replaceProducts(products)
                lastSyncText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }.onFailure {
                alertMessage = it.toUserMessage("商品同步失败")
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
                alertMessage = it.toUserMessage("订单同步失败")
            }
        }
    }

    fun refreshOrderDetail(orderId: String) {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val bundle = withContext(Dispatchers.IO) {
                    apiClient.orderDetail(authToken, orderId, currentUser?.role == "admin")
                }
                repository.upsertOrder(bundle)
            }.onFailure {
                alertMessage = it.toUserMessage("订单同步失败")
            }
        }
    }

    fun refreshDashboard() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching {
                dashboard = withContext(Dispatchers.IO) { apiClient.dashboard(authToken) }
            }.onFailure {
                alertMessage = it.toUserMessage("工作台同步失败")
            }
        }
    }

    fun refreshCutoff() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.cutoff(authToken) } }
                .onSuccess { cutoffInfo = it }
        }
    }

    fun refreshBadges() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.notificationBadges(authToken) } }
                .onSuccess { notificationBadges = it }
        }
    }

    fun refreshPreparationSummary() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.preparationSummary(authToken) } }
                .onSuccess { preparationSummaryItems = it }
                .onFailure { alertMessage = it.toUserMessage("备货单同步失败") }
        }
    }

    fun refreshDeliverySheets() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.deliverySheets(authToken) } }
                .onSuccess { deliverySheetUnits = it }
                .onFailure { alertMessage = it.toUserMessage("配送单同步失败") }
        }
    }

    fun refreshUnits() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.units(authToken) } }
                .onSuccess { adminUnits = it }
                .onFailure { alertMessage = it.toUserMessage("单位同步失败") }
        }
    }

    fun saveUnit(id: String, code: String, name: String, deliveryPoint: String) {
        if (code.isBlank() || name.isBlank() || deliveryPoint.isBlank()) {
            alertMessage = "请填写单位编码、单位名称和默认配送点"
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.saveUnit(authToken, id, code, name, deliveryPoint) } }
                .onSuccess {
                    refreshUnits()
                    snackbarMessage = "单位已保存"
                }.onFailure { alertMessage = it.toUserMessage("单位保存失败") }
        }
    }

    fun setUnitStatus(id: String, active: Boolean) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.setUnitStatus(authToken, id, active) } }
                .onSuccess {
                    refreshUnits()
                    snackbarMessage = if (active) "单位已启用" else "单位已停用"
                }.onFailure { alertMessage = it.toUserMessage("单位状态修改失败") }
        }
    }

    fun refreshUsers() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.users(authToken) } }
                .onSuccess { adminUsers = it }
                .onFailure { alertMessage = it.toUserMessage("账号同步失败") }
        }
    }

    fun createUnitUser(username: String, displayName: String, unitId: String, password: String) {
        if (username.isBlank() || displayName.isBlank() || unitId.isBlank() || password.isBlank()) {
            alertMessage = "请填写登录账号、显示名称、所属单位和初始密码"
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.createUnitUser(authToken, username, displayName, unitId, password) } }
                .onSuccess { user ->
                    refreshUsers()
                    val unitName = adminUnits.find { it.id == unitId }?.unitName ?: user.unitName
                    oneTimeCredentialNotice = OneTimeCredentialNotice(
                        unitName = unitName,
                        username = user.username,
                        displayName = user.displayName,
                        password = user.initialPassword.ifBlank { password },
                        type = "账号创建成功"
                    )
                }.onFailure { alertMessage = it.toUserMessage("账号创建失败") }
        }
    }

    fun setUserStatus(id: String, active: Boolean) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.setUserStatus(authToken, id, active) } }
                .onSuccess {
                    refreshUsers()
                    alertMessage = if (active) "账号已启用" else "账号已停用"
                }.onFailure { alertMessage = it.toUserMessage("账号状态修改失败") }
        }
    }

    fun resetUserPassword(id: String, newPassword: String) {
        if (newPassword.isBlank()) {
            alertMessage = "请填写新密码"
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.resetPassword(authToken, id, newPassword) } }
                .onSuccess { returnedPassword ->
                    val user = adminUsers.find { it.id == id }
                    oneTimeCredentialNotice = OneTimeCredentialNotice(
                        unitName = user?.unitName.orEmpty(),
                        username = user?.username.orEmpty(),
                        displayName = user?.displayName.orEmpty(),
                        password = returnedPassword.ifBlank { newPassword },
                        type = "密码已重置"
                    )
                }.onFailure { alertMessage = it.toUserMessage("密码重置失败") }
        }
    }

    fun revokeUserSessions(id: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.revokeUserSessions(authToken, id) } }
                .onSuccess { snackbarMessage = "该账号已从所有设备退出" }
                .onFailure { alertMessage = it.toUserMessage("强制退出失败") }
        }
    }

    fun openWebQrScanner() {
        if (authToken.isBlank()) {
            alertMessage = "请先登录后再扫码"
            popToRootAndNavigate(Screen.Login)
            return
        }
        pendingWebLogin = null
        navigateTo(Screen.WebQrScanner)
    }

    fun scanWebLoginQr(rawValue: String) {
        val qrToken = extractWebLoginToken(rawValue)
        if (qrToken.isBlank()) {
            alertMessage = "二维码无效，请扫描网页版登录二维码"
            return
        }
        if (webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.scanWebLoginQr(
                        token = authToken,
                        qrToken = qrToken,
                        deviceName = Build.MODEL.orEmpty().ifBlank { "Android 设备" },
                        appVersion = BuildConfig.VERSION_NAME
                    )
                }
            }
            webLoginActionLoading = false
            result.onSuccess {
                pendingWebLogin = it
                navigateTo(Screen.WebLoginConfirm)
            }.onFailure {
                alertMessage = it.toUserMessage("扫码失败")
            }
        }
    }

    fun approveWebLogin() {
        val challenge = pendingWebLogin ?: return
        if (webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.approveWebLoginQr(authToken, challenge.challengeId) } }
                .onSuccess {
                    snackbarMessage = "网页登录已确认"
                    pendingWebLogin = null
                    currentTab = "profile"
                    popToRootAndNavigate(Screen.Home)
                }
                .onFailure { alertMessage = it.toUserMessage("确认登录失败") }
            webLoginActionLoading = false
        }
    }

    fun rejectWebLogin() {
        val challenge = pendingWebLogin ?: return
        if (webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.rejectWebLoginQr(authToken, challenge.challengeId) } }
                .onSuccess {
                    snackbarMessage = "网页登录已拒绝"
                    pendingWebLogin = null
                    currentTab = "profile"
                    popToRootAndNavigate(Screen.Home)
                }
                .onFailure { alertMessage = it.toUserMessage("拒绝登录失败") }
            webLoginActionLoading = false
        }
    }

    fun refreshWebSessions() {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.webSessions(authToken) } }
                .onSuccess { webSessionRecords = it }
                .onFailure { alertMessage = it.toUserMessage("网页登录记录同步失败") }
        }
    }

    fun revokeWebSession(sessionId: String) {
        if (webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.revokeWebSession(authToken, sessionId) } }
                .onSuccess {
                    snackbarMessage = "该网页登录已退出"
                    refreshWebSessions()
                }
                .onFailure { alertMessage = it.toUserMessage("退出网页登录失败") }
            webLoginActionLoading = false
        }
    }

    fun revokeAllWebSessions() {
        if (webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.revokeAllWebSessions(authToken) } }
                .onSuccess {
                    snackbarMessage = "全部网页登录已退出"
                    refreshWebSessions()
                }
                .onFailure { alertMessage = it.toUserMessage("退出全部网页登录失败") }
            webLoginActionLoading = false
        }
    }

    fun generateInitialPassword(username: String): String = PasswordGenerator.generate(username)

    fun refreshLedger() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.ledger(authToken) } }
                .onSuccess { ledgerRows = it }
                .onFailure { alertMessage = it.toUserMessage("台账同步失败") }
        }
    }

    fun exportLedger(uri: Uri) {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { apiClient.exportLedger(authToken) }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess {
                snackbarMessage = "Excel 已保存"
            }.onFailure {
                alertMessage = it.toUserMessage("Excel 保存失败")
            }
        }
    }

    fun exportPreparationSummary(uri: Uri) {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { apiClient.exportPreparationSummary(authToken) }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess {
                snackbarMessage = "备货单 Excel 已保存"
            }.onFailure {
                alertMessage = it.toUserMessage("Excel 保存失败")
            }
        }
    }

    fun exportDeliverySheets(uri: Uri) {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { apiClient.exportDeliverySheets(authToken) }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess {
                snackbarMessage = "配送单 Excel 已保存"
            }.onFailure {
                alertMessage = it.toUserMessage("Excel 保存失败")
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
                availableQuantity = "",
                internalPrice = form.internalPrice
            )
        )
        ingredientErrors.clear()
        ingredientErrors.putAll(validation.errors)
        if (!validation.isValid) return
        if (form.isAvailable && form.status != "暂停供应" && Money.yuanTextToCents(form.internalPrice) <= 0) {
            ingredientErrors["internalPrice"] = "请先填写商品价格"
            return
        }

        isSavingIngredient = true
        productSaveState = ProductSaveState.SavingProduct
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
                reservedQuantity = existing?.reservedQuantity ?: "0",
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
                    repository.upsertRemoteProduct(saved)
                    if (form.imagePath.isNotBlank() && !form.imagePath.startsWith("http") && java.io.File(form.imagePath).exists()) {
                        productSaveState = ProductSaveState.UploadingImage
                        runCatching { apiClient.uploadProductImage(authToken, saved.id, form.imagePath) }
                            .onFailure { throw ProductImageUploadFailed(saved.id, form.imagePath) }
                    }
                    saved
                }
            }
            isSavingIngredient = false
            result.onSuccess {
                productSaveState = ProductSaveState.Success
                refreshProducts()
                snackbarMessage = if (existing == null) "食材新增已保存" else "食材修改已保存"
                onSuccess()
            }.onFailure {
                if (it is ProductImageUploadFailed) {
                    productSaveState = ProductSaveState.ProductSavedImageFailed(it.productId, it.imagePath)
                    refreshProducts()
                    snackbarMessage = "食材资料已保存，但图片上传失败"
                    onSuccess()
                } else {
                    val msg = it.toUserMessage("保存失败")
                    productSaveState = ProductSaveState.Error(msg)
                    alertMessage = msg
                }
            }
        }
    }

    fun updateProductPrice(product: ProductEntity, priceText: String, reason: String, onSuccess: () -> Unit) {
        val validation = ProductQuickActionValidator.validatePrice(priceText, reason, Money.yuanDoubleToCents(product.price))
        if (!validation.isValid) {
            alertMessage = validation.message
            return
        }
        if (activePriceProductId == product.id) return
        activePriceProductId = product.id
        viewModelScope.launch {
            runCatching {
                val saved = withContext(Dispatchers.IO) {
                    apiClient.updateProductPrice(authToken, product, validation.cents, reason)
                }
                repository.upsertRemoteProduct(saved)
                refreshProducts()
            }.onSuccess {
                snackbarMessage = "价格已更新"
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("价格保存失败")
            }
            activePriceProductId = ""
        }
    }

    fun adjustProductInventory(product: ProductEntity, mode: InventoryAdjustMode, quantityText: String, reason: String, onSuccess: () -> Unit) {
        val validation = ProductQuickActionValidator.validateInventory(mode, quantityText, reason, product.stockQuantity, product.reservedQuantity)
        if (!validation.isValid) {
            alertMessage = validation.message
            return
        }
        if (activeInventoryProductId == product.id) return
        activeInventoryProductId = product.id
        viewModelScope.launch {
            runCatching {
                val result = withContext(Dispatchers.IO) {
                    apiClient.adjustProductInventory(authToken, product, mode.apiValue, quantityText, reason)
                }
                repository.upsertRemoteProduct(result.product)
                refreshProducts()
            }.onSuccess {
                snackbarMessage = "库存已更新"
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("库存保存失败")
            }
            activeInventoryProductId = ""
        }
    }

    fun setIngredientAvailable(productId: String, available: Boolean) {
        if (!canManageIngredients()) {
            alertMessage = "当前账号无权调整供应状态"
            return
        }
        if (activeStatusProductId == productId) return
        activeStatusProductId = productId
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.setProductStatus(authToken, productId, if (available) "normal" else "off_shelf", available)
                }
            }.onSuccess {
                refreshProducts()
                snackbarMessage = if (available) "食材已重新上架" else "食材已下架"
            }.onFailure {
                alertMessage = it.toUserMessage("调整供应状态失败")
            }
            activeStatusProductId = ""
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
                    alertMessage = it.toUserMessage("删除失败")
                }
        }
    }

    fun restoreIngredient(productId: String) {
        if (!canRestoreIngredients()) {
            alertMessage = "当前账号无权恢复食材"
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.restoreProduct(authToken, productId) } }
                .onSuccess {
                    refreshProducts()
                    snackbarMessage = "食材已恢复"
                }.onFailure {
                    alertMessage = it.toUserMessage("恢复失败")
                }
        }
    }

    // --- Cart Actions ---
    fun addToCart(productId: String, quantity: Double, remarks: String = "") {
        val product = allProducts.value.find { it.id == productId }
        val validation = product?.let { validateCartQuantity(it, quantity) }
        if (validation != null && !validation.isValid) {
            alertMessage = validation.message
            return
        }
        viewModelScope.launch {
            repository.addToCart(productId, quantity, remarks)
        }
    }

    fun updateCartQty(productId: String, quantity: Double) {
        if (quantity <= 0) {
            deleteCartItem(productId)
            return
        }
        val product = allProducts.value.find { it.id == productId }
        val validation = product?.let { validateCartQuantity(it, quantity) }
        if (validation != null && !validation.isValid) {
            alertMessage = validation.message
            return
        }
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
    fun submitOrder(remarks: String) {
        submitOrder(
            date = "",
            timeRange = "",
            location = defaultDeliveryPoint,
            contact = "",
            urgent = false,
            allowSubstitute = false,
            remarks = remarks
        )
    }

    fun submitOrder(
        date: String,
        timeRange: String,
        location: String,
        contact: String,
        urgent: Boolean,
        allowSubstitute: Boolean,
        remarks: String
    ) {
        if (isSubmittingOrder) return
        viewModelScope.launch {
            val cartList = repository.getCartItemsDirect()
            if (cartList.isEmpty()) return@launch
            val invalid = cartList.firstNotNullOfOrNull { item ->
                val product = allProducts.value.find { it.id == item.productId }
                if (product == null) "食材不存在或已下架" else validateCartQuantity(product, item.quantity).message.takeIf { validateCartQuantity(product, item.quantity).isValid.not() }
            }
            if (invalid != null) {
                alertMessage = invalid
                return@launch
            }
            if (authToken.isBlank()) {
                alertMessage = "请先登录后再提交订单"
                popToRootAndNavigate(Screen.Login)
                return@launch
            }
            val cutoff = cutoffInfo ?: runCatching { withContext(Dispatchers.IO) { apiClient.cutoff(authToken) } }.getOrNull()
            cutoffInfo = cutoff
            if (cutoff?.isClosed == true) {
                alertMessage = "今日采购已经截止，请联系管理员"
                return@launch
            }

            isSubmittingOrder = true
            val remoteResult = runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.createOrder(authToken, remarks, cartList.map { it.productId to it.quantity })
                }
            }
            isSubmittingOrder = false
            if (remoteResult.isFailure) {
                alertMessage = remoteResult.exceptionOrNull().toUserMessage("订单提交失败")
                return@launch
            }
            val remoteOrder = RemoteOrderMapper.mapOrder(remoteResult.getOrThrow())
            repository.upsertOrder(remoteOrder)
            repository.clearCart()
            refreshProducts()
            refreshCutoff()
            refreshBadges()
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
            "已发货" -> if (order.openReceiptIssueCount > 0) null else "确认收货"
            "已完成" -> "再次下单"
            else -> null
        }
    }

    fun performOrderAction(order: OrderEntity) {
        if (activeOrderActionId == order.orderId) return
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后操作订单"
            return
        }
        activeOrderActionId = order.orderId
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
                            "已完成" -> throw IllegalStateException("请使用再次下单按钮")
                            else -> throw IllegalStateException("当前状态不可操作")
                        }
                    }
                }
                repository.upsertOrder(bundle)
                refreshProducts()
                bundle
            }
            activeOrderActionId = ""
            result.onSuccess {
                snackbarMessage = "订单状态已更新"
                refreshBadges()
            }.onFailure {
                alertMessage = it.toUserMessage("订单操作失败")
            }
        }
    }

    fun submitShippingProof(orderId: String, photoFiles: List<File>, note: String, onSuccess: () -> Unit) {
        if (activeShippingUploadId == orderId) return
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后操作订单"
            return
        }
        if (photoFiles.isEmpty()) {
            alertMessage = "请至少拍摄一张发货照片"
            return
        }
        activeShippingUploadId = orderId
        val requestId = UUID.randomUUID().toString()
        viewModelScope.launch {
            val result = runCatching {
                val bundle = withContext(Dispatchers.IO) {
                    apiClient.shipOrder(authToken, orderId, photoFiles, note, requestId)
                }
                repository.upsertOrder(bundle)
                refreshOrders()
                refreshProducts()
                bundle
            }
            activeShippingUploadId = ""
            result.onSuccess {
                snackbarMessage = "发货照片已保存，订单已更新为已发货"
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("发货失败")
            }
        }
    }

    fun reorder(orderId: String) {
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后操作订单"
            return
        }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { apiClient.reorderPreview(authToken, orderId) } }
            result.onSuccess { items ->
                val availableItems = items.filter { it.available }
                if (availableItems.isEmpty()) {
                    alertMessage = items.firstOrNull()?.message?.ifBlank { "暂无可再次采购的食材" } ?: "暂无可再次采购的食材"
                    return@onSuccess
                }
                availableItems.forEach { item ->
                    val quantity = item.previousQuantity.toDoubleOrNull() ?: 0.0
                    if (quantity > 0) repository.addToCart(item.productId, quantity)
                }
                val skipped = items.size - availableItems.size
                snackbarMessage = if (skipped > 0) "已加入可采购食材，${skipped} 种暂不可采购" else "已加入清单"
                currentTab = "cart"
                popToRootAndNavigate(Screen.Cart)
            }.onFailure {
                alertMessage = it.toUserMessage("再次采购失败")
            }
        }
    }

    fun adjustOrderItem(order: OrderEntity, item: OrderItemEntity, actualQuantity: String, reason: String) {
        if (authToken.isBlank() || item.remoteItemId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val bundle = withContext(Dispatchers.IO) {
                    apiClient.adjustOrderItemActualQuantity(authToken, order.orderId, item.remoteItemId, actualQuantity, reason, order.serverUpdatedAt)
                }
                repository.upsertOrder(bundle)
                refreshProducts()
                bundle
            }.onSuccess {
                snackbarMessage = "实发数量已调整"
            }.onFailure {
                alertMessage = it.toUserMessage("数量调整失败")
            }
        }
    }

    fun submitReceiptIssue(orderId: String, issueType: String, description: String, photoFiles: List<File>, onSuccess: () -> Unit) {
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后反馈异常"
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { apiClient.submitReceiptIssue(authToken, orderId, issueType, description, photoFiles) }
            }.onSuccess {
                refreshOrderDetail(orderId)
                refreshBadges()
                snackbarMessage = "收货异常已提交"
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("异常提交失败")
            }
        }
    }

    fun resolveReceiptIssue(issueId: String, orderId: String, note: String) {
        if (authToken.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.resolveReceiptIssue(authToken, issueId, note) } }
                .onSuccess {
                    refreshOrderDetail(orderId)
                    refreshBadges()
                    snackbarMessage = "收货异常已处理"
                }.onFailure {
                    alertMessage = it.toUserMessage("异常处理失败")
                }
        }
    }

    fun absoluteApiUrl(path: String): String {
        if (path.isBlank() || path.startsWith("http")) return path
        return BuildConfig.API_BASE_URL.substringBefore("/api/v1/").trimEnd('/') + path
    }

    fun bearerToken(): String = authToken

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

    private fun validateCartQuantity(product: ProductEntity, quantity: Double) = CartValidator.canOrder(
        CartProductSnapshot(
            availableQuantity = product.availableQuantity.ifBlank { product.stockQuantity },
            minQty = product.minQty,
            stepQty = product.stepQty,
            priceCents = Money.yuanDoubleToCents(product.price),
            status = product.status,
            isAvailable = product.isAvailable,
            isDeleted = product.isDeleted
        ),
        quantity
    )

    private fun Double.toCleanString(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun Throwable?.toUserMessage(fallback: String): String {
        val message = this?.message.orEmpty()
        val userMessage = when {
            message.contains("登录已过期") -> "登录已过期，请重新登录"
            message.contains("账号已停用") -> "账号已停用，请联系管理员"
            message.contains("所属单位已停用") -> "所属单位已停用，请联系管理员"
            message.contains("库存不足") -> "库存不足，请减少数量"
            message.contains("刚刚被其他管理员修改") -> "该食材刚刚被其他管理员修改，请刷新后重试"
            message.contains("已预占库存") -> "库存不能小于已预占库存"
            message.contains("价格未设置") -> "价格未设置"
            message.contains("暂停供应") -> "该商品已暂停供应"
            message.contains("订单状态已变化") -> "订单状态已变化，请刷新后重试"
            message.contains("低于最小申领量") -> "低于最小申领量"
            message.contains("步长") -> "数量不符合申领步长"
            message.contains("timeout", ignoreCase = true) || message.contains("failed", ignoreCase = true) || message.contains("connect", ignoreCase = true) -> "网络连接失败，请稍后重试"
            else -> message.ifBlank { fallback }
        }
        if (
            userMessage.contains("登录已过期") ||
            userMessage.contains("账号已停用") ||
            userMessage.contains("所属单位已停用")
        ) {
            clearLocalSessionForAuthError()
        }
        return userMessage
    }

    private fun clearLocalSessionForAuthError() {
        viewModelScope.launch {
            sessionStore.clearSession()
            repository.clearCart()
            repository.clearOrderCache()
            authToken = ""
            currentUser = null
            userName = "未登录"
            userId = ""
            userDept = ""
            currentUnitName = ""
            currentUnitCode = ""
            defaultDeliveryPoint = ""
            userRole = ""
            unitId = ""
            mustChangePassword = false
            currentTab = "home"
            popToRootAndNavigate(Screen.Login)
        }
    }

    private class ProductImageUploadFailed(val productId: String, val imagePath: String) : RuntimeException("图片上传失败")
}
