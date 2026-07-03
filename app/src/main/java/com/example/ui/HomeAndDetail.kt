package com.smartprocurement.internal.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.smartprocurement.internal.data.ProductEntity
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.GovernmentBottomActionBar
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentDimens
import com.smartprocurement.internal.ui.designsystem.GovernmentInfoBanner
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSectionHeader
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentShapes
import com.smartprocurement.internal.ui.designsystem.GovernmentStatusLabel
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import com.smartprocurement.internal.ui.designsystem.PoliceBrandConfig
import com.smartprocurement.internal.ui.designsystem.PoliceBrandHeader
import com.smartprocurement.internal.ui.designsystem.PoliceColors
import com.smartprocurement.internal.ui.product.AdminProductActionRow
import com.smartprocurement.internal.ui.product.ProductPublishConfirmDialog
import com.smartprocurement.internal.ui.product.QuickInventorySheet
import com.smartprocurement.internal.ui.product.QuickPriceSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IngredientCategories = listOf("全部", "蔬菜", "水果", "肉禽", "水产", "蛋奶", "粮油", "调料", "其他")
private val EditableCategories = IngredientCategories.drop(1)
private val SupplyStatuses = listOf("全部", "正常供应", "库存紧张", "库存不足", "暂停供应", "已下架")
private val EditableStatuses = listOf("正常供应", "库存紧张", "暂停供应")
private val Units = listOf("公斤", "斤", "箱", "袋", "筐", "盒", "瓶", "份", "个", "包")
private val StorageMethods = listOf("常温", "冷藏", "冷冻", "阴凉干燥")

@Composable
fun HomeScreen(viewModel: SupplyViewModel) {
    val products by viewModel.allProducts.collectAsState()
    val cartList by viewModel.cartItems.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var selectedStatus by remember { mutableStateOf("全部") }
    var priceProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var inventoryProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var toggleProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val filteredProducts = products.filter { product ->
        val matchQuery = product.name.contains(searchQuery, ignoreCase = true) || product.code.contains(searchQuery, ignoreCase = true)
        val matchCategory = selectedCategory == "全部" || product.category == selectedCategory
        val matchStatus = selectedStatus == "全部" || product.displayStatus() == selectedStatus
        matchQuery && matchCategory && matchStatus
    }

    Scaffold(
        floatingActionButton = {
            if (viewModel.canManageIngredients()) {
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.AddProduct) },
                    containerColor = PoliceColors.PolicePrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加食材")
                }
            }
        },
        topBar = {
            val title = if (viewModel.canManageIngredients()) "食材管理" else "食材申领"
            val subtitle = if (viewModel.canManageIngredients()) {
                "系统管理员 · ${viewModel.userName.ifBlank { PoliceBrandConfig.logisticsSubtitle }}"
            } else {
                viewModel.currentUnitName.ifBlank { PoliceBrandConfig.logisticsSubtitle }
            }
            PoliceBrandHeader(title = title, subtitle = subtitle)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!viewModel.canManageIngredients()) {
                item {
                    val cutoff = viewModel.cutoffInfo
                    GovernmentInfoBanner(
                        title = if (cutoff?.isClosed == true) "今日采购已截止" else "今日下单截止：${cutoff?.cutoffTime ?: "读取中"}",
                        message = if (cutoff?.isClosed == true) "如有特殊情况，请联系管理员" else "请在截止前提交采购清单",
                        danger = cutoff?.isClosed == true
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (viewModel.canManageIngredients()) "搜索食材名称或编码" else "搜索食材名称") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PoliceColors.PolicePrimary,
                        focusedLabelColor = PoliceColors.PolicePrimary,
                        cursorColor = PoliceColors.PolicePrimary,
                        focusedContainerColor = PoliceColors.SurfaceWhite,
                        unfocusedContainerColor = PoliceColors.SurfaceWhite
                    )
                )
            }
            item {
                FilterRow(IngredientCategories, selectedCategory) { selectedCategory = it }
            }
            item {
                FilterRow(SupplyStatuses, selectedStatus) { selectedStatus = it }
            }
            item {
                GovernmentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("当前可供应食材 ${products.count { it.isAvailable && !it.isDeleted }} 种", fontWeight = FontWeight.Bold)
                            Text("最后同步：${viewModel.lastSyncText.ifBlank { "等待同步" }}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                        }
                        IconButton(onClick = { viewModel.refreshProducts() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (filteredProducts.isEmpty()) {
                item {
                    EmptyState("暂无符合条件的食材")
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    IngredientCard(
                        product = product,
                        cartQuantity = cartList.find { it.productId == product.id }?.quantity ?: 0.0,
                        canOrder = !viewModel.canManageIngredients(),
                        onOpen = { viewModel.navigateTo(Screen.ProductDetail(product.id)) },
                        onAdd = { viewModel.addToCart(product.id, if (it == 0.0) product.minQty else it + product.stepQty) },
                        onRemove = { viewModel.updateCartQty(product.id, it - product.stepQty) },
                        onPrice = { priceProduct = product },
                        onInventory = { inventoryProduct = product },
                        onToggle = { toggleProduct = product },
                        adminActionLoading = viewModel.activePriceProductId == product.id ||
                            viewModel.activeInventoryProductId == product.id ||
                            viewModel.activeStatusProductId == product.id
                    )
                }
            }
        }
    }
    priceProduct?.let { product ->
        QuickPriceSheet(
            product = product,
            loading = viewModel.activePriceProductId == product.id,
            onDismiss = { priceProduct = null },
            onSave = { priceText, reason -> viewModel.updateProductPrice(product, priceText, reason) { priceProduct = null } }
        )
    }
    inventoryProduct?.let { product ->
        QuickInventorySheet(
            product = product,
            loading = viewModel.activeInventoryProductId == product.id,
            onDismiss = { inventoryProduct = null },
            onSave = { mode, quantity, reason -> viewModel.adjustProductInventory(product, mode, quantity, reason) { inventoryProduct = null } }
        )
    }
    toggleProduct?.let { product ->
        ProductPublishConfirmDialog(
            product = product,
            loading = viewModel.activeStatusProductId == product.id,
            onDismiss = { toggleProduct = null },
            onConfirm = {
                val publish = !product.isAvailable || product.status == "已下架"
                viewModel.setIngredientAvailable(product.id, publish)
                toggleProduct = null
            }
        )
    }
}

@Composable
private fun FilterRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PoliceColors.PoliceLight,
                    selectedLabelColor = PoliceColors.PoliceNavy,
                    containerColor = PoliceColors.SurfaceWhite,
                    labelColor = PoliceColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == option,
                    borderColor = PoliceColors.BorderColor,
                    selectedBorderColor = PoliceColors.PolicePrimary
                )
            )
        }
    }
}

@Composable
private fun IngredientCard(
    product: ProductEntity,
    cartQuantity: Double,
    canOrder: Boolean,
    onOpen: () -> Unit,
    onAdd: (Double) -> Unit,
    onRemove: (Double) -> Unit,
    onPrice: () -> Unit = {},
    onInventory: () -> Unit = {},
    onToggle: () -> Unit = {},
    adminActionLoading: Boolean = false
) {
    val available = product.availableQuantity.ifBlank { product.stockQuantity }
    val availableNumber = available.toDoubleOrNull() ?: 0.0
    val nextQuantity = if (cartQuantity == 0.0) product.minQty else cartQuantity + product.stepQty
    val disabledReason = when {
        product.isDeleted -> "已下架"
        !product.isAvailable || product.displayStatus() == "已下架" -> "已下架"
        product.displayStatus() == "暂停供应" -> "暂停供应"
        Money.yuanDoubleToCents(product.price) <= 0 -> "价格未设置"
        availableNumber <= 0.0 || availableNumber < product.minQty -> "库存不足"
        nextQuantity > availableNumber -> "库存不足"
        else -> ""
    }
    val canAdd = canOrder && disabledReason.isBlank()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !canOrder, onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IngredientImage(product.displayImage(), product.name, Modifier.size(86.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (canOrder) {
                        GovernmentStatusLabel(if (disabledReason.isNotBlank()) disabledReason else product.displayStatus())
                    } else {
                        GovernmentStatusLabel(product.displayStatus())
                    }
                }
                Text(product.spec, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (canOrder) {
                    Text("单价 ${Money.formatYuan(product.price)} / ${product.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GovernmentColors.GovernmentBlueDark)
                    Text("可用库存：$available ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            enabled = cartQuantity > 0,
                            onClick = { onRemove(cartQuantity) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${cartQuantity.clean()} ${product.unit}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            enabled = canAdd,
                            onClick = { onAdd(cartQuantity) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "增加${product.name}数量")
                        }
                    }
                } else {
                    Text("单价 ${Money.formatYuan(product.price)} / ${product.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GovernmentColors.GovernmentBlueDark)
                    Text("总库存：${product.stockQuantity.ifBlank { "0" }} ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                    Text("预占库存：${product.reservedQuantity.ifBlank { "0" }} ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                    Text("可用库存：$available ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    AdminProductActionRow(
                        product = product,
                        loading = adminActionLoading,
                        onPrice = onPrice,
                        onInventory = onInventory,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(productId: String, viewModel: SupplyViewModel) {
    val products by viewModel.allProducts.collectAsState()
    val cartList by viewModel.cartItems.collectAsState()
    val product = products.find { it.id == productId }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedQty by remember(productId, cartList) {
        mutableStateOf(cartList.find { it.productId == productId }?.quantity ?: (product?.minQty ?: 1.0))
    }

    if (product == null) {
        EmptyState("食材不存在或已删除")
        return
    }

    Scaffold(
        topBar = {
            GovernmentTopBar(title = "食材详情", onBack = { viewModel.navigateBack() })
        },
        bottomBar = {
            DetailActions(product, selectedQty, viewModel, onQtyChange = { selectedQty = it }, onDelete = { showDeleteDialog = true })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                IngredientImage(product.displayImage(), product.name, Modifier.fillMaxWidth().aspectRatio(1.5f))
            }
            item {
                CardBlock {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${product.category} · ${product.code.ifBlank { "未设置编码" }}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                        }
                        StatusBadge(product.displayStatus())
                    }
                }
            }
            item {
                CardBlock {
                    IngredientDetailRow("规格", product.spec)
                    IngredientDetailRow("单位", product.unit)
                    IngredientDetailRow("当前库存", "${product.stockQuantity.ifBlank { "0" }} ${product.unit}")
                    IngredientDetailRow("库存预警", product.warningQuantity.ifBlank { "未设置" })
                    IngredientDetailRow("可用库存", "${product.availableQuantity.ifBlank { product.stockQuantity }} ${product.unit}")
                    IngredientDetailRow("供应状态", product.displayStatus())
                    IngredientDetailRow("是否上架", if (product.isAvailable) "是" else "否")
                }
            }
            item {
                CardBlock {
                    IngredientDetailRow("产地", product.origin.ifBlank { "未填写" })
                    IngredientDetailRow("存储方式", product.storageMethod.ifBlank { "未填写" })
                    IngredientDetailRow("保质期说明", product.shelfLife.ifBlank { "未填写" })
                    IngredientDetailRow("是否允许替代", if (product.allowSubstitute) "允许" else "不允许")
                    IngredientDetailRow("备注", product.remark.ifBlank { "无" })
                    if (viewModel.canManageIngredients()) {
                        IngredientDetailRow("内部参考价", Money.formatYuan(product.price))
                    }
                    IngredientDetailRow("创建时间", product.createdAt.toTimeText())
                    IngredientDetailRow("最后更新时间", product.updatedAt.toTimeText())
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除该食材？") },
            text = { Text("删除后，该食材将不会继续出现在可申领列表中，历史业务记录不会受到影响。") },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.softDeleteIngredient(product.id)
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@Composable
private fun DetailActions(
    product: ProductEntity,
    selectedQty: Double,
    viewModel: SupplyViewModel,
    onQtyChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val availableNumber = product.availableQuantity.ifBlank { product.stockQuantity }.toDoubleOrNull() ?: 0.0
    val canAdd = product.isAvailable &&
        !product.isDeleted &&
        product.displayStatus() != "暂停供应" &&
        product.displayStatus() != "已下架" &&
        Money.yuanDoubleToCents(product.price) > 0 &&
        availableNumber >= selectedQty
    GovernmentBottomActionBar {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (viewModel.canManageIngredients()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GovernmentPrimaryButton(
                        text = "编辑食材",
                        onClick = { viewModel.navigateTo(Screen.EditProduct(product.id)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { viewModel.setIngredientAvailable(product.id, !product.isAvailable) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (product.isAvailable) "下架" else "上架")
                    }
                }
                if (viewModel.canDeleteIngredients()) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除食材")
                    }
                }
            } else if (product.isAvailable && !product.isDeleted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { if (selectedQty > product.minQty) onQtyChange(selectedQty - product.stepQty) }) {
                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${selectedQty.clean()} ${product.unit}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(enabled = selectedQty + product.stepQty <= availableNumber, onClick = { onQtyChange(selectedQty + product.stepQty) }) {
                        Icon(Icons.Default.Add, contentDescription = "增加")
                    }
                    Button(
                        enabled = canAdd,
                        onClick = {
                            viewModel.addToCart(product.id, selectedQty)
                            viewModel.snackbarMessage = "已加入采购清单"
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) { Text(if (canAdd) "加入清单" else "不可加入") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(productId: String?, viewModel: SupplyViewModel) {
    val productsForForm by viewModel.allProducts.collectAsState()
    val editingProduct = productId?.let { id -> productsForForm.find { it.id == id } }
    var priceProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var inventoryProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var form by remember(productId, productsForForm) {
        mutableStateOf(viewModel.formStateFor(productId))
    }
    var leavingConfirm by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.persistIngredientImage(it) { path -> form = form.copy(imagePath = path) } }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.pendingCameraUri?.let { uri ->
            viewModel.persistIngredientImage(uri) { path -> form = form.copy(imagePath = path) }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(viewModel.createCameraUri()) else viewModel.alertMessage = "需要相机权限才能拍照"
    }

    Scaffold(
        topBar = {
            GovernmentTopBar(
                title = if (productId == null) "添加食材" else "编辑食材",
                onBack = { leavingConfirm = true }
            )
        },
        bottomBar = {
            GovernmentBottomActionBar {
                GovernmentPrimaryButton(
                    text = if (productId == null) "保存并上架" else "保存资料",
                    onClick = { viewModel.saveIngredient(form) { viewModel.navigateBack() } },
                    enabled = !viewModel.isSavingIngredient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard("食材图片") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(GovernmentShapes.MediumRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (form.imagePath.isBlank()) {
                            Text("上传食材图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            AsyncImage(model = form.imagePath, contentDescription = form.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    takePicture.launch(viewModel.createCameraUri())
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (form.imagePath.isBlank()) "拍照" else "重新拍照") }
                        OutlinedButton(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (form.imagePath.isBlank()) "从相册选择" else "更换图片") }
                        if (form.imagePath.isNotBlank()) {
                            OutlinedButton(onClick = { form = form.copy(imagePath = "") }) { Text("删除图片") }
                        }
                    }
                }
            }
            item {
                SectionCard("基本信息") {
                    FormInput("食材名称", form.name, { form = form.copy(name = it) }, viewModel.ingredientErrors["name"])
                    DropDownInput("食材分类", form.category, EditableCategories) { form = form.copy(category = it) }
                    FormInput("规格描述", form.spec, { form = form.copy(spec = it) }, viewModel.ingredientErrors["spec"])
                    DropDownInput("计量单位", form.unit, Units) { form = form.copy(unit = it) }
                    if (productId == null) {
                        DecimalInput("单价", form.internalPrice, { form = form.copy(internalPrice = it) }, viewModel.ingredientErrors["internalPrice"])
                        DecimalInput("当前库存", form.stockQuantity, { form = form.copy(stockQuantity = it) }, viewModel.ingredientErrors["stockQuantity"])
                    } else if (editingProduct != null) {
                        ReadOnlyProductOperationSummary(
                            product = editingProduct,
                            onPrice = { priceProduct = editingProduct },
                            onInventory = { inventoryProduct = editingProduct }
                        )
                    }
                }
            }
            item {
                SectionCard("更多信息") {
                    TextButton(onClick = { showMore = !showMore }) {
                        Text(if (showMore) "收起更多信息" else "展开更多信息")
                    }
                    AnimatedVisibility(showMore) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormInput("食材编码", form.code, { form = form.copy(code = it) }, null)
                            DecimalInput("最小申领量", form.minOrderQuantity, { form = form.copy(minOrderQuantity = it) }, viewModel.ingredientErrors["minOrderQuantity"])
                            DecimalInput("数量步长", form.quantityStep, { form = form.copy(quantityStep = it) }, viewModel.ingredientErrors["quantityStep"])
                            DecimalInput("库存预警值", form.warningQuantity, { form = form.copy(warningQuantity = it) }, viewModel.ingredientErrors["warningQuantity"])
                            FormInput("产地", form.origin, { form = form.copy(origin = it) }, null)
                            FormInput("供应商", form.packagingSpec, { form = form.copy(packagingSpec = it) }, null)
                            FormInput("保质期说明", form.shelfLife, { form = form.copy(shelfLife = it) }, null)
                            DropDownInput("存储方式", form.storageMethod, StorageMethods) { form = form.copy(storageMethod = it) }
                            FormInput("商品说明", form.remark, { form = form.copy(remark = it) }, null, singleLine = false)
                        }
                    }
                }
            }
        }
    }

    if (leavingConfirm) {
        AlertDialog(
            onDismissRequest = { leavingConfirm = false },
            title = { Text("离开当前页面？") },
            text = { Text("未保存的内容不会写入系统。") },
            dismissButton = { TextButton(onClick = { leavingConfirm = false }) { Text("继续编辑") } },
            confirmButton = { TextButton(onClick = { viewModel.navigateBack() }) { Text("离开") } }
        )
    }
    priceProduct?.let { product ->
        QuickPriceSheet(
            product = product,
            loading = viewModel.activePriceProductId == product.id,
            onDismiss = { priceProduct = null },
            onSave = { priceText, reason -> viewModel.updateProductPrice(product, priceText, reason) { priceProduct = null } }
        )
    }
    inventoryProduct?.let { product ->
        QuickInventorySheet(
            product = product,
            loading = viewModel.activeInventoryProductId == product.id,
            onDismiss = { inventoryProduct = null },
            onSave = { mode, quantity, reason -> viewModel.adjustProductInventory(product, mode, quantity, reason) { inventoryProduct = null } }
        )
    }
}

@Composable
private fun ReadOnlyProductOperationSummary(product: ProductEntity, onPrice: () -> Unit, onInventory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("价格", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("${Money.formatYuan(product.price)} / ${product.unit}", fontSize = 14.sp)
        OutlinedButton(onClick = onPrice, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("修改价格")
        }
        Text("库存", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("总库存 ${product.stockQuantity} · 已预占 ${product.reservedQuantity} · 可用 ${product.availableQuantity.ifBlank { product.stockQuantity }}", fontSize = 14.sp)
        OutlinedButton(onClick = onInventory, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("调整库存")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedIngredientsScreen(viewModel: SupplyViewModel) {
    val deletedProducts by viewModel.deletedProducts.collectAsState()

    Scaffold(
        topBar = {
            GovernmentTopBar(title = "已删除食材", onBack = { viewModel.navigateBack() })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (deletedProducts.isEmpty()) {
                item { EmptyState("暂无已删除食材") }
            } else {
                items(deletedProducts, key = { it.id }) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(GovernmentShapes.MediumRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IngredientImage(product.displayImage(), product.name, Modifier.size(64.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.category} · ${product.spec}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                                Text("删除时间：${product.updatedAt.toTimeText()}", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                            }
                            Button(onClick = { viewModel.restoreIngredient(product.id) }) {
                                Text("恢复")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GovernmentShapes.MediumRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) = SectionCard("", content)

@Composable
private fun FormInput(label: String, value: String, onValueChange: (String) -> Unit, error: String?, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}

@Composable
private fun DecimalInput(label: String, value: String, onValueChange: (String) -> Unit, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropDownInput(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IngredientDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun IngredientImage(model: String, name: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model.isBlank()) {
            Text("暂无图片", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
        } else {
            AsyncImage(model = model, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    GovernmentStatusLabel(status)
}

@Composable
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(42.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun ProductEntity.displayImage(): String = imagePath.ifBlank { imageUrl }
private fun ProductEntity.displayStatus(): String = when {
    isDeleted -> "已下架"
    !isAvailable -> "已下架"
    status == "暂停供应" -> "暂停供应"
    (availableQuantity.ifBlank { stockQuantity }.toDoubleOrNull() ?: 0.0) <= 0.0 -> "库存不足"
    status.isNotBlank() -> status
    stockStatus == "紧张" -> "库存紧张"
    else -> "正常供应"
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.getDefault(), "%.1f", this)
private fun Long.toTimeText(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
