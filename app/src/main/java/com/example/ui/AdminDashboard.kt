package com.smartprocurement.internal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.ui.designsystem.PoliceBrandHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: SupplyViewModel) {
    val dashboard = viewModel.dashboard

    Scaffold(
        topBar = { PoliceBrandHeader(title = "工作台", subtitle = "系统管理员 · ${viewModel.userName}") }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isDashboardRefreshing,
            onRefresh = { viewModel.refreshDashboard(userInitiated = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("今日待办", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MetricTile("待接单", dashboard.pending.toString(), Modifier.weight(1f)) {
                                viewModel.openOrderTab("待接单")
                            }
                            MetricTile("等待发货", dashboard.preparing.toString(), Modifier.weight(1f)) {
                                viewModel.openOrderTab("备货中")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MetricTile("库存预警", dashboard.tightInventory.toString(), Modifier.weight(1f)) {
                                viewModel.openInventoryRisks()
                            }
                            MetricTile("收货异常", dashboard.openReceiptIssues.toString(), Modifier.weight(1f)) {
                                viewModel.openOrderTab()
                            }
                        }
                    }
                }

                item {
                    SectionPanel("今日概览") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OverviewValue("订单", "${dashboard.todayOrders} 笔")
                            OverviewValue("金额", Money.formatCents(dashboard.todayTotalCents))
                            OverviewValue("已发货", "${dashboard.shipped} 笔")
                        }
                    }
                }

                item {
                    SectionPanel("常用操作") {
                        DashboardAction("Excel 智能导入", { Icon(Icons.Default.Description, contentDescription = null) }) {
                            viewModel.navigateTo(Screen.PriceImports)
                        }
                        DashboardAction("配送批次与备货单", { Icon(Icons.Default.LocalShipping, contentDescription = null) }) {
                            viewModel.navigateTo(Screen.DeliveryBatches)
                        }
                        DashboardAction("新增食材", { Icon(Icons.Default.Add, contentDescription = null) }) {
                            viewModel.navigateTo(Screen.AddProduct)
                        }
                    }
                }

                item {
                    SectionPanel("最近订单") {
                        if (dashboard.recentOrders.isEmpty()) {
                            Text("还没有最近订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            dashboard.recentOrders.take(5).forEach { order ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                        .clickable { viewModel.navigateTo(Screen.OrderDetails(order.id)) }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(order.orderNo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(order.unitName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(order.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(Money.formatCents(order.totalCents), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            TextButton(onClick = { viewModel.openOrderTab() }, modifier = Modifier.fillMaxWidth()) {
                                Text("查看全部订单")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OverviewValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardAction(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
