package com.example.irondiary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A lightweight, elegant sparkline component for visualizing data trends
 * directly within the AI chat interface.
 */
@Composable
fun MiniSparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    if (data.size < 2) return

    val min = data.minOrNull() ?: 0f
    val max = data.maxOrNull() ?: 1f
    val range = if (max - min == 0f) 1f else (max - min) * 1.1f // Add 10% padding

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val xStep = width / (data.size - 1)

        val path = Path()
        val fillPath = Path()

        data.forEachIndexed { i, value ->
            val x = i * xStep
            // Invert Y as Canvas origin is top-left
            val normalizedY = (value - min) / range
            val y = height - (normalizedY * height)

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (i == data.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Draw points
        data.forEachIndexed { i, value ->
            val x = i * xStep
            val normalizedY = (value - min) / range
            val y = height - (normalizedY * height)
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
