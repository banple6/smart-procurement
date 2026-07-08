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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.PoliceIdentityHeader
import com.smartprocurement.internal.ui.theme.JrxpColors
import com.smartprocurement.internal.ui.theme.JrxpTheme
import com.smartprocurement.internal.ui.theme.JrxpDimensions
import com.smartprocurement.internal.ui.components.JrxpPrimaryButton
import com.smartprocurement.internal.ui.components.JrxpSecondaryButton
import com.smartprocurement.internal.ui.components.DocumentSection
import com.smartprocurement.internal.ui.components.LedgerRow
import com.smartprocurement.internal.ui.components.MenuActionRow

@Composable
fun SubmitSuccessScreen(orderId: String, viewModel: SupplyViewModel) {
    val orderFlow = remember(orderId) { viewModel.getOrderFlow(orderId) }
    val order by orderFlow.collectAsState(initial = null)
    val displayOrderNo = order?.displayOrderNo.orEmpty().ifBlank { "订单号生成中" }
    val deliveryPoint = order?.deliveryPoint.orEmpty().ifBlank { viewModel.defaultDeliveryPoint.ifBlank { "未设置" } }
    val totalCents = order?.totalCents ?: 0L

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            color = Color(0xFFCAC4D0),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "成功", tint = MaterialTheme.colorScheme.primary)
                    Text("订单已提交", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "成功", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("订单已提交", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("订单已提交，等待管理员接单。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Spacer(modifier = Modifier.height(24.dp))
                DocumentSection(title = "订单信息") {
                    SuccessRow("订单编号", displayOrderNo)
                    SuccessRow("当前状态", "待接单")
                    SuccessRow("配送点", deliveryPoint)
                    SuccessRow("订单金额", Money.formatCents(totalCents))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                JrxpPrimaryButton(
                    text = "查看订单",
                    onClick = {
                        viewModel.currentTab = "orders"
                        viewModel.popToRootAndNavigate(Screen.Home)
                        viewModel.navigateTo(Screen.OrderDetails(orderId))
                    }
                )
                JrxpSecondaryButton(
                    text = "返回首页",
                    onClick = {
                        viewModel.currentTab = "home"
                        viewModel.popToRootAndNavigate(Screen.Home)
                    }
                )
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
                line1 = "账号：${viewModel.userId}",
                line2 = "三公鲜配管理端",
            )
        } else {
            PoliceIdentityHeader(
                title = viewModel.userName.ifBlank { "子单位采购账号" },
                line1 = "所属单位：${viewModel.currentUnitName.ifBlank { "单位信息未配置" }}",
                line2 = "默认配送点：${viewModel.defaultDeliveryPoint.ifBlank { "单位信息未配置" }}",
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JrxpColors.PureSurface)
            ) {
                Column {
                    if (viewModel.canManageIngredients()) {
                        ProfileMenuItem(icon = Icons.Default.Home, title = "子单位管理") { viewModel.navigateTo(Screen.UnitManagement) }
                        ProfileMenuItem(icon = Icons.Default.Person, title = "账号管理") { viewModel.navigateTo(Screen.AccountManagement) }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "采购台账") { viewModel.navigateTo(Screen.Ledger) }
                        ProfileMenuItem(icon = Icons.Default.List, title = "库存记录") { viewModel.navigateTo(Screen.InventoryRecords) }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "系统状态") { viewModel.navigateTo(Screen.SystemStatus) }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "扫码登录网页版") { viewModel.navigateTo(Screen.WebQrScanner) }
                        ProfileMenuItem(icon = Icons.Default.Person, title = "网页版登录设备") { viewModel.navigateTo(Screen.WebSessions) }
                        ProfileMenuItem(icon = Icons.Default.Lock, title = "修改密码") { viewModel.navigateTo(Screen.ChangePassword) }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "关于与更新") { viewModel.navigateTo(Screen.AboutUpdate) }
                    } else {
                        ProfileMenuItem(icon = Icons.Default.Person, title = "账号", rightText = viewModel.userId) {}
                        ProfileMenuItem(icon = Icons.Default.Home, title = "所属单位", rightText = viewModel.currentUnitName) {}
                        ProfileMenuItem(icon = Icons.Default.LocationOn, title = "默认配送点", rightText = viewModel.defaultDeliveryPoint) {}
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "我的订单") { viewModel.currentTab = "orders" }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "扫码登录网页版") { viewModel.navigateTo(Screen.WebQrScanner) }
                        ProfileMenuItem(icon = Icons.Default.Person, title = "网页版登录设备") { viewModel.navigateTo(Screen.WebSessions) }
                        ProfileMenuItem(icon = Icons.Default.Lock, title = "修改密码") { viewModel.navigateTo(Screen.ChangePassword) }
                        ProfileMenuItem(icon = Icons.Default.Menu, title = "关于与更新") { viewModel.navigateTo(Screen.AboutUpdate) }
                    }
                }
            }

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "退出登录")
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}



@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, rightText: String = "", onClick: () -> Unit) {
    MenuActionRow(
        icon = icon,
        title = title,
        trailingText = rightText,
        onClick = onClick
    )
}
