package com.smartprocurement.internal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.CartItemEntity
import com.smartprocurement.internal.ui.designsystem.GovernmentBrandMark
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentShapes
import com.smartprocurement.internal.ui.designsystem.GovernmentThemeDefaults
import kotlinx.coroutines.delay

fun cartBadgeCount(cartList: List<CartItemEntity>): Int = cartList.size

@Composable
fun SupplyAppContent(viewModel: SupplyViewModel) {
    val currentScreen = viewModel.navigationStack.lastOrNull() ?: Screen.Splash
    val cartList by viewModel.cartItems.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(viewModel.snackbarMessage) {
        val message = viewModel.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.snackbarMessage = null
    }

    // Handle Android system hardware back buttons
    BackHandler(enabled = viewModel.navigationStack.size > 1) {
        viewModel.navigateBack()
    }

    viewModel.alertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.alertMessage = null },
            confirmButton = {
                TextButton(onClick = { viewModel.alertMessage = null }) {
                    Text("我知道了", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            title = { Text("提示", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(msg, fontSize = 14.sp) },
            shape = RoundedCornerShape(GovernmentShapes.LargeRadius),
            containerColor = Color.White
        )
    }

    viewModel.oneTimeCredentialNotice?.let { notice ->
        val copyText = """
            ${GovernmentThemeDefaults.appName}
            单位：${notice.unitName}
            账号：${notice.username}
            初始密码：${notice.password}
            首次登录后请按提示修改密码。
        """.trimIndent()
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = { viewModel.oneTimeCredentialNotice = null }) {
                    Text("关闭", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(copyText))
                    viewModel.snackbarMessage = "账号和密码已复制"
                }) {
                    Text("复制账号和密码")
                }
            },
            title = { Text(notice.type, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("单位：${notice.unitName}")
                    Text("账号：${notice.username}")
                    Text("初始密码：${notice.password}", fontWeight = FontWeight.Bold)
                    Text("请立即复制并交给该单位负责人。关闭后系统不会再次显示该密码。", fontSize = 13.sp)
                }
            },
            shape = RoundedCornerShape(GovernmentShapes.LargeRadius),
            containerColor = GovernmentColors.SurfaceWhite
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            is Screen.Splash -> {
                SplashScreen {
                    viewModel.finishSplash()
                }
            }
            is Screen.Login -> {
                LoginScreen(viewModel)
            }
            is Screen.ChangePassword -> {
                ChangePasswordScreen(viewModel)
            }
            is Screen.Home -> {
                MainTabFrame(viewModel)
            }
            is Screen.AddProduct -> {
                IngredientFormScreen(productId = null, viewModel = viewModel)
            }
            is Screen.EditProduct -> {
                IngredientFormScreen(productId = currentScreen.productId, viewModel = viewModel)
            }
            is Screen.DeletedProducts -> {
                DeletedIngredientsScreen(viewModel)
            }
            is Screen.ProductDetail -> {
                ProductDetailScreen(productId = currentScreen.productId, viewModel = viewModel)
            }
            is Screen.SubmitSuccess -> {
                SubmitSuccessScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            is Screen.OrderDetails -> {
                OrderDetailsScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            is Screen.ShippingProof -> {
                ShippingProofScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            is Screen.UnitManagement -> {
                UnitManagementScreen(viewModel)
            }
            is Screen.AccountManagement -> {
                AccountManagementScreen(viewModel)
            }
            is Screen.Ledger -> {
                LedgerScreen(viewModel)
            }
            is Screen.InventoryRecords -> {
                InventoryRecordsScreen(viewModel)
            }
            is Screen.PreparationSummary -> {
                PreparationSummaryScreen(viewModel)
            }
            is Screen.DeliverySheets -> {
                DeliverySheetsScreen(viewModel)
            }
            else -> {
                MainTabFrame(viewModel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GovernmentColors.PageBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GovernmentBrandMark(modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = GovernmentThemeDefaults.appName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = GovernmentColors.GovernmentBlueDark
            )
            Text(
                text = GovernmentThemeDefaults.appDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = GovernmentColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun MainTabFrame(viewModel: SupplyViewModel) {
    val cartList by viewModel.cartItems.collectAsState()
    val totalCartCount = cartBadgeCount(cartList)
    val isAdmin = viewModel.canManageIngredients()
    LaunchedEffect(isAdmin) {
        if (isAdmin && viewModel.currentTab == "home") viewModel.currentTab = "dashboard"
        if (!isAdmin && viewModel.currentTab == "dashboard") viewModel.currentTab = "home"
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = GovernmentColors.SurfaceWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = GovernmentColors.Divider,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
            ) {
                NavigationBarItem(
                    selected = viewModel.currentTab == if (isAdmin) "dashboard" else "home",
                    onClick = { viewModel.currentTab = if (isAdmin) "dashboard" else "home" },
                    icon = { Icon(imageVector = if (isAdmin) Icons.Default.DateRange else Icons.Default.Home, contentDescription = "home") },
                    label = { Text(if (isAdmin) "工作台" else "首页", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GovernmentColors.GovernmentBlue,
                        selectedTextColor = GovernmentColors.GovernmentBlue,
                        unselectedIconColor = GovernmentColors.TextTertiary,
                        unselectedTextColor = GovernmentColors.TextTertiary,
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == if (isAdmin) "ingredients" else "cart",
                    onClick = { viewModel.currentTab = if (isAdmin) "ingredients" else "cart" },
                    icon = {
                        if (isAdmin) {
                            Icon(imageVector = Icons.Default.List, contentDescription = "ingredients")
                        } else {
                            Box {
                                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "cart")
                                if (totalCartCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 10.dp, y = (-4).dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val countText = if (totalCartCount > 9) "9+" else totalCartCount.toString()
                                        Text(
                                            text = countText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    },
                    label = { Text(if (isAdmin) "食材" else "清单", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GovernmentColors.GovernmentBlue,
                        selectedTextColor = GovernmentColors.GovernmentBlue,
                        unselectedIconColor = GovernmentColors.TextTertiary,
                        unselectedTextColor = GovernmentColors.TextTertiary,
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "orders",
                    onClick = { viewModel.currentTab = "orders" },
                    icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "orders") },
                    label = { Text("订单", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GovernmentColors.GovernmentBlue,
                        selectedTextColor = GovernmentColors.GovernmentBlue,
                        unselectedIconColor = GovernmentColors.TextTertiary,
                        unselectedTextColor = GovernmentColors.TextTertiary,
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "profile",
                    onClick = { viewModel.currentTab = "profile" },
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "profile") },
                    label = { Text("我的", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GovernmentColors.GovernmentBlue,
                        selectedTextColor = GovernmentColors.GovernmentBlue,
                        unselectedIconColor = GovernmentColors.TextTertiary,
                        unselectedTextColor = GovernmentColors.TextTertiary,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.currentTab) {
                "dashboard" -> AdminDashboardScreen(viewModel)
                "home" -> HomeScreen(viewModel)
                "ingredients" -> HomeScreen(viewModel)
                "cart" -> CartScreen(viewModel)
                "orders" -> OrderListScreen(viewModel)
                "profile" -> ProfileScreen(viewModel)
                else -> HomeScreen(viewModel)
            }
        }
    }
}
