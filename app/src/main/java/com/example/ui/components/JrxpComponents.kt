package com.smartprocurement.internal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprocurement.internal.ui.theme.*

// ══════════════════════════════════════════════════════════════
// 景荣鲜配 (JRXP) 专属业务组件
//
// 每个组件体现业务结构，而不只是更换名称的通用卡片。
// ══════════════════════════════════════════════════════════════

// ── DocumentSection ─────────────────────────────────────────
// 文档式分区：左侧 3dp 短蓝线 + 模块标题 + 可选说明 + 内容
// 用于：基本信息、商品明细、配送信息、操作记录
@Composable
fun DocumentSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = JrxpTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = JrxpDimensions.spacingMd)
        ) {
            // 左侧短蓝线
            Box(
                modifier = Modifier
                    .width(JrxpDimensions.sectionIndicatorWidth)
                    .height(JrxpDimensions.sectionIndicatorHeight)
                    .background(ext.commandNavy, RoundedCornerShape(JrxpDimensions.cornerXs))
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ext.inkPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = JrxpDimensions.sectionIndicatorWidth + JrxpDimensions.spacingSm),
            content = content
        )
    }
}

// ── SupplyStatusMark ────────────────────────────────────────
// 状态印记：紧凑的状态标签，用于订单、库存、配送等状态
@Composable
fun SupplyStatusMark(
    label: String,
    type: StatusType,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    val (textColor, bgColor) = when (type) {
        StatusType.PENDING   -> ext.warningAmber to ext.warningAmberBg
        StatusType.ACTIVE    -> ext.dutyBlue to ext.paleBlueBg
        StatusType.SUCCESS   -> ext.supplyGreen to ext.supplyGreenBg
        StatusType.DANGER    -> ext.criticalRed to ext.criticalRedBg
        StatusType.CANCELLED -> ext.cancelledGray to ext.mutedSurface
    }
    Box(
        modifier = modifier
            .background(bgColor, JrxpDimensions.shapeSm)
            .padding(horizontal = JrxpDimensions.spacingSm, vertical = JrxpDimensions.spacingXs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

enum class StatusType { PENDING, ACTIVE, SUCCESS, DANGER, CANCELLED }

// ── QuantityStepper ─────────────────────────────────────────
// 完整数量控制器：48dp 触控区域、缩放反馈、触觉反馈
@Composable
fun QuantityStepper(
    value: Double,
    unit: String,
    step: Double = 1.0,
    maxValue: Double = Double.MAX_VALUE,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    val haptic = LocalHapticFeedback.current

    val minusInteraction = remember { MutableInteractionSource() }
    val plusInteraction = remember { MutableInteractionSource() }
    val isMinusPressed by minusInteraction.collectIsPressedAsState()
    val isPlusPressed by plusInteraction.collectIsPressedAsState()

    val valueScale by animateFloatAsState(
        targetValue = if (isMinusPressed || isPlusPressed) JrxpMotion.scalePress else JrxpMotion.scaleNormal,
        animationSpec = tween(JrxpMotion.durationFast),
        label = "stepperScale"
    )

    val qtyText = if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
    val atMax = value >= maxValue
    val atMin = value <= step

    Row(
        modifier = modifier
            .height(JrxpDimensions.stepperButtonSize)
            .background(ext.mutedSurface, JrxpDimensions.shapeMd)
            .border(JrxpDimensions.ruleLineWidth, ext.ruleLine, JrxpDimensions.shapeMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 减少按钮
        Box(
            modifier = Modifier
                .size(JrxpDimensions.stepperButtonSize)
                .clip(RoundedCornerShape(topStart = JrxpDimensions.cornerMd, bottomStart = JrxpDimensions.cornerMd))
                .clickable(
                    interactionSource = minusInteraction,
                    indication = ripple(bounded = true),
                    enabled = !atMin
                ) {
                    val newVal = (value - step).coerceAtLeast(0.0)
                    onValueChange(newVal)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "减少",
                tint = if (atMin) ext.inkTertiary else ext.dutyBlue,
                modifier = Modifier.size(JrxpDimensions.iconSm)
            )
        }

        // 数量显示
        Text(
            text = "$qtyText $unit",
            style = JrxpNumericStyles.quantity,
            color = ext.inkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .scale(valueScale)
                .padding(horizontal = JrxpDimensions.spacingSm)
        )

        // 增加按钮
        Box(
            modifier = Modifier
                .size(JrxpDimensions.stepperButtonSize)
                .clip(RoundedCornerShape(topEnd = JrxpDimensions.cornerMd, bottomEnd = JrxpDimensions.cornerMd))
                .clickable(
                    interactionSource = plusInteraction,
                    indication = ripple(bounded = true),
                    enabled = !atMax
                ) {
                    val newVal = (value + step).coerceAtMost(maxValue)
                    onValueChange(newVal)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "增加",
                tint = if (atMax) ext.inkTertiary else ext.dutyBlue,
                modifier = Modifier.size(JrxpDimensions.iconSm)
            )
        }
    }

    // 达到上限提示
    if (atMax) {
        Text(
            text = "已达库存上限",
            style = MaterialTheme.typography.labelSmall,
            color = ext.warningAmber,
            modifier = Modifier.padding(top = JrxpDimensions.spacingXs)
        )
    }
}

// ── OrderStatusRail ─────────────────────────────────────────
// 订单状态纵向轨道：当前节点明确，已完成简洁，未开始弱化
@Composable
fun OrderStatusRail(
    steps: List<StatusStep>,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    Column(modifier = modifier) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧轨道
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp)
                ) {
                    // 节点圆点
                    Box(
                        modifier = Modifier
                            .size(if (step.state == StepState.CURRENT) 12.dp else 8.dp)
                            .background(
                                when (step.state) {
                                    StepState.COMPLETED -> ext.supplyGreen
                                    StepState.CURRENT -> ext.dutyBlue
                                    StepState.CANCELLED -> ext.criticalRed
                                    StepState.UPCOMING -> ext.ruleLine
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {}
                    // 连接线
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(JrxpDimensions.ruleLineWidth)
                                .height(36.dp)
                                .background(
                                    if (step.state == StepState.COMPLETED) ext.supplyGreen.copy(alpha = 0.4f)
                                    else ext.ruleLine
                                )
                        )
                    }
                }

                // 右侧内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = JrxpDimensions.spacingSm)
                ) {
                    Text(
                        text = step.title,
                        style = if (step.state == StepState.CURRENT) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodySmall,
                        color = when (step.state) {
                            StepState.COMPLETED -> ext.inkSecondary
                            StepState.CURRENT -> ext.inkPrimary
                            StepState.CANCELLED -> ext.criticalRed
                            StepState.UPCOMING -> ext.inkTertiary
                        },
                        fontWeight = if (step.state == StepState.CURRENT) FontWeight.SemiBold else FontWeight.Normal
                    )
                    step.timestamp?.let {
                        Text(
                            text = it,
                            style = JrxpNumericStyles.timestamp,
                            color = ext.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (index < steps.lastIndex) {
                        Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
                    }
                }
            }
        }
    }
}

data class StatusStep(
    val title: String,
    val state: StepState,
    val timestamp: String? = null
) {
    val label: String get() = title
}

enum class StepState { COMPLETED, CURRENT, UPCOMING, CANCELLED }

// ── ProcurementDeadlineStrip ────────────────────────────────
// 采购截止时间条：窄条显示今日截止和配送时间
@Composable
fun ProcurementDeadlineStrip(
    deadlineLabel: String,
    deadlineValue: String,
    deliveryLabel: String,
    deliveryValue: String,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.readingSurface)
            .drawBehind {
                drawLine(
                    color = ext.ruleLine.copy(alpha = 0.6f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = JrxpDimensions.spacingLg, vertical = JrxpDimensions.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = deadlineLabel,
                style = MaterialTheme.typography.bodySmall,
                color = ext.inkSecondary
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingXs))
            Text(
                text = deadlineValue,
                style = JrxpNumericStyles.amountSmall,
                color = ext.criticalRed,
                fontWeight = FontWeight.Bold
            )
        }

        // 细分隔
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(ext.softDivider)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = deliveryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = ext.inkSecondary
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingXs))
            Text(
                text = deliveryValue,
                style = JrxpNumericStyles.amountSmall,
                color = ext.dutyBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── PrimaryActionDock ───────────────────────────────────────
// 底部主操作区：安全的主按钮停靠区
@Composable
fun PrimaryActionDock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = JrxpTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.readingSurface)
            .drawBehind {
                drawLine(
                    color = ext.ruleLine.copy(alpha = 0.5f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .padding(JrxpDimensions.spacingLg),
        content = content
    )
}

// ── UnitIdentityHeader ──────────────────────────────────────
// 单位身份信息头：显示单位名称、部门、角色
@Composable
fun UnitIdentityHeader(
    userName: String,
    userId: String,
    department: String,
    institution: String,
    role: String,
    isAuthorized: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.commandNavy)
            .statusBarsPadding()
            .padding(JrxpDimensions.spacingXl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "工号：$userId",
                    style = JrxpNumericStyles.timestamp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = JrxpDimensions.spacingXs)
                )
            }
            if (isAuthorized) {
                SupplyStatusMark(label = "账号已启用", type = StatusType.SUCCESS)
            }
        }

        Spacer(modifier = Modifier.height(JrxpDimensions.spacingLg))

        // 身份信息行（台账对齐风格）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(JrxpDimensions.spacingLg)
        ) {
            IdentityField(label = "机构名称", value = institution, modifier = Modifier.weight(1f))
            IdentityField(label = "所属部门", value = department, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(JrxpDimensions.spacingSm))
        IdentityField(label = "当前角色", value = role)
    }
}

@Composable
private fun IdentityField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ── InventoryRiskBlock ──────────────────────────────────────
// 库存风险提示块
@Composable
fun InventoryRiskBlock(
    itemName: String,
    currentStock: String,
    threshold: String,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.warningAmberBg, JrxpDimensions.shapeSm)
            .border(JrxpDimensions.ruleLineWidth, ext.warningAmber.copy(alpha = 0.3f), JrxpDimensions.shapeSm)
            .padding(JrxpDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = JrxpIcons.StockAlert,
            contentDescription = "库存预警",
            tint = ext.warningAmber,
            modifier = Modifier.size(JrxpDimensions.iconMd)
        )
        Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName,
                style = MaterialTheme.typography.bodyMedium,
                color = ext.inkPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "当前 $currentStock · 预警值 $threshold",
                style = JrxpNumericStyles.timestamp,
                color = ext.warningAmber
            )
        }
    }
}

// ── SystemStatusRibbon ──────────────────────────────────────
// 系统状态窄条：正常/异常状态简洁显示
@Composable
fun SystemStatusRibbon(
    isHealthy: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    val ext = JrxpTheme.colors
    val bgColor = if (isHealthy) ext.supplyGreenBg else ext.warningAmberBg
    val textColor = if (isHealthy) ext.supplyGreen else ext.warningAmber
    val icon = if (isHealthy) JrxpIcons.SystemStatus else JrxpIcons.StockAlert

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = JrxpDimensions.spacingLg, vertical = JrxpDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "系统状态",
            tint = textColor,
            modifier = Modifier.size(JrxpDimensions.iconXs)
        )
        Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

// ── LedgerRow ───────────────────────────────────────────────
// 台账行：左侧编号 + 内容 + 右对齐数字
@Composable
fun LedgerRow(
    index: Int,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val ext = JrxpTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ext.paleBlue else Color.Transparent,
        animationSpec = tween(JrxpMotion.durationFast),
        label = "ledgerRowBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = JrxpDimensions.ledgerRowPadding)
            .drawBehind {
                drawLine(
                    color = ext.softDivider.copy(alpha = 0.6f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧编号列
        Text(
            text = String.format("%02d", index),
            style = JrxpNumericStyles.timestamp,
            color = ext.inkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(JrxpDimensions.ledgerNumberColumnWidth)
        )
        content()
    }
}

// ── MenuActionRow ───────────────────────────────────────────
// 菜单操作行：替代 ProfileMenuItem，更简洁
@Composable
fun MenuActionRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String = "",
    showDot: Boolean = false,
    onClick: () -> Unit
) {
    val ext = JrxpTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JrxpDimensions.listItemMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = JrxpDimensions.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = ext.dutyBlue,
            modifier = Modifier.size(JrxpDimensions.iconMd)
        )
        Spacer(modifier = Modifier.width(JrxpDimensions.spacingMd))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = ext.inkPrimary,
            modifier = Modifier.weight(1f)
        )
        if (trailingText.isNotEmpty()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = ext.inkTertiary
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(ext.criticalRed, CircleShape)
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ext.ruleLine,
            modifier = Modifier.size(JrxpDimensions.iconSm)
        )
    }
}

// ── JrxpDivider ─────────────────────────────────────────────
// 统一分割线
@Composable
fun JrxpDivider(modifier: Modifier = Modifier) {
    val ext = JrxpTheme.colors
    HorizontalDivider(
        modifier = modifier,
        thickness = JrxpDimensions.ruleLineWidth,
        color = ext.softDivider
    )
}

// ── JrxpButton ──────────────────────────────────────────────
// 主操作按钮：统一样式
@Composable
fun JrxpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val ext = JrxpTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(JrxpDimensions.touchTargetMin),
        shape = JrxpDimensions.shapeMd,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = ext.commandNavy,
            contentColor = Color.White,
            disabledContainerColor = ext.ruleLine,
            disabledContentColor = ext.inkTertiary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(JrxpDimensions.iconMd),
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(JrxpDimensions.iconSm)
                )
                Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun JrxpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val ext = JrxpTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(JrxpDimensions.touchTargetMin),
        shape = JrxpDimensions.shapeMd,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(ext.ruleLine)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ext.dutyBlue
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(JrxpDimensions.iconSm)
            )
            Spacer(modifier = Modifier.width(JrxpDimensions.spacingSm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
