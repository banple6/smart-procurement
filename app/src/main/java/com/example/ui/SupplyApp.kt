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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.CartItemEntity
import com.smartprocurement.internal.ui.designsystem.PoliceOpeningBadge
import com.smartprocurement.internal.ui.designsystem.PoliceStatusBar
import com.smartprocurement.internal.ui.theme.JrxpColors
import com.smartprocurement.internal.ui.theme.JrxpTheme
import com.smartprocurement.internal.ui.components.JrxpIcons
import kotlinx.coroutines.delay

fun cartBadgeCount(cartList: List<CartItemEntity>): Int = cartList.size

@Composable
fun SupplyAppContent(viewModel: SupplyViewModel) {
    val currentScreen = viewModel.navigationStack.lastOrNull() ?: Screen.Splash
    val cartList by viewModel.cartItems.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.White
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(220)
                ).togetherWith(
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(220)
                    )
                )
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
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
                    IngredientFormScreen(productId = (screen as Screen.EditProduct).productId, viewModel = viewModel)
                }
                is Screen.DeletedProducts -> {
                    DeletedIngredientsScreen(viewModel)
                }
                is Screen.ProductDetail -> {
                    ProductDetailScreen(productId = (screen as Screen.ProductDetail).productId, viewModel = viewModel)
                }
                is Screen.SubmitSuccess -> {
                    SubmitSuccessScreen(orderId = (screen as Screen.SubmitSuccess).orderId, viewModel = viewModel)
                }
                is Screen.OrderDetails -> {
                    OrderDetailsScreen(orderId = (screen as Screen.OrderDetails).orderId, viewModel = viewModel)
                }
                is Screen.ShippingProof -> {
                    ShippingProofScreen(orderId = (screen as Screen.ShippingProof).orderId, viewModel = viewModel)
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
                is Screen.SystemStatus -> {
                    SystemStatusScreen(viewModel)
                }
                is Screen.PreparationSummary -> {
                    PreparationSummaryScreen(viewModel)
                }
                is Screen.DeliverySheets -> {
                    DeliverySheetsScreen(viewModel)
                }
                is Screen.InviteEntry -> {
                    InviteEntryScreen(viewModel)
                }
                is Screen.WebQrScanner -> {
                    WebQrScannerScreen(viewModel)
                }
                is Screen.WebLoginConfirm -> {
                    WebLoginConfirmScreen(viewModel)
                }
                is Screen.WebLoginResult -> {
                    WebLoginResultScreen(viewModel)
                }
                is Screen.WebSessions -> {
                    WebSessionsScreen(viewModel)
                }
                is Screen.AboutUpdate -> {
                    AboutUpdateScreen(viewModel)
                }
                is Screen.OnboardingGuide -> {
                    OnboardingWelcomeScreen(viewModel)
                }
                else -> {
                    MainTabFrame(viewModel)
                }
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
        delay(1000)
        onFinish()
    }

    PoliceStatusBar(JrxpColors.CommandNavy, darkIcons = false)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JrxpColors.CommandNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PoliceOpeningBadge(size = 80.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "三公鲜配",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "食材申领与配送平台",
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
                letterSpacing = 1.sp
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
                containerColor = JrxpColors.PureSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = JrxpColors.RuleLine,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
            ) {
                NavigationBarItem(
                    selected = viewModel.currentTab == if (isAdmin) "dashboard" else "home",
                    onClick = { viewModel.currentTab = if (isAdmin) "dashboard" else "home" },
                    icon = { Icon(imageVector = if (isAdmin) Icons.Default.DateRange else JrxpIcons.UnitBuilding, contentDescription = "home") },
                    label = { Text(if (isAdmin) "工作台" else "调度台", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JrxpColors.DutyBlue,
                        selectedTextColor = JrxpColors.DutyBlue,
                        unselectedIconColor = JrxpColors.InkTertiary,
                        unselectedTextColor = JrxpColors.InkTertiary,
                        indicatorColor = JrxpColors.PaleBlue
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
                                Icon(imageVector = JrxpIcons.SupplyBox, contentDescription = "cart")
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    },
                    label = { Text(if (isAdmin) "食材台账" else "申领单", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JrxpColors.DutyBlue,
                        selectedTextColor = JrxpColors.DutyBlue,
                        unselectedIconColor = JrxpColors.InkTertiary,
                        unselectedTextColor = JrxpColors.InkTertiary,
                        indicatorColor = JrxpColors.PaleBlue
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "orders",
                    onClick = { viewModel.currentTab = "orders" },
                    icon = { Icon(imageVector = JrxpIcons.OrderDocument, contentDescription = "orders") },
                    label = { Text("履约单据", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JrxpColors.DutyBlue,
                        selectedTextColor = JrxpColors.DutyBlue,
                        unselectedIconColor = JrxpColors.InkTertiary,
                        unselectedTextColor = JrxpColors.InkTertiary,
                        indicatorColor = JrxpColors.PaleBlue
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "profile",
                    onClick = { viewModel.currentTab = "profile" },
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "profile") },
                    label = { Text("身份", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JrxpColors.DutyBlue,
                        selectedTextColor = JrxpColors.DutyBlue,
                        unselectedIconColor = JrxpColors.InkTertiary,
                        unselectedTextColor = JrxpColors.InkTertiary,
                        indicatorColor = JrxpColors.PaleBlue
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
