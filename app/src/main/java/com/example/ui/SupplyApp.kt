package com.example.ui

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
import kotlinx.coroutines.delay

@Composable
fun SupplyAppContent(viewModel: SupplyViewModel) {
    val currentScreen = viewModel.navigationStack.lastOrNull() ?: Screen.Splash
    val cartList by viewModel.cartItems.collectAsState()

    // Handle Android system hardware back buttons
    BackHandler(enabled = viewModel.navigationStack.size > 1) {
        viewModel.navigateBack()
    }

    // Custom Toast Alert notification
    viewModel.alertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.alertMessage = null },
            confirmButton = {
                TextButton(onClick = { viewModel.alertMessage = null }) {
                    Text("确定", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            title = { Text("通知", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(msg, fontSize = 14.sp) },
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.White
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
            is Screen.AccountActivation -> {
                AccountActivationScreen(viewModel)
            }
            is Screen.DeviceAuth -> {
                DeviceAuthScreen(viewModel)
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
            is Screen.DeliveryForm -> {
                DeliveryFormScreen(viewModel = viewModel)
            }
            is Screen.ConfirmDetails -> {
                ConfirmDetailsScreen(details = currentScreen, viewModel = viewModel)
            }
            is Screen.SubmitSuccess -> {
                SubmitSuccessScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            is Screen.OrderDetails -> {
                OrderDetailsScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            is Screen.ReplacementConfirm -> {
                ReplacementConfirmScreen(orderId = currentScreen.orderId, viewModel = viewModel)
            }
            else -> {
                MainTabFrame(viewModel)
            }
        }
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
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "splash_logo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "智慧后勤采购",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "内部食材申领配送终端",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun MainTabFrame(viewModel: SupplyViewModel) {
    val cartList by viewModel.cartItems.collectAsState()
    val totalCartCount = cartList.sumOf { it.quantity }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = Color(0xFFCAC4D0),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
            ) {
                NavigationBarItem(
                    selected = viewModel.currentTab == "home",
                    onClick = { viewModel.currentTab = "home" },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "home") },
                    label = { Text("首页", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "cart",
                    onClick = { viewModel.currentTab = "cart" },
                    icon = {
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
                                    val countText = if (totalCartCount > 9) "9+" else if (totalCartCount % 1.0 == 0.0) totalCartCount.toInt().toString() else String.format("%.0f", totalCartCount)
                                    Text(
                                        text = countText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    },
                    label = { Text("清单", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "orders",
                    onClick = { viewModel.currentTab = "orders" },
                    icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "orders") },
                    label = { Text("订单", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )

                NavigationBarItem(
                    selected = viewModel.currentTab == "profile",
                    onClick = { viewModel.currentTab = "profile" },
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "profile") },
                    label = { Text("我的", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
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
                "home" -> HomeScreen(viewModel)
                "cart" -> CartScreen(viewModel)
                "orders" -> OrderListScreen(viewModel)
                "profile" -> ProfileScreen(viewModel)
                else -> HomeScreen(viewModel)
            }
        }
    }
}
