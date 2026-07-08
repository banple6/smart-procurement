package com.smartprocurement.internal.ui.designsystem

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.smartprocurement.internal.R

@Composable
fun PoliceStatusBar(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = color.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}

@Composable
fun PoliceBadgeImage(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "三公鲜配业务图标",
) {
    BusinessAppIconImage(size = size, modifier = modifier, contentDescription = contentDescription)
}

@Composable
fun BusinessAppIconImage(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "三公鲜配业务图标",
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun PoliceOpeningBadge(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.police_badge),
        contentDescription = "警徽",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun PoliceBrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    PoliceStatusBar(PoliceColors.Navy, darkIcons = false)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PoliceColors.Navy)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.76f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PoliceIdentityHeader(
    title: String,
    line1: String,
    line2: String,
    modifier: Modifier = Modifier,
) {
    PoliceStatusBar(PoliceColors.Navy, darkIcons = false)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PoliceColors.Navy)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line1, fontSize = 13.sp, color = Color.White.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line2, fontSize = 13.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PoliceTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
) {
    PoliceStatusBar(Color.White, darkIcons = true)
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind {
                drawLine(
                    color = PoliceColors.Divider,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = PoliceColors.Primary)
                    }
                }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PoliceColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, tint = PoliceColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(actionText, color = PoliceColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PoliceInfoBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PoliceColors.Light,
        border = BorderStroke(1.dp, PoliceColors.Border),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = PoliceColors.Navy, fontWeight = FontWeight.Bold)
            Text(message, color = PoliceColors.TextSecondary, fontSize = 14.sp)
        }
    }
}
