package com.smartprocurement.internal.ui.designsystem

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartprocurement.internal.R
import androidx.core.view.WindowCompat

@Composable
fun PoliceStatusBar(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = color.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
    }
}

@Composable
fun PoliceBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = "人民警察警徽"
) {
    Image(
        painter = painterResource(R.drawable.police_badge),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
fun PoliceBadgeImage(
    size: Dp,
    contentDescription: String? = "人民警察警徽",
    modifier: Modifier = Modifier
) {
    PoliceBadge(
        modifier = modifier.size(size),
        contentDescription = contentDescription
    )
}

@Composable
fun PoliceBrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    PoliceStatusBar(color = PoliceColors.PoliceNavy, darkIcons = false)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PoliceColors.PoliceNavy
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 84.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PoliceColors.TextOnBlue,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliceColors.TextOnBlue.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PoliceIdentityHeader(
    title: String,
    line1: String,
    line2: String,
    modifier: Modifier = Modifier
) {
    PoliceStatusBar(color = PoliceColors.PoliceNavy, darkIcons = false)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PoliceColors.PoliceNavy
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 128.dp, max = 180.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = PoliceColors.TextOnBlue, fontWeight = FontWeight.SemiBold)
                Text(line1, style = MaterialTheme.typography.bodyMedium, color = PoliceColors.TextOnBlue.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line2, style = MaterialTheme.typography.bodyMedium, color = PoliceColors.TextOnBlue.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PolicePrimaryButton(
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
            .height(PoliceDimens.PrimaryButtonHeight),
        shape = RoundedCornerShape(PoliceShapes.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = PoliceColors.PolicePrimary,
            contentColor = PoliceColors.TextOnBlue,
            disabledContainerColor = PoliceColors.TextDisabled,
            disabledContentColor = PoliceColors.SurfaceWhite
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PoliceSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PoliceDimens.MinTouchTarget),
        shape = RoundedCornerShape(PoliceShapes.ControlRadius),
        border = BorderStroke(1.dp, PoliceColors.BorderColor)
    ) {
        Text(text, color = PoliceColors.PolicePrimary)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PoliceTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null
) {
    PoliceStatusBar(color = PoliceColors.SurfaceWhite, darkIcons = true)
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = PoliceColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(PoliceDimens.MinTouchTarget)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = PoliceColors.PolicePrimary)
                }
            }
        },
        actions = {
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = PoliceDimens.MinTouchTarget)) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, tint = PoliceColors.PolicePrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(actionText, color = PoliceColors.PolicePrimary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PoliceColors.SurfaceWhite,
            titleContentColor = PoliceColors.TextPrimary,
            navigationIconContentColor = PoliceColors.PolicePrimary,
            actionIconContentColor = PoliceColors.PolicePrimary
        ),
        modifier = Modifier.drawBehind {
            drawLine(
                color = PoliceColors.DividerColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    )
}

@Composable
fun PoliceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(PoliceColors.PolicePrimary, RoundedCornerShape(PoliceShapes.StatusRadius))
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.TextPrimary)
    }
}

@Composable
fun PoliceStatusLabel(status: String, modifier: Modifier = Modifier) {
    val style = PoliceStatus.fromUiStatus(status)
    Text(
        text = style.label,
        modifier = modifier
            .background(style.containerColor, RoundedCornerShape(PoliceShapes.StatusRadius))
            .border(1.dp, style.contentColor.copy(alpha = 0.25f), RoundedCornerShape(PoliceShapes.StatusRadius))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = style.contentColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PoliceInfoBanner(title: String, message: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    val bg = if (danger) PoliceColors.StatusErrorBackground else PoliceColors.PoliceLight
    val fg = if (danger) PoliceColors.StatusError else PoliceColors.PolicePrimary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PoliceShapes.ControlRadius),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(PoliceDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(message, color = PoliceColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PoliceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PoliceShapes.CardRadius),
        color = PoliceColors.SurfaceWhite,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, PoliceColors.BorderColor)
    ) {
        Box(Modifier.padding(PoliceDimens.CardPadding)) {
            content()
        }
    }
}

@Composable
fun PoliceEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = PoliceColors.TextTertiary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.TextPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = PoliceColors.TextSecondary)
    }
}

@Composable
fun PoliceBottomActionBar(content: @Composable () -> Unit) {
    Surface(
        color = PoliceColors.SurfaceWhite,
        tonalElevation = 1.dp,
        modifier = Modifier.drawBehind {
            drawLine(
                color = PoliceColors.DividerColor,
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
fun PoliceRefreshAction(onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, modifier = Modifier.size(PoliceDimens.MinTouchTarget)) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = PoliceColors.PolicePrimary)
    }
}
