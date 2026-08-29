package com.smartprocurement.internal.ui

import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.ui.platform.LocalConfiguration
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val cartList by viewModel.cartItems.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    var note by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val rows = cartList.mapNotNull { item ->
        products.find { it.id == item.productId }?.let { product -> item to product }
    }
    val totalCents = rows.sumOf { (item, product) -> lineSubtotalCents(product.price, item.quantity) }
    val quota = viewModel.unitQuota
    val quotaExceeded = quota.enabled && totalCents > quota.availableCents

    Scaffold(
        topBar = {
            PoliceBrandHeader(
                title = "采购清单",
                subtitle = "${viewModel.currentUnitName} · ${viewModel.defaultDeliveryPoint}",
                compact = isLandscape,
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
                    Text("请前往首页挑选所需食材。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.spec, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isLandscape) {
                                        QuantityStepper(
                                            value = item.quantity,
                                            unit = p.unit,
                                            minValue = p.minQty,
                                            step = p.stepQty,
                                            onValueChange = { newVal ->
                                                if (newVal == 0.0) viewModel.deleteCartItem(p.id)
                                                else viewModel.updateCartQty(p.id, newVal)
                                            }
                                        )
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
                                if (!isLandscape) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        QuantityStepper(
                                            value = item.quantity,
                                            unit = p.unit,
                                            minValue = p.minQty,
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
                    }
                    item {
                        DocumentSection(title = "结算摘要") {
                            DetailRow("食材种类", "${rows.size} 种")
                            DetailRow("订单金额", Money.formatCents(totalCents))
                            if (quota.enabled) {
                                DetailRow("当前可用额度", Money.formatCents(quota.availableCents))
                                DetailRow("提交后预计余额", Money.formatCents((quota.availableCents - totalCents).coerceAtLeast(0)))
                                if (quotaExceeded) Text("本单超出可用额度 ${Money.formatCents(totalCents - quota.availableCents)}", color = JrxpTheme.colors.criticalRed, style = MaterialTheme.typography.bodySmall)
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
                PrimaryActionDock(compact = isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isLandscape) 0.dp else JrxpDimensions.spacingMd),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("订单合计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(Money.formatCents(totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    JrxpPrimaryButton(
                        text = "提交订单",
                        onClick = { showConfirm = true },
                        enabled = !viewModel.isSubmittingOrder && !quotaExceeded,
                        isLoading = viewModel.isSubmittingOrder
                    )
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认提交此采购单？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("共 ${rows.size} 种食材")
                    Text("合计 ${Money.formatCents(totalCents)}")
                    if (quota.enabled) Text("当前可用额度 ${Money.formatCents(quota.availableCents)}")
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

// --- ORDER LIST SCREEN ---
@Composable
fun OrderListScreen(viewModel: SupplyViewModel) {
    val orders by viewModel.allOrders.collectAsState()
    val isAdmin = viewModel.canManageIngredients()
    var selectedStatus by remember(viewModel.orderListPresetStatus) { mutableStateOf(viewModel.orderListPresetStatus) }
    var orderQuery by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var appliedStatus by remember(viewModel.orderListPresetStatus) { mutableStateOf(viewModel.orderListPresetStatus) }
    var appliedQuery by remember { mutableStateOf("") }
    var appliedDateFrom by remember { mutableStateOf("") }
    var appliedDateTo by remember { mutableStateOf("") }
    var filterError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val loadedOrders = remember(orders, viewModel.orderListOrderIds) {
        val byId = orders.associateBy { it.orderId }
        viewModel.orderListOrderIds.mapNotNull(byId::get)
    }
    val groupedOrders = remember(loadedOrders) { loadedOrders.groupBy(::orderMonthKey) }
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(viewModel.userId, viewModel.userRole, viewModel.orderListPresetStatus) {
        viewModel.loadOrderList(status = viewModel.orderListPresetStatus, reset = true)
    }
    LaunchedEffect(groupedOrders.keys.toList()) {
        groupedOrders.keys.forEachIndexed { index, month ->
            if (month !in expandedMonths) expandedMonths[month] = index == 0
        }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = orderQuery,
                            onValueChange = { orderQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isAdmin) "订单编号或单位" else "订单编号") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listOf("全部", "待接单", "已接单", "备货中", "已发货", "已完成", "已取消", "已作废"), key = { it }) { status ->
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = { selectedStatus = status },
                                    label = { Text(status, maxLines = 1) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showOrderDatePicker(context, dateFrom) { dateFrom = it } },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) { Text(dateFrom.ifBlank { "开始日期" }, maxLines = 1) }
                            OutlinedButton(
                                onClick = { showOrderDatePicker(context, dateTo) { dateTo = it } },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) { Text(dateTo.ifBlank { "结束日期" }, maxLines = 1) }
                        }
                        if (filterError.isNotBlank()) {
                            Text(filterError, color = MaterialTheme.colorScheme.error, style = JrxpTypography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    selectedStatus = "全部"
                                    orderQuery = ""
                                    dateFrom = ""
                                    dateTo = ""
                                    appliedStatus = "全部"
                                    appliedQuery = ""
                                    appliedDateFrom = ""
                                    appliedDateTo = ""
                                    filterError = ""
                                    viewModel.loadOrderList(reset = true)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) { Text("清除筛选") }
                            Button(
                                onClick = {
                                    if (dateFrom.isNotBlank() && dateTo.isNotBlank() && dateFrom > dateTo) {
                                        filterError = "开始日期不能晚于结束日期"
                                    } else {
                                        filterError = ""
                                        appliedStatus = selectedStatus
                                        appliedQuery = orderQuery.trim()
                                        appliedDateFrom = dateFrom
                                        appliedDateTo = dateTo
                                        viewModel.loadOrderList(appliedStatus, appliedDateFrom, appliedDateTo, appliedQuery, reset = true)
                                    }
                                },
                                enabled = !viewModel.isOrderListLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) { Text("筛选订单") }
                        }
                        Text(
                            "共 ${viewModel.orderListTotal} 笔，已显示 ${loadedOrders.size} 笔",
                            style = JrxpTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (loadedOrders.isEmpty() && !viewModel.isOrderListLoading) {
                    item {
                        Text(
                            "没有符合条件的订单",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                groupedOrders.forEach { (month, monthOrders) ->
                    item(key = "month-$month") {
                        val expanded = expandedMonths[month] == true
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { expandedMonths[month] = !expanded },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(orderMonthLabel(month), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${monthOrders.size} 笔订单", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "收起本月订单" else "展开本月订单"
                                )
                            }
                        }
                    }
                    if (expandedMonths[month] == true) {
                        items(monthOrders, key = { it.orderId }) { order ->
                            OrderListRow(order = order, isAdmin = isAdmin, viewModel = viewModel)
                        }
                    }
                }

                if (viewModel.orderListHasMore) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.loadOrderList(appliedStatus, appliedDateFrom, appliedDateTo, appliedQuery, reset = false) },
                            enabled = !viewModel.isOrderListLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text(if (viewModel.isOrderListLoading) "加载中" else "加载更多订单") }
                    }
                } else if (viewModel.isOrderListLoading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun OrderListRow(order: OrderEntity, isAdmin: Boolean, viewModel: SupplyViewModel) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val orderTitle = if (isAdmin) order.department.ifBlank { "未命名单位" } else order.displayOrderNo
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { viewModel.navigateTo(Screen.OrderDetails(order.orderId)) }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(orderTitle, style = JrxpTypography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = if (isAdmin) 2 else 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.width(8.dp))
                SupplyStatusMark(
                    label = order.status,
                    type = when (order.status) {
                        "待接单" -> StatusType.PENDING
                        "备货中" -> StatusType.ACTIVE
                        "已发货", "已接单", "已完成" -> StatusType.SUCCESS
                        else -> StatusType.CANCELLED
                    }
                )
            }
            if (isAdmin) {
                Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                Text(order.displayOrderNo, style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${order.itemCount} 种食材 · ${Money.formatCents(order.totalCents)}", style = JrxpTypography.bodySmall, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(JrxpDimensions.spacingMd))
            DetailRow("下单时间", order.submitTime)
            DetailRow("配送点", order.deliveryPoint)
            if (order.status == "备货中") Text("订单正在备货", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (order.shippingPhotoCount > 0) Text("发货凭证：${order.shippingPhotoCount} 张", style = JrxpTypography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(JrxpDimensions.spacingMd))
            OrderActionButton(order = order, viewModel = viewModel)
        }
    }
}

private fun orderMonthKey(order: OrderEntity): String =
    order.createdAt.ifBlank { order.submitTime }.take(7).takeIf { it.matches(Regex("\\d{4}-\\d{2}")) } ?: "unknown"

private fun orderMonthLabel(month: String): String = if (month == "unknown") {
    "时间未记录"
} else {
    val parts = month.split("-")
    "${parts[0]}年${parts[1].toIntOrNull() ?: parts[1]}月"
}

private fun showOrderDatePicker(context: Context, initial: String, onSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(initial) }
        .getOrNull()
        ?.let(calendar::setTime)
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(String.format(Locale.CHINA, "%04d-%02d-%02d", year, month + 1, day)) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
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

                DocumentSection(title = "食材明细", subtitle = "共 ${orderItems.size} 种") {
                    orderItems.forEach { item ->
                        Column(
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
                        ) {
                            Column {
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
    var cancelReason by remember(order.orderId) { mutableStateOf("数量填写错误") }
    val targetStatus = when (label) {
        "接单" -> "已接单"
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
                if (viewModel.canManageIngredients()) {
                    viewModel.alertMessage = "请通过出库单确认发货。完成备货后先按单位生成出库单，再在出库单详情上传发货照片。"
                    viewModel.navigateTo(Screen.Outbounds)
                } else {
                    viewModel.navigateTo(Screen.ShippingProof(order.orderId))
                }
            } else {
                showConfirm = true
            }
        },
        modifier = modifier,
        enabled = !loading
    )
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认将订单状态更改为“$targetStatus”？") },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
            text = {
                if (label == "取消订单") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("订单取消后，相关库存将自动释放。")
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("取消原因") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (label == "取消订单" && cancelReason.isBlank()) return@TextButton
                    showConfirm = false
                    viewModel.performOrderAction(order, cancelReason)
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
