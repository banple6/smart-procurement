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
import com.smartprocurement.internal.ui.components.OrderStatusRail
import com.smartprocurement.internal.ui.components.StatusStep
import com.smartprocurement.internal.ui.components.StepState
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

// --- CART SCREEN ---
@Composable
fun CartScreen(viewModel: SupplyViewModel) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
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
            PoliceBrandHeader(
                title = "采购清单",
                subtitle = "${viewModel.currentUnitName} · ${viewModel.defaultDeliveryPoint}",
            )
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
                    JrxpPrimaryButton(
                        text = "去挑选食材",
                        onClick = { viewModel.currentTab = "home" },
                        modifier = Modifier.width(200.dp)
                    )
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
                        DocumentSection(title = "结算摘要") {
                            DetailRow("商品种类", "${rows.size} 种")
                            DetailRow("订单金额", Money.formatCents(totalCents))
                        }
                    }
                    items(rows, key = { it.first.productId }) { (item, p) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.spec, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.deleteCartItem(p.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = JrxpTheme.colors.criticalRed)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("单价", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    Text(Money.formatYuan(p.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("小计", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    Text(Money.formatCents(lineSubtotalCents(p.price, item.quantity)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QuantityStepper(
                                        value = item.quantity,
                                        unit = p.unit,
                                        step = p.stepQty,
                                        onValueChange = { newVal ->
                                            if (newVal == 0.0) viewModel.deleteCartItem(p.id)
                                            else viewModel.updateCartQty(p.id, newVal)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        DocumentSection(title = "配送信息") {
                            DetailRow("默认配送点", viewModel.defaultDeliveryPoint.ifBlank { "未设置" })
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth().padding(top = JrxpDimensions.spacingMd),
                                label = { Text("备注（可选）") },
                                minLines = 2
                            )
                        }
                    }
                }
                PrimaryActionDock {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = JrxpDimensions.spacingMd), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("订单合计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(Money.formatCents(totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    JrxpPrimaryButton(
                        text = "提交订单",
                        onClick = { showConfirm = true },
                        enabled = !viewModel.isSubmittingOrder,
                        isLoading = viewModel.isSubmittingOrder
                    )
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
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
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
            val subtitle = if (isAdmin) "系统管理员 · ${viewModel.userName}" else "${viewModel.currentUnitName} · ${viewModel.defaultDeliveryPoint}"
            PoliceBrandHeader(title = "订单记录", subtitle = subtitle)
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
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(listOf("全部", "待接单", "已接单", "备货中", "已发货", "已完成", "已取消"), key = { it }) { status ->
                                    val isSelected = selectedStatus == status
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
                                        onClick = { selectedStatus = status },
                                        label = {
                                            Text(
                                                text = status,
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
                    items(visibleOrders, key = { it.orderId }) { order ->
                        val orderTitle = if (isAdmin) order.department.ifBlank { "未命名单位" } else order.displayOrderNo
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    viewModel.navigateTo(Screen.OrderDetails(order.orderId))
                                }
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
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = orderTitle,
                                        style = JrxpTypography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = if (isAdmin) 2 else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = order.status,
                                        label = "order_status"
                                    ) { status ->
                                        SupplyStatusMark(
                                            label = status,
                                            type = when (status) {
                                                "待接单" -> StatusType.PENDING
                                                "备货中" -> StatusType.ACTIVE
                                                "已发货", "已接单", "已完成" -> StatusType.SUCCESS
                                                "已取消" -> StatusType.CANCELLED
                                                else -> StatusType.CANCELLED
                                            }
                                        )
                                    }
                                }

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                                    Text(order.displayOrderNo, style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${order.itemCount} 种商品 · ${Money.formatCents(order.totalCents)}", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }

                                Spacer(modifier = Modifier.height(JrxpDimensions.spacingMd))

                                // Generic DetailRow inline for Order List
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("下单时间", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 64.dp))
                                    Text(order.submitTime, style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(top = JrxpDimensions.spacingXs), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("配送点", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 64.dp))
                                    Text(order.deliveryPoint, style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }

                                if (order.status == "备货中") {
                                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                                    Text("订单正在备货", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (order.shippingPhotoCount > 0) {
                                    Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                                    Text("发货凭证：${order.shippingPhotoCount} 张", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(JrxpDimensions.spacingMd))
                                OrderActionButton(order = order, viewModel = viewModel)
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
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
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
                            color = dividerColor,
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

                DocumentSection(title = "订单轨迹", subtitle = ord.displayOrderNo) {
                    OrderTimeline(ord)
                }

                DocumentSection(title = "订单信息") {
                    DetailRow(label = "订单编号", value = ord.displayOrderNo)
                    DetailRow(label = "下单单位", value = ord.department)
                    if (ord.requesterName.isNotBlank()) DetailRow(label = "下单账号", value = ord.requesterName)
                    DetailRow(label = "配送点", value = ord.deliveryPoint)
                    DetailRow(label = "下单时间", value = ord.createdAt.ifBlank { ord.submitTime })
                    DetailRow(label = "订单金额", value = Money.formatCents(ord.totalCents.takeIf { it > 0 } ?: orderItems.sumOf { lineSubtotalCents(it.price, it.requestedQty) }))
                    DetailRow(label = "订单状态", value = ord.status)
                    DetailRow(label = "订单备注", value = ord.remarks.ifBlank { "无备注" })
                }

                DocumentSection(title = "商品明细", subtitle = "共 ${orderItems.size} 种") {
                    orderItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = JrxpDimensions.spacingMd)
                                .drawBehind {
                                    drawLine(
                                        color = dividerColor,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1f
                                    )
                                },
                            horizontalArrangement = Arrangement.spacedBy(JrxpDimensions.spacingMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.productImageUrl,
                                contentDescription = item.productName,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(item.productSpec, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("单价：${Money.formatYuan(item.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("x${item.requestedQty.cleanQty()} ${item.productUnit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                Text("小计：${Money.formatCents(lineSubtotalCents(item.price, item.requestedQty))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
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
    OrderStatusRail(steps = orderTimelineNodes(order))
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
    JrxpSecondaryButton(
        text = if (loading) "正在提交" else label,
        onClick = {
            if (label == "确认发货") {
                viewModel.navigateTo(Screen.ShippingProof(order.orderId))
            } else {
                showConfirm = true
            }
        },
        modifier = modifier
    )
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



private fun lineSubtotalCents(priceYuan: Double, quantity: Double): Long {
    return BigDecimal.valueOf(priceYuan)
        .multiply(BigDecimal.valueOf(quantity))
        .multiply(BigDecimal(100))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}

private fun Double.cleanQty(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.getDefault(), "%.2f", this).trimEnd('0').trimEnd('.')
