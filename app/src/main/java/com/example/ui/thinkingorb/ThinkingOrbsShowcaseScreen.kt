package com.smartprocurement.internal.ui.thinkingorb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.ui.designsystem.GovernmentCard
import com.smartprocurement.internal.ui.designsystem.GovernmentPrimaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentSecondaryButton
import com.smartprocurement.internal.ui.designsystem.GovernmentTopBar
import kotlinx.coroutines.isActive

/** Debug-only visual comparison surface. It is only linked from the debug About page. */
@Composable
fun ThinkingOrbsShowcaseScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(ThinkingOrbState.Working) }
    var running by remember { mutableStateOf(true) }
    var resetKey by remember { mutableStateOf(0) }
    var elapsed by remember(resetKey) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(running, resetKey) {
        if (running) {
            var origin = 0L
            while (isActive) withFrameNanos { now ->
                if (origin == 0L) origin = now
                elapsed = (now - origin) / 1_000_000_000.0
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GovernmentTopBar(title = "Thinking Orbs 调试预览", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("官方 9 状态 · 64dp / 20dp 双 preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("仅用于核对原生 Canvas 移植；不接入业务流程。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                GovernmentPrimaryButton(text = if (running) "暂停" else "开始", onClick = { running = !running }, modifier = Modifier.width(96.dp))
                GovernmentSecondaryButton(text = "重置", onClick = { resetKey++; running = false }, modifier = Modifier.width(96.dp))
            }
            Text("当前状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OrbStatePicker(selected, onSelected = { selected = it })
            GovernmentCard {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThinkingOrb(state = selected, size = 64.dp, paused = !running, debugTimeSeconds = elapsed)
                    Text(selected.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Text("全状态对照", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ThinkingOrbState.entries.forEach { item ->
                GovernmentCard {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThinkingOrb(state = item, size = 64.dp, paused = !running, debugTimeSeconds = elapsed)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            ThinkingOrb(state = item, size = 20.dp, paused = !running, debugTimeSeconds = elapsed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbStatePicker(selected: ThinkingOrbState, onSelected: (ThinkingOrbState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ThinkingOrbState.entries.forEach { state ->
            FilterChip(selected = state == selected, onClick = { onSelected(state) }, label = { Text(state.label()) })
        }
    }
}

internal fun ThinkingOrbState.label(): String = when (this) {
    ThinkingOrbState.Working -> "工作中"
    ThinkingOrbState.Searching -> "检索中"
    ThinkingOrbState.Solving -> "求解中"
    ThinkingOrbState.Listening -> "监听中"
    ThinkingOrbState.Connecting -> "连接中"
    ThinkingOrbState.Weaving -> "编织中"
    ThinkingOrbState.Composing -> "组合中"
    ThinkingOrbState.Breathing -> "呼吸中"
    ThinkingOrbState.Shaping -> "塑形中"
}

internal fun isThinkingOrbPreviewBuild(): Boolean = BuildConfig.APP_VARIANT_LABEL == "动画预览版"

@Composable
fun ThinkingOrbPreviewShortcut(onClick: () -> Unit) {
    if (!isThinkingOrbPreviewBuild()) return
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThinkingOrb(ThinkingOrbState.Working, 20.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Thinking Orbs 动画演示", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("查看 9 种原生 Canvas 动画", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("查看", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
