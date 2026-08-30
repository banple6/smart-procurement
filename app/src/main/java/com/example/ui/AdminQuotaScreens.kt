package com.smartprocurement.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.RemoteUnit
import com.smartprocurement.internal.data.UnitQuota
import com.smartprocurement.internal.data.UnitQuotaLedgerRow
import com.smartprocurement.internal.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

internal fun quotaAmountToCents(value: String): Long? {
    val text = value.trim()
    if (!text.matches(Regex("\\d+(\\.\\d{1,2})?"))) return null
    return runCatching {
        BigDecimal(text)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }.getOrNull()
}

private fun quotaAmountText(cents: Long): String = BigDecimal(cents)
    .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitQuotaManagementScreen(viewModel: SupplyViewModel) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshAdminQuotaUnits() }
    val filtered = viewModel.adminUnits.filter { unit ->
        query.isBlank() || unit.unitName.contains(query.trim(), ignoreCase = true) ||
            unit.unitCode.contains(query.trim(), ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单位采购额度", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshAdminQuotaUnits) { Icon(Icons.Default.Refresh, "刷新") }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isAdminQuotaLoading,
            onRefresh = viewModel::refreshAdminQuotaUnits,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索单位名称或编码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "额度由服务端统一管理。启用、默认月额度和人工调整均需管理员确认。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                if (filtered.isEmpty()) {
                    item { Text("未找到单位", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(filtered, key = { it.id }) { unit ->
                    UnitQuotaListCard(unit = unit, onClick = { viewModel.openAdminQuota(unit.id) })
                }
            }
        }
    }
}

@Composable
private fun UnitQuotaListCard(unit: RemoteUnit, onClick: () -> Unit) {
    val quota = unit.quota
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(unit.unitName, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                QuotaStatus(quota.enabled)
            }
            Text("默认月额度：${Money.formatCents(quota.defaultMonthlyQuotaCents)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("当前可用余额：${Money.formatCents(quota.availableCents)}", fontWeight = FontWeight.Bold, color = if (quota.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (quota.enabled) {
                Text("本月已使用：${Money.formatCents(quota.usedThisMonthCents)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitQuotaDetailScreen(unitId: String, viewModel: SupplyViewModel) {
    val unit = viewModel.adminUnits.firstOrNull { it.id == unitId }
    val quota = viewModel.activeUnitQuota?.takeIf { viewModel.activeUnitQuotaUnitId == unitId } ?: unit?.quota
    var showSettings by remember { mutableStateOf(false) }
    var showAdjustment by remember { mutableStateOf(false) }
    var pendingEnabled by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(unitId) { viewModel.refreshAdminQuotaDetail(unitId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单位额度详情", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { viewModel.refreshAdminQuotaDetail(unitId) }) { Icon(Icons.Default.Refresh, "刷新") } }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isAdminQuotaLoading,
            onRefresh = { viewModel.refreshAdminQuotaDetail(unitId) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (unit == null || quota == null) {
                    item { Text("正在加载单位额度…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item { UnitQuotaOverview(unit, quota) }
                    item {
                        AdminFormCard {
                            Text("额度管理", fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("采购额度限制", fontWeight = FontWeight.Medium)
                                    Text(if (quota.enabled) "已启用，后续提交需求会校验可用余额。" else "未启用，不会自动修改单位任何额度数据。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = quota.enabled,
                                    onCheckedChange = { next ->
                                        if (next && quota.defaultMonthlyQuotaCents <= 0) {
                                            showSettings = true
                                        } else {
                                            pendingEnabled = next
                                        }
                                    },
                                    enabled = !viewModel.isAdminQuotaWriting
                                )
                            }
                            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !viewModel.isAdminQuotaWriting) {
                                Text("修改默认月额度")
                            }
                            Button(onClick = { showAdjustment = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = quota.enabled && !viewModel.isAdminQuotaWriting) {
                                Text("调整当前余额")
                            }
                            if (!quota.enabled) {
                                Text("请先启用额度控制，才可调整当前余额。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Text("额度变动记录", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    if (viewModel.unitQuotaLedgerRows.isEmpty()) {
                        item { Text("暂无额度变动记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(viewModel.unitQuotaLedgerRows, key = { it.id }) { row -> QuotaLedgerCard(row) }
                    }
                }
            }
        }
    }

    if (showSettings && quota != null) {
        QuotaSettingsDialog(
            quota = quota,
            saving = viewModel.isAdminQuotaWriting,
            onDismiss = { showSettings = false },
            onSave = { enabled, cents ->
                showSettings = false
                viewModel.saveAdminQuotaSettings(unitId, enabled, cents, quota.version)
            }
        )
    }
    if (showAdjustment && quota != null) {
        QuotaAdjustmentDialog(
            unitName = unit?.unitName.orEmpty(),
            quota = quota,
            saving = viewModel.isAdminQuotaWriting,
            onDismiss = { showAdjustment = false },
            onConfirm = { delta, reason ->
                showAdjustment = false
                viewModel.adjustAdminQuota(unitId, delta, reason, quota.version)
            }
        )
    }
    pendingEnabled?.let { enabled ->
        AlertDialog(
            onDismissRequest = { pendingEnabled = null },
            title = { Text(if (enabled) "开启采购额度限制" else "关闭采购额度限制") },
            text = { Text(if (enabled) "开启后，该单位后续提交需求将受到采购额度限制。" else "关闭后，该单位提交需求不再受额度限制，但历史额度账本会保留。") },
            confirmButton = {
                Button(onClick = {
                    pendingEnabled = null
                    quota?.let { viewModel.saveAdminQuotaSettings(unitId, enabled, it.defaultMonthlyQuotaCents, it.version) }
                }) { Text("确认") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingEnabled = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun UnitQuotaOverview(unit: RemoteUnit, quota: UnitQuota) {
    AdminFormCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(unit.unitName, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(unit.unitCode, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QuotaStatus(quota.enabled)
        }
        Text("默认月额度：${Money.formatCents(quota.defaultMonthlyQuotaCents)}")
        Text("当前可用余额：${Money.formatCents(quota.availableCents)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text("本月发放：${Money.formatCents(quota.baseQuotaCents)} · 本月已使用：${Money.formatCents(quota.usedThisMonthCents)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("本月人工调整：${signedMoney(quota.adjustmentCents)} · 版本：${quota.version}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("最近更新时间：${quota.updatedAt.ifBlank { "暂无记录" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("修改默认月额度后，将按系统现有规则作用于后续月份。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuotaStatus(enabled: Boolean) {
    Text(
        if (enabled) "已启用" else "未启用",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QuotaSettingsDialog(quota: UnitQuota, saving: Boolean, onDismiss: () -> Unit, onSave: (Boolean, Long) -> Unit) {
    var enabled by remember(quota.version) { mutableStateOf(quota.enabled) }
    var monthlyAmount by remember(quota.version) { mutableStateOf(quotaAmountText(quota.defaultMonthlyQuotaCents)) }
    val cents = quotaAmountToCents(monthlyAmount)
    val valid = cents != null && (!enabled || cents > 0)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("默认月采购额度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("开启采购额度限制")
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !saving)
                }
                OutlinedTextField(
                    value = monthlyAmount,
                    onValueChange = { monthlyAmount = it },
                    label = { Text("默认月额度（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = monthlyAmount.isNotBlank() && cents == null,
                    supportingText = { Text("金额不小于 0，最多两位小数；启用时必须大于 0。") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("修改默认月额度后，将按系统现有规则作用于后续月份。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { cents?.let { onSave(enabled, it) } }, enabled = valid && !saving) { Text(if (saving) "保存中…" else "保存") } },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun QuotaAdjustmentDialog(unitName: String, quota: UnitQuota, saving: Boolean, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var increase by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val cents = quotaAmountToCents(amount)
    val delta = cents?.let { if (increase) it else -it }
    val valid = delta != null && delta != 0L && reason.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("调整当前余额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("单位：$unitName", fontSize = 13.sp)
                Text("当前可用余额：${Money.formatCents(quota.availableCents)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { increase = true }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("增加额度") }
                    OutlinedButton(onClick = { increase = false }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("扣减额度") }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("调整备注") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "操作：${if (increase) "增加额度" else "扣减额度"} ${cents?.let(Money::formatCents) ?: "请输入合法金额"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { delta?.let { onConfirm(it, reason.trim()) } }, enabled = valid && !saving) { Text(if (saving) "调整中…" else "确认调整") } },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun QuotaLedgerCard(row: UnitQuotaLedgerRow) {
    AdminFormCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(quotaEventLabel(row.eventType), fontWeight = FontWeight.Bold)
            Text(signedMoney(row.deltaCents), color = if (row.deltaCents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Text("余额：${Money.formatCents(row.balanceAfterCents)}", fontSize = 13.sp)
        Text(row.createdAt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.orderNo.isNotBlank()) Text("订单：${row.orderNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.note.isNotBlank()) Text("备注：${row.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun signedMoney(cents: Long): String = (if (cents > 0) "+" else if (cents < 0) "-" else "") + Money.formatCents(kotlin.math.abs(cents))

private fun quotaEventLabel(eventType: String): String = when (eventType) {
    "MONTHLY_GRANT" -> "月度发放"
    "ORDER_RESERVE" -> "订单占用"
    "ORDER_RELEASE" -> "订单释放"
    "ORDER_ADJUST" -> "订单调整"
    "ORDER_FINALIZED" -> "订单完成"
    "MANUAL_INCREASE" -> "人工增加"
    "MANUAL_DECREASE" -> "人工扣减"
    else -> eventType.ifBlank { "额度变动" }
}
