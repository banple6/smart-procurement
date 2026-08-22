package com.smartprocurement.internal.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.PriceImportBatch
import com.smartprocurement.internal.data.PriceImportDefaults
import com.smartprocurement.internal.data.PriceImportNewProductPatch
import com.smartprocurement.internal.data.PriceImportRow
import com.smartprocurement.internal.data.PriceImportStructure
import com.smartprocurement.internal.data.ProductEntity
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentInfoBanner
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSectionHeader
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import com.smartprocurement.internal.ui.thinkingorb.ThinkingOrbState
import com.smartprocurement.internal.ui.thinkingorb.ThinkingOrbStatusPanel

private val importCategories = listOf("蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他")
private val importUnits = listOf("", "公斤", "斤", "箱", "袋", "个", "筐", "盒", "瓶", "份", "包")
private val importSupplyStatuses = listOf("paused" to "暂停供应", "normal" to "正常供应", "tight" to "库存紧张")
private val importMappingFields = listOf(
    "product_name" to "商品名称列",
    "product_code" to "商品编码列",
    "category" to "分类列",
    "spec" to "规格列",
    "unit" to "单位列",
    "stock" to "库存列",
    "price" to "本次执行价格列"
)

@Composable
fun PriceImportsScreen(viewModel: SupplyViewModel) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedFile = uri
        selectedFileName = uri?.let { importFileName(context, it) }.orEmpty()
    }

    LaunchedEffect(Unit) { viewModel.refreshPriceImports() }
    val isWorking = viewModel.isPriceImportLoading
    val isAnalyzing = isWorking && selectedFile != null
    val orbState = when {
        isAnalyzing -> ThinkingOrbState.Solving
        else -> ThinkingOrbState.Searching
    }
    val orbTitle = when {
        isAnalyzing -> "正在识别报价表"
        selectedFile != null -> "等待开始解析"
        else -> "Excel 智能导入与价格同步"
    }
    val orbDetail = when {
        isAnalyzing -> "正在读取字段和价格，请不要退出此页面"
        selectedFile != null -> "已选择：$selectedFileName"
        else -> "首次可批量建立商品目录，之后会同步已有商品价格"
    }

    Scaffold(topBar = { GovernmentTopBar(title = "Excel 智能导入", onBack = { viewModel.navigateBack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ThinkingOrbStatusPanel(
                    state = orbState,
                    title = orbTitle,
                    detail = orbDetail,
                    active = true
                )
            }
            item {
                GovernmentCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GovernmentSectionHeader("上传供应商报价")
                        Text("支持 .xlsx、.xls、.csv，文件最大 10 MB。系统不会直接改价，识别后仍需核对并确认。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
                        OutlinedButton(
                            onClick = {
                                picker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (selectedFileName.isBlank()) "选择 Excel 报价文件" else selectedFileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GovernmentPrimaryButton(
                            text = if (isWorking) "正在上传并识别" else "上传并识别",
                            onClick = { selectedFile?.let(viewModel::uploadAndAnalyzePriceImport) },
                            enabled = selectedFile != null && !isWorking
                        )
                    }
                }
            }
            item { GovernmentSectionHeader("导入记录") }
            if (viewModel.priceImportBatches.isEmpty() && !isWorking) {
                item { ImportEmptyState("暂无导入记录", "选择供应商报价 Excel 后，识别结果会显示在这里。") }
            } else {
                items(viewModel.priceImportBatches, key = { it.id }) { batch ->
                    PriceImportHistoryItem(batch = batch, onClick = { viewModel.openPriceImport(batch.id) })
                }
            }
        }
    }
}

@Composable
fun PriceImportDetailScreen(batchId: String, viewModel: SupplyViewModel) {
    val batch = viewModel.activePriceImport
    val products by viewModel.allProducts.collectAsState()
    var filter by remember { mutableStateOf("全部") }
    var productPickerRow by remember { mutableStateOf<PriceImportRow?>(null) }
    var newProductEditorRow by remember { mutableStateOf<PriceImportRow?>(null) }
    var confirmApply by remember { mutableStateOf(false) }
    var showFieldMapping by remember { mutableStateOf(false) }
    LaunchedEffect(batchId) {
        if (batch?.id != batchId) viewModel.openPriceImport(batchId)
    }

    Scaffold(topBar = { GovernmentTopBar(title = "核对 Excel 导入", onBack = { viewModel.navigateBack() }) }) { padding ->
        if (batch?.id != batchId) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ThinkingOrbStatusPanel(
                    state = ThinkingOrbState.Searching,
                    title = "正在读取导入批次",
                    detail = "请稍候",
                    active = true,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            PriceImportReviewContent(
                batch = batch,
                products = products,
                viewModel = viewModel,
                filter = filter,
                onFilterChange = { filter = it },
                onChooseProduct = { productPickerRow = it },
                onEditNewProduct = { newProductEditorRow = it },
                onEditFieldMapping = {
                    showFieldMapping = true
                    viewModel.loadPriceImportStructure()
                },
                onRequestApply = { confirmApply = true },
                padding = padding
            )
        }
    }
    if (showFieldMapping) {
        val structure = viewModel.activePriceImportStructure
        if (structure == null) {
            FieldMappingLoadingSheet(onDismiss = { showFieldMapping = false })
        } else {
            PriceImportFieldMappingSheet(
                structure = structure,
                saving = viewModel.isPriceImportLoading,
                onDismiss = { if (!viewModel.isPriceImportLoading) showFieldMapping = false },
                onConfirm = { sheetName, headerRow, mapping ->
                    viewModel.reanalyzePriceImport(sheetName, headerRow, mapping)
                    showFieldMapping = false
                }
            )
        }
    }

    productPickerRow?.let { row ->
        ProductPickerDialog(
            products = products,
            onDismiss = { productPickerRow = null },
            onSelected = { product ->
                viewModel.selectPriceImportProduct(row.id, product.id)
                productPickerRow = null
            }
        )
    }
    newProductEditorRow?.let { row ->
        PriceImportNewProductDialog(
            row = row,
            saving = viewModel.isPriceImportLoading,
            onDismiss = { newProductEditorRow = null },
            onSave = { patch ->
                viewModel.updatePriceImportNewProduct(row.id, patch)
                newProductEditorRow = null
            }
        )
    }
    if (confirmApply && batch != null) {
        val action = importApplyText(batch)
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("确认批量应用", fontWeight = FontWeight.Bold) },
            text = { Text("确认${action}吗？历史订单中的价格快照不会改变。") },
            confirmButton = {
                Button(onClick = {
                    confirmApply = false
                    viewModel.applyPriceImport()
                }, enabled = !viewModel.isPriceImportApplying) { Text("确认应用") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("返回检查") } }
        )
    }
}

@Composable
private fun PriceImportReviewContent(
    batch: PriceImportBatch,
    products: List<ProductEntity>,
    viewModel: SupplyViewModel,
    filter: String,
    onFilterChange: (String) -> Unit,
    onChooseProduct: (PriceImportRow) -> Unit,
    onEditNewProduct: (PriceImportRow) -> Unit,
    onEditFieldMapping: () -> Unit,
    onRequestApply: () -> Unit,
    padding: PaddingValues
) {
    val blockers = batch.rows.filter { it.validationStatus !in setOf("READY", "IGNORED") }
    val filteredRows = batch.rows.filter { row ->
        when (filter) {
            "待同步" -> row.operationType == "EXISTING_PRODUCT"
            "待新增" -> row.operationType == "NEW_PRODUCT"
            "需要确认" -> row.operationType == "NEEDS_REVIEW" || row.validationStatus == "NEEDS_REVIEW"
            "异常" -> row.validationStatus !in setOf("READY", "IGNORED", "NEEDS_REVIEW")
            else -> true
        }
    }
    val working = viewModel.isPriceImportLoading || viewModel.isPriceImportApplying
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ThinkingOrbStatusPanel(
                state = if (working) ThinkingOrbState.Solving else ThinkingOrbState.Working,
                title = if (working) "正在同步导入结果" else batch.sourceFilename,
                detail = if (working) "请稍候，正在与服务端核对数据" else "Excel 提供的信息会预填新增商品；应用后会留下价格历史。",
                active = true
            )
        }
        item {
            GovernmentCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ImportMetricRow("共识别", "${batch.metrics.parsedRows} 条")
                    ImportMetricRow("更新已有商品", "${batch.metrics.existingProductRows}")
                    ImportMetricRow("新增商品", "${batch.metrics.newProductRows}")
                    ImportMetricRow("需要确认", "${batch.metrics.needsReviewRows}")
                    ImportMetricRow("异常", "${batch.metrics.exceptionRows}")
                }
            }
        }
        if (batch.metrics.newProductRows > 0) {
            item { PriceImportDefaultsEditor(batch, working, viewModel) }
        }
        item {
            GovernmentSecondaryButton(
                text = "修改 Excel 字段识别",
                onClick = onEditFieldMapping,
                enabled = !working
            )
        }
        if (products.isEmpty() && batch.metrics.newProductRows > 0) {
            item {
                GovernmentInfoBanner(
                    title = "系统当前尚无商品",
                    message = "本次识别的 ${batch.metrics.newProductRows} 项会作为新增商品候选。设置默认资料后可一次确认导入。"
                )
            }
        }
        if (blockers.isNotEmpty()) {
            item {
                GovernmentInfoBanner(
                    title = "还有 ${blockers.size} 项需要处理",
                    message = "请选择系统商品、处理异常，或忽略本次不导入的项目。",
                    danger = true
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("全部", "待同步", "待新增", "需要确认", "异常").forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { onFilterChange(item) },
                        label = { Text(item) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
        if (filteredRows.isEmpty()) {
            item { ImportEmptyState("当前筛选没有报价行", "可以切换筛选条件查看其他项目。") }
        } else {
            itemsIndexed(filteredRows, key = { _, row -> row.id }) { _, row ->
                PriceImportRowCard(
                    row = row,
                    working = working,
                    onChooseProduct = { onChooseProduct(row) },
                    onEditNewProduct = { onEditNewProduct(row) },
                    onIgnore = { viewModel.ignorePriceImportRow(row.id) }
                )
            }
        }
        item {
            GovernmentPrimaryButton(
                text = if (working) "正在应用" else importApplyText(batch),
                onClick = onRequestApply,
                enabled = !working && blockers.isEmpty() && batch.metrics.readyRows > 0
            )
        }
    }
}

@Composable
private fun PriceImportDefaultsEditor(batch: PriceImportBatch, working: Boolean, viewModel: SupplyViewModel) {
    var category by remember(batch.id, batch.newProductDefaults.category) { mutableStateOf(batch.newProductDefaults.category) }
    var spec by remember(batch.id, batch.newProductDefaults.spec) { mutableStateOf(batch.newProductDefaults.spec) }
    var stock by remember(batch.id, batch.newProductDefaults.stockQuantity) { mutableStateOf(batch.newProductDefaults.stockQuantity) }
    var fallbackUnit by remember(batch.id, batch.newProductDefaults.fallbackUnit) { mutableStateOf(batch.newProductDefaults.fallbackUnit) }
    var supplyStatus by remember(batch.id, batch.newProductDefaults.supplyStatus) { mutableStateOf(batch.newProductDefaults.supplyStatus) }
    GovernmentCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GovernmentSectionHeader("新增商品默认设置")
            Text("只会补齐 Excel 未提供的字段。Excel 中明确填写的规格、单位和库存会优先保留。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            Text("分类", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                importCategories.take(4).forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                importCategories.drop(4).forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("默认规格") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("默认初始库存") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Excel 缺失单位时使用", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                importUnits.take(6).forEach { item ->
                    FilterChip(selected = fallbackUnit == item, onClick = { fallbackUnit = item }, label = { Text(item.ifBlank { "按 Excel 单位" }) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                importUnits.drop(6).forEach { item ->
                    FilterChip(selected = fallbackUnit == item, onClick = { fallbackUnit = item }, label = { Text(item) })
                }
            }
            Text("默认供应状态", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                importSupplyStatuses.forEach { (value, label) ->
                    FilterChip(selected = supplyStatus == value, onClick = { supplyStatus = value }, label = { Text(label) })
                }
            }
            GovernmentSecondaryButton(
                text = "应用到本批新增商品",
                onClick = {
                    viewModel.updatePriceImportDefaults(
                        PriceImportDefaults(category, spec, stock, supplyStatus, fallbackUnit, true)
                    )
                },
                enabled = !working && spec.isNotBlank() && stock.isNotBlank()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingLoadingSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("正在读取 Excel 字段", fontWeight = FontWeight.Bold)
            Text("请稍候", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceImportFieldMappingSheet(
    structure: PriceImportStructure,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (sheetName: String, headerRow: Int, mapping: Map<String, String>) -> Unit
) {
    val firstSheet = structure.sheets.firstOrNull()
    var sheetName by remember(structure.batchId) { mutableStateOf(firstSheet?.name.orEmpty()) }
    val sheet = structure.sheets.firstOrNull { it.name == sheetName } ?: firstSheet
    val maxPreviewRow = sheet?.preview?.size ?: 0
    var headerRowText by remember(structure.batchId, sheetName) {
        mutableStateOf((sheet?.headerCandidate ?: 1).coerceIn(1, maxPreviewRow.coerceAtLeast(1)).toString())
    }
    val headerRow = headerRowText.toIntOrNull()?.takeIf { it in 1..maxPreviewRow } ?: 0
    val headers = sheet?.preview?.getOrNull(headerRow - 1).orEmpty().filter { it.isNotBlank() }
    var mapping by remember(structure.batchId, sheetName, headerRow) {
        mutableStateOf(defaultPriceImportMapping(headers))
    }
    val canConfirm = !saving && sheet != null && headerRow > 0 &&
        mapping["product_name"].orEmpty().isNotBlank() && mapping["price"].orEmpty().isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("确认 Excel 字段", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择报价 Sheet、表头行和本次执行价格列后，服务端会重新分析本批报价。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            if (structure.sheets.isEmpty()) {
                item { ImportEmptyState("没有可用 Sheet", "请返回后重新选择 Excel 文件。") }
            } else {
                item {
                    PriceImportChoiceField(
                        label = "报价 Sheet",
                        value = sheetName,
                        options = structure.sheets.map { it.name },
                        onSelected = { sheetName = it }
                    )
                }
                item {
                    OutlinedTextField(
                        value = headerRowText,
                        onValueChange = { headerRowText = it.filter(Char::isDigit).take(2) },
                        label = { Text("表头行") },
                        supportingText = { Text("可确认前 ${maxPreviewRow.coerceAtLeast(1)} 行中的实际表头") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("选择字段", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("商品名称列和本次执行价格列为必选。其他字段只在 Excel 中存在时选择。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                items(importMappingFields, key = { it.first }) { (field, label) ->
                    PriceImportChoiceField(
                        label = label,
                        value = mapping[field].orEmpty(),
                        options = headers,
                        includeBlank = true,
                        onSelected = { mapping = mapping + (field to it) }
                    )
                }
                item {
                    GovernmentPrimaryButton(
                        text = if (saving) "正在重新识别" else "使用此字段重新识别",
                        onClick = { onConfirm(sheetName, headerRow, mapping) },
                        enabled = canConfirm
                    )
                }
                item {
                    GovernmentSecondaryButton(text = "返回核对", onClick = onDismiss, enabled = !saving)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceImportChoiceField(
    label: String,
    value: String,
    options: List<String>,
    includeBlank: Boolean = false,
    onSelected: (String) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.ifBlank { if (includeBlank) "不使用" else "请选择" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeBlank) {
                DropdownMenuItem(text = { Text("不使用") }, onClick = {
                    onSelected("")
                    expanded = false
                })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}

private fun defaultPriceImportMapping(headers: List<String>): Map<String, String> {
    fun findHeader(vararg words: String): String = headers.firstOrNull { header ->
        val normalized = header.lowercase().replace(" ", "")
        words.any { normalized.contains(it.lowercase().replace(" ", "")) }
    }.orEmpty()
    return mapOf(
        "product_name" to findHeader("商品名称", "货品名称", "菜品", "商品"),
        "product_code" to findHeader("商品编码", "货号", "编码"),
        "category" to findHeader("分类", "品类"),
        "spec" to findHeader("规格", "型号"),
        "unit" to findHeader("单位", "计量"),
        "stock" to findHeader("库存", "数量"),
        "price" to findHeader("执行价", "本期执行价", "今日价", "协议价", "报价", "单价", "价格")
    )
}

@Composable
private fun PriceImportRowCard(
    row: PriceImportRow,
    working: Boolean,
    onChooseProduct: () -> Unit,
    onEditNewProduct: () -> Unit,
    onIgnore: () -> Unit
) {
    val isNew = row.operationType == "NEW_PRODUCT"
    val needsSelection = row.validationStatus == "NEEDS_REVIEW" || row.operationType == "NEEDS_REVIEW" || row.matchedProductId.isBlank() && !isNew
    GovernmentCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.sourceProductName.ifBlank { "未填写商品名称" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(row.sourceSpec, row.sourceUnit).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Excel 未提供规格或单位" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PriceImportStatusTag(row)
            }
            if (isNew) {
                GovernmentDataRow("系统处理", "新增商品")
                GovernmentDataRow("商品编码", row.proposedProductCode.ifBlank { "需要确认" })
                GovernmentDataRow("分类", row.proposedCategory.ifBlank { "需要确认" })
                GovernmentDataRow("规格", row.proposedSpec.ifBlank { "散装" })
                GovernmentDataRow("单位", row.proposedUnit.ifBlank { "需要确认" })
                GovernmentDataRow("初始库存", row.proposedStockQuantity)
                GovernmentDataRow("供应状态", importSupplyStatuses.firstOrNull { it.first == row.proposedSupplyStatus }?.second ?: "暂停供应")
                GovernmentSecondaryButton(
                    text = "修改本项资料",
                    onClick = onEditNewProduct,
                    enabled = !working
                )
            } else {
                GovernmentDataRow("系统商品", row.matchedProductName.ifBlank { "请选择系统商品" })
                GovernmentDataRow("单位", row.systemUnit.ifBlank { row.sourceUnit.ifBlank { "--" } })
                GovernmentDataRow("当前价格", row.currentPriceCents?.let(Money::formatCents) ?: "--")
            }
            GovernmentDataRow("Excel 新价格", row.proposedPriceCents?.let(Money::formatCents) ?: "--")
            row.warning.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 19.sp) }
            if (needsSelection) {
                OutlinedButton(onClick = onChooseProduct, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("选择系统商品")
                }
            }
            if (row.validationStatus !in setOf("READY", "IGNORED")) {
                TextButton(onClick = onIgnore, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("忽略本项", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PriceImportNewProductDialog(
    row: PriceImportRow,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (PriceImportNewProductPatch) -> Unit
) {
    var code by remember(row.id, row.proposedProductCode) { mutableStateOf(row.proposedProductCode) }
    var category by remember(row.id, row.proposedCategory) { mutableStateOf(row.proposedCategory.ifBlank { "其他" }) }
    var spec by remember(row.id, row.proposedSpec) { mutableStateOf(row.proposedSpec.ifBlank { "散装" }) }
    var unit by remember(row.id, row.proposedUnit) { mutableStateOf(row.proposedUnit) }
    var stock by remember(row.id, row.proposedStockQuantity) { mutableStateOf(row.proposedStockQuantity.ifBlank { "0" }) }
    var supplyStatus by remember(row.id, row.proposedSupplyStatus) { mutableStateOf(row.proposedSupplyStatus.ifBlank { "paused" }) }
    val canSave = code.isNotBlank() && spec.isNotBlank() && unit.isNotBlank() && stock.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改本项资料", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        row.sourceProductName.ifBlank { "Excel 新增商品" },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    Text(
                        "这里只修改本次导入的这一项，不会影响其他新增商品。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("商品编码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("分类", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        importCategories.take(4).forEach { option ->
                            FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        importCategories.drop(4).forEach { option ->
                            FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = spec,
                        onValueChange = { spec = it },
                        label = { Text("规格") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("计量单位", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        importUnits.drop(1).take(5).forEach { option ->
                            FilterChip(selected = unit == option, onClick = { unit = option }, label = { Text(option) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        importUnits.drop(6).forEach { option ->
                            FilterChip(selected = unit == option, onClick = { unit = option }, label = { Text(option) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text("初始库存") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("供应状态", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        importSupplyStatuses.forEach { (value, label) ->
                            FilterChip(selected = supplyStatus == value, onClick = { supplyStatus = value }, label = { Text(label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PriceImportNewProductPatch(
                            productCode = code.trim(),
                            category = category,
                            spec = spec.trim(),
                            unit = unit,
                            stockQuantity = stock.trim(),
                            supplyStatus = supplyStatus
                        )
                    )
                },
                enabled = canSave && !saving
            ) { Text("保存本项资料") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun ProductPickerDialog(products: List<ProductEntity>, onDismiss: () -> Unit, onSelected: (ProductEntity) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    val rows = products.filter {
        keyword.isBlank() || it.name.contains(keyword, ignoreCase = true) || it.code.contains(keyword, ignoreCase = true)
    }.take(40)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择系统商品", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("搜索名称或编码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (rows.isEmpty()) {
                    Text("没有可选择的系统商品", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { it.id }) { product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelected(product) }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("${product.unit} · ${Money.formatCents(Money.yuanDoubleToCents(product.price))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("选择", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun PriceImportHistoryItem(batch: PriceImportBatch, onClick: () -> Unit) {
    GovernmentCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(batch.sourceFilename, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(batch.createdAt.replace("T", " ").take(19).ifBlank { "时间未记录" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(priceImportBatchStatus(batch.status), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PriceImportStatusTag(row: PriceImportRow) {
    val text = when {
        row.validationStatus == "IGNORED" -> "本次忽略"
        row.validationStatus == "READY" && row.operationType == "NEW_PRODUCT" -> "待新增"
        row.validationStatus == "READY" && row.operationType == "EXISTING_PRODUCT" -> "待同步"
        row.validationStatus == "NEEDS_REVIEW" || row.operationType == "NEEDS_REVIEW" -> "需要确认"
        row.validationStatus == "PRICE_CONFLICT" -> "重新确认"
        else -> "数据异常"
    }
    val color = when (text) {
        "待同步", "待新增" -> MaterialTheme.colorScheme.primary
        "需要确认", "重新确认" -> MaterialTheme.colorScheme.tertiary
        "本次忽略" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color, containerColor = color.copy(alpha = 0.10f))
    )
}

@Composable
private fun ImportMetricRow(label: String, value: String) {
    GovernmentDataRow(label, value, valueColor = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ImportEmptyState(title: String, message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

private fun importApplyText(batch: PriceImportBatch): String {
    val update = batch.rows.count { it.validationStatus == "READY" && it.operationType == "EXISTING_PRODUCT" }
    val create = batch.rows.count { it.validationStatus == "READY" && it.operationType == "NEW_PRODUCT" }
    return when {
        update > 0 && create > 0 -> "确认更新 $update 个价格并新增 $create 个商品"
        update > 0 -> "确认更新 $update 个价格"
        else -> "确认新增 $create 个商品"
    }
}

private fun priceImportBatchStatus(status: String): String = when (status) {
    "UPLOADED" -> "待分析"
    "ANALYZING" -> "分析中"
    "READY_FOR_REVIEW" -> "待确认"
    "APPLYING" -> "正在应用"
    "APPLIED" -> "已应用"
    "FAILED" -> "分析失败"
    else -> "已取消"
}

private fun importFileName(context: android.content.Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.getString(0)
    }.orEmpty().ifBlank { "已选择 Excel 文件" }
