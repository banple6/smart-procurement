package com.smartprocurement.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.BatchProductSummary
import com.smartprocurement.internal.data.BatchUnitSummary
import com.smartprocurement.internal.data.DeliveryBatch
import com.smartprocurement.internal.data.DeliveryBatchOrder
import com.smartprocurement.internal.domain.money.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeliveryBatchesScreen(viewModel: SupplyViewModel, preselectOrderId: String? = null) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedOrderIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(preselectOrderId) {
        viewModel.refreshDeliveryBatches { eligibleOrders ->
            val result = resolveDeliveryBatchPreselection(
                preselectOrderId = preselectOrderId,
                eligibleOrderIds = eligibleOrders.map { it.id },
                currentSelection = selectedOrderIds
            )
            selectedOrderIds = result.selectedOrderIds
            if (result.missingPreselectedOrder) {
                viewModel.snackbarMessage = "订单已接单，但当前无法加入新备货单，请刷新后检查订单状态。"
            }
        }
    }
    LaunchedEffect(viewModel.eligibleBatchOrders.map { it.id }) {
        selectedOrderIds = selectedOrderIds.intersect(viewModel.eligibleBatchOrders.mapTo(mutableSetOf()) { it.id })
    }

    Scaffold(
        topBar = {
            BatchTopBar("备货单", viewModel::navigateBack) { viewModel.refreshDeliveryBatches() }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                BatchPanel {
                    Text("建立本次备货范围", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "备货单只包含本次明确选择的订单，不会把其他日期或配送周期的订单自动混入。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("备货单名称（可选）") },
                        placeholder = { Text("例如：8月21日下午备货") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择已接单或备货中的订单", fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        selectedOrderIds = if (selectedOrderIds.size == viewModel.eligibleBatchOrders.size) {
                            emptySet()
                        } else {
                            viewModel.eligibleBatchOrders.mapTo(mutableSetOf()) { it.id }
                        }
                    }) {
                        Text(if (selectedOrderIds.size == viewModel.eligibleBatchOrders.size && selectedOrderIds.isNotEmpty()) "取消全选" else "全选")
                    }
                }
            }

            if (viewModel.eligibleBatchOrders.isEmpty()) {
                item { BatchEmpty("暂无可加入备货单的订单。订单接单后会直接进入备货状态并显示在这里。") }
            } else {
                items(viewModel.eligibleBatchOrders, key = { it.id }) { order ->
                    EligibleOrderRow(
                        order = order,
                        selected = order.id in selectedOrderIds,
                        onToggle = {
                            selectedOrderIds = if (order.id in selectedOrderIds) {
                                selectedOrderIds - order.id
                            } else {
                                selectedOrderIds + order.id
                            }
                        }
                    )
                }
                item {
                    Button(
                        onClick = {
                            viewModel.createDeliveryBatch(name, note, selectedOrderIds.toList()) {
                                name = ""
                                note = ""
                                selectedOrderIds = emptySet()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = selectedOrderIds.isNotEmpty() && !viewModel.isDeliveryBatchLoading
                    ) {
                        Text(if (viewModel.isDeliveryBatchLoading) "正在生成" else "生成备货单（${selectedOrderIds.size} 笔订单）")
                    }
                }
            }

            item {
                Text("备货单记录", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            if (viewModel.deliveryBatches.isEmpty()) {
                item { BatchEmpty("暂无备货单") }
            } else {
                items(viewModel.deliveryBatches, key = { it.id }) { batch ->
                    DeliveryBatchRow(batch = batch, onClick = { viewModel.openDeliveryBatch(batch.id) })
                }
            }
        }
    }
}

@Composable
fun DeliveryBatchDetailScreen(
    batchId: String,
    viewModel: SupplyViewModel,
    requestWorkbookDocument: WorkbookDocumentRequest
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    val batch = viewModel.activeDeliveryBatch?.takeIf { it.id == batchId }
    val summary = viewModel.activeDeliveryBatchSummary?.takeIf { it.batch.id == batchId }

    LaunchedEffect(batchId) { viewModel.refreshDeliveryBatch(batchId) }

    Scaffold(
        topBar = {
            BatchTopBar("备货单详情", viewModel::navigateBack) { viewModel.refreshDeliveryBatch(batchId) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (batch == null || summary == null) {
                item { BatchEmpty(if (viewModel.isDeliveryBatchLoading) "正在加载备货单汇总" else "备货单数据暂不可用") }
            } else {
                item {
                    BatchPanel {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(batch.name.ifBlank { batch.batchNo }, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(batch.batchNo, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(batchStatusLabel(batch.status), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text("${summary.orderCount} 笔订单 · ${summary.unitCount} 个单位 · ${summary.productCount} 种食材")
                        Text("备货金额：${Money.formatCents(summary.totalCents)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (batch.note.isNotBlank()) Text("备注：${batch.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedButton(
                            onClick = {
                                requestWorkbookDocument(
                                    ExternalActionType.BATCH_SUMMARY_EXPORT,
                                    batchId,
                                    batchFileName("批次汇总", batch.batchNo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            enabled = !viewModel.isDocumentExportBusy(ExternalActionType.BATCH_SUMMARY_EXPORT)
                        ) { Text(if (viewModel.isDocumentExportBusy(ExternalActionType.BATCH_SUMMARY_EXPORT)) "正在保存…" else "导出汇总表") }

                        val canPick = batch.orders.any { it.status == "已接单" || it.status == "备货中" }
                        Button(
                            onClick = {
                                requestWorkbookDocument(
                                    ExternalActionType.BATCH_PICKING_EXPORT,
                                    batchId,
                                    batchFileName("备货单", batch.batchNo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            enabled = canPick && !viewModel.isDocumentExportBusy(ExternalActionType.BATCH_PICKING_EXPORT)
                        ) { Text(if (viewModel.isDocumentExportBusy(ExternalActionType.BATCH_PICKING_EXPORT)) "正在保存…" else "导出备货单") }
                        if (!canPick) Text("当前没有已接单或备货中的订单，暂不能生成备货单。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (batch.status == "open") {
                            OutlinedButton(
                                onClick = { showCloseConfirm = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("完成备货") }
                        }
                    }
                }

                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("按单位") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("按食材") })
                    }
                }

                if (selectedTab == 0) {
                    if (summary.byUnit.isEmpty()) item { BatchEmpty("该备货单暂无可汇总订单") }
                    items(summary.byUnit, key = { it.unitId }) { unit -> BatchUnitCard(unit) }
                } else {
                    if (summary.byProduct.isEmpty()) item { BatchEmpty("该备货单暂无可汇总食材") }
                    items(summary.byProduct, key = { "${it.productId}:${it.unit}" }) { product -> BatchProductCard(product) }
                }

                item {
                    Text("备货单订单", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
                items(batch.orders, key = { it.id }) { order ->
                    DeliveryBatchOrderRow(order) { viewModel.navigateTo(Screen.OrderDetails(order.id)) }
                }
            }
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("确认完成备货？") },
            text = { Text("完成后不能再调整备货单中的订单，可在 Web 管理端按单位生成出库单；此操作不会自动发货。") },
            dismissButton = { TextButton(onClick = { showCloseConfirm = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    viewModel.closeDeliveryBatch(batchId)
                }) { Text("完成备货") }
            }
        )
    }
}

@Composable
private fun BatchUnitCard(unit: BatchUnitSummary) {
    var expanded by remember(unit.unitId) { mutableStateOf(true) }
    BatchPanel(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(unit.unitName, fontWeight = FontWeight.Bold)
                Text("${unit.orderCount} 笔订单 · ${unit.deliveryPoint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "收起" else "展开")
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                unit.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.productName} · ${item.spec}", modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${item.actualQuantity} ${item.unit}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchProductCard(product: BatchProductSummary) {
    var expanded by remember(product.productId, product.unit) { mutableStateOf(false) }
    BatchPanel(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.productName, fontWeight = FontWeight.Bold)
                Text("${product.category} · ${product.spec}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${product.actualQuantity} ${product.unit}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${product.unitCount} 个单位", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "收起" else "展开")
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                product.unitBreakdown.forEach { breakdown ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(breakdown.unitName, modifier = Modifier.weight(1f))
                        Text("${breakdown.actualQuantity} ${product.unit}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EligibleOrderRow(order: DeliveryBatchOrder, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(order.orderNo, fontWeight = FontWeight.Bold)
                Text("${order.unitName} · ${order.deliveryPoint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${order.status} · ${Money.formatCents(order.totalCents)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DeliveryBatchRow(batch: DeliveryBatch, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(batch.name.ifBlank { batch.batchNo }, fontWeight = FontWeight.Bold)
                Text(batch.batchNo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${batch.orderCount} 笔订单 · ${batch.unitCount} 个单位 · ${batch.createdAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(batchStatusLabel(batch.status), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeliveryBatchOrderRow(order: DeliveryBatchOrder, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(order.orderNo, fontWeight = FontWeight.Bold)
                Text("${order.unitName} · ${order.deliveryPoint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(order.status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(Money.formatCents(order.totalCents), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BatchTopBar(title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 8.dp),
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

@Composable
private fun BatchPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun BatchEmpty(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Text(message, modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun batchStatusLabel(status: String): String = when (status) {
    "open" -> "进行中"
    "closed" -> "已完成备货"
    "cancelled" -> "已取消"
    else -> "未知状态"
}

private fun batchFileName(type: String, batchNo: String): String =
    "三公鲜配_${type}_${batchNo}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.xlsx"
