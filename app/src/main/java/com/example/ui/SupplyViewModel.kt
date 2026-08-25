package com.smartprocurement.internal.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.data.*
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.validation.AuthValidator
import com.smartprocurement.internal.domain.validation.CartProductSnapshot
import com.smartprocurement.internal.domain.validation.CartValidator
import com.smartprocurement.internal.domain.validation.IngredientFormInput
import com.smartprocurement.internal.domain.validation.IngredientValidator
import com.smartprocurement.internal.domain.validation.LoginInput
import com.smartprocurement.internal.domain.validation.PasswordChangeInput
import com.smartprocurement.internal.notifications.PushDeepLinkPolicy
import com.smartprocurement.internal.notifications.PushEvent
import com.smartprocurement.internal.notifications.PushNotificationManager
import com.smartprocurement.internal.notifications.PushNotificationPolicy
import com.smartprocurement.internal.notifications.PushNotificationState
import com.smartprocurement.internal.notifications.PushRegistrationWorker
import com.smartprocurement.internal.notifications.OrderSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val PREFERRED_ENTRY_METHOD_KEY = "preferred_entry_method"
private const val SAVED_MAIN_TAB_KEY = "main_tab"
private const val SAVED_ANALYTICS_START_DATE_KEY = "analytics_start_date"
private const val SAVED_ANALYTICS_END_DATE_KEY = "analytics_end_date"
private const val SAVED_ANALYTICS_DAYS_KEY = "analytics_days"
private const val SAVED_ANALYTICS_UNIT_ID_KEY = "analytics_unit_id"
private const val SAVED_ANALYTICS_CATEGORY_KEY = "analytics_category"
private const val SAVED_ANALYTICS_UNIT_SORT_KEY = "analytics_unit_sort"
private const val SAVED_ANALYTICS_TAB_KEY = "analytics_tab"
private const val SAVED_ANALYTICS_RISK_ONLY_KEY = "analytics_risk_only"
private const val SAVED_ANALYTICS_SCROLL_INDEX_KEY = "analytics_scroll_index"
private const val SAVED_ANALYTICS_SCROLL_OFFSET_KEY = "analytics_scroll_offset"
private const val SAVED_EXTERNAL_ACTION_TYPE_KEY = "external_action_type"
private const val SAVED_EXTERNAL_ACTION_OWNER_KEY = "external_action_owner"
private const val SAVED_EXTERNAL_ACTION_PAYLOAD_KEY = "external_action_payload"

private fun restoredExternalAction(savedStateHandle: SavedStateHandle): PendingExternalAction? {
    val typeName = savedStateHandle.get<String>(SAVED_EXTERNAL_ACTION_TYPE_KEY) ?: return null
    val type = runCatching { ExternalActionType.valueOf(typeName) }.getOrNull() ?: return null
    return PendingExternalAction(
        type = type,
        ownerId = savedStateHandle[SAVED_EXTERNAL_ACTION_OWNER_KEY] ?: "",
        payload = savedStateHandle[SAVED_EXTERNAL_ACTION_PAYLOAD_KEY] ?: ""
    )
}

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
    object SystemStatus : Screen
    object PreparationSummary : Screen
    object DeliverySheets : Screen
    object DeliveryBatches : Screen
    data class DeliveryBatchCreate(val preselectOrderId: String) : Screen
    data class DeliveryBatchDetail(val batchId: String) : Screen
    object PriceImports : Screen
    data class PriceImportDetail(val batchId: String) : Screen
    object Analytics : Screen
    data class ProductAnalytics(val productId: String, val productName: String = "") : Screen
    object InviteEntry : Screen
    object WebQrScanner : Screen
    object WebLoginConfirm : Screen
    object WebLoginResult : Screen
    object WebSessions : Screen
    object AboutUpdate : Screen
    object OnboardingGuide : Screen
    object ThinkingOrbsShowcase : Screen
}

data class IngredientFormState(
    val id: String = "",
    val name: String = "",
    val category: String = "蔬菜",
    val code: String = "",
    val spec: String = "散装",
    val unit: String = "公斤",
    val minOrderQuantity: String = "1",
    val quantityStep: String = "1",
    val packagingSpec: String = "",
    val stockQuantity: String = "0",
    val warningQuantity: String = "0",
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

class SupplyViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository: SupplyRepository
    private val sessionStore = SessionStore(application)
    private val appUpdateInstaller = AppUpdateInstaller(application)
    private val apiClient = ProcurementApiClient()
    private val pushPreferences = PushPreferences(application)
    private val pushNotificationManager = PushNotificationManager(application)
    private var authToken by mutableStateOf("")
    private var pendingPushEvent: PushEvent? = null
    private val adminUiEventQueue = AdminUiEventQueue()
    private data class PendingWorkbookSave(
        val uri: Uri,
        val type: ExternalActionType,
        val successMessage: String,
        val failureMessage: String,
        val loader: (String) -> ByteArray
    )
    private var pendingWorkbookSave: PendingWorkbookSave? = null
    private val restoredExternalAction = restoredExternalAction(savedStateHandle)
    private val externalActivityState = ExternalActivityState(
        initialAction = restoredExternalAction,
        persist = ::persistExternalAction
    )

    val adminUiEvents = adminUiEventQueue.events

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SupplyRepository(database)

        viewModelScope.launch {
            sessionStore.sessionFlow.first()?.let { session ->
                if (session.token.isNotBlank()) {
                    runCatching {
                        val remoteUser = withContext(Dispatchers.IO) { apiClient.me(session.token) }
                        authToken = session.token
                        applyRemoteUser(remoteUser)
                        resumePendingWorkbookSave()
                        if (remoteUser.mustChangePassword) {
                            externalActivityState.clear()
                            popToRootAndNavigate(Screen.ChangePassword)
                        } else {
                            refreshProducts()
                            refreshOrders()
                            refreshDashboard()
                            restorePendingExternalContext()
                            preparePushNotifications()
                            OrderSyncWorker.schedule(getApplication())
                            processPendingPushEvent()
                        }
                    }.onFailure {
                        failPendingWorkbookSave()
                        externalActivityState.clear()
                        sessionStore.clearSession()
                    }
                }
            }
            if (authToken.isBlank()) failPendingWorkbookSave()
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

    var orderListOrderIds by mutableStateOf<List<String>>(emptyList())
        private set
    var orderListHasMore by mutableStateOf(false)
        private set
    var orderListTotal by mutableStateOf(0)
        private set
    var orderListPresetStatus by mutableStateOf("全部")
        private set
    var isOrderListLoading by mutableStateOf(false)
        private set
    private var orderListCursor = ""

    // --- Router Navigation ---
    val navigationStack = mutableStateListOf<Screen>().apply {
        val restored = restoredExternalAction?.restoredScreen()
        if (restored == null) {
            add(Screen.Splash)
        } else if (restored == Screen.Home) {
            add(Screen.Home)
        } else {
            add(Screen.Home)
            add(restored)
        }
    }

    private var currentTabState by mutableStateOf(savedStateHandle[SAVED_MAIN_TAB_KEY] ?: "home")
    var currentTab: String
        get() = currentTabState
        set(value) {
            currentTabState = value
            savedStateHandle[SAVED_MAIN_TAB_KEY] = value
        }

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
    var isSavingIngredient by mutableStateOf(false)
    var isDeletingIngredients by mutableStateOf(false)
        private set
    var isRefreshingProducts by mutableStateOf(false)
        private set
    var dashboard by mutableStateOf(AdminDashboard())
    var isDashboardRefreshing by mutableStateOf(false)
        private set
    private val defaultAnalyticsRange = AnalyticsDateRange.recent(30)
    private var analyticsRangeState by mutableStateOf(
        AnalyticsDateRange(
            startDate = savedStateHandle[SAVED_ANALYTICS_START_DATE_KEY] ?: defaultAnalyticsRange.startDate,
            endDate = savedStateHandle[SAVED_ANALYTICS_END_DATE_KEY] ?: defaultAnalyticsRange.endDate,
            days = (savedStateHandle[SAVED_ANALYTICS_DAYS_KEY] ?: defaultAnalyticsRange.days).coerceIn(1, 365)
        )
    )
    var analyticsRange: AnalyticsDateRange
        get() = analyticsRangeState
        private set(value) {
            analyticsRangeState = value
            savedStateHandle[SAVED_ANALYTICS_START_DATE_KEY] = value.startDate
            savedStateHandle[SAVED_ANALYTICS_END_DATE_KEY] = value.endDate
            savedStateHandle[SAVED_ANALYTICS_DAYS_KEY] = value.days
        }
    private var analyticsUnitIdState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_UNIT_ID_KEY] ?: "")
    var analyticsUnitId: String
        get() = analyticsUnitIdState
        private set(value) {
            analyticsUnitIdState = value
            savedStateHandle[SAVED_ANALYTICS_UNIT_ID_KEY] = value
        }
    private var analyticsCategoryState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_CATEGORY_KEY] ?: "")
    var analyticsCategory: String
        get() = analyticsCategoryState
        private set(value) {
            analyticsCategoryState = value
            savedStateHandle[SAVED_ANALYTICS_CATEGORY_KEY] = value
        }
    private var analyticsUnitSortState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_UNIT_SORT_KEY] ?: "amount")
    var analyticsUnitSort: String
        get() = analyticsUnitSortState
        private set(value) {
            analyticsUnitSortState = value
            savedStateHandle[SAVED_ANALYTICS_UNIT_SORT_KEY] = value
        }
    private var analyticsSelectedTabState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_TAB_KEY] ?: "overview")
    var analyticsSelectedTab: String
        get() = analyticsSelectedTabState
        private set(value) {
            analyticsSelectedTabState = value
            savedStateHandle[SAVED_ANALYTICS_TAB_KEY] = value
        }
    private var analyticsInventoryRiskOnlyState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_RISK_ONLY_KEY] ?: false)
    var analyticsInventoryRiskOnly: Boolean
        get() = analyticsInventoryRiskOnlyState
        private set(value) {
            analyticsInventoryRiskOnlyState = value
            savedStateHandle[SAVED_ANALYTICS_RISK_ONLY_KEY] = value
        }
    private var analyticsScrollIndexState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_SCROLL_INDEX_KEY] ?: 0)
    var analyticsScrollIndex: Int
        get() = analyticsScrollIndexState
        private set(value) {
            analyticsScrollIndexState = value
            savedStateHandle[SAVED_ANALYTICS_SCROLL_INDEX_KEY] = value
        }
    private var analyticsScrollOffsetState by mutableStateOf(savedStateHandle[SAVED_ANALYTICS_SCROLL_OFFSET_KEY] ?: 0)
    var analyticsScrollOffset: Int
        get() = analyticsScrollOffsetState
        private set(value) {
            analyticsScrollOffsetState = value
            savedStateHandle[SAVED_ANALYTICS_SCROLL_OFFSET_KEY] = value
        }
    var analyticsOverview by mutableStateOf(AnalyticsOverview())
        private set
    var analyticsUnits by mutableStateOf<List<AnalyticsUnitItem>>(emptyList())
        private set
    var analyticsPrices by mutableStateOf<List<AnalyticsPriceItem>>(emptyList())
        private set
    var analyticsInventory by mutableStateOf(AnalyticsInventory())
        private set
    var activeProductAnalytics by mutableStateOf<AnalyticsProductDetail?>(null)
        private set
    private val analyticsLoadStates = mutableStateMapOf<AnalyticsSection, AnalyticsLoadState>()
    private val analyticsLoadedSections = mutableStateMapOf<AnalyticsSection, Boolean>()
    private val analyticsJobs = mutableMapOf<AnalyticsSection, Job>()
    private val analyticsRequestTracker = AnalyticsRequestTracker()
    var adminUnits by mutableStateOf<List<RemoteUnit>>(emptyList())
    var adminUsers by mutableStateOf<List<RemoteAdminUser>>(emptyList())
    var ledgerRows by mutableStateOf<List<LedgerRow>>(emptyList())
    var preparationSummaryItems by mutableStateOf<List<PreparationSummaryItem>>(emptyList())
    var deliverySheetUnits by mutableStateOf<List<DeliverySheetUnit>>(emptyList())
    var deliveryBatches by mutableStateOf<List<DeliveryBatch>>(emptyList())
        private set
    var eligibleBatchOrders by mutableStateOf<List<DeliveryBatchOrder>>(emptyList())
        private set
    var activeDeliveryBatch by mutableStateOf<DeliveryBatch?>(null)
        private set
    var activeDeliveryBatchSummary by mutableStateOf<DeliveryBatchSummary?>(null)
        private set
    var isDeliveryBatchLoading by mutableStateOf(false)
        private set
    var activeExportType by mutableStateOf<ExternalActionType?>(null)
        private set
    var priceImportBatches by mutableStateOf<List<PriceImportBatch>>(emptyList())
        private set
    var activePriceImport by mutableStateOf<PriceImportBatch?>(null)
        private set
    var activePriceImportStructure by mutableStateOf<PriceImportStructure?>(null)
        private set
    var isPriceImportLoading by mutableStateOf(false)
        private set
    var isPriceImportApplying by mutableStateOf(false)
        private set
    var systemOverview by mutableStateOf(SystemOverview())
    var appUpdateCheckResult by mutableStateOf(AppUpdateCheckResult())
    var isCheckingAppUpdate by mutableStateOf(false)
    var isDownloadingAppUpdate by mutableStateOf(false)
    var appUpdateDownloadProgress by mutableStateOf(0)
    var showUpdateInstallPermissionDialog by mutableStateOf(false)
        private set
    private var pendingUpdateInstallFile: File? = null
    var selectedOnboardingPath by mutableStateOf("")
    var inviteInspectResult by mutableStateOf<InviteInspectResult?>(null)
    var inviteRegistrationLoading by mutableStateOf(false)
    var phoneVerificationTicket by mutableStateOf("")
    var pendingWebLogin by mutableStateOf<WebLoginScanResult?>(null)
    var webLoginActionLoading by mutableStateOf(false)
    var webLoginResultTitle by mutableStateOf("")
    var webLoginResultMessage by mutableStateOf("")
    var webSessionRecords by mutableStateOf<List<WebSessionRecord>>(emptyList())
    var showPushConsentDialog by mutableStateOf(false)
        private set
    var notificationPermissionRequestKey by mutableStateOf(0)
        private set
    var pushNotificationState by mutableStateOf(PushNotificationState.NOT_CONSENTED)
        private set

    // Alert dialog message helper
    var alertMessage by mutableStateOf<String?>(null)
    var snackbarMessage by mutableStateOf<String?>(null)

    init {
        checkForAppUpdate(showNoUpdate = false)
    }

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

    fun beginShippingCamera(orderId: String, photoPath: String) {
        externalActivityState.begin(
            PendingExternalAction(ExternalActionType.SHIPPING_CAMERA, ownerId = orderId, payload = photoPath)
        )
    }

    fun consumeShippingCamera(orderId: String): String? =
        externalActivityState.consume(ExternalActionType.SHIPPING_CAMERA, orderId)?.payload

    fun beginDocumentExport(type: ExternalActionType, ownerId: String = "") {
        require(type != ExternalActionType.SHIPPING_CAMERA)
        externalActivityState.begin(PendingExternalAction(type, ownerId = ownerId))
    }

    fun isDocumentExportBusy(type: ExternalActionType): Boolean =
        externalActivityState.pendingAction?.type == type || activeExportType == type

    fun consumeDocumentExport(type: ExternalActionType, ownerId: String = ""): Boolean =
        externalActivityState.consume(type, ownerId) != null

    fun onWorkbookDocumentResult(uri: Uri?) {
        val action = externalActivityState.pendingAction ?: return
        if (action.type !in WorkbookExportTypes) return
        externalActivityState.consume(action.type, action.ownerId)
        if (uri == null) return
        when (action.type) {
            ExternalActionType.BATCH_SUMMARY_EXPORT -> exportDeliveryBatchSummary(action.ownerId, uri)
            ExternalActionType.BATCH_PICKING_EXPORT -> exportDeliveryBatchPickingList(action.ownerId, uri)
            ExternalActionType.BATCH_OUTBOUND_EXPORT -> exportDeliveryBatchOutbound(action.ownerId, uri)
            ExternalActionType.LEDGER_EXPORT -> exportLedger(uri)
            ExternalActionType.PREPARATION_EXPORT -> exportPreparationSummary(uri)
            ExternalActionType.DELIVERY_SHEETS_EXPORT -> exportDeliverySheets(uri)
            else -> Unit
        }
    }

    private fun persistExternalAction(action: PendingExternalAction?) {
        if (action == null) {
            savedStateHandle.remove<String>(SAVED_EXTERNAL_ACTION_TYPE_KEY)
            savedStateHandle.remove<String>(SAVED_EXTERNAL_ACTION_OWNER_KEY)
            savedStateHandle.remove<String>(SAVED_EXTERNAL_ACTION_PAYLOAD_KEY)
            return
        }
        savedStateHandle[SAVED_EXTERNAL_ACTION_TYPE_KEY] = action.type.name
        savedStateHandle[SAVED_EXTERNAL_ACTION_OWNER_KEY] = action.ownerId
        savedStateHandle[SAVED_EXTERNAL_ACTION_PAYLOAD_KEY] = action.payload
    }

    private fun restorePendingExternalContext() {
        when (val action = externalActivityState.pendingAction ?: restoredExternalAction) {
            null -> Unit
            else -> when (action.type) {
                ExternalActionType.SHIPPING_CAMERA -> refreshOrderDetail(action.ownerId)
                ExternalActionType.BATCH_SUMMARY_EXPORT,
                ExternalActionType.BATCH_PICKING_EXPORT,
                ExternalActionType.BATCH_OUTBOUND_EXPORT -> refreshDeliveryBatch(action.ownerId)
                ExternalActionType.LEDGER_EXPORT -> refreshLedger()
                ExternalActionType.PREPARATION_EXPORT -> refreshPreparationSummary()
                ExternalActionType.DELIVERY_SHEETS_EXPORT -> refreshDeliverySheets()
                ExternalActionType.PRODUCT_IMPORT_TEMPLATE_EXPORT,
                ExternalActionType.PRICE_IMPORT_PICKER -> Unit
            }
        }
    }

    /** Only used by the co-installable animation preview build. */
    fun openThinkingOrbPreview() {
        popToRootAndNavigate(Screen.ThinkingOrbsShowcase)
    }

    fun finishSplash() {
        when {
            currentUser == null -> popToRootAndNavigate(Screen.Login)
            mustChangePassword -> popToRootAndNavigate(Screen.ChangePassword)
            else -> popToRootAndNavigate(Screen.Home)
        }
    }

    fun login(username: String, password: String, rememberLogin: Boolean) {
        val normalizedUsername = AuthValidator.normalizeUsername(username)
        val validation = AuthValidator.validateLogin(LoginInput(normalizedUsername, password))
        loginErrors.clear()
        loginErrors.putAll(validation.errors)
        if (!validation.isValid) return
        isAuthLoading = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { apiClient.login(normalizedUsername, password) } }
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
                    preparePushNotifications()
                    OrderSyncWorker.schedule(getApplication())
                    processPendingPushEvent()
                    popToRootAndNavigate(Screen.Home)
                }
            }.onFailure {
                loginErrors["password"] = it.toUserMessage("账号或密码错误")
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val validation = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = userId, oldPassword = oldPassword, newPassword = newPassword)
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
                unbindPushDevice(authToken)
                sessionStore.clearSession()
                repository.clearCart()
                repository.clearOrderCache()
                pushPreferences.clearAccountState()
                pushNotificationManager.stop()
                OrderSyncWorker.cancel(getApplication())
                PushRegistrationWorker.cancel(getApplication())
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
                unbindPushDevice(tokenToRevoke)
                runCatching { withContext(Dispatchers.IO) { apiClient.logout(tokenToRevoke) } }
            }
            sessionStore.clearSession()
            repository.clearCart()
            repository.clearOrderCache()
            pushPreferences.clearAccountState()
            pushNotificationManager.stop()
            OrderSyncWorker.cancel(getApplication())
            PushRegistrationWorker.cancel(getApplication())
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

    private suspend fun unbindPushDevice(token: String) {
        val installationId = pushPreferences.state.first().installationId
        if (token.isBlank() || installationId.isBlank()) return
        runCatching {
            withContext(Dispatchers.IO) { apiClient.unbindPushDevice(token, installationId) }
        }.onSuccess {
            pushPreferences.clearPendingUnbind()
        }.onFailure {
            pushPreferences.markPendingUnbind()
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
        if (authToken.isBlank() || isRefreshingProducts) return
        isRefreshingProducts = true
        viewModelScope.launch {
            try {
                runCatching {
                    val products = withContext(Dispatchers.IO) { apiClient.products(authToken) }
                    repository.replaceProducts(products)
                    lastSyncText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                }.onFailure {
                    snackbarMessage = it.toUserMessage("食材同步失败")
                }
            } finally {
                isRefreshingProducts = false
            }
        }
    }

    fun refreshPriceImports() {
        if (authToken.isBlank() || !canManageIngredients() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.priceImportBatches(authToken) } }
                .onSuccess { priceImportBatches = it }
                .onFailure { alertMessage = it.toUserMessage("导入记录加载失败") }
            isPriceImportLoading = false
        }
    }

    fun openPriceImport(batchId: String) {
        if (authToken.isBlank() || !canManageIngredients() || isPriceImportLoading) return
        activePriceImportStructure = null
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.priceImportDetail(authToken, batchId) } }
                .onSuccess {
                    activePriceImport = it
                    navigateTo(Screen.PriceImportDetail(batchId))
                }
                .onFailure { alertMessage = it.toUserMessage("导入批次加载失败") }
            isPriceImportLoading = false
        }
    }

    fun uploadAndAnalyzePriceImport(uri: Uri, openDetailAfterAnalyze: Boolean = true) {
        if (authToken.isBlank() || !canManageIngredients() || isPriceImportLoading) return
        activePriceImport = null
        activePriceImportStructure = null
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val localFile = copyPriceImportToCache(uri)
                    try {
                        val uploaded = apiClient.uploadPriceImport(authToken, localFile)
                        apiClient.analyzePriceImport(authToken, uploaded.id)
                    } finally {
                        localFile.delete()
                    }
                }
            }.onSuccess {
                activePriceImport = it
                priceImportBatches = listOf(it) + priceImportBatches.filterNot { batch -> batch.id == it.id }
                if (openDetailAfterAnalyze) navigateTo(Screen.PriceImportDetail(it.id))
                snackbarMessage = "报价表已识别，请核对后再确认应用"
            }.onFailure {
                alertMessage = it.toUserMessage("Excel 识别失败")
            }
            isPriceImportLoading = false
        }
    }

    fun loadPriceImportStructure() {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        activePriceImportStructure = null
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.priceImportStructure(authToken, batch.id) } }
                .onSuccess { activePriceImportStructure = it }
                .onFailure { alertMessage = it.toUserMessage("Excel 字段读取失败") }
            isPriceImportLoading = false
        }
    }

    fun reanalyzePriceImport(sheetName: String, headerRow: Int, mapping: Map<String, String>) {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.reanalyzePriceImport(authToken, batch.id, sheetName, headerRow, mapping)
                }
            }.onSuccess {
                activePriceImport = it
                activePriceImportStructure = null
                priceImportBatches = listOf(it) + priceImportBatches.filterNot { item -> item.id == it.id }
                snackbarMessage = "Excel 字段已确认，报价表已重新识别"
            }.onFailure {
                alertMessage = it.toUserMessage("Excel 字段确认失败")
            }
            isPriceImportLoading = false
        }
    }

    fun updatePriceImportDefaults(defaults: PriceImportDefaults) {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.updatePriceImportDefaults(authToken, batch.id, defaults) } }
                .onSuccess {
                    activePriceImport = it
                    snackbarMessage = "新增食材默认设置已应用"
                }
                .onFailure { alertMessage = it.toUserMessage("默认设置保存失败") }
            isPriceImportLoading = false
        }
    }

    fun selectPriceImportProduct(rowId: String, productId: String) {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.selectPriceImportProduct(authToken, batch.id, rowId, productId) } }
                .onSuccess {
                    activePriceImport = it
                    snackbarMessage = "系统食材已关联"
                }
                .onFailure { alertMessage = it.toUserMessage("关联食材失败") }
            isPriceImportLoading = false
        }
    }

    fun ignorePriceImportRow(rowId: String) {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.ignorePriceImportRow(authToken, batch.id, rowId) } }
                .onSuccess {
                    activePriceImport = it
                    snackbarMessage = "本项已忽略"
                }
                .onFailure { alertMessage = it.toUserMessage("忽略项目失败") }
            isPriceImportLoading = false
        }
    }

    fun updatePriceImportNewProduct(rowId: String, patch: PriceImportNewProductPatch) {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportLoading) return
        isPriceImportLoading = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.updatePriceImportNewProduct(authToken, batch.id, rowId, patch)
                }
            }.onSuccess {
                activePriceImport = it
                snackbarMessage = "本项食材资料已保存"
            }.onFailure {
                alertMessage = it.toUserMessage("保存食材资料失败")
            }
            isPriceImportLoading = false
        }
    }

    fun applyPriceImport() {
        val batch = activePriceImport ?: return
        if (authToken.isBlank() || isPriceImportApplying) return
        isPriceImportApplying = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.applyPriceImport(authToken, batch.id) } }
                .onSuccess {
                    activePriceImport = it
                    priceImportBatches = listOf(it) + priceImportBatches.filterNot { item -> item.id == it.id }
                    refreshProducts()
                    snackbarMessage = "Excel 导入已应用，价格历史已记录"
                }
                .onFailure { alertMessage = it.toUserMessage("批量应用失败") }
            isPriceImportApplying = false
        }
    }

    private fun copyPriceImportToCache(uri: Uri): File {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val suppliedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        }.orEmpty()
        val suffix = suppliedName.substringAfterLast('.', "xlsx").lowercase().takeIf { it in setOf("xlsx", "xls", "csv") } ?: "xlsx"
        val localFile = File(context.cacheDir, "price_import_${UUID.randomUUID()}.$suffix")
        resolver.openInputStream(uri)?.use { input -> localFile.outputStream().use(input::copyTo) }
            ?: throw IllegalStateException("无法读取所选 Excel 文件")
        return localFile
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
                snackbarMessage = it.toUserMessage("订单同步失败")
            }
        }
    }

    fun openOrderTab(status: String = "全部") {
        orderListPresetStatus = status
        currentTab = "orders"
    }

    fun loadOrderList(
        status: String = "全部",
        dateFrom: String = "",
        dateTo: String = "",
        query: String = "",
        reset: Boolean = true
    ) {
        if (authToken.isBlank() || isOrderListLoading) return
        isOrderListLoading = true
        viewModelScope.launch {
            runCatching {
                val page = withContext(Dispatchers.IO) {
                    apiClient.orderPage(
                        token = authToken,
                        isAdmin = currentUser?.role == "admin",
                        status = status,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        queryText = query,
                        cursor = if (reset) "" else orderListCursor,
                        limit = 20
                    )
                }
                page.items.forEach { repository.upsertOrder(it) }
                val pageIds = page.items.map { it.order.orderId }
                orderListOrderIds = if (reset) {
                    pageIds
                } else {
                    (orderListOrderIds + pageIds).distinct()
                }
                orderListCursor = page.nextCursor
                orderListHasMore = page.hasMore
                orderListTotal = page.total
            }.onFailure {
                snackbarMessage = it.toUserMessage("订单加载失败")
            }
            isOrderListLoading = false
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
                snackbarMessage = it.toUserMessage("订单同步失败")
            }
        }
    }

    fun refreshDashboard(userInitiated: Boolean = false) {
        if (authToken.isBlank() || !canManageIngredients() || isDashboardRefreshing) return
        isDashboardRefreshing = true
        viewModelScope.launch {
            runCatching {
                dashboard = withContext(Dispatchers.IO) { apiClient.dashboard(authToken) }
            }.onFailure {
                if (userInitiated) snackbarMessage = "更新失败，当前显示上次结果"
            }
            isDashboardRefreshing = false
        }
    }

    fun setAnalyticsRange(days: Int) {
        analyticsRange = AnalyticsDateRange.recent(days)
    }

    fun setAnalyticsCustomRange(startDate: String, endDate: String): Boolean {
        val start = runCatching { java.time.LocalDate.parse(startDate) }.getOrNull()
        val end = runCatching { java.time.LocalDate.parse(endDate) }.getOrNull()
        if (start == null || end == null || start.isAfter(end)) {
            alertMessage = "请选择正确的开始和结束日期"
            return false
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
        if (days !in 1..365) {
            alertMessage = "单次查询最多支持 365 天"
            return false
        }
        analyticsRange = AnalyticsDateRange(start.toString(), end.toString(), days)
        return true
    }

    fun setAnalyticsFilters(unitId: String, category: String) {
        analyticsUnitId = unitId
        analyticsCategory = category
    }

    fun applyAnalyticsFilters(draft: AnalyticsFilterDraft, tab: String = analyticsSelectedTab) {
        val fields = analyticsFilterFields(tab)
        if ("unitId" in fields) analyticsUnitId = draft.unitId
        if ("category" in fields) analyticsCategory = draft.category
        if ("inventoryRiskOnly" in fields) analyticsInventoryRiskOnly = draft.inventoryRiskOnly
        if (tab != "inventory") refreshAnalytics(tab, userInitiated = true)
    }

    fun selectAnalyticsTab(tab: String) {
        analyticsSelectedTab = AnalyticsSection.fromTab(tab).tabId
    }

    fun openInventoryRisks() {
        analyticsSelectedTab = "inventory"
        analyticsInventoryRiskOnly = true
        currentTab = "analytics"
        if (analyticsLoadState("inventory") == AnalyticsLoadState.Idle) refreshAnalytics("inventory")
    }

    fun saveAnalyticsScroll(index: Int, offset: Int) {
        analyticsScrollIndex = index.coerceAtLeast(0)
        analyticsScrollOffset = offset.coerceAtLeast(0)
    }

    fun updateAnalyticsUnitSort(sort: String) {
        if (sort in setOf("amount", "orders", "products")) analyticsUnitSort = sort
    }

    fun analyticsLoadState(tab: String): AnalyticsLoadState =
        analyticsLoadStates[AnalyticsSection.fromTab(tab)] ?: AnalyticsLoadState.Idle

    fun refreshAnalytics(tab: String = analyticsSelectedTab, userInitiated: Boolean = false) {
        if (authToken.isBlank() || !canManageIngredients()) return
        val section = AnalyticsSection.fromTab(tab)
        if (section == AnalyticsSection.PRODUCT) return
        val generation = analyticsRequestTracker.begin(section)
        analyticsJobs[section]?.cancel()
        val keepsContent = analyticsLoadedSections[section] == true
        analyticsLoadStates[section] = AnalyticsLoadState.Loading(keepsContent)
        analyticsJobs[section] = viewModelScope.launch {
            try {
                when (section) {
                    AnalyticsSection.PRICE -> {
                        val result = withContext(Dispatchers.IO) { apiClient.analyticsPrices(
                            authToken, analyticsRange.startDate, analyticsRange.endDate, analyticsCategory
                        ) }
                        if (analyticsRequestTracker.isLatest(section, generation)) analyticsPrices = result
                    }
                    AnalyticsSection.INVENTORY -> {
                        val result = withContext(Dispatchers.IO) { apiClient.analyticsInventory(authToken) }
                        if (analyticsRequestTracker.isLatest(section, generation)) analyticsInventory = result
                    }
                    AnalyticsSection.UNITS -> {
                        val result = withContext(Dispatchers.IO) { apiClient.analyticsUnits(
                            authToken, analyticsRange.startDate, analyticsRange.endDate, analyticsCategory, analyticsUnitSort
                        ) }
                        if (analyticsRequestTracker.isLatest(section, generation)) analyticsUnits = result
                    }
                    else -> {
                        val result = withContext(Dispatchers.IO) { apiClient.analyticsOverview(
                            authToken, analyticsRange.startDate, analyticsRange.endDate,
                            analyticsUnitId, analyticsCategory
                        ) }
                        if (analyticsRequestTracker.isLatest(section, generation)) analyticsOverview = result
                    }
                }
                if (!analyticsRequestTracker.isLatest(section, generation)) return@launch
                analyticsLoadedSections[section] = true
                analyticsLoadStates[section] = AnalyticsLoadState.Ready(System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!analyticsRequestTracker.isLatest(section, generation)) return@launch
                val message = error.toUserMessage("数据更新失败")
                analyticsLoadStates[section] = AnalyticsLoadState.Error(message, keepsContent)
                if (keepsContent || userInitiated) snackbarMessage = "更新失败，当前显示上次结果"
            }
        }
    }

    fun openProductAnalytics(productId: String, productName: String = "") {
        if (authToken.isBlank() || !canManageIngredients()) return
        navigateTo(productAnalyticsNavigationTarget(productId, productName))
        refreshProductAnalytics(productId)
    }

    fun refreshProductAnalytics(productId: String, userInitiated: Boolean = false) {
        if (authToken.isBlank() || !canManageIngredients()) return
        val section = AnalyticsSection.PRODUCT
        val generation = analyticsRequestTracker.begin(section)
        analyticsJobs[section]?.cancel()
        val keepsContent = activeProductAnalytics?.product?.id == productId
        if (!keepsContent) activeProductAnalytics = null
        analyticsLoadStates[section] = AnalyticsLoadState.Loading(keepsContent)
        analyticsJobs[section] = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) {
                    apiClient.analyticsProduct(authToken, productId, analyticsRange.startDate, analyticsRange.endDate)
                }
                if (!analyticsRequestTracker.isLatest(section, generation)) return@launch
                activeProductAnalytics = detail
                analyticsLoadedSections[section] = true
                analyticsLoadStates[section] = AnalyticsLoadState.Ready(System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!analyticsRequestTracker.isLatest(section, generation)) return@launch
                analyticsLoadStates[section] = AnalyticsLoadState.Error(
                    error.toUserMessage("食材分析加载失败"),
                    keepsContent
                )
                if (keepsContent || userInitiated) snackbarMessage = "更新失败，当前显示上次结果"
            }
        }
    }

    fun refreshActiveData() {
        if (authToken.isBlank() || mustChangePassword) return
        refreshProducts()
        refreshOrders()
        refreshDashboard()
    }

    fun onAppResumed() {
        refreshActiveData()
        retryPushRegistration()
        processPendingPushEvent()
        continuePendingUpdateInstall()
    }

    fun preparePushNotifications() {
        if (BuildConfig.JPUSH_APP_KEY.isBlank()) {
            pushNotificationState = PushNotificationState.NOT_CONFIGURED
            return
        }
        if (authToken.isBlank() || currentUser == null || mustChangePassword) return
        viewModelScope.launch {
            val state = pushPreferences.state.first()
            if (!state.privacyConsented) {
                pushNotificationState = PushNotificationState.NOT_CONSENTED
                showPushConsentDialog = true
                return@launch
            }
            initializeAndRegisterPush()
        }
    }

    fun acceptPushConsent() {
        showPushConsentDialog = false
        viewModelScope.launch {
            pushPreferences.setPrivacyConsented(true)
            pushNotificationManager.initialize()
            if (!pushNotificationManager.hasRuntimePermission()) {
                pushNotificationState = PushNotificationState.PERMISSION_REQUIRED
                notificationPermissionRequestKey += 1
            } else {
                initializeAndRegisterPush()
            }
        }
    }

    fun declinePushConsent() {
        showPushConsentDialog = false
        viewModelScope.launch { pushPreferences.setPrivacyConsented(false) }
        pushNotificationState = PushNotificationState.NOT_CONSENTED
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            pushNotificationState = PushNotificationState.DISABLED
            return
        }
        retryPushRegistration()
    }

    fun retryPushRegistration() {
        if (authToken.isBlank() || currentUser == null || mustChangePassword) return
        viewModelScope.launch {
            val state = pushPreferences.state.first()
            if (!state.privacyConsented) return@launch
            initializeAndRegisterPush()
        }
    }

    private suspend fun initializeAndRegisterPush() {
        if (!PushNotificationPolicy.canInitialize(
                consented = true,
                loggedIn = authToken.isNotBlank(),
                appKey = BuildConfig.JPUSH_APP_KEY
            )
        ) return
        pushNotificationManager.initialize()
        if (!pushNotificationManager.hasRuntimePermission() || !pushNotificationManager.notificationsEnabled()) {
            pushNotificationState = PushNotificationState.DISABLED
            return
        }
        pushNotificationState = PushNotificationState.REGISTERING
        PushRegistrationWorker.schedule(getApplication())
        var registrationId = pushPreferences.state.first().registrationId
        repeat(12) {
            if (registrationId.isNotBlank()) return@repeat
            registrationId = pushNotificationManager.registrationId()
            if (registrationId.isNotBlank()) {
                pushPreferences.saveRegistrationId(registrationId)
            } else {
                delay(500)
            }
        }
        if (registrationId.isBlank()) {
            pushNotificationState = PushNotificationState.CONNECTION_FAILED
            return
        }
        val installationId = pushPreferences.getOrCreateInstallationId()
        runCatching {
            withContext(Dispatchers.IO) {
                apiClient.registerPushDevice(
                    token = authToken,
                    registrationId = registrationId,
                    installationId = installationId,
                    appVersion = BuildConfig.VERSION_NAME
                )
            }
        }.onSuccess {
            pushPreferences.markRegistered()
            pushPreferences.clearPendingUnbind()
            pushNotificationState = PushNotificationState.ENABLED
        }.onFailure {
            pushNotificationState = PushNotificationState.CONNECTION_FAILED
        }
    }

    fun openNotificationSettings() = pushNotificationManager.openSettings()

    fun onPushNotificationMenuClick() {
        when (pushNotificationState) {
            PushNotificationState.NOT_CONSENTED -> showPushConsentDialog = true
            PushNotificationState.PERMISSION_REQUIRED -> notificationPermissionRequestKey += 1
            PushNotificationState.DISABLED, PushNotificationState.ENABLED -> openNotificationSettings()
            PushNotificationState.REGISTERING, PushNotificationState.CONNECTION_FAILED -> retryPushRegistration()
            PushNotificationState.NOT_CONFIGURED -> alertMessage = "订单通知尚未配置"
        }
    }

    fun pushNotificationStatusText(): String = when (pushNotificationState) {
        PushNotificationState.NOT_CONSENTED -> "未开启"
        PushNotificationState.PERMISSION_REQUIRED -> "需要授权"
        PushNotificationState.DISABLED -> "系统已关闭"
        PushNotificationState.REGISTERING -> "正在连接"
        PushNotificationState.CONNECTION_FAILED -> "连接失败，请点击重试"
        PushNotificationState.ENABLED -> "已开启"
        PushNotificationState.NOT_CONFIGURED -> "暂不可用"
    }

    fun handlePushIntent(extras: Map<String, String>) {
        val event = PushDeepLinkPolicy.fromExtras(extras) ?: return
        pendingPushEvent = event
        processPendingPushEvent()
    }

    private fun processPendingPushEvent() {
        val event = pendingPushEvent ?: return
        if (currentUser == null || authToken.isBlank()) {
            popToRootAndNavigate(Screen.Login)
            return
        }
        if (mustChangePassword) {
            popToRootAndNavigate(Screen.ChangePassword)
            return
        }
        viewModelScope.launch {
            if (pushPreferences.state.first().processedEventIds.contains(event.eventId)) {
                pendingPushEvent = null
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.orderDetail(authToken, event.orderId, currentUser?.role == "admin")
                }
            }.onSuccess { bundle ->
                repository.upsertOrder(bundle)
                pendingPushEvent = null
                pushPreferences.addProcessedEvent(event.eventId)
                navigateTo(Screen.OrderDetails(event.orderId))
                if (!event.eventId.startsWith("sync:")) {
                    runCatching {
                        withContext(Dispatchers.IO) { apiClient.markPushOpened(authToken, event.eventId) }
                    }
                }
            }.onFailure {
                pendingPushEvent = null
                alertMessage = it.toUserMessage("无法查看这份订单")
            }
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
                .onSuccess {
                    refreshUsers()
                    alertMessage = "账号已创建，初始密码：$password"
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
                .onSuccess {
                    alertMessage = "密码已重置，新密码：$newPassword"
                }.onFailure { alertMessage = it.toUserMessage("密码重置失败") }
        }
    }

    fun refreshLedger() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.ledger(authToken) } }
                .onSuccess { ledgerRows = it }
                .onFailure { alertMessage = it.toUserMessage("台账同步失败") }
        }
    }

    fun exportLedger(uri: Uri) = saveWorkbook(
        uri = uri,
        type = ExternalActionType.LEDGER_EXPORT,
        successMessage = "采购台账已保存",
        failureMessage = "采购台账保存失败，请重试"
    ) { token -> apiClient.exportLedger(token) }

    fun refreshPreparationSummary() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.preparationSummary(authToken) } }
                .onSuccess { preparationSummaryItems = it }
                .onFailure { alertMessage = it.toUserMessage("备货汇总同步失败") }
        }
    }

    fun exportPreparationSummary(uri: Uri) = saveWorkbook(
        uri = uri,
        type = ExternalActionType.PREPARATION_EXPORT,
        successMessage = "备货单已保存",
        failureMessage = "备货单保存失败，请重试"
    ) { token -> apiClient.exportPreparationSummary(token) }

    fun refreshDeliverySheets() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.deliverySheets(authToken) } }
                .onSuccess { deliverySheetUnits = it }
                .onFailure { alertMessage = it.toUserMessage("配送单同步失败") }
        }
    }

    fun exportDeliverySheets(uri: Uri) = saveWorkbook(
        uri = uri,
        type = ExternalActionType.DELIVERY_SHEETS_EXPORT,
        successMessage = "配送单已保存",
        failureMessage = "配送单保存失败，请重试"
    ) { token -> apiClient.exportDeliverySheets(token) }

    fun refreshDeliveryBatches(onSuccess: (List<DeliveryBatchOrder>) -> Unit = {}) {
        if (authToken.isBlank() || !canManageIngredients() || isDeliveryBatchLoading) return
        isDeliveryBatchLoading = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.deliveryBatches(authToken) to apiClient.eligibleDeliveryBatchOrders(authToken)
                }
            }.onSuccess { (batches, orders) ->
                deliveryBatches = batches
                eligibleBatchOrders = orders
                onSuccess(orders)
            }.onFailure {
                snackbarMessage = it.toUserMessage("配送批次加载失败")
            }
            isDeliveryBatchLoading = false
        }
    }

    fun createDeliveryBatch(name: String, note: String, orderIds: List<String>, onSuccess: () -> Unit = {}) {
        if (authToken.isBlank() || !canManageIngredients() || isDeliveryBatchLoading) return
        if (orderIds.isEmpty()) {
            alertMessage = "请至少选择一笔已接单或备货中的订单"
            return
        }
        isDeliveryBatchLoading = true
        viewModelScope.launch {
            var createdBatchId = ""
            runCatching {
                withContext(Dispatchers.IO) { apiClient.createDeliveryBatch(authToken, name, note, orderIds) }
            }.onSuccess { batch ->
                activeDeliveryBatch = batch
                activeDeliveryBatchSummary = null
                createdBatchId = batch.id
                deliveryBatches = listOf(batch) + deliveryBatches.filterNot { it.id == batch.id }
                eligibleBatchOrders = eligibleBatchOrders.filterNot { it.id in orderIds }
                snackbarMessage = "配送批次已创建"
                onSuccess()
                if (navigationStack.lastOrNull() is Screen.DeliveryBatchCreate) {
                    navigationStack[navigationStack.lastIndex] = Screen.DeliveryBatches
                }
                navigateTo(Screen.DeliveryBatchDetail(batch.id))
            }.onFailure {
                alertMessage = it.toUserMessage("配送批次创建失败")
            }
            isDeliveryBatchLoading = false
            if (createdBatchId.isNotBlank()) loadDeliveryBatch(createdBatchId, navigate = false)
        }
    }

    fun openDeliveryBatch(batchId: String) = loadDeliveryBatch(batchId, navigate = true)

    fun refreshDeliveryBatch(batchId: String) = loadDeliveryBatch(batchId, navigate = false)

    private fun loadDeliveryBatch(batchId: String, navigate: Boolean) {
        if (authToken.isBlank() || !canManageIngredients() || isDeliveryBatchLoading) return
        isDeliveryBatchLoading = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.deliveryBatchDetail(authToken, batchId) to
                        apiClient.deliveryBatchSummary(authToken, batchId)
                }
            }.onSuccess { (batch, summary) ->
                activeDeliveryBatch = batch
                activeDeliveryBatchSummary = summary
                if (navigate) navigateTo(Screen.DeliveryBatchDetail(batchId))
            }.onFailure {
                snackbarMessage = it.toUserMessage("批次汇总加载失败")
            }
            isDeliveryBatchLoading = false
        }
    }

    fun closeDeliveryBatch(batchId: String) {
        val batch = activeDeliveryBatch?.takeIf { it.id == batchId } ?: return
        if (authToken.isBlank() || isDeliveryBatchLoading) return
        isDeliveryBatchLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.closeDeliveryBatch(authToken, batch) } }
                .onSuccess {
                    activeDeliveryBatch = it
                    deliveryBatches = deliveryBatches.map { existing -> if (existing.id == it.id) it else existing }
                    snackbarMessage = "配送批次已结束"
                }
                .onFailure { alertMessage = it.toUserMessage("结束配送批次失败") }
            isDeliveryBatchLoading = false
        }
    }

    fun exportDeliveryBatchSummary(batchId: String, uri: Uri) = saveWorkbook(
        uri, ExternalActionType.BATCH_SUMMARY_EXPORT, "批次汇总表已保存", "汇总表保存失败，请重试"
    ) { token ->
        apiClient.exportDeliveryBatchSummary(token, batchId)
    }

    fun exportDeliveryBatchPickingList(batchId: String, uri: Uri) = saveWorkbook(
        uri, ExternalActionType.BATCH_PICKING_EXPORT, "备货单已保存", "备货单保存失败，请重试"
    ) { token ->
        apiClient.exportDeliveryBatchPickingList(token, batchId)
    }

    fun exportDeliveryBatchOutbound(batchId: String, uri: Uri) = saveWorkbook(
        uri, ExternalActionType.BATCH_OUTBOUND_EXPORT, "出库单已保存", "出库单保存失败，请重试"
    ) { token ->
        apiClient.exportDeliveryBatchOutbound(token, batchId)
    }

    private fun saveWorkbook(
        uri: Uri,
        type: ExternalActionType,
        successMessage: String,
        failureMessage: String,
        loader: (String) -> ByteArray
    ) {
        if (activeExportType == type) return
        activeExportType = type
        val request = PendingWorkbookSave(uri, type, successMessage, failureMessage, loader)
        if (authToken.isBlank()) {
            pendingWorkbookSave = request
            return
        }
        startWorkbookSave(request, authToken)
    }

    private fun resumePendingWorkbookSave() {
        val request = pendingWorkbookSave ?: return
        pendingWorkbookSave = null
        startWorkbookSave(request, authToken)
    }

    private fun failPendingWorkbookSave() {
        val request = pendingWorkbookSave ?: return
        pendingWorkbookSave = null
        activeExportType = null
        alertMessage = request.failureMessage
    }

    private fun startWorkbookSave(request: PendingWorkbookSave, token: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                check(token.isNotBlank()) { "登录已过期，请重新登录" }
                val bytes = withContext(Dispatchers.IO) { request.loader(token) }
                withContext(Dispatchers.IO) {
                    writeWorkbookBytes(bytes) {
                        getApplication<Application>().contentResolver.openOutputStream(request.uri, "wt")
                    }
                }
            }.onSuccess {
                snackbarMessage = request.successMessage
            }.onFailure {
                alertMessage = it.toUserMessage(request.failureMessage)
            }
            activeExportType = null
        }
    }

    fun refreshSystemOverview() {
        if (authToken.isBlank() || !canManageIngredients()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.systemOverview(authToken) } }
                .onSuccess { systemOverview = it }
                .onFailure { alertMessage = it.toUserMessage("系统状态同步失败") }
        }
    }

    fun startOnboarding() {
        selectedOnboardingPath = ""
        navigateTo(Screen.Login)
    }

    fun chooseOnboardingPath(path: String) {
        selectedOnboardingPath = path.ifBlank { PREFERRED_ENTRY_METHOD_KEY }
        if (path == "scan_invite") {
            navigateTo(Screen.InviteEntry)
        } else {
            navigateTo(Screen.InviteEntry)
        }
    }

    fun openLoginFromOnboarding(path: String = "login") {
        selectedOnboardingPath = path
        popToRootAndNavigate(Screen.Login)
    }

    fun inspectInvite(inviteToken: String) {
        if (inviteToken.isBlank()) {
            alertMessage = "请填写邀请码"
            return
        }
        inviteRegistrationLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.inspectInvite(inviteToken) } }
                .onSuccess { inviteInspectResult = it }
                .onFailure { alertMessage = it.toUserMessage("邀请码验证失败") }
            inviteRegistrationLoading = false
        }
    }

    fun sendRegisterPhoneCode(phone: String) {
        val inviteToken = inviteInspectResult?.token.orEmpty()
        if (phone.isBlank() || inviteToken.isBlank()) {
            alertMessage = "请先填写手机号并验证邀请码"
            return
        }
        inviteRegistrationLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.sendRegisterPhoneCode(phone, inviteToken) } }
                .onSuccess { snackbarMessage = "验证码已发送" }
                .onFailure { alertMessage = it.toUserMessage("验证码发送失败") }
            inviteRegistrationLoading = false
        }
    }

    fun verifyRegisterPhoneCode(phone: String, code: String) {
        val inviteToken = inviteInspectResult?.token.orEmpty()
        if (phone.isBlank() || code.isBlank() || inviteToken.isBlank()) {
            alertMessage = "请填写手机号和验证码"
            return
        }
        inviteRegistrationLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.verifyRegisterPhoneCode(phone, code, inviteToken) } }
                .onSuccess {
                    phoneVerificationTicket = it
                    snackbarMessage = "手机号已验证"
                }
                .onFailure { alertMessage = it.toUserMessage("手机号验证失败") }
            inviteRegistrationLoading = false
        }
    }

    fun registerWithInvite(username: String, displayName: String, password: String, confirmPassword: String, phone: String) {
        val invite = inviteInspectResult
        if (invite == null || !invite.valid) {
            alertMessage = "请先验证邀请码"
            return
        }
        if (username.isBlank() || displayName.isBlank() || password.isBlank()) {
            alertMessage = "请填写账号、显示名称和密码"
            return
        }
        if (password != confirmPassword) {
            alertMessage = "两次密码不一致"
            return
        }
        if (invite.phoneRequired && phoneVerificationTicket.isBlank()) {
            alertMessage = "请先验证手机号"
            return
        }
        inviteRegistrationLoading = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.registerWithInvite(invite.token, username, displayName, password, phone, phoneVerificationTicket)
                }
            }
            inviteRegistrationLoading = false
            result.onSuccess { login ->
                if (login.pendingApproval) {
                    alertMessage = login.message.ifBlank { "管理者申请已提交" }
                    popToRootAndNavigate(Screen.Login)
                    return@onSuccess
                }
                authToken = login.token
                applyRemoteUser(login.user)
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
                refreshProducts()
                refreshOrders()
                popToRootAndNavigate(if (login.user.mustChangePassword) Screen.ChangePassword else Screen.Home)
            }.onFailure {
                alertMessage = it.toUserMessage("注册失败")
            }
        }
    }

    fun blockingAppUpdateRelease(): AppUpdateRelease? {
        val release = appUpdateCheckResult.release ?: return null
        return if (release.versionCode > BuildConfig.VERSION_CODE && !AppUpdatePolicy.canEnterBusiness(release)) release else null
    }

    fun checkForAppUpdate(showNoUpdate: Boolean = false, showFailure: Boolean = false) {
        isCheckingAppUpdate = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.checkAppUpdate(authToken, AppUpdatePolicy.channelForVariant(BuildConfig.APP_VARIANT_LABEL))
                }
            }.onSuccess {
                appUpdateCheckResult = it
                if (showNoUpdate && it.release == null) snackbarMessage = "当前已是最新版本"
            }.onFailure {
                if (showFailure || showNoUpdate) alertMessage = it.toUserMessage("检查更新失败")
            }
            isCheckingAppUpdate = false
        }
    }

    fun downloadAndInstallUpdate() {
        val release = appUpdateCheckResult.release ?: return
        if (isDownloadingAppUpdate) return
        isDownloadingAppUpdate = true
        appUpdateDownloadProgress = 5
        viewModelScope.launch {
            val result = runCatching {
                val file = appUpdateInstaller.targetFile(release)
                val bytes = withContext(Dispatchers.IO) {
                    apiClient.downloadAppRelease(authToken, release) { progress ->
                        appUpdateDownloadProgress = progress.coerceIn(1, 60)
                    }
                }
                appUpdateDownloadProgress = 70
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    appUpdateDownloadProgress = 80
                    val apk = appUpdateInstaller.inspect(file)
                    val verify = AppUpdateVerifier.verify(apk, release, BuildConfig.VERSION_CODE, android.os.Build.VERSION.SDK_INT)
                    if (!verify.isValid) throw IllegalStateException(AppUpdatePolicy.userFacingError(verify.failureCode))
                }
                appUpdateDownloadProgress = 100
                requestInstallOrOpenInstaller(file)
            }
            isDownloadingAppUpdate = false
            result.onFailure { alertMessage = it.toUserMessage("更新下载失败") }
        }
    }

    fun openUpdateInstallPermissionSettings() {
        showUpdateInstallPermissionDialog = false
        appUpdateInstaller.openInstallPermissionSettings()
    }

    fun postponeUpdateInstallPermission() {
        showUpdateInstallPermissionDialog = false
    }

    private fun requestInstallOrOpenInstaller(file: File) {
        if (appUpdateInstaller.requiresInstallPermission()) {
            pendingUpdateInstallFile = file
            showUpdateInstallPermissionDialog = true
            return
        }
        pendingUpdateInstallFile = null
        appUpdateInstaller.openSystemInstaller(file)
    }

    private fun continuePendingUpdateInstall() {
        val file = pendingUpdateInstallFile ?: return
        if (!file.exists() || appUpdateInstaller.requiresInstallPermission()) return
        pendingUpdateInstallFile = null
        showUpdateInstallPermissionDialog = false
        appUpdateInstaller.openSystemInstaller(file)
    }

    fun viewOnboardingGuide() {
        navigateTo(Screen.OnboardingGuide)
    }

    fun scanWebLoginQr(qrToken: String) {
        if (authToken.isBlank() || webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.scanWebLoginQr(authToken, qrToken) } }
                .onSuccess {
                    pendingWebLogin = it
                    navigateTo(Screen.WebLoginConfirm)
                }
                .onFailure { alertMessage = it.toUserMessage("扫码失败") }
            webLoginActionLoading = false
        }
    }

    fun approveWebLogin() {
        val login = pendingWebLogin ?: return
        if (authToken.isBlank() || webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.approveWebLogin(authToken, login.loginToken) } }
                .onSuccess {
                    webLoginResultTitle = "网页登录已确认"
                    webLoginResultMessage = "网页端可以继续使用当前账号。"
                    pendingWebLogin = null
                    popToRootAndNavigate(Screen.WebLoginResult)
                }
                .onFailure { alertMessage = it.toUserMessage("确认登录失败") }
            webLoginActionLoading = false
        }
    }

    fun rejectWebLogin() {
        val login = pendingWebLogin ?: return
        if (authToken.isBlank() || webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.rejectWebLogin(authToken, login.loginToken) } }
                .onSuccess {
                    webLoginResultTitle = "已拒绝网页登录"
                    webLoginResultMessage = "网页端登录请求已取消。"
                    pendingWebLogin = null
                    popToRootAndNavigate(Screen.WebLoginResult)
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
        if (authToken.isBlank() || webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.revokeWebSession(authToken, sessionId) } }
                .onSuccess {
                    snackbarMessage = "网页登录已退出"
                    refreshWebSessions()
                }
                .onFailure { alertMessage = it.toUserMessage("退出网页登录失败") }
            webLoginActionLoading = false
        }
    }

    fun revokeAllWebSessions() {
        if (authToken.isBlank() || webLoginActionLoading) return
        webLoginActionLoading = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { apiClient.revokeAllWebSessions(authToken) } }
                .onSuccess {
                    snackbarMessage = "全部网页登录已退出"
                    refreshWebSessions()
                }
                .onFailure { alertMessage = it.toUserMessage("退出网页登录失败") }
            webLoginActionLoading = false
        }
    }

    fun canManageIngredients(): Boolean = currentUser?.role == "admin"
    fun canDeleteIngredients(): Boolean = currentUser?.role == "admin"
    fun canRestoreIngredients(): Boolean = currentUser?.role == "admin"

    fun formStateFor(productId: String?): IngredientFormState {
        val product = productId?.let { allProducts.value.find { product -> product.id == it } }
        return product?.toFormState() ?: IngredientFormState()
    }

    fun saveIngredient(form: IngredientFormState, onSuccess: () -> Unit) {
        if (isSavingIngredient) return
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
                internalPrice = form.internalPrice,
                supplyStatus = form.status,
                active = form.isAvailable
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
                imageUrl = existing?.imageUrl ?: "",
                origin = form.origin.trim(),
                minQty = form.minOrderQuantity.toDoubleOrNull() ?: 1.0,
                stepQty = form.quantityStep.toDoubleOrNull() ?: 1.0,
                allowSubstitute = form.allowSubstitute,
                stockStatus = "充足",
                price = form.internalPrice.toDoubleOrNull() ?: 0.0,
                category = form.category,
                code = form.code.trim(),
                imagePath = existing?.imagePath ?: "",
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
                    apiClient.saveProduct(authToken, product)
                }
            }
            isSavingIngredient = false
            result.onSuccess {
                refreshProducts()
                snackbarMessage = when {
                    existing == null -> "食材新增已保存"
                    else -> "食材修改已保存"
                }
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("保存失败")
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
                    apiClient.setProductStatus(authToken, productId, "normal", available)
                }
            }.onSuccess {
                refreshProducts()
                snackbarMessage = if (available) "食材已重新上架" else "食材已下架"
            }.onFailure {
                alertMessage = it.toUserMessage("调整供应状态失败")
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
                    alertMessage = it.toUserMessage("删除失败")
                }
        }
    }

    fun deleteIngredientsBatch(productIds: Set<String>, onSuccess: () -> Unit = {}) {
        if (!canDeleteIngredients()) {
            alertMessage = "当前账号无权删除食材"
            return
        }
        if (productIds.isEmpty() || isDeletingIngredients) return
        isDeletingIngredients = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { apiClient.deleteProductsBatch(authToken, productIds.toList()) }
            }.onSuccess { archivedCount ->
                refreshProducts()
                snackbarMessage = "已删除 $archivedCount 项食材，历史业务记录已保留"
                onSuccess()
            }.onFailure {
                alertMessage = it.toUserMessage("批量删除失败")
            }
            isDeletingIngredients = false
        }
    }

    fun downloadProductImportTemplate(uri: Uri) {
        saveWorkbook(
            uri = uri,
            type = ExternalActionType.PRODUCT_IMPORT_TEMPLATE_EXPORT,
            successMessage = "食材导入模板已保存",
            failureMessage = "模板保存失败，请重试"
        ) { token -> apiClient.downloadProductImportTemplate(token) }
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
                "已接单" -> "确认发货"
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

    fun performOrderAction(order: OrderEntity, cancelReason: String = "数量填写错误") {
        if (activeOrderActionId == order.orderId) return
        if (authToken.isBlank()) {
            alertMessage = "请重新登录后操作订单"
            return
        }
        val isAdminAction = currentUser?.role == "admin"
        val isAcceptAction = isAdminAction && order.status == "待接单"
        activeOrderActionId = order.orderId
        viewModelScope.launch {
            val result = runCatching {
                val bundle = withContext(Dispatchers.IO) {
                    if (isAdminAction) {
                        val nextStatus = RemoteOrderMapper.apiStatusForNextUiAction(order.status, isAdmin = true)
                            ?: throw IllegalStateException("当前状态不可推进")
                        apiClient.setAdminOrderStatus(authToken, order, nextStatus)
                    } else {
                        when (order.status) {
                            "待接单" -> apiClient.cancelOrder(authToken, order.orderId, cancelReason)
                            "已发货" -> apiClient.confirmReceipt(authToken, order.orderId)
                            else -> throw IllegalStateException("当前状态不可操作")
                        }
                    }
                }
                repository.upsertOrder(bundle)
                refreshProducts()
                bundle
            }
            activeOrderActionId = ""
            result.onSuccess { bundle ->
                snackbarMessage = "订单状态已更新"
                val event = adminOrderActionSuccessEvent(
                    isAdmin = isAdminAction,
                    previousStatus = order.status,
                    updatedStatus = bundle.order.status,
                    orderId = order.orderId
                )
                if (event != null) {
                    adminUiEventQueue.emit(event)
                } else if (isAcceptAction) {
                    alertMessage = "订单已提交接单，但服务端返回的状态不允许加入配送批次，请刷新后检查订单状态。"
                }
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
            message.contains("价格未设置") -> "价格未设置"
            message.contains("暂停供应") -> "该食材已暂停供应"
            message.contains("订单状态已变化") -> "订单状态已变化，请刷新后重试"
            message.contains("低于最小申领量") -> "低于最小申领量"
            message.contains("步长") -> "数量不符合申领步长"
            message.contains("configured root", ignoreCase = true) || message.contains("FileProvider", ignoreCase = true) -> "更新包打开失败，请重新下载安装包"
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
}
