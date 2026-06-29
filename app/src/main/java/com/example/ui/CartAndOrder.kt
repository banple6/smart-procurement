package com.smartprocurement.internal.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartprocurement.internal.data.*
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

// --- CART SCREEN ---
@Composable
fun CartScreen(viewModel: SupplyViewModel) {
    val cartList by viewModel.cartItems.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    var note by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val rows = cartList.mapNotNull { item ->
        products.find { it.id == item.productId }?.let { product -> item to product }
    }
    val totalCents = rows.sumOf { (item, product) -> lineSubtotalCents(product.price, item.quantity) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            color = Color(0xFFCAC4D0),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "cart", tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "采购清单",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (cartList.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearCart() }) {
                            Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (cartList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "empty cart",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("清单空空如也", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("请前往首页挑选所需食材，并在此提交需求。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.currentTab = "home" },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("去挑选食材", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailRow("商品种类", "${rows.size} 种")
                                DetailRow("订单金额", Money.formatCents(totalCents))
                            }
                        }
                    }
                    items(rows, key = { it.first.productId }) { (item, p) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (p.imageUrl.isBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("暂无图片", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        AsyncImage(
                                            model = p.imageUrl,
                                            contentDescription = p.name,
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.spec, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.deleteCartItem(p.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("单价", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Money.formatYuan(p.price), fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("小计", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Money.formatCents(lineSubtotalCents(p.price, item.quantity)), fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.updateCartQty(p.id, item.quantity - p.stepQty) }, modifier = Modifier.size(48.dp)) {
                                        Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${item.quantity.cleanQty()} ${p.unit}", modifier = Modifier.widthIn(min = 76.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.updateCartQty(p.id, item.quantity + p.stepQty) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "增加")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailRow("默认配送点", viewModel.defaultDeliveryPoint.ifBlank { "未设置" })
                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("备注（可选）") },
                                    minLines = 2
                                )
                            }
                        }
                    }
                }
                Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("订单合计", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(Money.formatCents(totalCents), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { showConfirm = true },
                            enabled = !viewModel.isSubmittingOrder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (viewModel.isSubmittingOrder) "正在提交" else "提交订单", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认提交这份采购单吗？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("共 ${rows.size} 种食材")
                    Text("合计 ${Money.formatCents(totalCents)}")
                    Text("配送点：${viewModel.defaultDeliveryPoint.ifBlank { "未设置" }}")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.submitOrder(note)
                }) { Text("提交订单") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicTextFieldWithoutUnderline(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

// --- HISTORIC ORDER LIST SCREEN ---
@Composable
fun OrderListScreen(viewModel: SupplyViewModel) {
    val orders by viewModel.allOrders.collectAsState()
    val isAdmin = viewModel.canManageIngredients()
    var selectedStatus by remember { mutableStateOf("全部") }
    val visibleOrders = remember(orders, selectedStatus) {
        if (selectedStatus == "全部") orders else orders.filter { it.status == selectedStatus }
    }
    LaunchedEffect(viewModel.userId, viewModel.userRole) {
        viewModel.refreshOrders()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            color = Color(0xFFCAC4D0),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "orders", tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "订单记录",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.refreshOrders() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新订单", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (orders.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "No order", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无订单记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isAdmin) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("全部", "待接单", "已接单", "备货中", "已发货", "已完成", "已取消").forEach { status ->
                                    FilterChip(
                                        selected = selectedStatus == status,
                                        onClick = { selectedStatus = status },
                                        label = { Text(status) }
                                    )
                                }
                            }
                        }
                    }
                    if (visibleOrders.isEmpty()) {
                        item {
                            Text(
                                "暂无该状态订单",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(visibleOrders) { order ->
                        val orderTitle = if (isAdmin) order.department.ifBlank { "未命名单位" } else order.displayOrderNo
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.navigateTo(Screen.OrderDetails(order.orderId))
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        orderTitle,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (order.status) {
                                                    "待接单" -> Color(0xFFEFF6FF)
                                                    "备货中" -> Color(0xFFFEF3C7)
                                                    "已发货" -> Color(0xFFECFDF5)
                                                    "已接单", "已完成" -> Color(0xFFD1FAE5)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                CircleShape
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = order.status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (order.status) {
                                                "待接单" -> Color(0xFF1D4ED8)
                                                "备货中" -> Color(0xFFB45309)
                                                "已发货" -> Color(0xFF047857)
                                                "已接单", "已完成" -> Color(0xFF065F46)
                                                else -> MaterialTheme.colorScheme.outline
                                            }
                                        )
                                    }
                                }

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(order.displayOrderNo, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${order.itemCount} 种商品 · ${Money.formatCents(order.totalCents)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text(order.deliveryPoint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("下单时间", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(order.submitTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("配送点", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(order.deliveryPoint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(180.dp), textAlign = TextAlign.End)
                                }

                                if (order.status == "备货中") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("订单正在备货", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (order.shippingPhotoCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("发货凭证：${order.shippingPhotoCount} 张", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }

                                OrderActionButton(order = order, viewModel = viewModel, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ORDER DETAILS SCREEN ---
@Composable
fun OrderDetailsScreen(orderId: String, viewModel: SupplyViewModel) {
    val orderFlow = remember(orderId) { viewModel.getOrderFlow(orderId) }
    val orderItemsFlow = remember(orderId) { viewModel.getOrderItemsFlow(orderId) }

    val order by orderFlow.collectAsState(initial = null)
    val orderItems by orderItemsFlow.collectAsState(initial = emptyList())

    LaunchedEffect(orderId) {
        viewModel.refreshOrderDetail(orderId)
    }

    val ord = order ?: return

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            color = Color(0xFFCAC4D0),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "订单详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Tracker timeline progress
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("订单编号：${ord.displayOrderNo.ifBlank { "未生成订单号" }}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(ord.status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OrderTimeline(ord)
                    }
                }

                // Submitter info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("订单信息", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow(label = "订单编号", value = ord.displayOrderNo)
                        DetailRow(label = "下单单位", value = ord.department)
                        if (ord.requesterName.isNotBlank()) DetailRow(label = "下单账号", value = ord.requesterName)
                        DetailRow(label = "配送点", value = ord.deliveryPoint)
                        DetailRow(label = "下单时间", value = ord.createdAt.ifBlank { ord.submitTime })
                        DetailRow(label = "订单金额", value = Money.formatCents(ord.totalCents.takeIf { it > 0 } ?: orderItems.sumOf { lineSubtotalCents(it.price, it.requestedQty) }))
                        DetailRow(label = "订单状态", value = ord.status)
                        DetailRow(label = "订单备注", value = ord.remarks.ifBlank { "无备注" })
                    }
                }

                // Order items list
                Text("商品明细 (${orderItems.size} 种)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                orderItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.productImageUrl,
                                contentDescription = item.productName,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(item.productSpec, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Spacer(modifier = Modifier.height(4.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("单价：${Money.formatYuan(item.price)} / ${item.productUnit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("数量：${item.requestedQty.cleanQty()} ${item.productUnit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("小计：${Money.formatCents(lineSubtotalCents(item.price, item.requestedQty))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                ShippingProofSummary(order = ord, viewModel = viewModel)

                Spacer(modifier = Modifier.height(24.dp))
            }

            OrderActionButton(order = ord, viewModel = viewModel, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun ShippingProofSummary(order: OrderEntity, viewModel: SupplyViewModel) {
    if (order.shippingPhotoCount <= 0 && order.status != "已发货" && order.status != "已完成") return
    var previewIndex by remember(order.orderId, order.shippingPhotosJson) { mutableStateOf<Int?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("发货凭证", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (order.shippingPhotos.isEmpty()) {
                Text("历史订单暂无发货照片", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    order.shippingPhotos.forEachIndexed { index, photo ->
                        AsyncImage(
                            model = authenticatedImageRequest(photo.thumbnailUrl, viewModel),
                            contentDescription = "发货凭证",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { previewIndex = index },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                val first = order.shippingPhotos.first()
                Text("发货时间：${first.uploadedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("发货账号：${first.uploadedByUsername}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (order.shippingNote.isNotBlank()) {
                    Text("发货备注：${order.shippingNote}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val currentIndex = previewIndex
    if (currentIndex != null && order.shippingPhotos.isNotEmpty()) {
        val boundedIndex = currentIndex.coerceIn(0, order.shippingPhotos.lastIndex)
        Dialog(onDismissRequest = { previewIndex = null }) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        AsyncImage(
                            model = authenticatedImageRequest(order.shippingPhotos[boundedIndex].fullUrl, viewModel),
                            contentDescription = "发货凭证大图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${boundedIndex + 1}/${order.shippingPhotos.size} 张", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = boundedIndex > 0, onClick = { previewIndex = boundedIndex - 1 }) { Text("上一张") }
                            TextButton(enabled = boundedIndex < order.shippingPhotos.lastIndex, onClick = { previewIndex = boundedIndex + 1 }) { Text("下一张") }
                            TextButton(onClick = { previewIndex = null }) { Text("返回") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun authenticatedImageRequest(path: String, viewModel: SupplyViewModel): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(viewModel.absoluteApiUrl(path))
        .addHeader("Authorization", "Bearer ${viewModel.bearerToken()}")
        .crossfade(true)
        .build()
}

@Composable
private fun OrderTimeline(order: OrderEntity) {
    if (order.status == "已取消") {
        Text("订单已取消", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
    }
    LogisticsTimelineItem(title = "订单已提交", desc = order.createdAt.ifBlank { order.submitTime }, isDone = true)
    LogisticsTimelineItem(title = "管理员已接单", desc = order.acceptedAt.ifBlank { "尚未开始" }, isDone = order.acceptedAt.isNotBlank())
    LogisticsTimelineItem(title = "开始备货", desc = order.preparingAt.ifBlank { "尚未开始" }, isDone = order.preparingAt.isNotBlank())
    LogisticsTimelineItem(title = "已发货", desc = order.shippedAt.ifBlank { "尚未开始" }, isDone = order.shippedAt.isNotBlank())
    LogisticsTimelineItem(title = "已完成", desc = order.completedAt.ifBlank { "尚未开始" }, isDone = order.completedAt.isNotBlank())
}

@Composable
private fun OrderActionButton(order: OrderEntity, viewModel: SupplyViewModel, modifier: Modifier = Modifier) {
    val label = viewModel.nextOrderActionLabel(order) ?: return
    var showConfirm by remember(order.orderId, label) { mutableStateOf(false) }
    val targetStatus = when (label) {
        "接单" -> "已接单"
        "开始备货" -> "备货中"
        "确认发货" -> "已发货"
        "完成订单" -> "已完成"
        "取消订单" -> "已取消"
        "确认收货" -> "已完成"
        else -> label
    }
    val loading = viewModel.activeOrderActionId == order.orderId
    Button(
        onClick = {
            if (label == "确认发货") {
                viewModel.navigateTo(Screen.ShippingProof(order.orderId))
            } else {
                showConfirm = true
            }
        },
        enabled = !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(if (loading) "正在提交" else label, fontWeight = FontWeight.Bold)
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认将订单改为“$targetStatus”吗？") },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.performOrderAction(order)
                }) { Text(label) }
            }
        )
    }
}

@Composable
fun LogisticsTimelineItem(title: String, desc: String, isDone: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun lineSubtotalCents(priceYuan: Double, quantity: Double): Long {
    return BigDecimal.valueOf(priceYuan)
        .multiply(BigDecimal.valueOf(quantity))
        .multiply(BigDecimal(100))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}

private fun Double.cleanQty(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.getDefault(), "%.2f", this).trimEnd('0').trimEnd('.')
