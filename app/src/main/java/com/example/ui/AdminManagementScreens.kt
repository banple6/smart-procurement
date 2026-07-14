package com.smartprocurement.internal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.quantity.QuantityFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun exportDateText(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

@Composable
fun UnitManagementScreen(viewModel: SupplyViewModel) {
    var editingId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var point by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshUnits() }

    AdminListScreen(
        title = "子单位管理",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshUnits() }
    ) {
        item {
            AdminFormCard {
                Text(if (editingId.isBlank()) "新增子单位" else "编辑子单位", fontWeight = FontWeight.Bold)
                SimpleTextField("单位编码", code) { code = it }
                SimpleTextField("单位名称", name) { name = it }
                SimpleTextField("默认配送点", point) { point = it }
                Button(
                    onClick = {
                        viewModel.saveUnit(editingId, code, name, point)
                        editingId = ""
                        code = ""
                        name = ""
                        point = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("保存") }
            }
        }
        items(viewModel.adminUnits, key = { it.id }) { unit ->
            AdminFormCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(unit.unitName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("编码：${unit.unitCode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("配送点：${unit.defaultDeliveryPoint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("账号数量：${unit.accountCount} · 订单数量：${unit.orderCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("最近下单：${unit.lastOrderAt.ifBlank { "暂无" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (unit.active) "启用" else "停用", color = if (unit.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            editingId = unit.id
                            code = unit.unitCode
                            name = unit.unitName
                            point = unit.defaultDeliveryPoint
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("编辑") }
                    Button(
                        onClick = { viewModel.setUnitStatus(unit.id, !unit.active) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (unit.active) "停用" else "启用") }
                }
            }
        }
    }
}

@Composable
fun AccountManagementScreen(viewModel: SupplyViewModel) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var unitId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetPasswordFor by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.refreshUnits()
        viewModel.refreshUsers()
    }

    AdminListScreen(
        title = "账号管理",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshUsers() }
    ) {
        item {
            AdminFormCard {
                Text("创建账号", fontWeight = FontWeight.Bold)
                SimpleTextField("登录账号", username) { username = it }
                SimpleTextField("显示名称", displayName) { displayName = it }
                UnitSelector(viewModel.adminUnits, unitId) { unitId = it }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("初始密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.createUnitUser(username, displayName, unitId, password)
                        username = ""
                        displayName = ""
                        unitId = ""
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("创建账号") }
            }
        }
        items(viewModel.adminUsers, key = { it.id }) { user ->
            AdminFormCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("账号：${user.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("所属单位：${user.unitName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("最近登录：${user.lastLoginAt.ifBlank { "暂无" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (user.active) "启用" else "停用", color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.setUserStatus(user.id, !user.active) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (user.active) "停用" else "启用") }
                    OutlinedButton(
                        onClick = {
                            resetPasswordFor = user.id
                            resetPassword = ""
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("重置密码") }
                }
                if (resetPasswordFor == user.id) {
                    OutlinedTextField(
                        value = resetPassword,
                        onValueChange = { resetPassword = it },
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.resetUserPassword(user.id, resetPassword)
                            resetPasswordFor = ""
                            resetPassword = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("确认重置") }
                }
            }
        }
    }
}

@Composable
fun LedgerScreen(viewModel: SupplyViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportLedger(it) }
    }
    LaunchedEffect(Unit) { viewModel.refreshLedger() }
    val orderTotals = viewModel.ledgerRows.groupBy { it.orderNo }.mapValues { entry -> entry.value.maxOf { it.totalCents } }
    val totalAmount = orderTotals.values.sum()
    val productSummaries = viewModel.ledgerRows
        .groupBy { Triple(it.productName, it.productSpec, it.productUnit) }
        .map { (key, rows) ->
            ProductLedgerSummary(
                name = key.first,
                spec = key.second,
                unit = key.third,
                quantity = rows.sumOf { it.quantity.toDoubleOrNull() ?: 0.0 },
                orderCount = rows.map { it.orderNo }.distinct().size
            )
        }

    AdminListScreen(
        title = "采购台账",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshLedger() }
    ) {
        item {
            AdminFormCard {
                Text("订单数：${orderTotals.size}", fontWeight = FontWeight.Bold)
                Text("总金额：${Money.formatCents(totalAmount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("订单台账") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("商品汇总") })
                }
                Button(
                    onClick = { createDocument.launch("三公鲜配_采购台账_${exportDateText()}.xlsx") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("导出 Excel") }
            }
        }
        if (selectedTab == 0) {
            items(viewModel.ledgerRows) { row ->
                AdminFormCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.orderNo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(row.unitName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("下单时间：${row.createdAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${row.productName} ${row.productSpec}  ${row.quantity} ${row.productUnit}", fontSize = 13.sp)
                    Text("小计 ${Money.formatCents(row.subtotalCents)} / 订单合计 ${Money.formatCents(row.totalCents)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(productSummaries) { item ->
                AdminFormCard {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text("规格：${item.spec.ifBlank { "未填写" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("总订购数量：${item.quantity.cleanSummaryQty()} ${item.unit}", fontSize = 13.sp)
                    Text("订单数：${item.orderCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class ProductLedgerSummary(
    val name: String,
    val spec: String,
    val unit: String,
    val quantity: Double,
    val orderCount: Int
)

@Composable
fun InventoryRecordsScreen(viewModel: SupplyViewModel) {
    val products by viewModel.allProducts.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshProducts() }
    AdminListScreen(
        title = "库存记录",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshProducts() }
    ) {
        item {
            Text("库存流水和价格记录已由服务端记录。此页显示当前库存、预占库存和价格。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(products, key = { it.id }) { product ->
            AdminFormCard {
                Text(product.name, fontWeight = FontWeight.Bold)
                Text("价格：${Money.formatYuan(product.price)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("总库存：${QuantityFormatter.format(product.stockQuantity)} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("预占库存：${QuantityFormatter.format(product.reservedQuantity)} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("可用库存：${QuantityFormatter.format(product.availableQuantity.ifBlank { product.stockQuantity })} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PreparationSummaryScreen(viewModel: SupplyViewModel) {
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportPreparationSummary(it) }
    }
    LaunchedEffect(Unit) { viewModel.refreshPreparationSummary() }
    AdminListScreen(
        title = "今日备货单",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshPreparationSummary() }
    ) {
        item {
            AdminFormCard {
                Text("按商品汇总", fontWeight = FontWeight.Bold)
                Text("用于备货称重和拣货，数量来自真实订单。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { createDocument.launch("三公鲜配_今日备货单_${exportDateText()}.xlsx") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("导出 Excel") }
            }
        }
        items(viewModel.preparationSummaryItems) { item ->
            AdminFormCard {
                Text(item.productName, fontWeight = FontWeight.Bold)
                Text("${item.spec} · ${item.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("申领：${item.requestedQuantity} ${item.unit}", fontSize = 13.sp)
                Text("备货：${item.actualQuantity} ${item.unit}", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("单位数：${item.unitCount} · 订单数：${item.orderCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DeliverySheetsScreen(viewModel: SupplyViewModel) {
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportDeliverySheets(it) }
    }
    LaunchedEffect(Unit) { viewModel.refreshDeliverySheets() }
    AdminListScreen(
        title = "单位配送单",
        onBack = { viewModel.navigateBack() },
        onRefresh = { viewModel.refreshDeliverySheets() }
    ) {
        item {
            AdminFormCard {
                Text("按单位查看", fontWeight = FontWeight.Bold)
                Text("用于发货前核对每个单位的订单和配送点。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { createDocument.launch("三公鲜配_配送单_${exportDateText()}.xlsx") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("导出 Excel") }
            }
        }
        items(viewModel.deliverySheetUnits) { unit ->
            AdminFormCard {
                Text(unit.unitName, fontWeight = FontWeight.Bold)
                Text("配送点：${unit.deliveryPoint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("订单数：${unit.orderCount} · 明细数：${unit.itemCount}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminListScreen(title: String, onBack: () -> Unit, onRefresh: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun AdminFormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SimpleTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSelector(units: List<com.smartprocurement.internal.data.RemoteUnit>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = units.find { it.id == selectedId }?.unitName.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("所属单位") },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.filter { it.active }.forEach { unit ->
                DropdownMenuItem(text = { Text(unit.unitName) }, onClick = {
                    onSelected(unit.id)
                    expanded = false
                })
            }
        }
    }
}

private fun Double.cleanSummaryQty(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format("%.2f", this).trimEnd('0').trimEnd('.')
