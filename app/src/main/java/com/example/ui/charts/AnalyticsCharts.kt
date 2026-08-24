package com.smartprocurement.internal.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt


fun chartSelectionIndex(x: Float, width: Float, count: Int): Int? {
    if (count <= 0 || width <= 0f) return null
    if (count == 1) return 0
    return ((x.coerceIn(0f, width) / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}


@Composable
fun AnalyticsLineChart(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    selectedIndex: Int? = null,
    onSelected: (Int) -> Unit = {}
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    val pointColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { offset -> chartSelectionIndex(offset.x, size.width.toFloat(), values.size)?.let(onSelected) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 18.dp.toPx()
            val right = size.width - 18.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            if (values.isEmpty()) return@Canvas
            val minimum = values.minOrNull() ?: 0f
            val maximum = values.maxOrNull() ?: minimum
            val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
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
                drawCircle(lineColor, radius = (if (selectedIndex == index) 8 else 5).dp.toPx(), center = point)
                drawCircle(pointColor, radius = 2.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
fun AnalyticsBarChart(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.secondary,
    selectedIndex: Int? = null,
    onSelected: (Int) -> Unit = {}
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { offset -> chartSelectionIndex(offset.y, size.height.toFloat(), values.size)?.let(onSelected) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.isEmpty()) return@Canvas
            val left = 18.dp.toPx()
            val right = size.width - 18.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val maximum = (values.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f
            val slot = (bottom - top) / values.size
            drawLine(gridColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { index, value ->
                val height = slot * 0.62f
                val y = top + slot * index + (slot - height) / 2f
                val width = (right - left) * value.coerceAtLeast(0f) / maximum
                drawRect(
                    color = if (selectedIndex == index) MaterialThemeColorFallback else barColor,
                    topLeft = Offset(left, y),
                    size = androidx.compose.ui.geometry.Size(width, height)
                )
            }
        }
    }
}

private val MaterialThemeColorFallback = Color(0xFF123D72)
