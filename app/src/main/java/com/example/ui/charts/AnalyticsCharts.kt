package com.smartprocurement.internal.ui.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

fun chartSelectionIndex(x: Float, width: Float, count: Int): Int? {
    if (count <= 0 || width <= 0f) return null
    if (count == 1) return 0
    return ((x.coerceIn(0f, width) / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

fun barSelectionIndex(y: Float, height: Float, count: Int, top: Float = 0f, bottom: Float = height): Int? {
    if (count <= 0 || height <= 0f || bottom <= top) return null
    val slot = (bottom - top) / count
    return ((y.coerceIn(top, bottom - 0.001f) - top) / slot).toInt().coerceIn(0, count - 1)
}

fun chartLabelIndices(count: Int): List<Int> = when {
    count <= 0 -> emptyList()
    count == 1 -> listOf(0)
    count == 2 -> listOf(0, 1)
    else -> listOf(0, count / 2, count - 1).distinct()
}

@Composable
fun AnalyticsLineChart(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    formattedValues: List<String> = emptyList(),
    axisPrefix: String = "",
    lineColor: Color = MaterialTheme.colorScheme.primary,
    selectedIndex: Int? = null,
    onSelected: (Int) -> Unit = {}
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier.fillMaxWidth().height(230.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val left = 52.dp.toPx()
                    val right = size.width - 14.dp.toPx()
                    chartSelectionIndex(offset.x - left, right - left, values.size)?.let(onSelected)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 52.dp.toPx()
            val right = size.width - 14.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 34.dp.toPx()
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = 11.dp.toPx()
            }
            val minimum = values.minOrNull() ?: 0f
            val maximum = values.maxOrNull() ?: minimum
            val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                val value = maximum - range * index / 3f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(axisPrefix + compactChartValue(value), 4.dp.toPx(), y + 4.dp.toPx(), labelPaint)
            }
            if (values.isEmpty()) return@Canvas
            val points = values.mapIndexed { index, value ->
                val x = if (values.size == 1) (left + right) / 2f else left + (right - left) * index / (values.size - 1)
                val y = bottom - (bottom - top) * (value - minimum) / range
                Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            points.forEachIndexed { index, point ->
                if (selectedIndex == index) {
                    drawLine(lineColor.copy(alpha = 0.35f), Offset(point.x, top), Offset(point.x, bottom), 1.dp.toPx())
                }
                drawCircle(lineColor, radius = (if (selectedIndex == index) 8 else 5).dp.toPx(), center = point)
                drawCircle(pointColor, radius = 2.dp.toPx(), center = point)
            }
            chartLabelIndices(values.size).forEach { index ->
                val label = labels.getOrNull(index) ?: (index + 1).toString()
                val x = points[index].x
                val textWidth = labelPaint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(label, (x - textWidth / 2f).coerceIn(left, right - textWidth), size.height - 10.dp.toPx(), labelPaint)
            }
            selectedIndex?.let { index ->
                formattedValues.getOrNull(index)?.let { value ->
                    val point = points.getOrNull(index) ?: return@let
                    drawContext.canvas.nativeCanvas.drawText(value, (point.x + 6.dp.toPx()).coerceAtMost(right - labelPaint.measureText(value)), top + 13.dp.toPx(), labelPaint)
                }
            }
        }
    }
}

@Composable
fun AnalyticsBarChart(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    formattedValues: List<String> = emptyList(),
    barColor: Color = MaterialTheme.colorScheme.secondary,
    selectedIndex: Int? = null,
    onSelected: (Int) -> Unit = {}
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartHeight = maxOf(176, values.size.coerceAtLeast(1) * 48)
    Box(
        modifier = modifier.fillMaxWidth().height(chartHeight.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val top = 12.dp.toPx()
                    val bottom = size.height - 12.dp.toPx()
                    barSelectionIndex(offset.y, size.height.toFloat(), values.size, top, bottom)?.let(onSelected)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.isEmpty()) return@Canvas
            val left = 78.dp.toPx()
            val right = size.width - 68.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val maximum = (values.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f
            val slot = (bottom - top) / values.size
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = 11.dp.toPx()
            }
            drawLine(gridColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { index, value ->
                val barHeight = slot * 0.58f
                val y = top + slot * index + (slot - barHeight) / 2f
                val width = (right - left) * value.coerceAtLeast(0f) / maximum
                val selected = selectedIndex == index
                drawRect(
                    color = if (selected) Color(0xFF123D72) else barColor,
                    topLeft = Offset(left, y),
                    size = Size(width.coerceAtLeast(2.dp.toPx()), barHeight)
                )
                val name = labels.getOrNull(index) ?: "${index + 1}"
                val shownName = if (name.length > 6) name.take(6) + "…" else name
                drawContext.canvas.nativeCanvas.drawText(shownName, 6.dp.toPx(), y + barHeight * 0.7f, labelPaint)
                val displayValue = formattedValues.getOrNull(index) ?: compactChartValue(value)
                drawContext.canvas.nativeCanvas.drawText(displayValue, right + 8.dp.toPx(), y + barHeight * 0.7f, labelPaint)
            }
        }
    }
}

private fun compactChartValue(value: Float): String = when {
    value >= 10_000f -> "${"%.1f".format(value / 10_000f)}万"
    value >= 1_000f -> "${"%.1f".format(value / 1_000f)}千"
    value % 1f == 0f -> value.toInt().toString()
    else -> "%.1f".format(value)
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)
