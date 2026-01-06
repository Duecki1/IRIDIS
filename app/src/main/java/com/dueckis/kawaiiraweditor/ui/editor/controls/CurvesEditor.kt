package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import com.dueckis.kawaiiraweditor.data.model.CurvesState
import com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints
import com.dueckis.kawaiiraweditor.domain.CurvesMath
import com.dueckis.kawaiiraweditor.domain.HistogramData
import kotlin.math.sqrt

// --- Helper Functions ---
private fun CurvesState.pointsFor(channel: CurveChannel): List<CurvePointState> = when (channel) {
    CurveChannel.Luma -> luma
    CurveChannel.Red -> red
    CurveChannel.Green -> green
    CurveChannel.Blue -> blue
}

private fun CurvesState.withPoints(channel: CurveChannel, points: List<CurvePointState>): CurvesState = when (channel) {
    CurveChannel.Luma -> copy(luma = points)
    CurveChannel.Red -> copy(red = points)
    CurveChannel.Green -> copy(green = points)
    CurveChannel.Blue -> copy(blue = points)
}

@Composable
internal fun CurvesEditor(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    var activeChannel by remember { mutableStateOf(CurveChannel.Luma) }
    val points = adjustments.curves.pointsFor(activeChannel)

    // 1. Color Animation
    val targetChannelColor = when (activeChannel) {
        CurveChannel.Luma -> MaterialTheme.colorScheme.primary
        CurveChannel.Red -> Color(0xFFFF6B6B)
        CurveChannel.Green -> Color(0xFF6BCB77)
        CurveChannel.Blue -> Color(0xFF4D96FF)
    }
    val animatedChannelColor by animateColorAsState(
        targetValue = targetChannelColor,
        animationSpec = tween(400),
        label = "ChannelColor"
    )

    // 2. Data Preparation
    val targetCurveLut = remember(points) { calculateCurveLut(points) }
    val targetHistoLut = remember(histogramData, activeChannel) {
        histogramData?.let { data ->
            when (activeChannel) {
                CurveChannel.Luma -> data.luma
                CurveChannel.Red -> data.red
                CurveChannel.Green -> data.green
                CurveChannel.Blue -> data.blue
            }
        } ?: FloatArray(256)
    }

    // 3. Animation State
    val animationProgress = remember { Animatable(1f) }

    val startCurveLut = remember { FloatArray(256) }
    val startHistoLut = remember { FloatArray(256) }
    var startPoints by remember { mutableStateOf<List<CurvePointState>>(emptyList()) }

    val previousFrameCurveLut = remember { FloatArray(256) }
    val previousFrameHistoLut = remember { FloatArray(256) }

    var lastSettledChannel by remember { mutableStateOf(activeChannel) }
    var lastSettledPoints by remember { mutableStateOf(points) }

    var animationTriggerKey by remember { mutableLongStateOf(0L) }
    var currentAnimationId by remember { mutableLongStateOf(0L) }

    val isChannelSwitch = activeChannel != lastSettledChannel
    val isReset = points == defaultCurvePoints() && lastSettledPoints != points && !isChannelSwitch

    if (isChannelSwitch || isReset) {
        System.arraycopy(previousFrameCurveLut, 0, startCurveLut, 0, 256)
        System.arraycopy(previousFrameHistoLut, 0, startHistoLut, 0, 256)
        startPoints = lastSettledPoints

        animationTriggerKey++
        lastSettledChannel = activeChannel
        lastSettledPoints = points
    } else {
        lastSettledPoints = points
    }

    LaunchedEffect(animationTriggerKey) {
        if (animationTriggerKey > 0L) {
            animationProgress.snapTo(0f)
            currentAnimationId = animationTriggerKey
            animationProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
    }

    val effectiveProgress = if (animationTriggerKey != currentAnimationId) 0f else animationProgress.value

    val latestAdjustments by rememberUpdatedState(adjustments)
    val latestOnAdjustmentsChange by rememberUpdatedState(onAdjustmentsChange)
    val latestCurves by rememberUpdatedState(adjustments.curves)
    val latestPoints by rememberUpdatedState(points)

    // --- MAIN CARD LAYOUT ---
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- LEFT COLUMN: CONTROLS ---
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween // Pushes Reset button to bottom
            ) {
                // Top: Title and Channels
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CHANNELS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )

                    val channels = remember { listOf(CurveChannel.Luma, CurveChannel.Red, CurveChannel.Green, CurveChannel.Blue) }
                    channels.forEach { channel ->
                        val isSelected = activeChannel == channel
                        val color = when (channel) {
                            CurveChannel.Luma -> MaterialTheme.colorScheme.onSurface
                            CurveChannel.Red -> Color(0xFFFF6B6B)
                            CurveChannel.Green -> Color(0xFF6BCB77)
                            CurveChannel.Blue -> Color(0xFF4D96FF)
                        }

                        val label = when(channel) {
                            CurveChannel.Luma -> "Luma"
                            CurveChannel.Red -> "Red"
                            CurveChannel.Green -> "Green"
                            CurveChannel.Blue -> "Blue"
                        }

                        CompactChannelRow(
                            label = label,
                            color = color,
                            isSelected = isSelected,
                            onClick = { activeChannel = channel }
                        )
                    }
                }

                // Bottom: Reset Icon
                IconButton(
                    onClick = {
                        onBeginEditInteraction()
                        val updated = latestCurves.withPoints(activeChannel, defaultCurvePoints())
                        latestOnAdjustmentsChange(latestAdjustments.copy(curves = updated))
                        onEndEditInteraction()
                    },
                    modifier = Modifier.size(32.dp).align(Alignment.Start) // Align to bottom-left
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = animatedChannelColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- RIGHT COLUMN: GRAPH ---
            var lastClickTime by remember { mutableLongStateOf(0L) }
            var lastClickedIndex by remember { mutableIntStateOf(-1) }

            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .pointerInput(activeChannel) {
                        val hitRadius = 28.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onBeginEditInteraction()

                            var working = latestPoints
                            val draggingIndex = closestPointIndex(down.position, working, size, hitRadius)

                            if (draggingIndex == null) {
                                val slop = viewConfiguration.touchSlop
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                    if (!change.pressed) {
                                        if (!moved && working.size < 16) {
                                            val newPoint = toCurvePoint(change.position, size)
                                            val newPoints = (working + newPoint).sortedBy { it.x }
                                            latestOnAdjustmentsChange(latestAdjustments.copy(curves = latestCurves.withPoints(activeChannel, newPoints)))
                                        }
                                        onEndEditInteraction()
                                        break
                                    }
                                    change.consume()
                                    if ((change.position - down.position).getDistance() > slop) moved = true
                                }
                            } else {
                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastClickTime
                                val isDoubleClick = draggingIndex == lastClickedIndex && timeDiff < 300

                                if (isDoubleClick && draggingIndex > 0 && draggingIndex < working.size - 1) {
                                    val newPoints = working.toMutableList().apply { removeAt(draggingIndex) }
                                    latestOnAdjustmentsChange(latestAdjustments.copy(curves = latestCurves.withPoints(activeChannel, newPoints)))
                                    down.consume()
                                    onEndEditInteraction()
                                    lastClickedIndex = -1
                                } else {
                                    lastClickTime = now
                                    lastClickedIndex = draggingIndex
                                    drag(down.id) { change ->
                                        change.consume()
                                        val target = toCurvePoint(change.position, size)
                                        working = moveCurvePoint(working, draggingIndex, target)
                                        latestOnAdjustmentsChange(latestAdjustments.copy(curves = latestCurves.withPoints(activeChannel, working)))
                                    }
                                    onEndEditInteraction()
                                }
                            }
                        }
                    }
            ) {
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                Canvas(modifier = Modifier.matchParentSize()) {
                    val curveStrokeWidth = 3.dp.toPx()
                    val pointRadius = 6.dp.toPx()
                    val pointStrokeWidth = 2.dp.toPx()

                    val isAnimating = effectiveProgress < 1f
                    val fadeOutAlpha = (1f - effectiveProgress * 4f).coerceAtLeast(0f)
                    val fadeInAlpha = effectiveProgress

                    // 1. Grid (Dashed)
                    for (i in 1..3) {
                        val t = i / 4f
                        drawLine(
                            color = gridColor,
                            start = Offset(size.width * t, 0f),
                            end = Offset(size.width * t, size.height),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height * t),
                            end = Offset(size.width, size.height * t),
                            pathEffect = dashEffect
                        )
                    }

                    // 2. Histogram
                    if (isAnimating) {
                        val interpolatedHisto = FloatArray(256) { i ->
                            lerp(startHistoLut[i], targetHistoLut[i], effectiveProgress)
                        }
                        drawHistoFromLut(interpolatedHisto, animatedChannelColor.copy(alpha = 0.12f), size)
                    } else {
                        drawHistoFromLut(targetHistoLut, animatedChannelColor.copy(alpha = 0.12f), size)
                    }

                    // 3. Curve
                    if (isAnimating) {
                        val interpolatedCurve = FloatArray(256) { i ->
                            lerp(startCurveLut[i], targetCurveLut[i], effectiveProgress)
                        }
                        drawCurveFill(interpolatedCurve, animatedChannelColor, size)
                        drawCurveFromLut(interpolatedCurve, animatedChannelColor, size, curveStrokeWidth)

                        if (fadeOutAlpha > 0.01f) drawCurvePoints(startPoints, size, fadeOutAlpha, pointRadius, pointStrokeWidth)
                        if (fadeInAlpha > 0.01f) drawCurvePoints(points, size, fadeInAlpha, pointRadius, pointStrokeWidth)

                    } else {
                        val curvePath = CurvesMath.buildCurvePath(points, size)
                        drawCurveFill(targetCurveLut, animatedChannelColor, size)
                        drawPath(curvePath, color = animatedChannelColor, style = Stroke(curveStrokeWidth))
                        drawCurvePoints(points, size, 1f, pointRadius, pointStrokeWidth)
                    }
                }

                SideEffect {
                    System.arraycopy(targetCurveLut, 0, previousFrameCurveLut, 0, 256)
                    System.arraycopy(targetHistoLut, 0, previousFrameHistoLut, 0, 256)
                }
            }
        }
    }
}

// --- Compact Channel Row ---
@Composable
private fun CompactChannelRow(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

// --- Drawing Helpers ---
private fun DrawScope.drawCurveFill(data: FloatArray, color: Color, size: Size) {
    val path = Path().apply {
        moveTo(0f, size.height)
        data.forEachIndexed { i, yVal ->
            val x = (i / 255f) * size.width
            val y = (255f - yVal) / 255f * size.height
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        close()
    }

    val brush = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.2f), Color.Transparent),
        startY = 0f,
        endY = size.height
    )
    drawPath(path, brush)
}

private fun DrawScope.drawCurvePoints(
    points: List<CurvePointState>,
    size: Size,
    alpha: Float,
    radiusPx: Float,
    strokePx: Float
) {
    points.forEach { p ->
        val x = p.x / 255f * size.width
        val y = (255f - p.y) / 255f * size.height
        drawCircle(Color.White, radius = radiusPx, center = Offset(x, y), alpha = alpha)
        drawCircle(
            Color.Black.copy(0.2f),
            radius = radiusPx,
            center = Offset(x, y),
            style = Stroke(strokePx),
            alpha = alpha
        )
    }
}

private fun DrawScope.drawHistoFromLut(data: FloatArray, color: Color, size: Size) {
    val maxVal = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val path = Path().apply {
        moveTo(0f, size.height)
        data.forEachIndexed { i, v ->
            val x = (i / 255f) * size.width
            val y = size.height - (v / maxVal * size.height)
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawCurveFromLut(data: FloatArray, color: Color, size: Size, strokeWidth: Float) {
    val path = Path().apply {
        data.forEachIndexed { i, yVal ->
            val x = (i / 255f) * size.width
            val y = (255f - yVal) / 255f * size.height
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(path, color, style = Stroke(strokeWidth))
}

// --- Interpolation & Math Helpers ---
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun calculateCurveLut(points: List<CurvePointState>): FloatArray {
    if (points.isEmpty()) return FloatArray(256) { it.toFloat() }

    val sorted = points.sortedBy { it.x }
    val n = sorted.size
    val x = FloatArray(n) { sorted[it].x }
    val y = FloatArray(n) { sorted[it].y }

    val m = FloatArray(n)
    val delta = FloatArray(n - 1)

    for (i in 0 until n - 1) {
        val dx = x[i + 1] - x[i]
        val dy = y[i + 1] - y[i]
        delta[i] = if (dx == 0f) 0f else dy / dx
    }

    m[0] = delta[0]
    m[n - 1] = delta[n - 2]
    for (i in 1 until n - 1) {
        m[i] = (delta[i - 1] + delta[i]) * 0.5f
    }

    for (i in 0 until n - 1) {
        if (delta[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val alpha = m[i] / delta[i]
            val beta = m[i + 1] / delta[i]
            val s = alpha * alpha + beta * beta
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                m[i] = tau * alpha * delta[i]
                m[i + 1] = tau * beta * delta[i]
            }
        }
    }

    val lut = FloatArray(256)
    var segmentIndex = 0

    for (i in 0..255) {
        val currentX = i.toFloat()
        while (segmentIndex < n - 2 && currentX > x[segmentIndex + 1]) {
            segmentIndex++
        }
        val x0 = x[segmentIndex]
        val x1 = x[segmentIndex + 1]
        val y0 = y[segmentIndex]
        val y1 = y[segmentIndex + 1]
        val m0 = m[segmentIndex]
        val m1 = m[segmentIndex + 1]

        val h = x1 - x0
        if (h <= 0f) {
            lut[i] = y0
        } else {
            val t = (currentX - x0) / h
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2 * t3 - 3 * t2 + 1
            val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2
            val h11 = t3 - t2
            val interpolatedY = h00 * y0 + h10 * h * m0 + h01 * y1 + h11 * h * m1
            lut[i] = interpolatedY.coerceIn(0f, 255f)
        }
    }
    return lut
}

private fun toCurvePoint(pos: Offset, size: IntSize): CurvePointState {
    val x = (pos.x / size.width.toFloat() * 255f).coerceIn(0f, 255f)
    val y = (255f - (pos.y / size.height.toFloat() * 255f)).coerceIn(0f, 255f)
    return CurvePointState(x, y)
}

private fun closestPointIndex(pos: Offset, pts: List<CurvePointState>, size: IntSize, radius: Float): Int? {
    val idx = pts.indexOfFirst { p ->
        val sx = p.x / 255f * size.width.toFloat()
        val sy = (255f - p.y) / 255f * size.height.toFloat()
        sqrt((sx - pos.x) * (sx - pos.x) + (sy - pos.y) * (sy - pos.y)) <= radius
    }
    return if (idx == -1) null else idx
}

private fun moveCurvePoint(pts: List<CurvePointState>, index: Int, target: CurvePointState): List<CurvePointState> {
    val out = pts.toMutableList()
    val isEnd = index == 0 || index == pts.lastIndex
    val x = if (isEnd) pts[index].x else target.x.coerceIn(pts[index - 1].x + 0.1f, pts[index + 1].x - 0.1f)
    out[index] = CurvePointState(x, target.y.coerceIn(0f, 255f))
    return out
}