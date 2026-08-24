package com.smartprocurement.internal.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.*
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.charts.AnalyticsBarChart
import com.smartprocurement.internal.ui.charts.AnalyticsLineChart
import java.time.LocalDate


private val analyticsCategories = listOf("", "蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他")
private val analyticsTabs = listOf("overview" to "采购", "price" to "价格", "inventory" to "库存", "units" to "单位")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: SupplyViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf("overview") }
    var showUnitDialog by remember { mutableStateOf(false) }
    var trendMetric by rememberSaveable { mutableStateOf("amount") }
    var selectedTrendIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedDemandIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedUnitIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAllDemand by rememberSaveable { mutableStateOf(false) }
    var showAllUnits by rememberSaveable { mutableStateOf(false) }
    var priceSearch by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshUnits()
        viewModel.refreshAnalytics(selectedTab)
    }

    if (showUnitDialog) {
        AnalyticsUnitDialog(
            units = viewModel.adminUnits,
            selectedId = viewModel.analyticsUnitId,
            onDismiss = { showUnitDialog = false },
            onSelect = { unitId ->
                showUnitDialog = false
                viewModel.setAnalyticsFilters(unitId, viewModel.analyticsCategory)
                viewModel.refreshAnalytics(selectedTab)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("数据分析", fontWeight = FontWeight.Bold); Text("统一服务端统计口径", fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { viewModel.refreshAnalytics(selectedTab) }, enabled = !viewModel.isAnalyticsLoading) {
                        Icon(Icons.Default.Refresh, "刷新数据")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AnalyticsRangeControls(viewModel, selectedTab) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showUnitDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val unit = viewModel.adminUnits.firstOrNull { it.id == viewModel.analyticsUnitId }
                        Text("单位：${unit?.unitName ?: "全部单位"}")
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        analyticsCategories.forEach { category ->
                            FilterChip(
                                selected = viewModel.analyticsCategory == category,
                                onClick = {
                                    viewModel.setAnalyticsFilters(viewModel.analyticsUnitId, category)
                                    viewModel.refreshAnalytics(selectedTab)
                                },
                                label = { Text(category.ifBlank { "全部分类" }) }
                            )
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    analyticsTabs.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab.first,
                            onClick = {
                                selectedTab = tab.first
                                viewModel.refreshAnalytics(selectedTab)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, analyticsTabs.size)
                        ) { Text(tab.second) }
                    }
                }
            }
            if (viewModel.isAnalyticsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when (selectedTab) {
                "price" -> priceContent(viewModel, priceSearch) { priceSearch = it }
                "inventory" -> inventoryContent(viewModel)
                "units" -> unitsContent(
                    viewModel = viewModel,
                    showAll = showAllUnits,
                    onToggle = { showAllUnits = !showAllUnits },
                    selectedIndex = selectedUnitIndex,
                    onSelected = { selectedUnitIndex = it },
                    onSort = { sort ->
                        selectedUnitIndex = null
                        viewModel.updateAnalyticsUnitSort(sort)
                        viewModel.refreshAnalytics("units")
                    }
                )
                else -> overviewContent(
                    viewModel = viewModel,
                    trendMetric = trendMetric,
                    selectedTrendIndex = selectedTrendIndex,
                    onTrendMetric = { trendMetric = it; selectedTrendIndex = null },
                    onTrendSelected = { selectedTrendIndex = it },
                    showAllDemand = showAllDemand,
                    selectedDemandIndex = selectedDemandIndex,
                    onDemandSelected = { selectedDemandIndex = it },
                    onToggleDemand = { showAllDemand = !showAllDemand }
                )
            }
        }
    }
}

@Composable
private fun AnalyticsRangeControls(viewModel: SupplyViewModel, selectedTab: String) {
    val context = LocalContext.current
    val range = viewModel.analyticsRange
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = range.days == days,
                    onClick = { viewModel.setAnalyticsRange(days); viewModel.refreshAnalytics(selectedTab) },
                    label = { Text("近 $days 日") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton("开始", range.startDate, Modifier.weight(1f)) {
                showDatePicker(context, range.startDate) { selected ->
                    if (viewModel.setAnalyticsCustomRange(selected, range.endDate)) viewModel.refreshAnalytics(selectedTab)
                }
            }
            DateButton("结束", range.endDate, Modifier.weight(1f)) {
                showDatePicker(context, range.endDate) { selected ->
                    if (viewModel.setAnalyticsCustomRange(range.startDate, selected)) viewModel.refreshAnalytics(selectedTab)
                }
            }
        }
    }
}

@Composable
private fun DateButton(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) {
        Column(horizontalAlignment = Alignment.Start) { Text(label, fontSize = 11.sp); Text(value, fontWeight = FontWeight.Bold) }
    }
}

private fun showDatePicker(context: android.content.Context, value: String, onSelect: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, year, month, day ->
        onSelect(LocalDate.of(year, month + 1, day).toString())
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewContent(
    viewModel: SupplyViewModel,
    trendMetric: String,
    selectedTrendIndex: Int?,
    onTrendMetric: (String) -> Unit,
    onTrendSelected: (Int) -> Unit,
    showAllDemand: Boolean,
    selectedDemandIndex: Int?,
    onDemandSelected: (Int) -> Unit,
    onToggleDemand: () -> Unit
) {
    val overview = viewModel.analyticsOverview
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMetric("有效订单", overview.summary.validOrderCount.toString(), comparisonText(overview.comparison.validOrderCountPercent), Modifier.weight(1f))
                AnalyticsMetric("采购金额", Money.formatCents(overview.summary.totalCents), comparisonText(overview.comparison.totalCentsPercent), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMetric("采购单位", overview.summary.unitCount.toString(), comparisonText(overview.comparison.unitCountPercent), Modifier.weight(1f))
                AnalyticsMetric("食材品种", overview.summary.productCount.toString(), comparisonText(overview.comparison.productCountPercent), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMetric("库存预警", overview.summary.inventoryAlertCount.toString(), "当前库存快照", Modifier.weight(1f))
                AnalyticsMetric("待处理异常", overview.summary.openReceiptIssues.toString(), "当前筛选范围", Modifier.weight(1f))
            }
        }
    }
    item {
        AnalyticsPanel("采购金额趋势", "金额来自订单历史快照") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = trendMetric == "amount", onClick = { onTrendMetric("amount") }, label = { Text("采购金额") })
                FilterChip(selected = trendMetric == "orders", onClick = { onTrendMetric("orders") }, label = { Text("订单量") })
            }
            if (overview.summary.validOrderCount == 0) {
                Text("暂无该时间范围的数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AnalyticsLineChart(
                    values = overview.trend.map { if (trendMetric == "amount") it.totalCents.toFloat() else it.orderCount.toFloat() },
                    description = "${viewModel.analyticsRange.days}天${if (trendMetric == "amount") "采购金额" else "订单量"}趋势，共${overview.summary.validOrderCount}笔订单",
                    selectedIndex = selectedTrendIndex,
                    onSelected = onTrendSelected
                )
            }
            selectedTrendIndex?.let { index ->
                overview.trend.getOrNull(index)?.let { point ->
                    Text("${point.date} · ${point.orderCount} 笔 · ${Money.formatCents(point.totalCents)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    item {
        AnalyticsPanel("需求排行", "商品按计量单位分别汇总") {
            if (overview.demandRank.isEmpty()) Text("暂无该时间范围的需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else AnalyticsBarChart(
                    values = overview.demandRank.map { it.quantity.toFloatOrNull() ?: 0f },
                    description = overview.demandRank.joinToString("，") { "${it.productName} ${it.quantity}${it.unit}" },
                    selectedIndex = selectedDemandIndex,
                    onSelected = onDemandSelected
                )
            selectedDemandIndex?.let { index ->
                overview.demandRank.getOrNull(index)?.let { Text("${it.productName} · ${it.quantity} ${it.unit}", fontWeight = FontWeight.Bold) }
            }
            overview.demandRank.take(if (showAllDemand) 10 else 5).forEachIndexed { index, item ->
                AnalyticsListRow("${index + 1}. ${item.productName}", "${item.quantity} ${item.unit}") {
                    viewModel.openProductAnalytics(item.productId)
                }
            }
            if (overview.demandRank.size > 5) TextButton(onClick = onToggleDemand) { Text(if (showAllDemand) "收起" else "查看更多") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.priceContent(viewModel: SupplyViewModel, search: String, onSearch: (String) -> Unit) {
    val filtered = viewModel.analyticsPrices.filter { it.productName.contains(search.trim(), ignoreCase = true) }
    val increases = viewModel.analyticsPrices.filter { (it.changePercent ?: 0.0) > 0 }.sortedByDescending { it.changePercent }.take(3)
    val decreases = viewModel.analyticsPrices.filter { (it.changePercent ?: 0.0) < 0 }.sortedBy { it.changePercent }.take(3)
    item {
        AnalyticsPanel("价格变动", "涨跌基数为 0 时不计算百分比") {
            OutlinedTextField(value = search, onValueChange = onSearch, label = { Text("搜索食材") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (increases.isNotEmpty()) {
                Text("上涨最多", fontWeight = FontWeight.Bold)
                increases.forEach { AnalyticsListRow(it.productName, analyticsPriceChangeText(it.changePercent)) { viewModel.openProductAnalytics(it.productId) } }
            }
            if (decreases.isNotEmpty()) {
                Text("下降最多", fontWeight = FontWeight.Bold)
                decreases.forEach { AnalyticsListRow(it.productName, analyticsPriceChangeText(it.changePercent)) { viewModel.openProductAnalytics(it.productId) } }
            }
            if (viewModel.analyticsPrices.isEmpty() && !viewModel.isAnalyticsLoading) Text("当前范围暂无价格变更")
        }
    }
    items(filtered, key = { it.productId }) { item ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.openProductAnalytics(item.productId) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text(item.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${item.initialPriceCents?.let(Money::formatCents) ?: "新建"} → ${Money.formatCents(item.currentPriceCents)}")
                Text(analyticsPriceChangeText(item.changePercent), color = priceChangeColor(item.changePercent), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.inventoryContent(viewModel: SupplyViewModel) {
    val inventory = viewModel.analyticsInventory
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsMetric("库存不足", inventory.summary.outOfStock.toString(), "实时快照", Modifier.weight(1f))
            AnalyticsMetric("低于预警", inventory.summary.warning.toString(), "实时快照", Modifier.weight(1f))
            AnalyticsMetric("库存紧张", inventory.summary.tight.toString(), "实时快照", Modifier.weight(1f))
        }
    }
    item {
        AnalyticsPanel("可用库存", "总库存减去已预占库存") {
            if (inventory.items.isEmpty()) Text("暂无库存食材数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else AnalyticsBarChart(
                values = inventory.items.take(12).map { it.availableQuantity.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f },
                description = inventory.items.take(12).joinToString("，") { "${it.productName}可用${it.availableQuantity}${it.unit}" }
            )
        }
    }
    items(inventory.items, key = { it.productId }) { item ->
        val stock = item.stockQuantity.toFloatOrNull() ?: 0f
        val available = item.availableQuantity.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.openProductAnalytics(item.productId) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text(analyticsInventoryRiskText(item.risk), color = if (item.risk == "normal") Color(0xFF147D50) else MaterialTheme.colorScheme.error)
                }
                Text("可用 ${item.availableQuantity} ${item.unit} · 预占 ${item.reservedQuantity} ${item.unit} · 预警 ${item.warningQuantity} ${item.unit}")
                LinearProgressIndicator(progress = { if (stock > 0f) (available / stock).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text(item.estimatedDaysAvailable?.let { "预计可用天数 $it 天" } ?: "暂无足够需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.unitsContent(
    viewModel: SupplyViewModel,
    showAll: Boolean,
    onToggle: () -> Unit,
    selectedIndex: Int?,
    onSelected: (Int) -> Unit,
    onSort: (String) -> Unit
) {
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("amount" to "采购金额", "orders" to "订单数", "products" to "食材种类").forEach { (value, label) ->
                FilterChip(selected = viewModel.analyticsUnitSort == value, onClick = { onSort(value) }, label = { Text(label) })
            }
        }
    }
    if (viewModel.analyticsUnits.isNotEmpty()) item {
        AnalyticsPanel("单位排行", "点击柱形查看单位数据") {
            val top = viewModel.analyticsUnits.take(5)
            AnalyticsBarChart(
                values = top.map {
                    when (viewModel.analyticsUnitSort) {
                        "orders" -> it.orderCount.toFloat()
                        "products" -> it.productCount.toFloat()
                        else -> it.totalCents.toFloat()
                    }
                },
                description = top.joinToString("，") { "${it.unitName}${it.orderCount}笔订单" },
                selectedIndex = selectedIndex,
                onSelected = onSelected
            )
            selectedIndex?.let { index ->
                top.getOrNull(index)?.let { Text("${it.unitName} · ${it.orderCount} 笔 · ${it.productCount} 种 · ${Money.formatCents(it.totalCents)}", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (viewModel.analyticsUnits.isEmpty() && !viewModel.isAnalyticsLoading) item { Text("当前范围暂无单位采购数据") }
    items(viewModel.analyticsUnits.take(if (showAll) viewModel.analyticsUnits.size else 5), key = { it.unitId }) { item ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.unitName, fontWeight = FontWeight.Bold)
                Text("${item.orderCount} 笔订单 · ${item.productCount} 种食材 · ${Money.formatCents(item.totalCents)}")
                if (item.openReceiptIssues > 0) Text("待处理异常 ${item.openReceiptIssues} 项", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (viewModel.analyticsUnits.size > 5) item { TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text(if (showAll) "收起" else "查看完整列表") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAnalyticsScreen(productId: String, viewModel: SupplyViewModel) {
    val detail = viewModel.activeProductAnalytics
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.product?.name ?: "食材分析", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        if (detail == null || detail.product.id != productId) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalyticsMetric("周期订单", detail.period.orderCount.toString(), "${detail.period.unitCount} 个单位", Modifier.weight(1f))
                        AnalyticsMetric("周期金额", Money.formatCents(detail.period.amountCents), "需求 ${detail.period.quantity} ${detail.period.unit}", Modifier.weight(1f))
                    }
                }
                item {
                    AnalyticsPanel("价格趋势", "价格事件来自服务端变更日志") {
                        Text("当前价格 ${Money.formatCents(detail.price.currentCents)} / ${detail.product.unit}", fontWeight = FontWeight.Bold)
                        Text("期初 ${detail.price.rangeStartCents?.let(Money::formatCents) ?: "暂无"} · 最低 ${Money.formatCents(detail.price.minCents)} · 最高 ${Money.formatCents(detail.price.maxCents)}")
                        Text(analyticsPriceChangeText(detail.price.changePercent), color = priceChangeColor(detail.price.changePercent), fontWeight = FontWeight.Bold)
                        Text("可用库存 ${detail.inventory.availableQuantity} ${detail.product.unit}（预占 ${detail.inventory.reservedQuantity}）")
                        if (detail.priceHistory.isEmpty()) Text("暂无价格变动记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else AnalyticsLineChart(detail.priceHistory.map { it.newPriceCents.toFloat() }, "${detail.product.name}价格趋势，${detail.priceHistory.size}次变价")
                        detail.priceHistory.asReversed().forEach { event ->
                            AnalyticsListRow(
                                event.createdAt.replace('T', ' ').take(16),
                                "${event.oldPriceCents?.let(Money::formatCents) ?: "新建"} → ${Money.formatCents(event.newPriceCents)}"
                            )
                        }
                    }
                }
                item {
                    AnalyticsPanel("采购需求", "实际供应数量优先，按${detail.product.unit}汇总") {
                        if (detail.period.orderCount == 0) Text("暂无该时间范围的需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else AnalyticsBarChart(detail.demandTrend.map { it.quantity.toFloatOrNull() ?: 0f }, "${detail.product.name}采购需求趋势")
                    }
                }
                item {
                    AnalyticsPanel("单位需求", "同一计量单位内排序") {
                        detail.unitRank.forEach { item -> AnalyticsListRow(item.unitName, "${item.quantity} ${item.unit}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetric(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Surface(modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun AnalyticsListRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalyticsUnitDialog(units: List<RemoteUnit>, selectedId: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("选择子单位") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { AnalyticsListRow("全部单位", if (selectedId.isBlank()) "已选择" else "") { onSelect("") } }
                items(units, key = { it.id }) { unit -> AnalyticsListRow(unit.unitName, if (selectedId == unit.id) "已选择" else "") { onSelect(unit.id) } }
            }
        }
    )
}

private fun comparisonText(value: Double?): String = when {
    value == null -> "上期无可比数据"
    value > 0 -> "较上期 +${"%.1f".format(value)}%"
    else -> "较上期 ${"%.1f".format(value)}%"
}

internal fun analyticsPriceChangeText(value: Double?): String = when {
    value == null -> "新建或无可比基数"
    value > 0 -> "+${"%.1f".format(value)}%"
    else -> "${"%.1f".format(value)}%"
}

@Composable
private fun priceChangeColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
    value > 0 -> MaterialTheme.colorScheme.error
    value < 0 -> Color(0xFF147D50)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun analyticsInventoryRiskText(risk: String): String = when (risk) {
    "out_of_stock" -> "库存不足"
    "warning" -> "低于预警"
    "tight" -> "库存紧张"
    "paused" -> "暂停供应"
    else -> "正常"
}
