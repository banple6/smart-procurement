package com.smartprocurement.internal.ui.thinkingorb

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.isActive

/** Native Canvas port of Jakub Antalik's MIT-licensed Thinking Orbs. */
@Composable
fun ThinkingOrb(
    state: ThinkingOrbState = ThinkingOrbState.Working,
    size: Dp = 64.dp,
    color: Color? = null,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
    debugTimeSeconds: Double? = null
) {
    val preset = when (size) { 64.dp -> OrbPresetSize.Large; 20.dp -> OrbPresetSize.Inline; else -> error("ThinkingOrb supports only the official 64.dp and 20.dp presets.") }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animatorScale by animatorDurationScale()
    // MaterialTheme can be explicitly dark even when the device setting is light (and in previews/tests).
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val buffer = remember { OrbFrameBuffer() }
    var frameTime by remember { mutableDoubleStateOf(0.0) }
    val reducedMotion = animatorScale == 0f
    val active = debugTimeSeconds == null && !paused && !reducedMotion && lifecycle == Lifecycle.State.RESUMED
    LaunchedEffect(active) {
        if (active) while (isActive) withFrameNanos { frameTime = it / 1_000_000_000.0 }
    }
    val time = debugTimeSeconds ?: if (reducedMotion) .6 else frameTime
    Canvas(
        modifier = modifier.size(size).clipToBounds().semantics { contentDescription = "动态状态：${state.name}" }
    ) {
        ThinkingOrbEngine.frame(state, preset, time, buffer)
        paintFrame(buffer, dark, color, preset.logicalPixels)
    }
}

@Composable
private fun animatorDurationScale(): State<Float> {
    val context = LocalContext.current
    val state = remember { mutableFloatStateOf(readAnimatorScale(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state.floatValue = readAnimatorScale(context) }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}
private fun readAnimatorScale(context: android.content.Context): Float = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
private fun DrawScope.paintFrame(frame: OrbFrameBuffer, dark: Boolean, customColor: Color?, logicalSize: Double) {
    val scale = minOf(size.width, size.height) / logicalSize.toFloat()
    val xOffset=(size.width-logicalSize.toFloat()*scale)/2; val yOffset=(size.height-logicalSize.toFloat()*scale)/2
    fun ink(white: Double, alpha: Double): Color {
        val level = (if (dark) 1 - white else white).coerceIn(0.0, 1.0).toFloat()
        val resolvedAlpha = alpha.toFloat().coerceIn(0f, 1f)
        // Match the upstream Canvas renderer's grayscale ink. A brand tint is
        // still available to callers outside the import lifecycle, but the
        // status panel intentionally uses the unmodified official treatment.
        return customColor?.copy(alpha = (customColor.alpha * resolvedAlpha).coerceIn(0f, 1f))
            ?: Color(level, level, level, resolvedAlpha)
    }
    for(i in 0 until frame.lineCount) drawLine(ink(frame.lineWhite(i),frame.lineAlpha(i)),Offset(xOffset+frame.lineX1(i).toFloat()*scale,yOffset+frame.lineY1(i).toFloat()*scale),Offset(xOffset+frame.lineX2(i).toFloat()*scale,yOffset+frame.lineY2(i).toFloat()*scale),frame.lineWidth(i).toFloat()*scale)
    for(i in 0 until frame.dotCount) drawCircle(ink(frame.dotWhite(i),frame.dotAlpha(i)),frame.dotRadius(i).toFloat()*scale,Offset(xOffset+frame.dotX(i).toFloat()*scale,yOffset+frame.dotY(i).toFloat()*scale))
}

/**
 * Shared business-status treatment for import, sync and other short-lived background work.
 * The motion stays inside its own surface so the surrounding operational UI remains stable.
 */
@Composable
fun ThinkingOrbStatusPanel(
    state: ThinkingOrbState,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val border by animateColorAsState(
        targetValue = if (active) primary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.62f),
        animationSpec = tween(220),
        label = "orb_status_border"
    )
    val highlight = rememberInfiniteTransition(label = "orb_status_highlight")
        .animateFloat(
            initialValue = 0.10f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(tween(1200)),
            label = "orb_status_highlight_alpha"
        ).value
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (active) {
                    drawRoundRect(
                        color = primary.copy(alpha = highlight * 0.34f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 5.dp.toPx())
                    )
                }
            },
        shape = shape,
        color = surface,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            // The state is supplied by the real import lifecycle: searching, solving, then working.
            // The stronger border glow remains reserved for a real upload, analysis, or apply operation.
            ThinkingOrb(state = state, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
