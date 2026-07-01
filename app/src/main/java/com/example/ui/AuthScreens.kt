package com.smartprocurement.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smartprocurement.internal.ui.designsystem.GovernmentBrandMark
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentColors
import com.smartprocurement.internal.ui.designsystem.GovernmentDimens
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentThemeDefaults

@Composable
fun LoginScreen(viewModel: SupplyViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberLogin by remember { mutableStateOf(true) }

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
                .background(GovernmentColors.GovernmentBlue)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GovernmentBrandMark(modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(GovernmentThemeDefaults.appName, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = GovernmentColors.TextOnPrimary)
            Text(GovernmentThemeDefaults.appDescription, style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextOnPrimary.copy(alpha = 0.88f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GovernmentCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("内部账号登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = GovernmentColors.TextPrimary)

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("账号") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        isError = viewModel.loginErrors.containsKey("username"),
                        supportingText = { viewModel.loginErrors["username"]?.let { Text(it) } }
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "隐藏" else "显示")
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        isError = viewModel.loginErrors.containsKey("password"),
                        supportingText = { viewModel.loginErrors["password"]?.let { Text(it) } }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = GovernmentDimens.MinTouchTarget)) {
                        Checkbox(checked = rememberLogin, onCheckedChange = { rememberLogin = it })
                        Text("记住登录状态", style = MaterialTheme.typography.bodyMedium)
                    }

                    GovernmentPrimaryButton(
                        text = if (viewModel.isAuthLoading) "正在登录" else "登录",
                        onClick = { viewModel.login(username, password, rememberLogin) },
                        enabled = !viewModel.isAuthLoading
                    )

                    Text(
                        "本系统仅限内部授权账号使用。忘记密码请联系系统管理员。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GovernmentColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(viewModel: SupplyViewModel) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GovernmentCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("首次登录，请修改密码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("修改成功后需要使用新密码重新登录。", style = MaterialTheme.typography.bodyMedium, color = GovernmentColors.TextSecondary)
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
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "确认新密码",
                    error = viewModel.passwordErrors["confirmPassword"]
                )
                GovernmentPrimaryButton(
                    text = if (viewModel.isChangingPassword) "正在修改" else "修改密码",
                    onClick = { viewModel.changePassword(oldPassword, newPassword, confirmPassword) },
                    enabled = !viewModel.isChangingPassword
                )
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        singleLine = true,
        shape = RoundedCornerShape(6.dp),
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}
