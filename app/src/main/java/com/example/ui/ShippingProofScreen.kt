package com.smartprocurement.internal.ui

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.smartprocurement.internal.ui.designsystem.GovernmentBottomActionBar
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentInfoBanner
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingProofScreen(orderId: String, viewModel: SupplyViewModel) {
    val context = LocalContext.current
    val orderFlow = remember(orderId) { viewModel.getOrderFlow(orderId) }
    val order by orderFlow.collectAsState(initial = null)
    var note by remember { mutableStateOf("") }
    var photoPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoPath by remember { mutableStateOf("") }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    val uploading = viewModel.activeShippingUploadId == orderId

    fun cleanupLocalPhotos() {
        photoPaths.forEach { File(it).delete() }
        pendingPhotoPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        photoPaths = emptyList()
        pendingPhotoPath = ""
    }

    LaunchedEffect(orderId) {
        viewModel.refreshOrderDetail(orderId)
    }

    fun leavePage() {
        if (photoPaths.isNotEmpty() && !uploading) {
            showExitConfirm = true
        } else {
            viewModel.navigateBack()
        }
    }

    BackHandler { leavePage() }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhotoPath
        pendingPhotoPath = ""
        if (ok && path.isNotBlank() && File(path).length() > 0) {
            photoPaths = (photoPaths + path).take(3)
        } else if (path.isNotBlank()) {
            File(path).delete()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            viewModel.alertMessage = "需要相机权限才能拍照"
            return@rememberLauncherForActivityResult
        }
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shipping-${UUID.randomUUID()}.jpg")
        pendingPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePicture.launch(uri)
    }

    val ord = order
    Scaffold(
        topBar = {
            GovernmentTopBar(title = "发货凭证", onBack = { leavePage() })
        },
        bottomBar = {
            GovernmentBottomActionBar {
                GovernmentPrimaryButton(
                    text = if (uploading) "正在上传" else "上传照片并确认发货",
                    onClick = {
                        viewModel.submitShippingProof(
                            orderId = orderId,
                            photoFiles = photoPaths.map(::File),
                            note = note,
                        ) {
                            cleanupLocalPhotos()
                            viewModel.popToRootAndNavigate(Screen.Home)
                        }
                    },
                    enabled = photoPaths.isNotEmpty() && !uploading && ord?.status == "备货中",
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GovernmentColors.PageBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ord == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }
            if (ord.status != "备货中") {
                GovernmentInfoBanner(
                    title = "订单状态已变化",
                    message = "当前订单不是备货中，不能上传发货照片。",
                    danger = true
                )
            }
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("订单：${ord.displayOrderNo}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("收货单位：${ord.department}", style = MaterialTheme.typography.bodyMedium)
                    Text("配送点：${ord.deliveryPoint}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            GovernmentInfoBanner(
                title = "拍照留存",
                message = "请拍摄装车或交接前的食材照片，至少 1 张，最多 3 张。"
            )
            OutlinedButton(
                onClick = {
                    if (photoPaths.size >= 3) return@OutlinedButton
                    val hasCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
                    if (!hasCamera) {
                        viewModel.alertMessage = "当前设备无法打开相机"
                        return@OutlinedButton
                    }
                    cameraPermission.launch(Manifest.permission.CAMERA)
                },
                enabled = photoPaths.size < 3 && !uploading && ord.status == "备货中",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (photoPaths.size >= 3) "已达到 3 张上限" else "拍摄发货照片", fontWeight = FontWeight.Bold)
            }
            Text("已拍照片：${photoPaths.size}/3", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (photoPaths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    photoPaths.forEach { path ->
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "发货照片",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { previewPath = path },
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    File(path).delete()
                                    photoPaths = photoPaths.filterNot { it == path }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(120) },
                label = { Text("发货备注（选填）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !uploading
            )
        }
    }

    previewPath?.let { path ->
        Dialog(onDismissRequest = { previewPath = null }) {
            Box(modifier = Modifier.fillMaxWidth().height(420.dp).background(GovernmentColors.SurfaceWhite, RoundedCornerShape(10.dp))) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "发货照片预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出后已拍照片不会保留，确认退出吗？") },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    cleanupLocalPhotos()
                    viewModel.navigateBack()
                }) { Text("确认退出") }
            }
        )
    }
}
