package com.smartprocurement.internal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smartprocurement.internal.data.WebSessionRecord
import com.smartprocurement.internal.ui.designsystem.GovernmentBottomActionBar
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentStatusLabel
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

fun extractWebLoginToken(rawValue: String): String {
    return runCatching {
        val prefix = "jingrongxianpei://web-login?token="
        if (!rawValue.startsWith(prefix)) return@runCatching ""
        URLDecoder.decode(rawValue.removePrefix(prefix).substringBefore('&'), StandardCharsets.UTF_8.name())
    }.getOrDefault("")
}

private fun String.toDisplayTime(): String = replace('T', ' ').take(16).ifBlank { "未知" }

@Composable
fun WebQrScannerScreen(viewModel: SupplyViewModel) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) viewModel.alertMessage = "请允许使用相机后再扫码"
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(topBar = { GovernmentTopBar(title = "扫码登录网页版", onBack = { viewModel.navigateBack() }) }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (hasPermission) {
                QrCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onQrCode = { value ->
                        val token = extractWebLoginToken(value)
                        if (token.isBlank()) {
                            viewModel.alertMessage = "这不是三公鲜配的网页登录二维码"
                        } else {
                            viewModel.scanWebLoginQr(token)
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("将网站登录二维码放入框内", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("仅扫描三公鲜配管理平台二维码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("需要使用相机扫码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("系统不会保存或上传相机画面。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GovernmentPrimaryButton(text = "允许相机权限", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
                    GovernmentSecondaryButton(
                        text = "前往设置",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (viewModel.webLoginActionLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@AndroidXOptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(modifier: Modifier = Modifier, onQrCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var handledValue by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull()?.rawValue.orEmpty()
                                if (value.isNotBlank()) {
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        if (value != handledValue) {
                                            handledValue = value
                                            onQrCode(value)
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                },
                ContextCompat.getMainExecutor(ctx)
            )
            previewView
        }
    )
}

@Composable
fun WebLoginConfirmScreen(viewModel: SupplyViewModel) {
    val scan = viewModel.pendingWebLogin
    var showAdminConfirm by remember { mutableStateOf(false) }

    if (showAdminConfirm) {
        AlertDialog(
            onDismissRequest = { showAdminConfirm = false },
            title = { Text("确认登录网页版？", fontWeight = FontWeight.Bold) },
            text = { Text("请再次确认是你本人正在操作的电脑。确认后，当前账号会在该浏览器登录对应网页版。") },
            confirmButton = {
                TextButton(onClick = {
                    showAdminConfirm = false
                    viewModel.approveWebLogin()
                }) { Text("确认登录") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { GovernmentTopBar(title = "网页登录确认", onBack = { viewModel.navigateBack() }) },
        bottomBar = {
            GovernmentBottomActionBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GovernmentSecondaryButton(
                        text = "拒绝登录",
                        enabled = scan != null && !viewModel.webLoginActionLoading,
                        onClick = { viewModel.rejectWebLogin() },
                        modifier = Modifier.weight(1f)
                    )
                    GovernmentPrimaryButton(
                        text = "确认登录",
                        enabled = scan != null && !viewModel.webLoginActionLoading,
                        onClick = {
                            if (viewModel.canManageIngredients()) showAdminConfirm = true else viewModel.approveWebLogin()
                        }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (scan == null) {
                GovernmentCard {
                    Text("没有待确认的网页登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                GovernmentCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GovernmentColors.GovernmentBlue)
                            Text(
                                if (scan.targetRole == "admin") "即将登录三公鲜配管理后台" else "即将登录三公鲜配单位网页版",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        GovernmentDataRow("账号", scan.displayName.ifBlank { scan.username.ifBlank { "当前账号" } })
                        GovernmentDataRow("所属单位", scan.unitName.ifBlank { if (scan.targetRole == "admin") "管理员" else "所属单位" })
                        GovernmentDataRow("浏览器", scan.browser.name.ifBlank { "浏览器" })
                        GovernmentDataRow("操作系统", scan.browser.os.ifBlank { "未知系统" })
                        GovernmentDataRow("登录地址", scan.browser.ip.ifBlank { "未知" })
                        GovernmentDataRow("登录时间", scan.createdAt.toDisplayTime())
                        GovernmentDataRow("有效期至", scan.expiresAt.toDisplayTime())
                    }
                }
                Text(
                    "请确认是你本人正在操作的电脑。不要确认来源不明的登录请求。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GovernmentColors.StatusError
                )
            }
        }
    }
}

@Composable
fun WebLoginResultScreen(viewModel: SupplyViewModel) {
    Scaffold(
        topBar = { GovernmentTopBar(title = "网页登录") },
        bottomBar = {
            GovernmentBottomActionBar {
                GovernmentPrimaryButton(
                    text = "完成",
                    onClick = { viewModel.popToRootAndNavigate(Screen.Home) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GovernmentColors.GovernmentBlue, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                viewModel.webLoginResultTitle.ifBlank { "网页登录请求已处理" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (viewModel.webLoginResultMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(viewModel.webLoginResultMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WebSessionsScreen(viewModel: SupplyViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshWebSessions() }

    Scaffold(
        topBar = {
            GovernmentTopBar(
                title = "网页登录记录",
                onBack = { viewModel.navigateBack() },
                actionText = "刷新",
                actionIcon = Icons.Default.Refresh,
                onAction = { viewModel.refreshWebSessions() }
            )
        },
        bottomBar = {
            if (viewModel.webSessionRecords.any { it.active }) {
                GovernmentBottomActionBar {
                    GovernmentSecondaryButton(
                        text = "退出全部网页登录",
                        enabled = !viewModel.webLoginActionLoading,
                        onClick = { viewModel.revokeAllWebSessions() },
                        modifier = Modifier.fillMaxWidth()
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.webSessionRecords.isEmpty()) {
                GovernmentCard {
                    Text("暂无网页登录记录", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                viewModel.webSessionRecords.forEach { record ->
                    WebSessionCard(record = record, viewModel = viewModel)
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun WebSessionCard(record: WebSessionRecord, viewModel: SupplyViewModel) {
    GovernmentCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${record.browserName.ifBlank { "浏览器" }} · ${record.browserOs.ifBlank { "未知系统" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                GovernmentStatusLabel(if (record.active) "当前在线" else "已退出")
            }
            GovernmentDataRow("登录时间", record.createdAt.ifBlank { "未知" })
            GovernmentDataRow("最近活动", record.lastSeenAt.ifBlank { "未知" })
            GovernmentDataRow("地址", record.browserIp.ifBlank { "未知" })
            GovernmentDataRow("设备", record.deviceName.ifBlank { "Android 设备" })
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            if (record.active) {
                GovernmentSecondaryButton(
                    text = "退出该设备",
                    enabled = !viewModel.webLoginActionLoading,
                    onClick = { viewModel.revokeWebSession(record.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("该登录已失效", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
