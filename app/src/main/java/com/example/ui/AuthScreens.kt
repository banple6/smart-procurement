package com.smartprocurement.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.ui.designsystem.PoliceBadgeImage
import com.smartprocurement.internal.ui.designsystem.PoliceColors
import com.smartprocurement.internal.ui.designsystem.PoliceStatusBar

@Composable
fun LoginScreen(viewModel: SupplyViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberLogin by remember { mutableStateOf(true) }

    PoliceStatusBar(PoliceColors.Navy, darkIcons = false)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PoliceColors.Navy)
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PoliceBadgeImage(size = 68.dp, contentDescription = "人民警察警徽")
            Spacer(modifier = Modifier.height(16.dp))
            Text("景荣鲜配", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("XX公安局后勤食材采购配送系统", fontSize = 14.sp, color = Color.White.copy(alpha = 0.82f))
            Text("公安内部使用", fontSize = 13.sp, color = Color.White.copy(alpha = 0.68f), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(modifier = Modifier.height(26.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("内部账号登录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("本系统仅限内部授权人员使用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账号或工号") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    isError = viewModel.loginErrors.containsKey("username"),
                    supportingText = { viewModel.loginErrors["username"]?.let { Text(it) } }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        Text(
                            text = if (showPassword) "隐藏" else "显示",
                            modifier = Modifier
                                .clickable { showPassword = !showPassword }
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    isError = viewModel.loginErrors.containsKey("password"),
                    supportingText = { viewModel.loginErrors["password"]?.let { Text(it) } }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberLogin, onCheckedChange = { rememberLogin = it })
                    Text("记住登录状态", fontSize = 14.sp)
                }

                Button(
                    onClick = { viewModel.login(username, password, rememberLogin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !viewModel.isAuthLoading,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (viewModel.isAuthLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("登录", fontWeight = FontWeight.Bold)
                    }
                }

                Text("账号由系统管理员创建。忘记密码请联系管理员", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ChangePasswordScreen(viewModel: SupplyViewModel) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("首次登录修改密码", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("新密码至少 8 位，必须包含字母和数字。修改成功后请重新登录。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = "原密码",
                    error = viewModel.passwordErrors["oldPassword"]
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "新密码",
                    error = viewModel.passwordErrors["newPassword"]
                )
                Button(
                    onClick = { viewModel.changePassword(oldPassword, newPassword) },
                    enabled = !viewModel.isChangingPassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (viewModel.isChangingPassword) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("修改密码", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}
