package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.data.ProductEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IngredientCategories = listOf("全部", "蔬菜", "水果", "肉禽", "水产", "蛋奶", "粮油", "调料", "其他")
private val EditableCategories = IngredientCategories.drop(1)
private val SupplyStatuses = listOf("全部", "正常供应", "库存紧张", "暂停供应", "已下架")
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
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.AddProduct) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加食材")
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("食材管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${viewModel.userName} · ${viewModel.userDept}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = roleLabel(viewModel.userRole),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("当前可供应食材 ${products.count { it.isAvailable && !it.isDeleted }} 种", fontWeight = FontWeight.Bold)
                            Text("最后同步：${viewModel.lastSyncText.ifBlank { "等待同步" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onRemove = { viewModel.updateCartQty(product.id, it - product.stepQty) }
                    )
                }
            }
        }
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
                label = { Text(option, fontSize = 12.sp) }
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
    onRemove: (Double) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IngredientImage(product.displayImage(), product.name, Modifier.size(86.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusBadge(product.displayStatus())
                }
                Text("${product.category} · ${product.spec}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("单位：${product.unit}    库存：${product.stockQuantity.ifBlank { "0" }} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("最小申领 ${product.minQty.clean()} ${product.unit}，步长 ${product.stepQty.clean()} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canOrder && product.isAvailable && product.displayStatus() != "暂停供应") {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (cartQuantity > 0) {
                            IconButton(onClick = { onRemove(cartQuantity) }, modifier = Modifier.size(32.dp)) {
                                Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${cartQuantity.clean()} ${product.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onAdd(cartQuantity) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
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
                CardBlock {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${product.category} · ${product.code.ifBlank { "未设置编码" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    IngredientDetailRow("今日可供数量", product.availableQuantity.ifBlank { "未设置" })
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
                        IngredientDetailRow("内部参考价", if (product.price == 0.0) "未填写" else "${product.price.clean()} 元")
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
    Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (viewModel.canManageIngredients()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.navigateTo(Screen.EditProduct(product.id)) }, modifier = Modifier.weight(1f)) {
                        Text("编辑食材")
                    }
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
            } else if (product.isAvailable && product.displayStatus() != "暂停供应") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { if (selectedQty > product.minQty) onQtyChange(selectedQty - product.stepQty) }) {
                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${selectedQty.clean()} ${product.unit}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onQtyChange(selectedQty + product.stepQty) }) {
                        Icon(Icons.Default.Add, contentDescription = "增加")
                    }
                    Button(onClick = {
                        viewModel.addToCart(product.id, selectedQty)
                        viewModel.alertMessage = "已加入需求清单"
                    }) { Text("加入清单") }
                }
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
            Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = { viewModel.saveIngredient(form) { viewModel.navigateBack() } },
                    enabled = !viewModel.isSavingIngredient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (productId == null) "保存食材" else "保存修改", fontWeight = FontWeight.Bold)
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
                SectionCard("基本信息") {
                    FormInput("食材名称", form.name, { form = form.copy(name = it) }, viewModel.ingredientErrors["name"])
                    DropDownInput("食材分类", form.category, EditableCategories) { form = form.copy(category = it) }
                    FormInput("食材编码", form.code, { form = form.copy(code = it) }, null)
                    DropDownInput("供应状态", form.status, EditableStatuses) { form = form.copy(status = it) }
                    SwitchRow("是否上架", form.isAvailable) { form = form.copy(isAvailable = it) }
                    FormInput("产地", form.origin, { form = form.copy(origin = it) }, null)
                }
            }
            item {
                SectionCard("规格与单位") {
                    FormInput("规格描述", form.spec, { form = form.copy(spec = it) }, viewModel.ingredientErrors["spec"])
                    DropDownInput("计量单位", form.unit, Units) { form = form.copy(unit = it) }
                    DecimalInput("最小申领量", form.minOrderQuantity, { form = form.copy(minOrderQuantity = it) }, viewModel.ingredientErrors["minOrderQuantity"])
                    DecimalInput("数量增减步长", form.quantityStep, { form = form.copy(quantityStep = it) }, viewModel.ingredientErrors["quantityStep"])
                    FormInput("包装规格", form.packagingSpec, { form = form.copy(packagingSpec = it) }, null)
                }
            }
            item {
                SectionCard("库存信息") {
                    DecimalInput("当前库存", form.stockQuantity, { form = form.copy(stockQuantity = it) }, viewModel.ingredientErrors["stockQuantity"])
                    DecimalInput("库存预警值", form.warningQuantity, { form = form.copy(warningQuantity = it) }, viewModel.ingredientErrors["warningQuantity"])
                    DecimalInput("今日可供数量", form.availableQuantity, { form = form.copy(availableQuantity = it) }, viewModel.ingredientErrors["availableQuantity"])
                }
            }
            item {
                SectionCard("其他信息") {
                    DecimalInput("内部参考价", form.internalPrice, { form = form.copy(internalPrice = it) }, viewModel.ingredientErrors["internalPrice"])
                    SwitchRow("是否允许缺货替代", form.allowSubstitute) { form = form.copy(allowSubstitute = it) }
                    FormInput("替代食材", form.substituteId, { form = form.copy(substituteId = it) }, null)
                    DropDownInput("存储方式", form.storageMethod, StorageMethods) { form = form.copy(storageMethod = it) }
                    FormInput("保质期说明", form.shelfLife, { form = form.copy(shelfLife = it) }, null)
                    FormInput("备注", form.remark, { form = form.copy(remark = it) }, null, singleLine = false)
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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
                                Text("${product.category} · ${product.spec}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("删除时间：${product.updatedAt.toTimeText()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(16.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model.isBlank()) {
            Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = model, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "库存紧张" -> Color(0xFFE69532)
        "暂停供应", "已下架" -> MaterialTheme.colorScheme.error
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
    status.isNotBlank() -> status
    stockStatus == "紧张" -> "库存紧张"
    else -> "正常供应"
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.getDefault(), "%.1f", this)
private fun Long.toTimeText(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
private fun roleLabel(role: String): String = when (role) {
    "admin" -> "系统管理员"
    "warehouse" -> "后勤管理"
    else -> "普通员工"
}
