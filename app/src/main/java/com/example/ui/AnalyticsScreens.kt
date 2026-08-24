package com.smartprocurement.internal.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
fun AnalyticsScreen(viewModel: SupplyViewModel, showBack: Boolean = true) {
    val selectedTab = viewModel.analyticsSelectedTab
    var showTimeSheet by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var trendMetric by rememberSaveable { mutableStateOf("amount") }
    var selectedTrendIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedDemandIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedUnitIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAllDemand by rememberSaveable { mutableStateOf(false) }
    var showAllUnits by rememberSaveable { mutableStateOf(false) }
    var priceSearch by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState(viewModel.analyticsScrollIndex, viewModel.analyticsScrollOffset)
    DisposableEffect(Unit) {
        onDispose { viewModel.saveAnalyticsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshUnits()
        if (viewModel.analyticsLoadState(selectedTab) == AnalyticsLoadState.Idle) {
            viewModel.refreshAnalytics(selectedTab)
        }
    }

    if (showTimeSheet) {
        AnalyticsTimeSheet(
            current = viewModel.analyticsRange,
            onDismiss = { showTimeSheet = false },
            onApply = { start, end, days ->
                showTimeSheet = false
                if (days != null) viewModel.setAnalyticsRange(days)
                else viewModel.setAnalyticsCustomRange(start, end)
                viewModel.refreshAnalytics(selectedTab, userInitiated = true)
            }
        )
    }

    if (showFilterSheet) {
        AnalyticsFilterSheet(
            tab = selectedTab,
            units = viewModel.adminUnits,
            initial = AnalyticsFilterDraft(
                unitId = viewModel.analyticsUnitId,
                category = viewModel.analyticsCategory,
                inventoryRiskOnly = viewModel.analyticsInventoryRiskOnly
            ),
            onDismiss = { showFilterSheet = false },
            onApply = {
                showFilterSheet = false
                viewModel.applyAnalyticsFilters(it, selectedTab)
            }
        )
    }

    val loadState = viewModel.analyticsLoadState(selectedTab)
    val refreshing = loadState is AnalyticsLoadState.Loading && loadState.keepsContent
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("数据", fontWeight = FontWeight.Bold); Text("采购与库存概览", fontSize = 12.sp) } },
                navigationIcon = {
                    if (showBack) IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAnalytics(selectedTab, userInitiated = true) }) {
                        Icon(Icons.Default.Refresh, "刷新数据")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshAnalytics(selectedTab, userInitiated = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selectedTab == "inventory") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("当前库存") } }
                        } else {
                            OutlinedButton(onClick = { showTimeSheet = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (viewModel.analyticsRange.days in listOf(7, 30, 90)) "近 ${viewModel.analyticsRange.days} 天" else "自定义日期")
                            }
                        }
                        Text(
                            analyticsFilterSummary(selectedTab, viewModel),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    OutlinedButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                        ) { Text("筛选") }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        analyticsTabs.forEachIndexed { index, tab ->
                            SegmentedButton(
                                selected = selectedTab == tab.first,
                                onClick = {
                                    viewModel.selectAnalyticsTab(tab.first)
                                    if (viewModel.analyticsLoadState(tab.first) == AnalyticsLoadState.Idle) {
                                        viewModel.refreshAnalytics(tab.first)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, analyticsTabs.size)
                            ) { Text(tab.second) }
                        }
                    }
                }
                if (loadState is AnalyticsLoadState.Loading && !loadState.keepsContent) {
                    item { AnalyticsLoadingPlaceholder() }
                } else if (loadState is AnalyticsLoadState.Error && !loadState.keepsContent) {
                    item {
                        AnalyticsInlineError(loadState.message) {
                            viewModel.refreshAnalytics(selectedTab, userInitiated = true)
                        }
                    }
                } else {
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
    }
}

private fun analyticsFilterSummary(tab: String, viewModel: SupplyViewModel): String {
    if (tab == "inventory") {
        val category = viewModel.analyticsCategory.ifBlank { "全部分类" }
        return if (viewModel.analyticsInventoryRiskOnly) "$category · 仅看风险" else "$category · 全部库存"
    }
    val parts = mutableListOf<String>()
    if (tab == "overview") {
        parts += viewModel.adminUnits.firstOrNull { it.id == viewModel.analyticsUnitId }?.unitName ?: "全部单位"
    }
    parts += viewModel.analyticsCategory.ifBlank { "全部分类" }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsTimeSheet(
    current: AnalyticsDateRange,
    onDismiss: () -> Unit,
    onApply: (startDate: String, endDate: String, presetDays: Int?) -> Unit
) {
    val context = LocalContext.current
    var presetDays by remember { mutableStateOf(current.days.takeIf { it in listOf(7, 30, 90) }) }
    var startDate by remember { mutableStateOf(current.startDate) }
    var endDate by remember { mutableStateOf(current.endDate) }
    var error by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("选择统计时间", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = presetDays == days,
                        onClick = { presetDays = days; error = ""; onApply(startDate, endDate, days) },
                        label = { Text("近 $days 天") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text("自定义日期", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("开始", startDate, Modifier.weight(1f)) {
                    showDatePicker(context, startDate) { startDate = it; presetDays = null; error = "" }
                }
                DateButton("结束", endDate, Modifier.weight(1f)) {
                    showDatePicker(context, endDate) { endDate = it; presetDays = null; error = "" }
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("取消") }
                Button(
                    onClick = {
                        val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
                        val end = runCatching { LocalDate.parse(endDate) }.getOrNull()
                        val customDays = if (start != null && end != null) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 else 0
                        if (presetDays == null && (start == null || end == null || start.isAfter(end))) {
                            error = "开始日期不能晚于结束日期"
                        } else if (presetDays == null && customDays !in 1..365) {
                            error = "单次查询最多支持 365 天"
                        } else {
                            onApply(startDate, endDate, presetDays)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("应用") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsFilterSheet(
    tab: String,
    units: List<RemoteUnit>,
    initial: AnalyticsFilterDraft,
    onDismiss: () -> Unit,
    onApply: (AnalyticsFilterDraft) -> Unit
) {
    val fields = analyticsFilterFields(tab)
    var unitId by remember { mutableStateOf(initial.unitId) }
    var category by remember { mutableStateOf(initial.category) }
    var riskOnly by remember { mutableStateOf(initial.inventoryRiskOnly) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("筛选数据", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if ("unitId" in fields) {
                Text("子单位", fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    item { AnalyticsChoiceRow("全部单位", unitId.isBlank()) { unitId = "" } }
                    items(units, key = { it.id }) { unit ->
                        AnalyticsChoiceRow(unit.unitName, unitId == unit.id) { unitId = unit.id }
                    }
                }
            }
            if ("category" in fields) {
                Text("食材分类", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    analyticsCategories.forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value.ifBlank { "全部" }) }
                        )
                    }
                }
            }
            if ("inventoryRiskOnly" in fields) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("仅看需要关注的库存", fontWeight = FontWeight.Bold)
                        Text("库存不足、低于预警、库存紧张或暂停供应", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = riskOnly, onCheckedChange = { riskOnly = it })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { unitId = ""; category = ""; riskOnly = false },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("重置") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("取消") }
                Button(
                    onClick = { onApply(AnalyticsFilterDraft(unitId, category, riskOnly)) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("应用筛选") }
            }
        }
    }
}

@Composable
private fun AnalyticsChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AnalyticsLoadingPlaceholder() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        repeat(3) {
            Surface(
                Modifier.fillMaxWidth().height(72.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp)
            ) {}
        }
        Text("正在更新数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalyticsInlineError(message: String, onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新加载") }
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
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${overview.summary.unitCount} 个单位 · ${overview.summary.productCount} 种食材", fontWeight = FontWeight.Medium)
                    Text(
                        "${overview.summary.inventoryAlertCount} 项库存预警 · ${overview.summary.openReceiptIssues} 项待处理异常",
                        color = if (overview.summary.inventoryAlertCount + overview.summary.openReceiptIssues > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    item {
        AnalyticsPanel("采购趋势", "按订单实际价格统计") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = trendMetric == "amount", onClick = { onTrendMetric("amount") }, label = { Text("采购金额") })
                FilterChip(selected = trendMetric == "orders", onClick = { onTrendMetric("orders") }, label = { Text("订单量") })
            }
            if (overview.summary.validOrderCount == 0) {
                Text("暂无该时间范围的数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AnalyticsLineChart(
                    values = overview.trend.map { if (trendMetric == "amount") it.totalCents / 100f else it.orderCount.toFloat() },
                    labels = overview.trend.map { it.date.drop(5) },
                    formattedValues = overview.trend.map { if (trendMetric == "amount") Money.formatCents(it.totalCents) else "${it.orderCount}笔" },
                    axisPrefix = if (trendMetric == "amount") "¥" else "",
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
        AnalyticsPanel("需求排行", "不同计量单位分别统计") {
            if (overview.demandRank.isEmpty()) Text("暂无该时间范围的需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else AnalyticsBarChart(
                    values = overview.demandRank.take(5).map { it.quantity.toFloatOrNull() ?: 0f },
                    labels = overview.demandRank.take(5).map { it.productName },
                    formattedValues = overview.demandRank.take(5).map { "${it.quantity}${it.unit}" },
                    description = overview.demandRank.take(5).joinToString("，") { "${it.productName} ${it.quantity}${it.unit}" },
                    selectedIndex = selectedDemandIndex,
                    onSelected = onDemandSelected
                )
            selectedDemandIndex?.let { index ->
                overview.demandRank.getOrNull(index)?.let { Text("${it.productName} · ${it.quantity} ${it.unit}", fontWeight = FontWeight.Bold) }
            }
            overview.demandRank.take(if (showAllDemand) 10 else 5).forEachIndexed { index, item ->
                AnalyticsListRow("${index + 1}. ${item.productName}", "${item.quantity} ${item.unit}") {
                    viewModel.openProductAnalytics(item.productId, item.productName)
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
                increases.forEach { AnalyticsListRow(it.productName, analyticsPriceChangeText(it.changePercent)) { viewModel.openProductAnalytics(it.productId, it.productName) } }
            }
            if (decreases.isNotEmpty()) {
                Text("下降最多", fontWeight = FontWeight.Bold)
                decreases.forEach { AnalyticsListRow(it.productName, analyticsPriceChangeText(it.changePercent)) { viewModel.openProductAnalytics(it.productId, it.productName) } }
            }
            if (viewModel.analyticsPrices.isEmpty()) Text("当前时间范围内还没有价格变动")
        }
    }
    items(filtered, key = { it.productId }) { item ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.openProductAnalytics(item.productId, item.productName) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text(item.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${item.initialPriceCents?.let(Money::formatCents) ?: "新建"} → ${Money.formatCents(item.currentPriceCents)}")
                Text(if (item.isNew) "新食材" else analyticsPriceChangeText(item.changePercent), color = priceChangeColor(item.changePercent), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.inventoryContent(viewModel: SupplyViewModel) {
    val inventory = viewModel.analyticsInventory
    val riskOrder = mapOf("out_of_stock" to 0, "warning" to 1, "tight" to 2, "paused" to 3, "normal" to 4)
    val visibleItems = inventory.items
        .asSequence()
        .filter { viewModel.analyticsCategory.isBlank() || it.category == viewModel.analyticsCategory }
        .filter { !viewModel.analyticsInventoryRiskOnly || it.risk != "normal" }
        .sortedBy { riskOrder[it.risk] ?: 5 }
        .toList()
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventorySummaryTile("库存不足", inventory.summary.outOfStock, Modifier.weight(1f))
                InventorySummaryTile("低于预警", inventory.summary.warning, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventorySummaryTile("库存紧张", inventory.summary.tight, Modifier.weight(1f))
                InventorySummaryTile("暂停供应", inventory.summary.paused, Modifier.weight(1f))
            }
        }
    }
    item {
        AnalyticsPanel("可用库存", "总库存减去已预占库存") {
            if (visibleItems.isEmpty()) Text("当前筛选下没有需要关注的库存", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else AnalyticsBarChart(
                values = visibleItems.take(5).map { it.availableQuantity.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f },
                labels = visibleItems.take(5).map { it.productName },
                formattedValues = visibleItems.take(5).map { "${it.availableQuantity}${it.unit}" },
                description = visibleItems.take(5).joinToString("，") { "${it.productName}可用${it.availableQuantity}${it.unit}" }
            )
        }
    }
    items(visibleItems, key = { it.productId }) { item ->
        val stock = item.stockQuantity.toFloatOrNull() ?: 0f
        val available = item.availableQuantity.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.openProductAnalytics(item.productId, item.productName) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text(analyticsInventoryRiskText(item.risk), color = if (item.risk == "normal") Color(0xFF147D50) else MaterialTheme.colorScheme.error)
                }
                Text("可用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.availableQuantity} ${item.unit}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("已预占 ${item.reservedQuantity} ${item.unit} · 总库存 ${item.stockQuantity} ${item.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { if (stock > 0f) (available / stock).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text(item.estimatedDaysAvailable?.let { "预计可用天数 $it 天" } ?: "暂无足够需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InventorySummaryTile(label: String, value: Int, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp)
            Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (value > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
                        else -> it.totalCents / 100f
                    }
                },
                labels = top.map { it.unitName },
                formattedValues = top.map {
                    when (viewModel.analyticsUnitSort) {
                        "orders" -> "${it.orderCount}笔"
                        "products" -> "${it.productCount}种"
                        else -> Money.formatCents(it.totalCents)
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
    if (viewModel.analyticsUnits.isEmpty()) item { Text("当前时间范围内还没有单位采购数据") }
    items(viewModel.analyticsUnits.take(if (showAll) viewModel.analyticsUnits.size else 5), key = { it.unitId }) { item ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.unitName, fontWeight = FontWeight.Bold)
                Text(Money.formatCents(item.totalCents), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("订单 ${item.orderCount} 笔", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("食材 ${item.productCount} 种", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.openReceiptIssues > 0) Text("待处理异常 ${item.openReceiptIssues} 项", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (viewModel.analyticsUnits.size > 5) item { TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text(if (showAll) "收起" else "查看完整列表") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAnalyticsScreen(productId: String, productName: String = "", viewModel: SupplyViewModel) {
    val detail = viewModel.activeProductAnalytics
    val loadState = viewModel.analyticsLoadState("product")
    val hasDetail = detail?.product?.id == productId
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.product?.name ?: productName.ifBlank { "食材分析" }, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        when {
            !hasDetail && loadState is AnalyticsLoadState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    AnalyticsInlineError(loadState.message) { viewModel.refreshProductAnalytics(productId, userInitiated = true) }
                }
            }
            !hasDetail -> {
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { AnalyticsLoadingPlaceholder() }
            }
            else -> PullToRefreshBox(
                isRefreshing = loadState is AnalyticsLoadState.Loading && loadState.keepsContent,
                onRefresh = { viewModel.refreshProductAnalytics(productId, userInitiated = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnalyticsMetric("周期订单", detail!!.period.orderCount.toString(), "${detail.period.unitCount} 个单位", Modifier.weight(1f))
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
                            else AnalyticsLineChart(
                                values = detail.priceHistory.map { it.newPriceCents / 100f },
                                labels = detail.priceHistory.map { it.createdAt.take(10).drop(5) },
                                formattedValues = detail.priceHistory.map { Money.formatCents(it.newPriceCents) },
                                axisPrefix = "¥",
                                description = "${detail.product.name}价格趋势，${detail.priceHistory.size}次变价"
                            )
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
                            else AnalyticsBarChart(
                                values = detail.demandTrend.take(5).map { it.quantity.toFloatOrNull() ?: 0f },
                                labels = detail.demandTrend.take(5).map { it.date.drop(5) },
                                formattedValues = detail.demandTrend.take(5).map { "${it.quantity}${detail.product.unit}" },
                                description = "${detail.product.name}采购需求趋势"
                            )
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
    value == null -> "暂无同期数据"
    value > 0 -> "较上期 +${"%.1f".format(value)}%"
    else -> "较上期 ${"%.1f".format(value)}%"
}

internal fun analyticsPriceChangeText(value: Double?): String = when {
    value == null -> "暂无可比价格"
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
