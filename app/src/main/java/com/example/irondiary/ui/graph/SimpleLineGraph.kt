package com.example.irondiary.ui.graph

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.toSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A robust, state-hoisted Line Graph with Zoom, Pan, and Selection capabilities.
 */
@Composable
fun SimpleLineGraph(
    dataPoints: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    tooltipFormatter: (value: Double, label: String) -> String = { value, label ->
        "${String.format("%.1f", value)} on $label"
    },
    state: SimpleLineGraphState = rememberSimpleLineGraphState(
        dataPoints = dataPoints,
        key = dataPoints.hashCode()
    ),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    graphPadding: PaddingValues = PaddingValues(start = 50.dp, bottom = 40.dp, top = 20.dp, end = 20.dp),
    strokeWidth: Dp = 4.dp,
    pointRadius: Dp = 2.dp,
    selectedPointRadius: Dp = 8.dp,
    selectedPointInnerRadius: Dp = 4.dp,
    selectionTranslateY: Dp = 10.dp,
    tooltipHorizontalPadding: Dp = 32.dp,
    tooltipVerticalPadding: Dp = 16.dp,
    tooltipCornerRadius: Dp = 8.dp,
    tooltipYSpacing: Dp = 8.dp,
    yAxisTextColor: Color = MaterialTheme.colorScheme.onSurface,
    yAxisTextSize: Float = 28f,
    yAxisLabelPadding: Dp = 10.dp,
    xAxisTextColor: Color = MaterialTheme.colorScheme.onSurface,
    xAxisTextSize: Float = 32f,
    xAxisLabelVerticalOffset: Dp = 50.dp,
    gridLineColor: Color = yAxisTextColor.copy(alpha = 0.2f)
) {
    require(dataPoints.size == labels.size) { "dataPoints and labels must have the same size." }

    if (dataPoints.isEmpty()) return

    val animationProgress = remember { Animatable(0f) }
    val selectionAnimation = remember { Animatable(0f) }

    LaunchedEffect(dataPoints) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
    }

    LaunchedEffect(state.selectedPointIndex) {
        selectionAnimation.snapTo(0f)
        if (state.selectedPointIndex != null) {
            selectionAnimation.animateTo(1f, animationSpec = tween(300))
        }
    }

    val textPaint = remember(xAxisTextColor, xAxisTextSize) {
        Paint().apply {
            color = xAxisTextColor.toArgb()
            textSize = xAxisTextSize
            textAlign = Paint.Align.CENTER
        }
    }

    val yAxisPaint = remember(yAxisTextColor, yAxisTextSize) {
        Paint().apply {
            color = yAxisTextColor.toArgb()
            textSize = yAxisTextSize
            textAlign = Paint.Align.RIGHT
        }
    }

    val bounds = remember { Rect() }

    Canvas(
        modifier = modifier
            .padding(graphPadding)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { state.onDoubleTap() },
                    onTap = { state.onTap(it, size.toSize()) }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    state.onTransform(centroid, pan, zoom, size.toSize())
                }
            }
    ) {
        val strokeWidthPx = strokeWidth.toPx()
        val allPointsRadiusPx = pointRadius.toPx()
        val selectionTranslatePx = selectionTranslateY.toPx()
        val selectedPointRadiusPx = selectedPointRadius.toPx()
        val selectedPointInnerRadiusPx = selectedPointInnerRadius.toPx()
        val tooltipHPaddingPx = tooltipHorizontalPadding.toPx()
        val tooltipVPaddingPx = tooltipVerticalPadding.toPx()
        val tooltipCornerRadiusPx = tooltipCornerRadius.toPx()
        val tooltipYSpacingPx = tooltipYSpacing.toPx()

        val width = size.width
        val height = size.height

        val graphContentWidth = width * state.zoomLevel.value
        val spacing = if (dataPoints.size > 1) graphContentWidth / (dataPoints.size - 1) else 0f

        val maxVal = dataPoints.maxOrNull() ?: 100.0
        val minVal = dataPoints.minOrNull() ?: 0.0
        val paddedMin: Double
        val paddedMax: Double

        if (maxVal == minVal) {
            paddedMin = minVal - 1.0
            paddedMax = maxVal + 1.0
        } else {
            val range = (maxVal - minVal)
            paddedMin = (minVal - range * 0.1).let { if (it.isInfinite() || it.isNaN()) 0.0 else it }
            paddedMax = (maxVal + range * 0.1).let { if (it.isInfinite() || it.isNaN()) 100.0 else it }
        }
        val yRange = (paddedMax - paddedMin).coerceAtLeast(1.0)

        val yAxisLabelCount = 5
        val yAxisLabelPaddingPx = yAxisLabelPadding.toPx()
        for (i in 0 until yAxisLabelCount) {
            val value = paddedMin + (yRange / (yAxisLabelCount - 1)) * i
            val y = height - (i * height / (yAxisLabelCount - 1))
            drawLine(gridLineColor, start = Offset(0f, y), end = Offset(width, y))
            drawContext.canvas.nativeCanvas.drawText(
                value.roundToInt().toString(),
                -yAxisLabelPaddingPx,
                y + yAxisTextSize / 3,
                yAxisPaint
            )
        }

        val coordinates = dataPoints.mapIndexed { index, value ->
            val x = index * spacing + state.panOffset.value
            val normalizedY = ((value - paddedMin) / yRange)
            val y = height - (normalizedY * height).toFloat()
            Offset(x, y)
        }

        clipRect(right = width, bottom = height) {
            clipRect(right = width * animationProgress.value) {
                val path = Path()
                coordinates.forEachIndexed { index, offset ->
                    if (index == 0) {
                        path.moveTo(offset.x, offset.y)
                    } else {
                        val previous = coordinates[index - 1]
                        path.cubicTo(
                            (previous.x + offset.x) / 2f, previous.y,
                            (previous.x + offset.x) / 2f, offset.y,
                            offset.x, offset.y
                        )
                    }
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(coordinates.last().x, height)
                    lineTo(coordinates.first().x, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillColor, Color.Transparent),
                        endY = height
                    )
                )

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )

                coordinates.forEach { offset ->
                    if (offset.x in -allPointsRadiusPx..(width + allPointsRadiusPx)) {
                        drawCircle(lineColor.copy(alpha = 0.5f), allPointsRadiusPx, offset)
                    }
                }
            }

            state.selectedPointIndex?.let { index ->
                if (index in coordinates.indices) {
                    val selectedOffset = coordinates[index]
                    if (selectedOffset.x in 0f..(width * animationProgress.value)) {
                        withTransform({
                            scale(selectionAnimation.value, selectionAnimation.value, pivot = selectedOffset)
                            translate(left = 0f, top = (1 - selectionAnimation.value) * selectionTranslatePx)
                        }) {
                            val selectedValue = dataPoints[index]

                            drawLine(
                                color = gridLineColor.copy(alpha = selectionAnimation.value),
                                start = Offset(selectedOffset.x, 0f),
                                end = Offset(selectedOffset.x, height)
                            )

                            drawCircle(lineColor, selectedPointRadiusPx, selectedOffset)
                            drawCircle(Color.White, selectedPointInnerRadiusPx, selectedOffset)

                            val labelText = labels.getOrElse(index) { "" }
                            val tooltipText = tooltipFormatter(selectedValue, labelText)

                            textPaint.getTextBounds(tooltipText, 0, tooltipText.length, bounds)

                            val tooltipWidth = bounds.width() + tooltipHPaddingPx
                            val tooltipHeight = bounds.height() + tooltipVPaddingPx

                            val tooltipX = (selectedOffset.x - tooltipWidth / 2)
                                .coerceIn(0f, size.width - tooltipWidth)
                            val tooltipY = (selectedOffset.y - tooltipHeight - tooltipYSpacingPx)
                                .coerceAtLeast(0f)

                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.7f * selectionAnimation.value),
                                topLeft = Offset(tooltipX, tooltipY),
                                size = Size(tooltipWidth, tooltipHeight),
                                cornerRadius = CornerRadius(tooltipCornerRadiusPx)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                tooltipText,
                                tooltipX + tooltipWidth / 2,
                                tooltipY + tooltipHeight / 2 + bounds.height() / 2,
                                textPaint.apply {
                                    color = Color.White.copy(alpha = selectionAnimation.value).toArgb()
                                }
                            )
                        }
                    }
                }
            }
        }

        val desiredLabelsOnScreen = 5
        val totalLabels = (desiredLabelsOnScreen * state.zoomLevel.value).toInt()
        val labelInterval = (dataPoints.size / totalLabels).coerceAtLeast(1)
        val xAxisLabelVerticalOffsetPx = xAxisLabelVerticalOffset.toPx()

        coordinates.forEachIndexed { index, offset ->
            if (offset.x in -50f..(width + 50f) && index % labelInterval == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    labels.getOrElse(index) { "" },
                    offset.x,
                    height + xAxisLabelVerticalOffsetPx,
                    textPaint.apply { color = xAxisTextColor.toArgb() }
                )
            }
        }
    }
}

@Composable
fun rememberSimpleLineGraphState(
    dataPoints: List<Double>,
    key: Any? = Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    hapticFeedback: HapticFeedback = LocalHapticFeedback.current
): SimpleLineGraphState {
    return rememberSaveable(
        key,
        saver = SimpleLineGraphState.getSaver(dataPoints, coroutineScope, hapticFeedback)
    ) {
        SimpleLineGraphState(dataPoints, coroutineScope, hapticFeedback)
    }
}

class SimpleLineGraphState(
    private val dataPoints: List<Double>,
    private val coroutineScope: CoroutineScope,
    private val hapticFeedback: HapticFeedback,
    initialZoom: Float = 1f,
    initialPan: Float = 0f,
    initialSelection: Int? = null
) {
    var selectedPointIndex by mutableStateOf(initialSelection)
        private set

    val zoomLevel = Animatable(initialZoom)
    val panOffset = Animatable(initialPan)

    fun onDoubleTap() {
        coroutineScope.launch {
            zoomLevel.animateTo(1f)
            panOffset.animateTo(0f)
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedPointIndex = null
    }

    fun onTap(tapOffset: Offset, size: Size) {
        val graphContentWidth = size.width * zoomLevel.value
        val spacing = if (dataPoints.isNotEmpty()) graphContentWidth / (dataPoints.size - 1) else 0f

        if (spacing > 0f) {
            val xInData = tapOffset.x - panOffset.value
            val closestIndex = (xInData / spacing).roundToInt().coerceIn(dataPoints.indices)
            val pointXOnScreen = closestIndex * spacing + panOffset.value

            if (abs(tapOffset.x - pointXOnScreen) < spacing / 2 + 20) {
                if (selectedPointIndex != closestIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedPointIndex = closestIndex
                }
            } else {
                selectedPointIndex = null
            }
        }
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, size: Size) {
        coroutineScope.launch {
            val newZoom = (zoomLevel.value * zoom).coerceIn(1f, 10f)
            val oldWidth = size.width * zoomLevel.value
            val newWidth = size.width * newZoom

            val panCorrection = (centroid.x - panOffset.value) * (newWidth / oldWidth - 1)
            val newPanOffset = (panOffset.value + pan.x - panCorrection)
            val maxPan = newWidth - size.width

            panOffset.snapTo(newPanOffset.coerceIn(-maxPan, 0f))
            zoomLevel.snapTo(newZoom)
        }
        selectedPointIndex = null
    }

    companion object {
        fun getSaver(
            dataPoints: List<Double>,
            coroutineScope: CoroutineScope,
            hapticFeedback: HapticFeedback
        ): Saver<SimpleLineGraphState, *> = listSaver(
            save = { listOf(it.zoomLevel.value, it.panOffset.value, it.selectedPointIndex) },
            restore = {
                SimpleLineGraphState(
                    dataPoints = dataPoints,
                    coroutineScope = coroutineScope,
                    hapticFeedback = hapticFeedback,
                    initialZoom = it[0] as Float,
                    initialPan = it[1] as Float,
                    initialSelection = it[2] as? Int
                )
            }
        )
    }
}
