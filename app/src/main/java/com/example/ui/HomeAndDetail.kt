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
                        .background(JrxpColors.ReadingSurface, RoundedCornerShape(JrxpDimensions.cornerLg))
                        .border(JrxpDimensions.ruleLineWidth, JrxpColors.RuleLine, RoundedCornerShape(JrxpDimensions.cornerLg))
                        .padding(JrxpDimensions.spacingLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前可供调度品种：${products.count { it.isAvailable && !it.isDeleted }}", style = MaterialTheme.typography.titleMedium, color = JrxpColors.InkPrimary, fontWeight = FontWeight.Bold)
                        Text("台账最后更新：${viewModel.lastSyncText.ifBlank { "等待同步" }}", style = MaterialTheme.typography.bodySmall, color = JrxpColors.InkTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconButton(onClick = { viewModel.refreshProducts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = JrxpColors.DutyBlue)
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
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else JrxpColors.PureSurface,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                label = "chipBg"
            )
            val textColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else JrxpColors.InkPrimary,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(JrxpColors.PureSurface)
            .clickable(enabled = !canOrder, onClick = onOpen)
            .padding(vertical = JrxpDimensions.spacingMd)
            .drawBehind {
                drawLine(
                    color = JrxpColors.SoftDivider,
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
                        color = JrxpColors.InkPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (canOrder) {
                        if (disabledReason.isNotBlank()) Text(disabledReason, style = JrxpTypography.labelMedium, color = JrxpColors.CriticalRed, fontWeight = FontWeight.Bold)
                    } else {
                        androidx.compose.animation.AnimatedContent(
                            targetState = product.displayStatus(),
                            label = "status_anim"
                        ) { status ->
                            StatusBadge(status)
                        }
                    }
                }

                Text(product.spec, style = JrxpTypography.bodySmall, color = JrxpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (canOrder) {
                    Text("内控价 ${Money.formatYuan(product.price)} / ${product.unit}", style = JrxpTypography.bodyMedium, fontWeight = FontWeight.SemiBold, color = JrxpColors.DutyBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    if (isNarrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("当前可用：$available ${product.unit}", style = JrxpTypography.bodySmall, color = JrxpColors.InkTertiary)
                        }
                    } else {
                        Text("当前可用：$available ${product.unit}", style = JrxpTypography.bodySmall, color = JrxpColors.InkTertiary)
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
                    Text("内控价 ${Money.formatYuan(product.price)} / ${product.unit}", style = JrxpTypography.bodyMedium, fontWeight = FontWeight.SemiBold, color = JrxpColors.DutyBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingXs))

                    if (isNarrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("物理总库：${product.stockQuantity.ifBlank { "0" }}", style = JrxpTypography.bodySmall, color = JrxpColors.InkSecondary)
                            Text("调度可用：$available", style = JrxpTypography.bodySmall, color = JrxpColors.SupplyGreen, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("物理总库：${product.stockQuantity.ifBlank { "0" }}", style = JrxpTypography.bodySmall, color = JrxpColors.InkSecondary)
                            Text("调度可用：$available", style = JrxpTypography.bodySmall, color = JrxpColors.SupplyGreen, fontWeight = FontWeight.Medium)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                            Text(product.name, style = MaterialTheme.typography.headlineSmall, color = JrxpColors.InkPrimary, fontWeight = FontWeight.Bold)
                            Text("${product.category} · ${product.code.ifBlank { "未编目" }}", style = MaterialTheme.typography.bodyMedium, color = JrxpColors.InkSecondary)
                        }
                        StatusBadge(product.displayStatus())
                    }
                }
            }
            item {
                DocumentSection(title = "库存与调度", subtitle = "实时同步至各申领单位") {
                    IngredientDetailRow("规格", product.spec)
                    IngredientDetailRow("计量单位", product.unit)
                    IngredientDetailRow("物理总库存", "${product.stockQuantity.ifBlank { "0" }} ${product.unit}")
                    IngredientDetailRow("预警阈值", product.warningQuantity.ifBlank { "未设置" })
                    IngredientDetailRow("今日调度额度", product.availableQuantity.ifBlank { "未受限" })
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JrxpColors.CriticalRed)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(productId: String?, viewModel: SupplyViewModel) {
    var form by remember(productId, viewModel.allProducts.collectAsState().value) {
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
                JrxpPrimaryButton(
                    text = if (productId == null) "新建食材并上架" else "保存修改",
                    onClick = { viewModel.saveIngredient(form) { viewModel.navigateBack() } },
                    enabled = !viewModel.isSavingIngredient,
                    isLoading = viewModel.isSavingIngredient
                )
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
                SectionCard("食材图片") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(14.dp))
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
                DocumentSection("基本信息") {
                    FormInput("食材名称", form.name, { form = form.copy(name = it) }, viewModel.ingredientErrors["name"])
                    DropDownInput("食材分类", form.category, EditableCategories) { form = form.copy(category = it) }
                    FormInput("规格描述", form.spec, { form = form.copy(spec = it) }, viewModel.ingredientErrors["spec"])
                    DropDownInput("计量单位", form.unit, Units) { form = form.copy(unit = it) }
                    DecimalInput("内控参考单价", form.internalPrice, { form = form.copy(internalPrice = it) }, viewModel.ingredientErrors["internalPrice"])
                    DecimalInput("当前物理库存", form.stockQuantity, { form = form.copy(stockQuantity = it) }, viewModel.ingredientErrors["stockQuantity"])
                }
            }
            item {
                DocumentSection("合规与溯源信息") {
                    TextButton(onClick = { showMore = !showMore }) {
                        Text(if (showMore) "收起更多信息" else "展开更多信息")
                    }
                    AnimatedVisibility(showMore) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormInput("系统内码", form.code, { form = form.copy(code = it) }, null)
                            DecimalInput("单次最小调度量", form.minOrderQuantity, { form = form.copy(minOrderQuantity = it) }, viewModel.ingredientErrors["minOrderQuantity"])
                            DecimalInput("数量增减步长", form.quantityStep, { form = form.copy(quantityStep = it) }, viewModel.ingredientErrors["quantityStep"])
                            DecimalInput("库存预警阈值", form.warningQuantity, { form = form.copy(warningQuantity = it) }, viewModel.ingredientErrors["warningQuantity"])
                            FormInput("原产地", form.origin, { form = form.copy(origin = it) }, null)
                            FormInput("供应商/包装", form.packagingSpec, { form = form.copy(packagingSpec = it) }, null)
                            FormInput("保质期要求", form.shelfLife, { form = form.copy(shelfLife = it) }, null)
                            DropDownInput("标准存储方式", form.storageMethod, StorageMethods) { form = form.copy(storageMethod = it) }
                            FormInput("特殊注意事项", form.remark, { form = form.copy(remark = it) }, null, singleLine = false)
                            DropDownInput("当前供应状态", form.status, EditableStatuses) { form = form.copy(status = it) }
                            SwitchRow("是否在台账可见", form.isAvailable) { form = form.copy(isAvailable = it) }
                            DecimalInput("今日最大调度额度", form.availableQuantity, { form = form.copy(availableQuantity = it) }, viewModel.ingredientErrors["availableQuantity"])
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
                            .background(JrxpColors.ReadingSurface, RoundedCornerShape(JrxpDimensions.cornerLg))
                            .border(JrxpDimensions.ruleLineWidth, JrxpColors.RuleLine, RoundedCornerShape(JrxpDimensions.cornerLg))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IngredientImage(product.displayImage(), product.name, Modifier.size(64.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium, color = JrxpColors.InkPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.category} · ${product.spec}", style = MaterialTheme.typography.bodySmall, color = JrxpColors.InkSecondary)
                                Text("下线时间：${product.updatedAt.toTimeText()}", style = MaterialTheme.typography.bodySmall, color = JrxpColors.InkTertiary)
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
