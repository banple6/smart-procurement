package com.smartprocurement.internal.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartprocurement.internal.ui.theme.JrxpTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernmentTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    PoliceStatusBar(color = MaterialTheme.colorScheme.surface, darkIcons = !isSystemInDarkTheme())
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(GovernmentDimens.MinTouchTarget)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = {
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = GovernmentDimens.MinTouchTarget)) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(actionText)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.drawBehind {
            drawLine(
                color = dividerColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    )
}

@Composable
fun GovernmentSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(GovernmentShapes.SmallRadius))
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun GovernmentPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(GovernmentDimens.PrimaryButtonHeight),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GovernmentSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = GovernmentDimens.MinTouchTarget),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun GovernmentDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = GovernmentDimens.MinTouchTarget)) {
        Text(text, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GovernmentStatusLabel(status: String, modifier: Modifier = Modifier) {
    val style = GovernmentStatus.fromUiStatus(status)
    val semanticColor = when (status) {
        "库存不足" -> MaterialTheme.colorScheme.error
        "库存紧张", "暂停供应", "备货中" -> JrxpTheme.colors.warningAmber
        "正常供应", "启用", "当前在线", "使用中", "已接单", "已完成" -> JrxpTheme.colors.supplyGreen
        "已取消", "停用", "已退出", "已下架" -> JrxpTheme.colors.cancelledGray
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = if (isSystemInDarkTheme()) semanticColor.copy(alpha = 0.18f) else style.containerColor
    val contentColor = if (isSystemInDarkTheme()) semanticColor else style.contentColor
    Text(
        text = style.label,
        modifier = modifier
            .background(containerColor, RoundedCornerShape(GovernmentShapes.SmallRadius))
            .border(1.dp, contentColor.copy(alpha = 0.35f), RoundedCornerShape(GovernmentShapes.SmallRadius))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun GovernmentInfoBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val bg = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(GovernmentDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun GovernmentCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GovernmentShapes.MediumRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.padding(GovernmentDimens.CardPadding)) {
            content()
        }
    }
}

@Composable
fun GovernmentDataRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GovernmentOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        supportingText = { supportingText?.let { Text(it) } },
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    )
}

@Composable
fun GovernmentEmptyState(title: String, message: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(60.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) {
            GovernmentSecondaryButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun GovernmentBottomActionBar(content: @Composable () -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.drawBehind {
            drawLine(
                color = dividerColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
    ) {
        Box(Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp))) {
            content()
        }
    }
}

@Composable
fun GovernmentRefreshAction(onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, modifier = Modifier.size(GovernmentDimens.MinTouchTarget)) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
    }
}
