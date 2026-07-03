package com.smartprocurement.internal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.R
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.GovernmentBottomActionBar
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDataRow
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentStatusLabel
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import com.smartprocurement.internal.ui.designsystem.PoliceBrandConfig
import com.smartprocurement.internal.ui.designsystem.PoliceColors
import com.smartprocurement.internal.ui.designsystem.PoliceIdentityHeader

@Composable
fun SubmitSuccessScreen(orderId: String, viewModel: SupplyViewModel) {
    val orderFlow = remember(orderId) { viewModel.getOrderFlow(orderId) }
    val order by orderFlow.collectAsState(initial = null)
    val displayOrderNo = order?.displayOrderNo.orEmpty().ifBlank { "订单号生成中" }
    val deliveryPoint = order?.deliveryPoint.orEmpty().ifBlank { viewModel.defaultDeliveryPoint.ifBlank { "未设置" } }
    val totalCents = order?.totalCents ?: 0L

    Scaffold(
        topBar = {
            GovernmentTopBar(title = "提交成功")
        },
        bottomBar = {
            GovernmentBottomActionBar {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GovernmentPrimaryButton(
                        text = "查看订单",
                        onClick = {
                            viewModel.currentTab = "orders"
                            viewModel.popToRootAndNavigate(Screen.Home)
                            viewModel.navigateTo(Screen.OrderDetails(orderId))
                        }
                    )
                    GovernmentSecondaryButton(
                        text = "返回首页",
                        onClick = {
                            viewModel.currentTab = "home"
                            viewModel.popToRootAndNavigate(Screen.Home)
                        },
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
                .background(GovernmentColors.PageBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                GovernmentCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GovernmentColors.StatusCompleted)
                            Text("提交成功", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GovernmentColors.TextPrimary)
                        }
                        GovernmentDataRow("订单编号", displayOrderNo)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("当前状态", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                            GovernmentStatusLabel("待接单")
                        }
                        GovernmentDataRow("配送点", deliveryPoint)
                        GovernmentDataRow("订单金额", Money.formatCents(totalCents), valueColor = GovernmentColors.GovernmentBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}

@Composable
fun ProfileScreen(viewModel: SupplyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        if (viewModel.canManageIngredients()) {
            PoliceIdentityHeader(
                title = "系统管理员",
                line1 = "账号：${viewModel.userName.ifBlank { viewModel.userId }}",
                line2 = PoliceBrandConfig.logisticsSubtitle
            )
        } else {
            PoliceIdentityHeader(
                title = viewModel.userName.ifBlank { "子单位采购账号" },
                line1 = "所属单位：${viewModel.currentUnitName}",
                line2 = "默认配送点：${viewModel.defaultDeliveryPoint}"
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column {
                    if (viewModel.canManageIngredients()) {
                        ProfileResourceMenuItem(icon = painterResource(R.drawable.ic_qr_scan), title = "扫码登录网页版") { viewModel.openWebQrScanner() }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileResourceMenuItem(icon = painterResource(R.drawable.ic_web_session), title = "网页登录记录") { viewModel.navigateTo(Screen.WebSessions) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Home, title = "子单位管理") { viewModel.navigateTo(Screen.UnitManagement) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Person, title = "账号管理") { viewModel.navigateTo(Screen.AccountManagement) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "采购台账") { viewModel.navigateTo(Screen.Ledger) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.List, title = "库存记录") { viewModel.navigateTo(Screen.InventoryRecords) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Lock, title = "修改密码") { viewModel.navigateTo(Screen.ChangePassword) }
                    } else {
                        ProfileMenuItem(icon = Icons.Default.Person, title = "账号", rightText = viewModel.userId) {}
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Home, title = "所属单位", rightText = viewModel.currentUnitName) {}
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.LocationOn, title = "默认配送点", rightText = viewModel.defaultDeliveryPoint) {}
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "我的订单") { viewModel.currentTab = "orders" }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileResourceMenuItem(icon = painterResource(R.drawable.ic_qr_scan), title = "扫码登录网页版") { viewModel.openWebQrScanner() }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileResourceMenuItem(icon = painterResource(R.drawable.ic_web_session), title = "网页登录记录") { viewModel.navigateTo(Screen.WebSessions) }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileMenuItem(icon = Icons.Default.Lock, title = "修改密码") { viewModel.navigateTo(Screen.ChangePassword) }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, PoliceColors.StatusError.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceColors.StatusError)
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, rightText: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = icon, contentDescription = title, tint = PoliceColors.PolicePrimary)
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (rightText.isNotEmpty()) {
                Text(rightText, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
            }
            Text(">", fontSize = 14.sp, color = MaterialTheme.colorScheme.outlineVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileResourceMenuItem(icon: Painter, title: String, rightText: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painter = icon, contentDescription = title, tint = PoliceColors.PolicePrimary)
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (rightText.isNotEmpty()) {
                Text(rightText, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
            }
            Text(">", fontSize = 14.sp, color = MaterialTheme.colorScheme.outlineVariant, fontWeight = FontWeight.Bold)
        }
    }
}
