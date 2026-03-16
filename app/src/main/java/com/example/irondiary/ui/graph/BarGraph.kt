package com.example.irondiary.ui.graph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private val LabelAreaHeight = 30.dp
private val YAxisAreaWidth = 40.dp
private val BarTextStyle = TextStyle(fontSize = 12.sp)
private const val MinZoom = 0.7f
private const val MaxZoom = 5f

private fun calculateNiceMaxValue(maxValue: Float, gridLines: Int = 4): Float {
    if (maxValue <= 0f) return gridLines.toFloat()

    val unroundedTickSize = maxValue / gridLines
    val x = ceil(log10(unroundedTickSize.toDouble()) - 1).toInt()
    val pow10x = 10.0.pow(x)
    val roundedTickRange = ceil(unroundedTickSize / pow10x) * pow10x
    return (gridLines * roundedTickRange).toFloat()
}

@Composable
fun SimpleBarGraph(
    modifier: Modifier = Modifier,
    data: Map<LocalDate, Float>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    onBarClick: (LocalDate) -> Unit = {}
) {
    if (data.isEmpty()) return

    val dataEntries = remember(data) { data.entries.toList() }

    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var tappedBarIndex by remember { mutableStateOf<Int?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(dataEntries) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing)
        )
        tappedBarIndex = null
    }

    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tooltipBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val tooltipTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }


    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(dataEntries) { // Tap gesture to show tooltips
                detectTapGestures { offset ->
                    val yAxisAreaWidthPx = YAxisAreaWidth.toPx()
                    // Adjust tap position for pan, zoom, and Y-axis offset
                    val graphX = (offset.x - pan.x - yAxisAreaWidthPx) / zoom

                    val barWidth = ((size.width - yAxisAreaWidthPx) / (dataEntries.size * 2))
                    val barAndSpaceWidth = barWidth * 2
                    val tappedIndex = (graphX / barAndSpaceWidth).toInt()

                    if (tappedIndex in dataEntries.indices) {
                        tappedBarIndex = if (tappedBarIndex == tappedIndex) null else tappedIndex
                        if (tappedBarIndex != null) {
                            onBarClick(dataEntries[tappedIndex].key)
                        }
                    }
                }
            }
            .pointerInput(Unit) { // Transform gesture for pan and zoom
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldZoom = zoom
                    val newZoom = (zoom * zoomChange).coerceIn(MinZoom, MaxZoom)

                    if (newZoom != oldZoom) {
                        val yAxisAreaWidthPx = YAxisAreaWidth.toPx()
                        // Calculate pan correction to zoom towards the gesture's centroid
                        val panCorrectionX = (centroid.x - yAxisAreaWidthPx - pan.x) * (newZoom / oldZoom - 1f)
                        pan -= Offset(panCorrectionX, 0f)
                    }

                    pan += panChange.copy(y = 0f) // We only pan horizontally
                    zoom = newZoom
                }
            }
    ) {
        val labelAreaHeightPx = LabelAreaHeight.toPx()
        val yAxisAreaWidthPx = YAxisAreaWidth.toPx()
        val barAreaHeightPx = size.height - labelAreaHeightPx
        val barAreaWidth = size.width - yAxisAreaWidthPx

        if (barAreaHeightPx <= 0 || barAreaWidth <= 0) return@Canvas

        // --- Draw Y-Axis Labels and Grid Lines ---
        val maxValue = dataEntries.maxOfOrNull { it.value } ?: 0f
        val numberOfGridLines = 4
        val maxLabelValue = calculateNiceMaxValue(maxValue, numberOfGridLines)
        val gridLineColor = onSurfaceColor.copy(alpha = 0.2f)

        (0..numberOfGridLines).forEach { i ->
            val ratio = i.toFloat() / numberOfGridLines
            val y = barAreaHeightPx * (1 - ratio)

            // Draw grid line
            drawLine(
                color = gridLineColor,
                start = Offset(yAxisAreaWidthPx, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Y-axis label
            val labelValue = maxLabelValue * ratio
            val labelText = if (labelValue == labelValue.toInt().toFloat()) {
                labelValue.toInt().toString()
            } else {
                String.format("%.1f", labelValue)
            }

            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = BarTextStyle.copy(color = onSurfaceColor, fontSize = 10.sp)
            )
            drawText(
                textLayoutResult,
                topLeft = Offset(
                    x = (yAxisAreaWidthPx - textLayoutResult.size.width) - 4.dp.toPx(), // Right-align labels
                    y = y - textLayoutResult.size.height / 2
                )
            )
        }

        // --- Draw Bars and X-Axis Labels ---
        val barWidthPx = (barAreaWidth / (dataEntries.size * 2))
        val totalContentWidth = barWidthPx * 2 * dataEntries.size * zoom
        val maxPan = max(0f, totalContentWidth - barAreaWidth)
        pan = Offset(pan.x.coerceIn(-maxPan, 0f), 0f) // Clamp pan to valid bounds

        withTransform({
            translate(left = pan.x + yAxisAreaWidthPx) // Apply pan and account for Y-axis area
            scale(scaleX = zoom, scaleY = 1f)
        }) {
            val barAndSpaceWidth = barWidthPx * 2

            dataEntries.forEachIndexed { index, (date, value) ->
                val currentXPx = barWidthPx + (index * barAndSpaceWidth)
                val barHeightPx = if (maxLabelValue > 0f) (value / maxLabelValue) * barAreaHeightPx else 0f
                val animatedBarHeight = barHeightPx * animationProgress.value

                drawRect(
                    color = barColor,
                    topLeft = Offset(x = currentXPx, y = barAreaHeightPx - animatedBarHeight),
                    size = Size(barWidthPx, animatedBarHeight)
                )

                val label = date.format(formatter)
                val textLayoutResult = textMeasurer.measure(text = label, style = BarTextStyle.copy(color = onSurfaceColor))
                val effectiveBarAndSpaceWidth = barAndSpaceWidth * zoom
                // Dynamically adjust label density based on zoom to prevent overlap
                val labelDensity = ceil((textLayoutResult.size.width + 12.dp.toPx()) / effectiveBarAndSpaceWidth).toInt()

                if (index % labelDensity == 0) {
                    drawText(
                        textLayoutResult,
                        topLeft = Offset(
                            x = currentXPx + barWidthPx / 2 - textLayoutResult.size.width / 2,
                            y = barAreaHeightPx + (labelAreaHeightPx - textLayoutResult.size.height) / 2
                        )
                    )
                }
            }
        }

        // --- Draw Tooltip for Tapped Bar ---
        tappedBarIndex?.let { index ->
            val entry = dataEntries.getOrNull(index) ?: return@let
            val barWidth = ((size.width - yAxisAreaWidthPx) / (dataEntries.size * 2))
            val barAndSpaceWidth = barWidth * 2
            val barXPx = (barWidth + (index * barAndSpaceWidth)) * zoom + pan.x + yAxisAreaWidthPx

            // Ensure the tooltip is drawn within the visible area
            if(barXPx < yAxisAreaWidthPx || barXPx > size.width) return@let

            val tooltipText = entry.value.toString()
            val tooltipLayout = textMeasurer.measure(text = tooltipText, style = BarTextStyle.copy(color = tooltipTextColor))
            val tooltipWidth = tooltipLayout.size.width + 16.dp.toPx()
            val tooltipHeight = tooltipLayout.size.height + 8.dp.toPx()
            val tooltipX = (barXPx + (barWidth * zoom / 2) - (tooltipWidth / 2)).coerceIn(0f, size.width - tooltipWidth)
            val tooltipY = barAreaHeightPx - (if (maxLabelValue > 0) (entry.value / maxLabelValue) * barAreaHeightPx else 0f) * animationProgress.value - tooltipHeight - 4.dp.toPx()

            drawRect(
                color = tooltipBackgroundColor,
                topLeft = Offset(tooltipX, tooltipY),
                size = Size(tooltipWidth, tooltipHeight)
            )
            drawText(
                tooltipLayout,
                topLeft = Offset(tooltipX + 8.dp.toPx(), tooltipY + 4.dp.toPx())
            )
        }
    }
}
