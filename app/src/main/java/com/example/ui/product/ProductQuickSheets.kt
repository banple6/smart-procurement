package com.smartprocurement.internal.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.data.ProductEntity
import com.smartprocurement.internal.domain.money.Money
import com.smartprocurement.internal.domain.quantity.QuantityFormatter
import com.smartprocurement.internal.domain.validation.InventoryAdjustMode
import com.smartprocurement.internal.domain.validation.ProductQuickActionValidator

@Composable
fun AdminProductActionRow(
    product: ProductEntity,
    loading: Boolean,
    onPrice: () -> Unit,
    onInventory: () -> Unit,
    onToggle: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrice, enabled = !loading, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("改价格", fontSize = 13.sp)
        }
        OutlinedButton(onClick = onInventory, enabled = !loading, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("调库存", fontSize = 13.sp)
        }
        OutlinedButton(onClick = onToggle, enabled = !loading, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(if (product.isAvailable && product.status != "已下架") Icons.Default.Close else Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (product.isAvailable && product.status != "已下架") "下架" else "重新上架", fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPriceSheet(
    product: ProductEntity,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (priceText: String, reason: String) -> Unit
) {
    var priceText by remember(product.id) { mutableStateOf("") }
    var reason by remember(product.id) { mutableStateOf("") }
    val validation = ProductQuickActionValidator.validatePrice(priceText, reason, Money.yuanDoubleToCents(product.price))
    ModalBottomSheet(onDismissRequest = { if (!loading) onDismiss() }) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("修改价格", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("当前价格：${Money.formatYuan(product.price)} / ${product.unit}", fontSize = 14.sp)
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.take(10) },
                label = { Text("新价格") },
                suffix = { Text("元 / ${product.unit}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = priceText.isNotBlank() && !validation.isValid,
                supportingText = { if (priceText.isNotBlank() && !validation.isValid) Text(validation.message) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(80) },
                label = { Text("调整原因（必填）") },
                singleLine = false,
                minLines = 2,
                isError = reason.isNotBlank() && !validation.isValid && validation.message == "请填写价格调整原因",
                supportingText = { if (priceText.isNotBlank() || reason.isNotBlank()) if (!validation.isValid) Text(validation.message) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f).height(52.dp)) { Text("取消") }
                Button(
                    onClick = { onSave(priceText, reason) },
                    enabled = validation.isValid && !loading,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(if (loading) "正在保存" else "保存价格") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickInventorySheet(
    product: ProductEntity,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (mode: InventoryAdjustMode, quantityText: String, reason: String) -> Unit
) {
    var mode by remember(product.id) { mutableStateOf(InventoryAdjustMode.SET) }
    var quantityText by remember(product.id, mode) { mutableStateOf("") }
    var reason by remember(product.id) { mutableStateOf("") }
    val validation = ProductQuickActionValidator.validateInventory(mode, quantityText, reason, product.stockQuantity, product.reservedQuantity)
    ModalBottomSheet(onDismissRequest = { if (!loading) onDismiss() }) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("调整库存", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("当前总库存：${QuantityFormatter.format(product.stockQuantity)} ${product.unit}", fontSize = 14.sp)
            Text("已预占库存：${QuantityFormatter.format(product.reservedQuantity)} ${product.unit}", fontSize = 14.sp)
            Text("当前可用库存：${QuantityFormatter.format(product.availableQuantity.ifBlank { product.stockQuantity })} ${product.unit}", fontSize = 14.sp)
            InventoryAdjustMode.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == option, onClick = { mode = option }, enabled = !loading)
                    Text(option.label, fontSize = 14.sp)
                }
            }
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.take(12) },
                label = { Text(if (mode == InventoryAdjustMode.SET) "调整后总库存" else if (mode == InventoryAdjustMode.INCREASE) "本次入库数量" else "本次减少数量") },
                suffix = { Text(product.unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = quantityText.isNotBlank() && !validation.isValid && validation.message != "请填写库存调整原因",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(80) },
                label = { Text("调整原因（必填）") },
                isError = reason.isNotBlank() && !validation.isValid && validation.message == "请填写库存调整原因",
                supportingText = { if (quantityText.isNotBlank() || reason.isNotBlank()) if (!validation.isValid) Text(validation.message) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f).height(52.dp)) { Text("取消") }
                Button(
                    onClick = { onSave(mode, quantityText, reason) },
                    enabled = validation.isValid && !loading,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(if (loading) "正在保存" else "保存库存") }
            }
        }
    }
}

@Composable
fun ProductPublishConfirmDialog(
    product: ProductEntity,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val publish = !product.isAvailable || product.status == "已下架"
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (publish) "确认重新上架“${product.name}”吗？" else "确认下架“${product.name}”吗？") },
        text = { Text(if (publish) "重新上架后，子单位可在库存充足时选择该食材。" else "下架后子单位将无法继续选择该食材，历史订单不受影响。") },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !loading) { Text(if (loading) "正在提交" else if (publish) "确认上架" else "确认下架") } }
    )
}
