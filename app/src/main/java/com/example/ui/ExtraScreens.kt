package com.smartprocurement.internal.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.R
import com.smartprocurement.internal.data.InviteInspectResult
import com.smartprocurement.internal.data.SystemOverview
import com.smartprocurement.internal.data.AppUpdatePolicy
import com.smartprocurement.internal.ui.designsystem.GovernmentBottomActionBar
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import kotlinx.coroutines.delay

fun extractInviteToken(rawValue: String): String {
    return runCatching {
        val value = rawValue.trim()
        if (value.startsWith("jingrongxianpei://invite")) {
            Uri.parse(value).getQueryParameter("token").orEmpty()
        } else if (value.startsWith("jingrongxianpei://register")) {
            Uri.parse(value).getQueryParameter("invite").orEmpty()
        } else {
            value
        }
    }.getOrDefault("")
}

@Composable
fun SystemStatusScreen(viewModel: SupplyViewModel) {
    LaunchedEffect(Unit) {
        viewModel.refreshSystemOverview()
        while (true) {
            delay(60_000)
            viewModel.refreshSystemOverview()
        }
    }
    val overview = viewModel.systemOverview
    Scaffold(
        topBar = { GovernmentTopBar(title = "系统运行与数据安全", onBack = { viewModel.navigateBack() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(statusTitle(overview.overallStatus), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("数据更新于 ${overview.checkedAt.ifBlank { "尚未同步" }}", color = GovernmentColors.TextSecondary)
                    GovernmentDataRow("连续运行", formatDuration(overview.uptimeSeconds))
                    GovernmentDataRow("API 平均响应", "${overview.performance.averageLatencyMs}ms")
                    GovernmentDataRow("API P95 响应", "${overview.performance.p95LatencyMs}ms")
                    GovernmentDataRow("最近 5 分钟请求", overview.performance.requestCount5m.toString())
                    GovernmentDataRow("最近错误数", overview.performance.errorCount5m.toString())
                    GovernmentPrimaryButton(text = "立即刷新", onClick = { viewModel.refreshSystemOverview() }, modifier = Modifier.fillMaxWidth())
                }
            }
            ResourceSection(overview)
            ServiceSection(overview)
            StorageSection(overview)
            CapacitySection(overview)
            BackupSection(overview)
            AlertSection(overview)
            BusinessSection(overview)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ResourceSection(overview: SystemOverview) {
    val memoryPercent = percent(overview.resources.memoryUsedBytes, overview.resources.memoryTotalBytes)
    val diskPercent = percent(overview.resources.diskUsedBytes, overview.resources.diskTotalBytes)
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("资源使用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (overview.resources.scope == "container") "应用容器资源" else "服务器资源", color = GovernmentColors.TextSecondary)
            MetricProgress("CPU 使用率", overview.resources.cpuPercent, "${overview.resources.cpuPercent}%")
            MetricProgress("内存使用", memoryPercent, "${formatBytes(overview.resources.memoryUsedBytes)} / ${formatBytes(overview.resources.memoryTotalBytes)}")
            MetricProgress("磁盘使用", diskPercent, "${formatBytes(overview.resources.diskUsedBytes)} / ${formatBytes(overview.resources.diskTotalBytes)}")
            GovernmentDataRow("数据库大小", formatBytes(overview.storage.databaseBytes))
            GovernmentDataRow("商品图片", formatBytes(overview.storage.productImagesBytes))
            GovernmentDataRow("发货照片", formatBytes(overview.storage.shippingPhotosBytes))
            GovernmentDataRow("收货异常照片", formatBytes(overview.storage.receiptIssuePhotosBytes))
            GovernmentDataRow("备份占用", formatBytes(overview.storage.backupsBytes))
        }
    }
}

@Composable
private fun MetricProgress(label: String, percent: Double, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(resourceStatus(percent), color = GovernmentColors.TextSecondary)
        }
        LinearProgressIndicator(progress = { (percent / 100.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(value, color = GovernmentColors.TextSecondary)
    }
}

@Composable
private fun ServiceSection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("服务状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GovernmentDataRow("API 服务", serviceLabel(overview.services.api))
            GovernmentDataRow("数据库", serviceLabel(overview.services.database))
            GovernmentDataRow("图片存储", serviceLabel(overview.services.uploads))
            GovernmentDataRow("备份任务", serviceLabel(overview.services.backup))
            GovernmentDataRow("Web 管理端", serviceLabel(overview.services.web))
            GovernmentDataRow("短信服务", serviceLabel(overview.services.sms))
            GovernmentDataRow("App 会话", overview.sessions.activeAppSessions.toString())
            GovernmentDataRow("Web 会话", overview.sessions.activeWebSessions.toString())
        }
    }
}

@Composable
private fun StorageSection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据与存储", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GovernmentDataRow("启用单位", overview.activeUnits.toString())
            GovernmentDataRow("启用账号", overview.activeUsers.toString())
            GovernmentDataRow("商品数量", overview.products.toString())
            GovernmentDataRow("收货异常", overview.openReceiptIssues.toString())
        }
    }
}

@Composable
private fun CapacitySection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前负载评估", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(capacityLabel(overview.capacity.status), fontWeight = FontWeight.Bold)
            Text(overview.capacity.summary, color = GovernmentColors.TextSecondary)
            GovernmentDataRow("API P95 延迟", "${overview.capacity.apiP95LatencyMs}ms")
            GovernmentDataRow("请求错误率", "${overview.capacity.errorRatePercent}%")
            GovernmentDataRow("SQLite 写锁", overview.capacity.sqliteLockCount24h.toString())
            Text(overview.capacity.disclaimer, color = GovernmentColors.TextSecondary)
        }
    }
}

@Composable
private fun BackupSection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GovernmentDataRow("最近备份", overview.latestBackup.createdAt.ifBlank { "暂无记录" })
            GovernmentDataRow("备份状态", backupLabel(overview.latestBackup.status))
            GovernmentDataRow("完整性校验", if (overview.latestBackup.verified) "校验通过" else "未通过或未校验")
            GovernmentDataRow("备份大小", formatBytes(overview.latestBackup.sizeBytes))
            GovernmentDataRow("异地备份", if (overview.latestBackup.offsiteSynced) "已同步" else "异地备份未启用")
            GovernmentDataRow("数据库版本", overview.latestBackup.databaseVersion.ifBlank { "未知" })
        }
    }
}

@Composable
private fun AlertSection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("风险提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (overview.alerts.isEmpty()) {
                Text("暂无需要处理的系统提醒", color = GovernmentColors.TextSecondary)
            } else {
                overview.alerts.take(5).forEach { alert ->
                    Text("${alertLevel(alert.level)}：${alert.title}", fontWeight = FontWeight.Medium)
                    Text(alert.impact, color = GovernmentColors.TextSecondary)
                    if (alert.suggestion.isNotBlank()) Text(alert.suggestion, color = GovernmentColors.TextSecondary)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun BusinessSection(overview: SystemOverview) {
    GovernmentCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("运行概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GovernmentDataRow("今日订单", overview.todayOrders.toString())
            GovernmentDataRow("待接单", overview.pendingOrders.toString())
            GovernmentDataRow("备货中", overview.preparingOrders.toString())
            GovernmentDataRow("已发货", overview.shippedOrders.toString())
        }
    }
}

private fun statusTitle(status: String): String = when (status) {
    "critical" -> "系统存在严重问题"
    "warning" -> "系统需要关注"
    else -> "系统运行正常"
}

private fun resourceStatus(percent: Double): String = when {
    percent >= 90 -> "资源不足"
    percent >= 75 -> "接近警戒值"
    else -> "使用正常"
}

private fun serviceLabel(status: String): String = when (status) {
    "healthy" -> "正常"
    "disabled" -> "未启用"
    "unconfigured" -> "未配置"
    "error" -> "异常"
    else -> "未知"
}

private fun capacityLabel(status: String): String = when (status) {
    "risk" -> "存在性能风险"
    "expand_recommended" -> "建议扩容"
    "moderate" -> "容量余量一般"
    else -> "容量余量充足"
}

private fun backupLabel(status: String): String = when (status) {
    "success" -> "备份成功"
    "running" -> "正在备份"
    "queued" -> "等待执行"
    "failed" -> "备份失败"
    "corrupted" -> "备份文件损坏"
    else -> "暂无备份"
}

private fun alertLevel(level: String): String = when (level) {
    "critical" -> "严重"
    "warning" -> "提醒"
    else -> "信息"
}

private fun percent(used: Long, total: Long): Double = if (total <= 0) 0.0 else (used.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) "${bytes}B" else String.format("%.1f%s", value, units[index])
}

private fun formatDuration(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days} 天 ${hours} 小时"
        hours > 0 -> "${hours} 小时 ${minutes} 分钟"
        else -> "${minutes} 分钟"
    }
}

@Composable
fun AboutUpdateScreen(viewModel: SupplyViewModel) {
    val release = viewModel.appUpdateCheckResult.release
    Scaffold(
        topBar = { GovernmentTopBar(title = "关于与更新", onBack = { viewModel.navigateBack() }) },
        bottomBar = {
            GovernmentBottomActionBar {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (release != null) {
                        GovernmentPrimaryButton(
                            text = if (viewModel.isDownloadingAppUpdate) "正在下载 ${viewModel.appUpdateDownloadProgress}%" else "下载并安装",
                            enabled = !viewModel.isDownloadingAppUpdate && !viewModel.isCheckingAppUpdate,
                            onClick = { viewModel.downloadAndInstallUpdate() }
                        )
                    }
                    GovernmentSecondaryButton(
                        text = if (viewModel.isCheckingAppUpdate) "正在检查" else "检查更新",
                        enabled = !viewModel.isCheckingAppUpdate && !viewModel.isDownloadingAppUpdate,
                        onClick = { viewModel.checkForAppUpdate(showNoUpdate = true) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("三公鲜配", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    GovernmentDataRow("当前版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    GovernmentDataRow("版本渠道", if (AppUpdatePolicy.channelForVariant(BuildConfig.APP_VARIANT_LABEL) == "production") "正式版" else "测试版")
                    if (viewModel.isDownloadingAppUpdate) {
                        LinearProgressIndicator(progress = { viewModel.appUpdateDownloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    GovernmentSecondaryButton(
                        text = "查看使用引导",
                        onClick = { viewModel.viewOnboardingGuide() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("新版本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (release == null) {
                        Text("暂无可用更新", color = GovernmentColors.TextSecondary)
                    } else {
                        GovernmentDataRow("版本号", "${release.versionName} (${release.versionCode})")
                        GovernmentDataRow("更新方式", if (release.mandatory) "必须更新" else "可选更新")
                        release.releaseNotes.forEach { note -> Text(note, color = GovernmentColors.TextPrimary) }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun HelpTutorialScreen(viewModel: SupplyViewModel) {
    val isAdmin = viewModel.canManageIngredients()
    var showFullWorkflow by remember { mutableStateOf(false) }
    val sections = if (isAdmin) {
        listOf(
            "完善单位" to listOf("进入子单位管理", "填写单位名称、编码", "设置默认配送点"),
            "创建账号" to listOf("进入账号管理", "创建子单位账号", "初始密码只告知使用人"),
            "维护食材" to listOf("进入食材列表", "维护名称、规格、价格", "核对库存和供应状态"),
            "处理订单" to listOf("进入订单管理", "按状态接单、备货", "上传照片并确认发货"),
            "备货配送" to listOf("查看当前备货", "查看单位配送", "导出 Excel 清单"),
            "检查系统" to listOf("进入系统状态", "确认服务和备份", "核查 Web 会话情况")
        )
    } else {
        listOf(
            "账号登录" to listOf("使用管理员分配账号", "登录三公鲜配 App", "首次登录后确认单位信息"),
            "浏览食材" to listOf("进入食材申领", "查看规格、价格、库存", "确认今日可申领状态"),
            "加入清单" to listOf("选择所需食材", "填写申领数量", "核对默认配送点"),
            "提交订单" to listOf("进入采购清单", "核对明细和数量", "提交后到我的订单查看"),
            "确认收货" to listOf("订单发货后查看照片", "核对食材与数量", "无误后点击确认收货"),
            "异常说明" to listOf("数量或质量异常", "按页面提示填写说明", "等待管理员处理")
        )
    }
    val workflowImage = if (isAdmin) R.drawable.workflow_admin_tutorial else R.drawable.workflow_unit_tutorial
    Scaffold(
        topBar = { GovernmentTopBar(title = "帮助与教程", onBack = { viewModel.navigateBack() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("三公鲜配", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (isAdmin) "管理员常用流程" else "子单位常用流程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isAdmin) "管理员公测上线操作指引" else "子单位食材申领操作指引",
                        color = GovernmentColors.TextSecondary
                    )
                    GovernmentSecondaryButton(
                        text = if (showFullWorkflow) "收起完整流程图" else "查看完整流程图",
                        onClick = { showFullWorkflow = !showFullWorkflow },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showFullWorkflow) {
                        Image(
                            painter = painterResource(workflowImage),
                            contentDescription = if (isAdmin) "管理员常用流程图" else "子单位常用流程图",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
            sections.forEachIndexed { index, section ->
                GovernmentCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${section.first}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        section.second.forEach { step ->
                            Text("• $step", color = GovernmentColors.TextPrimary)
                        }
                    }
                }
            }
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("常见问题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("忘记密码、账号停用或单位信息不正确时，请联系系统管理员处理。", color = GovernmentColors.TextSecondary)
                    Text("网络失败时请不要重复快速点击，等待页面提示后再重试。", color = GovernmentColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun InviteEntryScreen(viewModel: SupplyViewModel) {
    var inviteToken by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var phoneCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val inspected = viewModel.inviteInspectResult
    Scaffold(
        topBar = { GovernmentTopBar(title = "邀请码加入", onBack = { viewModel.navigateBack() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("已有账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("登录成功后由服务器返回真实身份和所属单位。", color = GovernmentColors.TextSecondary)
                    GovernmentPrimaryButton(
                        text = "使用已有账号登录",
                        onClick = { viewModel.openLoginFromOnboarding("account_login") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            GovernmentCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("使用邀请码加入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "邀请码必须由系统管理员签发。管理者权限需要系统管理员审批，单位归属由服务端绑定。",
                        color = GovernmentColors.TextSecondary
                    )
                    OutlinedTextField(
                        value = inviteToken,
                        onValueChange = { inviteToken = it },
                        label = { Text("邀请码或扫码内容") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 1
                    )
                    GovernmentSecondaryButton(
                        text = if (viewModel.inviteRegistrationLoading) "正在验证" else "验证邀请码",
                        enabled = !viewModel.inviteRegistrationLoading,
                        onClick = { viewModel.inspectInvite(extractInviteToken(inviteToken)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inspected?.valid == true) {
                        InviteSummary(inspected)
                        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("账号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("显示名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (inspected.phoneRequired) {
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("手机号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = phoneCode, onValueChange = { phoneCode = it }, label = { Text("验证码") }, modifier = Modifier.weight(1f), singleLine = true)
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { viewModel.sendRegisterPhoneCode(phone) }) { Text("发送") }
                            }
                            GovernmentSecondaryButton(text = "验证手机号", onClick = { viewModel.verifyRegisterPhoneCode(phone, phoneCode) }, modifier = Modifier.fillMaxWidth())
                        } else {
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("手机号（选填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        GovernmentPrimaryButton(
                            text = if (viewModel.inviteRegistrationLoading) "正在提交" else if (inspected.inviteType == "manager") "提交申请" else "创建账号",
                            enabled = !viewModel.inviteRegistrationLoading,
                            onClick = { viewModel.registerWithInvite(username, displayName, password, confirmPassword, phone) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Text(
                "身份权限由服务端邀请码或已分配账号确定，客户端选择不会改变真实角色。",
                color = GovernmentColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun InviteSummary(result: InviteInspectResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Divider()
        Text("邀请信息", fontWeight = FontWeight.Bold)
        GovernmentDataRow("邀请类型", result.roleLabel)
        GovernmentDataRow("签发单位", result.issuerOrg.ifBlank { "三公鲜配" })
        GovernmentDataRow("签发人", result.issuerName.ifBlank { "系统管理员" })
        if (result.inviteType == "unit") {
            GovernmentDataRow("单位名称", result.unitName)
            GovernmentDataRow("单位编码", result.unitCode)
            GovernmentDataRow("默认配送点", result.deliveryPoint)
        }
        GovernmentDataRow("有效期", result.expiresAt)
        GovernmentDataRow("剩余次数", result.remainingUses.toString())
        GovernmentDataRow("手机验证", if (result.phoneRequired) "需要" else "不需要")
    }
}
