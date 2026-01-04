package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Luma is primary (darker), others are colored
    val targetChannelColor = when (activeChannel) {
        CurveChannel.Luma -> MaterialTheme.colorScheme.primary
        CurveChannel.Red -> Color(0xFFFF6B6B)
        CurveChannel.Green -> Color(0xFF6BCB77)
        CurveChannel.Blue -> Color(0xFF4D96FF)
    }

    // Animation states
    val transitionAlpha = remember { Animatable(1f) }
    val prevPoints = remember { mutableStateOf(points) }
    val prevChannel = remember { mutableStateOf(activeChannel) }
    val prevColor = remember { mutableStateOf(targetChannelColor) }

    val animatedChannelColor by animateColorAsState(
        targetValue = targetChannelColor,
        animationSpec = tween(400),
        label = "ChannelColor"
    )

    // Transition Logic for switching channels
    LaunchedEffect(activeChannel) {
        if (prevChannel.value != activeChannel) {
            transitionAlpha.snapTo(0f)
            transitionAlpha.animateTo(1f, tween(400))
            prevPoints.value = points
            prevColor.value = when (prevChannel.value) {
                CurveChannel.Luma -> targetChannelColor // Fallback
                CurveChannel.Red -> Color(0xFFFF6B6B)
                CurveChannel.Green -> Color(0xFF6BCB77)
                CurveChannel.Blue -> Color(0xFF4D96FF)
            }
            prevChannel.value = activeChannel
        }
    }

    // Reset Animation Listener
    LaunchedEffect(points) {
        if (points == defaultCurvePoints() && prevPoints.value != points) {
            transitionAlpha.snapTo(0f)
            transitionAlpha.animateTo(1f, tween(400))
        }
        prevPoints.value = points
    }

    val latestAdjustments by rememberUpdatedState(adjustments)
    val latestOnAdjustmentsChange by rememberUpdatedState(onAdjustmentsChange)
    val latestCurves by rememberUpdatedState(adjustments.curves)
    val latestPoints by rememberUpdatedState(points)

    Row(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- Left Side: 50% ---
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val channels = remember { listOf(CurveChannel.Luma, CurveChannel.Red, CurveChannel.Green, CurveChannel.Blue) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { channel ->
                            val selected = channel == activeChannel
                            val accent = when (channel) {
                                CurveChannel.Luma -> MaterialTheme.colorScheme.primary
                                CurveChannel.Red -> Color(0xFFFF6B6B)
                                CurveChannel.Green -> Color(0xFF6BCB77)
                                CurveChannel.Blue -> Color(0xFF4D96FF)
                            }
                            FilledTonalButton(
                                onClick = { activeChannel = channel },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onSurface else accent
                                ),
                                modifier = Modifier.size(44.dp).then(if (selected) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(channel.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    onBeginEditInteraction()
                    val updated = latestCurves.withPoints(activeChannel, defaultCurvePoints())
                    latestOnAdjustmentsChange(latestAdjustments.copy(curves = updated))
                    onEndEditInteraction()
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Reset Channel", fontSize = 13.sp, color = animatedChannelColor)
            }
        }

        // --- Right Side: 50% ---
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(activeChannel) {
                    val hitRadius = 28.dp.toPx()
                    awaitEachGesture {
                        // 1. On touch down, call onBeginEditInteraction to lock parent scrolling
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
                                    // 2. On release, unlock scrolling
                                    onEndEditInteraction()
                                    break
                                }
                                // Consume move events to prevent parent scroll
                                change.consume()
                                if ((change.position - down.position).getDistance() > slop) moved = true
                            }
                        } else {
                            // Instant dragging
                            drag(down.id) { change ->
                                // Consume drag events to prevent parent scroll
                                change.consume()
                                val target = toCurvePoint(change.position, size)
                                working = moveCurvePoint(working, draggingIndex, target)
                                latestOnAdjustmentsChange(latestAdjustments.copy(curves = latestCurves.withPoints(activeChannel, working)))
                            }
                            // 2. On release, unlock scrolling
                            onEndEditInteraction()
                        }
                    }
                }
        ) {
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            Canvas(modifier = Modifier.matchParentSize()) {
                val alpha = transitionAlpha.value

                // Grid
                for (i in 1..3) {
                    val t = i / 4f
                    drawLine(gridColor, Offset(size.width * t, 0f), Offset(size.width * t, size.height))
                    drawLine(gridColor, Offset(0f, size.height * t), Offset(size.width, size.height * t))
                }

                // Histogram
                histogramData?.let { data ->
                    val activeHisto = when(activeChannel) {
                        CurveChannel.Luma -> data.luma; CurveChannel.Red -> data.red;
                        CurveChannel.Green -> data.green; CurveChannel.Blue -> data.blue
                    }
                    drawHisto(activeHisto, animatedChannelColor.copy(alpha = 0.12f * alpha), size)
                }

                // Curves Cross-fade
                if (alpha < 1f) {
                    val oldPath = CurvesMath.buildCurvePath(prevPoints.value, size)
                    drawPath(oldPath, prevColor.value.copy(alpha = 1f - alpha), style = Stroke(6f))
                    val newPath = CurvesMath.buildCurvePath(points, size)
                    drawPath(newPath, animatedChannelColor.copy(alpha = alpha), style = Stroke(7f))
                } else {
                    val curvePath = CurvesMath.buildCurvePath(points, size)
                    drawPath(curvePath, color = animatedChannelColor, style = Stroke(7f))
                }

                // Points
                points.forEach { p ->
                    val x = p.x / 255f * size.width
                    val y = (255f - p.y) / 255f * size.height
                    drawCircle(Color.White, radius = 10f, center = Offset(x, y), alpha = alpha)
                    drawCircle(Color.Black.copy(0.2f), radius = 10f, center = Offset(x, y), style = Stroke(4f), alpha = alpha)
                }
            }
        }
    }
}

// Coordinate Helpers
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

private fun DrawScope.drawHisto(data: FloatArray?, color: Color, size: Size) {
    data ?: return
    val maxVal = data.maxOrNull() ?: 0f
    if (maxVal <= 0f) return
    val path = Path().apply {
        moveTo(0f, size.height)
        data.forEachIndexed { i, v ->
            lineTo((i / 255f) * size.width, size.height - (v / maxVal) * size.height)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}