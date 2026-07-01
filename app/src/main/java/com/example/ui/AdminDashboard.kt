package com.smartprocurement.internal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSectionHeader
import com.smartprocurement.internal.ui.designsystem.GovernmentStatusLabel
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar

@Composable
fun AdminDashboardScreen(viewModel: SupplyViewModel) {
    val dashboard = viewModel.dashboard
    LaunchedEffect(Unit) {
        viewModel.refreshDashboard()
        viewModel.refreshOrders()
    }

    Scaffold(
        topBar = {
            GovernmentTopBar(
                title = "工作台",
                actionText = "刷新",
                actionIcon = Icons.Default.Refresh,
                onAction = { viewModel.refreshDashboard() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GovernmentColors.PageBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("今日订单", dashboard.todayOrders.toString(), Modifier.weight(1f))
                        MetricTile("待接单", dashboard.pending.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("备货中", dashboard.preparing.toString(), Modifier.weight(1f))
                        MetricTile("已发货", dashboard.shipped.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("今日金额", Money.formatCents(dashboard.todayTotalCents), Modifier.weight(1f))
                        MetricTile("库存紧张", dashboard.tightInventory.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("收货异常", dashboard.openReceiptIssues.toString(), Modifier.weight(1f))
                        MetricTile("今日备货", "查看", Modifier.weight(1f).clickable { viewModel.navigateTo(Screen.PreparationSummary) })
                    }
                    GovernmentPrimaryButton(
                        text = "查看单位配送单",
                        onClick = { viewModel.navigateTo(Screen.DeliverySheets) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                SectionPanel("最近订单") {
                    if (dashboard.recentOrders.isEmpty()) {
                        Text("暂无订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        dashboard.recentOrders.forEach { order ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { viewModel.navigateTo(Screen.OrderDetails(order.id)) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(order.orderNo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(order.unitName, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    GovernmentStatusLabel(order.status)
                                    Text(Money.formatCents(order.totalCents), style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionPanel("需求排行") {
                    if (dashboard.demandRank.isEmpty()) {
                        Text("暂无需求数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        dashboard.demandRank.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}. ${item.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${item.quantity} ${item.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    GovernmentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GovernmentSectionHeader(title)
            content()
        }
    }
}
