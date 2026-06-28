package com.example.ui

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
import com.example.domain.validation.ActivationInput

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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("智慧后勤采购", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("内部生鲜采购与配送管理系统", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountActivationScreen(viewModel: SupplyViewModel) {
    var inviteCode by remember { mutableStateOf("") }
    var realName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("后勤管理处") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("warehouse") }
    var departmentExpanded by remember { mutableStateOf(false) }
    var roleExpanded by remember { mutableStateOf(false) }
    val departments = listOf("后勤管理处", "仓储配送部", "机关食堂", "综合管理处", "财务科")
    val roles = listOf("warehouse" to "后勤/超市管理人员", "staff" to "普通内部工作人员", "admin" to "系统管理员")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("内部账号激活", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { viewModel.navigateBack() }) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("仅限内部授权人员使用，请使用管理员提供的邀请码。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormField(inviteCode, { inviteCode = it }, "邀请码或激活码", viewModel.activationErrors["inviteCode"])
            FormField(realName, { realName = it }, "姓名", viewModel.activationErrors["realName"])
            FormField(phone, { phone = it }, "手机号", viewModel.activationErrors["phone"])

            ExposedDropdownMenuBox(expanded = departmentExpanded, onExpandedChange = { departmentExpanded = !departmentExpanded }) {
                OutlinedTextField(
                    value = department,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("所属部门") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    isError = viewModel.activationErrors.containsKey("department"),
                    supportingText = { viewModel.activationErrors["department"]?.let { Text(it) } }
                )
                ExposedDropdownMenu(expanded = departmentExpanded, onDismissRequest = { departmentExpanded = false }) {
                    departments.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = {
                            department = it
                            departmentExpanded = false
                        })
                    }
                }
            }

            FormField(username, { username = it }, "登录账号或工号", viewModel.activationErrors["username"])
            PasswordField(password, { password = it }, "设置密码", viewModel.activationErrors["password"])
            PasswordField(confirmPassword, { confirmPassword = it }, "确认密码", viewModel.activationErrors["confirmPassword"])

            ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = !roleExpanded }) {
                OutlinedTextField(
                    value = roles.first { it.first == role }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("账号角色") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                    roles.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            role = value
                            roleExpanded = false
                        })
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.activateAccount(
                        ActivationInput(inviteCode, realName, phone, department, username, password, confirmPassword),
                        role
                    )
                },
                enabled = !viewModel.isAuthLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("激活并登录", fontWeight = FontWeight.Bold)
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
