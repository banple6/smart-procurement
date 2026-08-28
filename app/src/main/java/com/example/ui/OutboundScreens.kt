package com.smartprocurement.internal.ui

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.smartprocurement.internal.data.OutboundOrder
import com.smartprocurement.internal.domain.money.Money
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundsScreen(viewModel: SupplyViewModel, requestWorkbookDocument: WorkbookDocumentRequest) {
    LaunchedEffect(Unit) { viewModel.refreshOutboundOrders() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("出库单", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshOutboundOrders) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isOutboundLoading && viewModel.outboundOrders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            }
        } else if (viewModel.outboundOrders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无出库单。完成备货后，可在备货单详情中按单位生成出库单。", modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(viewModel.outboundOrders, key = { it.id }) { outbound ->
                    OutboundRow(outbound, onClick = { viewModel.openOutboundOrder(outbound.id) })
                }
            }
        }
    }
}

@Composable
private fun OutboundRow(outbound: OutboundOrder, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(outbound.outboundNo, fontWeight = FontWeight.Bold)
                Text(outboundStatusLabel(outbound.status), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(outbound.unitName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${outbound.orderCount} 笔订单 · ${outbound.productCount} 种食材 · ${outbound.batchNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("生成时间：${outbound.createdAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundDetailScreen(
    outboundId: String,
    viewModel: SupplyViewModel,
    requestWorkbookDocument: WorkbookDocumentRequest
) {
    val outbound = viewModel.activeOutboundOrder?.takeIf { it.id == outboundId }
    LaunchedEffect(outboundId) { viewModel.refreshOutboundOrder(outboundId) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("出库单详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshOutboundOrder(outboundId) }) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                }
            )
        }
    ) { padding ->
        if (outbound == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (viewModel.isOutboundLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) else Text("出库单暂不可用")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(outbound.outboundNo, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            DetailLine("状态", outboundStatusLabel(outbound.status))
                            DetailLine("收货单位", outbound.unitName)
                            DetailLine("配送点", outbound.deliveryPoint.ifBlank { "未设置" })
                            DetailLine("来源备货单", outbound.batchNo)
                            DetailLine("生成时间", outbound.createdAt)
                            if (outbound.shippedAt.isNotBlank()) DetailLine("发货时间", outbound.shippedAt)
                            OutlinedButton(
                                onClick = {
                                    requestWorkbookDocument(
                                        ExternalActionType.OUTBOUND_EXPORT,
                                        outbound.id,
                                        "出库单_${outbound.unitName}_${outbound.outboundNo}.xlsx"
                                    )
                                },
                                enabled = !viewModel.isDocumentExportBusy(ExternalActionType.OUTBOUND_EXPORT),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text(if (viewModel.isDocumentExportBusy(ExternalActionType.OUTBOUND_EXPORT)) "正在保存…" else "导出出库单") }
                            if (outbound.status == "pending") {
                                Button(
                                    onClick = { viewModel.navigateTo(Screen.OutboundShippingProof(outbound.id)) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) { Text("拍照并确认发货") }
                            }
                        }
                    }
                }
                item { Text("食材明细", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                items(outbound.lines, key = { "${it.productId}:${it.unit}" }) { line ->
                    Card(shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.productName, fontWeight = FontWeight.Bold)
                                Text("${line.category} · ${line.spec}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${line.quantity} ${line.unit}", fontWeight = FontWeight.Bold)
                                Text(Money.formatCents(line.subtotalCents), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Text("关联订单", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp)) }
                items(outbound.orders, key = { it.id }) { order ->
                    OutlinedButton(onClick = { viewModel.navigateTo(Screen.OrderDetails(order.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Text("${order.orderNo} · ${order.status}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundShippingProofScreen(outboundId: String, viewModel: SupplyViewModel) {
    val context = LocalContext.current
    val outbound = viewModel.activeOutboundOrder?.takeIf { it.id == outboundId }
    var note by rememberSaveable(outboundId) { mutableStateOf("") }
    var photoPaths by rememberSaveable(outboundId) { mutableStateOf<List<String>>(emptyList()) }
    val uploading = viewModel.activeOutboundShippingId == outboundId
    LaunchedEffect(outboundId) { viewModel.refreshOutboundOrder(outboundId) }

    fun cleanPhotos() {
        photoPaths.forEach { File(it).delete() }
        photoPaths = emptyList()
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = viewModel.consumeOutboundShippingCamera(outboundId).orEmpty()
        if (ok && path.isNotBlank() && File(path).length() > 0) photoPaths = (photoPaths + path).take(3)
        else if (path.isNotBlank()) File(path).delete()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            viewModel.alertMessage = "需要相机权限才能拍照"
            return@rememberLauncherForActivityResult
        }
        val file = File(context.cacheDir, "camera/outbound-${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
        viewModel.beginOutboundShippingCamera(outboundId, file.absolutePath)
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("出库发货", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { cleanPhotos(); viewModel.navigateBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.submitOutboundShippingProof(outboundId, photoPaths.map(::File), note) {
                            cleanPhotos()
                            viewModel.navigateBack()
                        }
                    },
                    enabled = outbound?.status == "pending" && photoPaths.isNotEmpty() && !uploading,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp)
                ) { Text(if (uploading) "正在确认发货…" else "上传照片并确认发货") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (outbound == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            if (outbound.status != "pending") Text("出库单状态已发生变化，请返回详情刷新。", color = MaterialTheme.colorScheme.error)
            Text("${outbound.unitName} · ${outbound.outboundNo}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("请拍摄装车或交接前照片。提交后会同步更新该出库单关联的全部订单。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            OutlinedButton(
                onClick = {
                    if (Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) == null) viewModel.alertMessage = "当前设备无法打开相机"
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                enabled = photoPaths.size < 3 && !uploading && outbound.status == "pending",
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("拍摄发货照片（${photoPaths.size}/3）") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photoPaths.forEach { path ->
                    AsyncImage(model = File(path), contentDescription = "待上传发货照片", modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).clickable { File(path).delete(); photoPaths = photoPaths - path }, contentScale = ContentScale.Crop)
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(120) }, label = { Text("发货备注（选填）") }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !uploading)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun outboundStatusLabel(status: String): String = when (status) {
    "pending" -> "待发货"
    "shipped" -> "已发货"
    "archived" -> "已归档"
    else -> status
}
