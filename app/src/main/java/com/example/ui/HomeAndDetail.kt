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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.smartprocurement.internal.domain.product.ProductOptions
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.quantity.QuantityFormatter
import com.smartprocurement.internal.ui.designsystem.PoliceBrandHeader
import com.smartprocurement.internal.ui.theme.JrxpColors
import com.smartprocurement.internal.ui.theme.JrxpTheme
import com.smartprocurement.internal.ui.theme.JrxpDimensions
import com.smartprocurement.internal.ui.theme.JrxpTypography
import com.smartprocurement.internal.ui.components.JrxpPrimaryButton
import com.smartprocurement.internal.ui.components.JrxpSecondaryButton
import com.smartprocurement.internal.ui.components.DocumentSection
import com.smartprocurement.internal.ui.components.SupplyStatusMark
import com.smartprocurement.internal.ui.components.StatusType
import com.smartprocurement.internal.ui.components.QuantityStepper
import com.smartprocurement.internal.ui.components.PrimaryActionDock
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IngredientCategories = listOf("全部", "蔬菜", "水果", "肉禽", "水产", "蛋奶", "粮油", "调料", "其他")
private val SupplyStatuses = listOf("全部", "正常供应", "库存紧张", "库存不足", "暂停供应", "已下架")

@Composable
fun HomeScreen(viewModel: SupplyViewModel) {
    val products by viewModel.allProducts.collectAsState()
    val cartList by viewModel.cartItems.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var selectedStatus by remember { mutableStateOf("全部") }

    val filteredProducts = products.filter { product ->
        val matchQuery = product.name.contains(searchQuery, ignoreCase = true) || product.code.contains(searchQuery, ignoreCase = true)
        val matchCategory = selectedCategory == "全部" || product.category == selectedCategory
        val matchStatus = selectedStatus == "全部" || product.displayStatus() == selectedStatus
        matchQuery && matchCategory && matchStatus
    }

    Scaffold(
        floatingActionButton = {
            if (viewModel.canManageIngredients()) {
                var isFabVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isFabVisible = true }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isFabVisible,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn()
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.navigateTo(Screen.AddProduct) },
                        containerColor = JrxpColors.CommandNavy,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加食材")
                    }
                }
            }
        },
        topBar = {
            val title = if (viewModel.canManageIngredients()) "食材管理" else "食材申领"
            val subtitle = if (viewModel.canManageIngredients()) {
                "系统管理员 · ${viewModel.userName}"
            } else {
                "${viewModel.currentUnitName} · ${viewModel.defaultDeliveryPoint}"
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
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("按名称或编码搜索") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            item {
                FilterRow(IngredientCategories, selectedCategory) { selectedCategory = it }
            }
            item {
                FilterRow(SupplyStatuses, selectedStatus) { selectedStatus = it }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(JrxpDimensions.cornerLg))
                        .border(JrxpDimensions.ruleLineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(JrxpDimensions.cornerLg))
                        .padding(JrxpDimensions.spacingLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前可供调度品种：${products.count { it.isAvailable && !it.isDeleted }}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("台账最后更新：${viewModel.lastSyncText.ifBlank { "等待同步" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconButton(onClick = { viewModel.refreshProducts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (filteredProducts.isEmpty()) {
                item {
                    EmptyState("暂无符合条件的食材")
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    val cartQty = cartList.find { it.productId == product.id }?.quantity ?: 0.0
                    IngredientCard(
                        modifier = Modifier.animateItem(),
                        product = product,
                        cartQuantity = cartQty,
                        canOrder = !viewModel.canManageIngredients(),
                        onOpen = { viewModel.navigateTo(Screen.ProductDetail(product.id)) },
                        onQtyChange = { newVal ->
                            if (newVal == 0.0) {
                                viewModel.updateCartQty(product.id, 0.0)
                            } else {
                                if (cartQty == 0.0) viewModel.addToCart(product.id, newVal)
                                else viewModel.updateCartQty(product.id, newVal)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it }) { option ->
            val isSelected = selected == option
            val backgroundColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                label = "chipBg"
            )
            val textColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                label = "chipText"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option) },
                label = {
                    Text(
                        text = option,
                        style = JrxpTypography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = backgroundColor,
                    selectedContainerColor = backgroundColor,
                    labelColor = textColor,
                    selectedLabelColor = textColor
                )
            )
        }
    }
}

@Composable
private fun IngredientCard(
    modifier: Modifier = Modifier,
    product: ProductEntity,
    cartQuantity: Double,
    canOrder: Boolean,
    onOpen: () -> Unit,
    onQtyChange: (Double) -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val stock = QuantityFormatter.format(product.stockQuantity)
    val available = QuantityFormatter.format(product.availableQuantity.ifBlank { stock })
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = !canOrder, onClick = onOpen)
            .padding(vertical = JrxpDimensions.spacingMd)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
    ) {
        val isNarrow = maxWidth < 360.dp

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JrxpDimensions.spacingMd)) {
            IngredientImage(product.displayImage(), product.name, Modifier.size(88.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Name and Status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = product.name,
                        style = JrxpTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (canOrder) {
                        if (disabledReason.isNotBlank()) Text(disabledReason, style = JrxpTypography.labelMedium, color = JrxpTheme.colors.criticalRed, fontWeight = FontWeight.Bold)
                    } else {
                        androidx.compose.animation.AnimatedContent(
                            targetState = product.displayStatus(),
                            label = "status_anim"
                        ) { status ->
                            StatusBadge(status)
                        }
                    }
                }

                Text(product.spec, style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (canOrder) {
                    Text("内控价 ${Money.formatYuan(product.price)} / ${product.unit}", style = JrxpTypography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    if (isNarrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("当前可用：$available ${product.unit}", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("当前可用：$available ${product.unit}", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                    Box(modifier = Modifier.align(Alignment.End)) {
                        QuantityStepper(
                            value = cartQuantity,
                            unit = product.unit,
                            step = product.stepQty,
                            maxValue = availableNumber,
                            onValueChange = onQtyChange
                        )
                    }
                } else {
                    Text("内控价 ${Money.formatYuan(product.price)} / ${product.unit}", style = JrxpTypography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingXs))

                    if (isNarrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("物理总库：$stock", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("调度可用：$available", style = JrxpTypography.bodySmall, color = JrxpTheme.colors.supplyGreen, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("物理总库：$stock", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("调度可用：$available", style = JrxpTypography.bodySmall, color = JrxpTheme.colors.supplyGreen, fontWeight = FontWeight.Medium)
                        }
                    }
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
            CenterAlignedTopAppBar(
                title = { Text("食材详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            DetailActions(product, selectedQty, viewModel, onQtyChange = { selectedQty = it }, onDelete = { showDeleteDialog = true })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshingProducts,
            onRefresh = { viewModel.refreshProducts() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    IngredientImage(product.displayImage(), product.name, Modifier.fillMaxWidth().aspectRatio(1.5f))
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = JrxpDimensions.spacingMd)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text("${product.category} · ${product.code.ifBlank { "未编目" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(product.displayStatus())
                        }
                    }
                }
                item {
                    DocumentSection(title = "库存与调度", subtitle = "下拉可同步最新数据") {
                        IngredientDetailRow("规格", product.spec)
                        IngredientDetailRow("计量单位", product.unit)
                        IngredientDetailRow("物理总库存", "${QuantityFormatter.format(product.stockQuantity)} ${product.unit}")
                        IngredientDetailRow("预警阈值", QuantityFormatter.format(product.warningQuantity.ifBlank { "0" }))
                        IngredientDetailRow("可用库存", "${QuantityFormatter.format(product.availableQuantity.ifBlank { product.stockQuantity })} ${product.unit}")
                        IngredientDetailRow("可见状态", if (product.isAvailable) "上架中" else "已隐藏")
                    }
                }
                item {
                    DocumentSection(title = "溯源与合规") {
                        IngredientDetailRow("原产地", product.origin.ifBlank { "未填写" })
                        IngredientDetailRow("保存方式", product.storageMethod.ifBlank { "未填写" })
                        IngredientDetailRow("保质期(天)", product.shelfLife.ifBlank { "未填写" })
                        IngredientDetailRow("是否允许替换", if (product.allowSubstitute) "允许" else "严格不可替换")
                        IngredientDetailRow("特殊备注", product.remark.ifBlank { "无" })
                        if (viewModel.canManageIngredients()) {
                            IngredientDetailRow("内控参考价", Money.formatYuan(product.price))
                        }
                        IngredientDetailRow("系统建档", product.createdAt.toTimeText())
                        IngredientDetailRow("最后更新", product.updatedAt.toTimeText())
                    }
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
    PrimaryActionDock {
        if (viewModel.canManageIngredients()) {
            Row(horizontalArrangement = Arrangement.spacedBy(JrxpDimensions.spacingMd)) {
                JrxpPrimaryButton(
                    text = "编辑食材信息",
                    onClick = { viewModel.navigateTo(Screen.EditProduct(product.id)) },
                    modifier = Modifier.weight(1f)
                )
                JrxpSecondaryButton(
                    text = if (product.isAvailable) "隐藏不可见" else "上架显示",
                    onClick = { viewModel.setIngredientAvailable(product.id, !product.isAvailable) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (viewModel.canDeleteIngredients()) {
                Spacer(modifier = Modifier.height(JrxpDimensions.spacingMd))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().height(JrxpDimensions.touchTargetMin),
                    shape = JrxpDimensions.shapeMd,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JrxpTheme.colors.criticalRed)
                ) {
                    Text("从数据库中删除", fontWeight = FontWeight.Bold)
                }
            }
        } else if (product.isAvailable && !product.isDeleted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JrxpDimensions.spacingMd)) {
                QuantityStepper(
                    value = selectedQty,
                    unit = product.unit,
                    step = product.stepQty,
                    maxValue = availableNumber,
                    onValueChange = onQtyChange,
                    modifier = Modifier.weight(1f)
                )
                JrxpPrimaryButton(
                    text = if (canAdd) "更新台账" else "不可加入",
                    enabled = canAdd,
                    onClick = {
                        viewModel.addToCart(product.id, selectedQty)
                        viewModel.snackbarMessage = "已同步至清单"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientFormScreen(productId: String?, viewModel: SupplyViewModel) {
    var form by remember(productId, viewModel.allProducts.collectAsState().value) {
        mutableStateOf(viewModel.formStateFor(productId))
    }
    var leavingConfirm by remember { mutableStateOf(false) }
    var imageSheet by remember { mutableStateOf(false) }
    var rulesSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var showMoreCategories by remember { mutableStateOf(false) }
    var showMoreUnits by remember { mutableStateOf(false) }
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
    fun resetForNext(saved: IngredientFormState): IngredientFormState = IngredientFormState(
        category = saved.category,
        unit = saved.unit,
        minOrderQuantity = saved.minOrderQuantity,
        quantityStep = saved.quantityStep,
        warningQuantity = saved.warningQuantity,
        storageMethod = saved.storageMethod,
        status = saved.status,
        isAvailable = saved.isAvailable
    )
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture.launch(viewModel.createCameraUri())
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (productId == null) "添加食材" else "编辑食材", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { leavingConfirm = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            PrimaryActionDock {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    JrxpSecondaryButton(
                        text = "保存并继续添加",
                        onClick = {
                            viewModel.saveIngredient(form) {
                                form = resetForNext(form)
                            }
                        },
                        enabled = !viewModel.isSavingIngredient,
                        modifier = Modifier.weight(1f)
                    )
                    JrxpPrimaryButton(
                        text = "保存食材",
                        onClick = { viewModel.saveIngredient(form) { viewModel.navigateBack() } },
                        enabled = !viewModel.isSavingIngredient,
                        isLoading = viewModel.isSavingIngredient,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { imageSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (form.imagePath.isBlank()) {
                            Text("暂无图片", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                        } else {
                            AsyncImage(model = form.imagePath, contentDescription = form.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormInput("食材名称", form.name, { form = form.copy(name = it) }, viewModel.ingredientErrors["name"])
                        FormInput("规格", form.spec, { form = form.copy(spec = it) }, viewModel.ingredientErrors["spec"])
                    }
                }
            }
            item {
                PlainSection("食材分类") {
                    QuickOptionFlow(
                        primary = ProductOptions.primaryCategories,
                        extra = ProductOptions.extraCategories,
                        selected = form.category,
                        showExtra = showMoreCategories,
                        onToggleExtra = { showMoreCategories = !showMoreCategories },
                        onSelected = { form = form.copy(category = it) }
                    )
                    FieldError(viewModel.ingredientErrors["category"])
                }
            }
            item {
                PlainSection("计量单位") {
                    QuickOptionFlow(
                        primary = ProductOptions.primaryUnits,
                        extra = ProductOptions.extraUnits,
                        selected = form.unit,
                        showExtra = showMoreUnits,
                        onToggleExtra = { showMoreUnits = !showMoreUnits },
                        onSelected = { form = form.copy(unit = it) }
                    )
                    FieldError(viewModel.ingredientErrors["unit"])
                }
            }
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val twoColumns = maxWidth >= 420.dp
                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            DecimalInput("单价", form.internalPrice, { form = form.copy(internalPrice = it) }, viewModel.ingredientErrors["internalPrice"], Modifier.weight(1f), suffix = "元")
                            DecimalInput("当前库存", form.stockQuantity, { form = form.copy(stockQuantity = it) }, viewModel.ingredientErrors["stockQuantity"], Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DecimalInput("单价", form.internalPrice, { form = form.copy(internalPrice = it) }, viewModel.ingredientErrors["internalPrice"], suffix = "元")
                            DecimalInput("当前库存", form.stockQuantity, { form = form.copy(stockQuantity = it) }, viewModel.ingredientErrors["stockQuantity"])
                        }
                    }
                }
            }
            item {
                PlainSection("供应状态") {
                    SegmentedOptionRow(ProductOptions.supplyStatuses, form.status) { form = form.copy(status = it) }
                    SwitchRow("是否上架", form.isAvailable) { form = form.copy(isAvailable = it) }
                }
            }
            item {
                SummaryRow(
                    title = "申领规则",
                    subtitle = "最小 ${form.minOrderQuantity.ifBlank { "1" }} · 步长 ${form.quantityStep.ifBlank { "1" }} · 预警 ${form.warningQuantity.ifBlank { "0" }}",
                    onClick = { rulesSheet = true }
                )
            }
            item {
                SummaryRow(
                    title = "更多资料",
                    subtitle = listOf(form.origin, form.packagingSpec, form.storageMethod).filter { it.isNotBlank() }.joinToString("、").ifBlank { "产地、供应商、储存方式等" },
                    onClick = { moreSheet = true }
                )
            }
        }
    }

    if (imageSheet) {
        ModalBottomSheet(onDismissRequest = { imageSheet = false }) {
            SheetAction("拍照") {
                imageSheet = false
                openCamera()
            }
            SheetAction("从相册选择") {
                imageSheet = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SheetAction("移除图片", enabled = form.imagePath.isNotBlank()) {
                imageSheet = false
                form = form.copy(imagePath = "")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (rulesSheet) {
        ModalBottomSheet(onDismissRequest = { rulesSheet = false }) {
            SheetContent("申领规则") {
                DecimalInput("最小申领量", form.minOrderQuantity, { form = form.copy(minOrderQuantity = it) }, viewModel.ingredientErrors["minOrderQuantity"])
                DecimalInput("数量步长", form.quantityStep, { form = form.copy(quantityStep = it) }, viewModel.ingredientErrors["quantityStep"])
                DecimalInput("库存预警值", form.warningQuantity, { form = form.copy(warningQuantity = it) }, viewModel.ingredientErrors["warningQuantity"])
                JrxpPrimaryButton(text = "完成", onClick = { rulesSheet = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (moreSheet) {
        ModalBottomSheet(onDismissRequest = { moreSheet = false }) {
            SheetContent("更多资料") {
                FormInput("食材编码", form.code, { form = form.copy(code = it) }, null)
                FormInput("产地", form.origin, { form = form.copy(origin = it) }, null)
                FormInput("供应商", form.packagingSpec, { form = form.copy(packagingSpec = it) }, null)
                Text("储存方式", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                QuickOptionFlow(
                    primary = ProductOptions.storageMethods,
                    extra = emptyList(),
                    selected = form.storageMethod,
                    showExtra = false,
                    onToggleExtra = {},
                    onSelected = { form = form.copy(storageMethod = it) }
                )
                FormInput("保质期说明", form.shelfLife, { form = form.copy(shelfLife = it) }, null)
                FormInput("商品说明", form.remark, { form = form.copy(remark = it) }, null, singleLine = false)
                JrxpPrimaryButton(text = "完成", onClick = { moreSheet = false }, modifier = Modifier.fillMaxWidth())
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedIngredientsScreen(viewModel: SupplyViewModel) {
    val deletedProducts by viewModel.deletedProducts.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("已删除食材", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (deletedProducts.isEmpty()) {
                item { EmptyState("暂无已删除食材") }
            } else {
                items(deletedProducts, key = { it.id }) { product ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(JrxpDimensions.cornerLg))
                            .border(JrxpDimensions.ruleLineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(JrxpDimensions.cornerLg))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IngredientImage(product.displayImage(), product.name, Modifier.size(64.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.category} · ${product.spec}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("下线时间：${product.updatedAt.toTimeText()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            JrxpSecondaryButton(
                                text = "恢复",
                                onClick = { viewModel.restoreIngredient(product.id) },
                                modifier = Modifier.width(80.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    DocumentSection(title = title, content = content)
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) = SectionCard("", content)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickOptionFlow(
    primary: List<String>,
    extra: List<String>,
    selected: String,
    showExtra: Boolean,
    onToggleExtra: () -> Unit,
    onSelected: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (primary + if (showExtra) extra else emptyList()).forEach { option ->
            QuickOptionChip(option, selected == option) { onSelected(option) }
        }
        if (extra.isNotEmpty()) {
            QuickOptionChip(if (showExtra) "收起" else "更多", false, onToggleExtra)
        }
    }
}

@Composable
private fun QuickOptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) JrxpColors.CommandNavy else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) JrxpColors.CommandNavy else MaterialTheme.colorScheme.outline),
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp), fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedOptionRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun PlainSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun SummaryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontSize = 16.sp)
    }
}

@Composable
private fun SheetContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FieldError(error: String?) {
    if (!error.isNullOrBlank()) {
        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun FormInput(label: String, value: String, onValueChange: (String) -> Unit, error: String?, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}

@Composable
private fun DecimalInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model.isBlank()) {
            Text("暂无图片", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = model, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "库存紧张" -> Color(0xFFE69532)
        "库存不足", "暂停供应", "已下架" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Text(
        text = status,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
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
