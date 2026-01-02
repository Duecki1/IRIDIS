package com.dueckis.kawaiiraweditor.ui.editor.controls

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import com.dueckis.kawaiiraweditor.data.model.CurvesState
import com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints
import com.dueckis.kawaiiraweditor.domain.CurvesMath
import com.dueckis.kawaiiraweditor.domain.HistogramData
import kotlin.math.sqrt

private fun CurvesState.pointsFor(channel: CurveChannel): List<CurvePointState> {
    return when (channel) {
        CurveChannel.Luma -> luma
        CurveChannel.Red -> red
        CurveChannel.Green -> green
        CurveChannel.Blue -> blue
    }
}

private fun CurvesState.withPoints(channel: CurveChannel, points: List<CurvePointState>): CurvesState {
    return when (channel) {
        CurveChannel.Luma -> copy(luma = points)
        CurveChannel.Red -> copy(red = points)
        CurveChannel.Green -> copy(green = points)
        CurveChannel.Blue -> copy(blue = points)
    }
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

    val latestAdjustments by rememberUpdatedState(adjustments)
    val latestOnAdjustmentsChange by rememberUpdatedState(onAdjustmentsChange)
    val latestCurves by rememberUpdatedState(adjustments.curves)
    val latestPoints by rememberUpdatedState(points)

    val channelColor =
        when (activeChannel) {
            CurveChannel.Luma -> MaterialTheme.colorScheme.primary
            CurveChannel.Red -> Color(0xFFFF6B6B)
            CurveChannel.Green -> Color(0xFF6BCB77)
            CurveChannel.Blue -> Color(0xFF4D96FF)
        }

    val histogram =
        when (activeChannel) {
            CurveChannel.Luma -> histogramData?.luma
            CurveChannel.Red -> histogramData?.red
            CurveChannel.Green -> histogramData?.green
            CurveChannel.Blue -> histogramData?.blue
        }

    val pointHitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val resetChannel: () -> Unit = {
        onBeginEditInteraction()
        val updatedCurves = latestCurves.withPoints(activeChannel, defaultCurvePoints())
        latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
        onEndEditInteraction()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val channels = remember { listOf(CurveChannel.Luma, CurveChannel.Red, CurveChannel.Green, CurveChannel.Blue) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { channel ->
                            val selected = channel == activeChannel
                            val accent =
                                when (channel) {
                                    CurveChannel.Luma -> MaterialTheme.colorScheme.primary
                                    CurveChannel.Red -> Color(0xFFFF6B6B)
                                    CurveChannel.Green -> Color(0xFF6BCB77)
                                    CurveChannel.Blue -> Color(0xFF4D96FF)
                                }
                            FilledTonalButton(
                                onClick = { activeChannel = channel },
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            if (selected) accent.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else accent
                                    ),
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .then(if (selected) Modifier.border(2.dp, accent, CircleShape) else Modifier),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(channel.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = resetChannel,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset")
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 340.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(activeChannel, pointHitRadiusPx) {
                            fun toCurvePoint(pos: Offset): CurvePointState {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val x = (pos.x / w * 255f).coerceIn(0f, 255f)
                                val y = (255f - (pos.y / h * 255f)).coerceIn(0f, 255f)
                                return CurvePointState(x = x, y = y)
                            }

                            fun toScreenPoint(p: CurvePointState): Offset {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val x = p.x / 255f * w
                                val y = (255f - p.y) / 255f * h
                                return Offset(x, y)
                            }

                            fun closestPointIndex(pos: Offset, pts: List<CurvePointState>): Int? {
                                var best: Int? = null
                                var bestDist = Float.MAX_VALUE
                                pts.forEachIndexed { index, p ->
                                    val sp = toScreenPoint(p)
                                    val dx = sp.x - pos.x
                                    val dy = sp.y - pos.y
                                    val d = dx * dx + dy * dy
                                    if (d < bestDist) {
                                        bestDist = d
                                        best = index
                                    }
                                }
                                return if (best != null && sqrt(bestDist) <= pointHitRadiusPx) best else null
                            }

                            fun movePoint(
                                pts: List<CurvePointState>,
                                index: Int,
                                target: CurvePointState
                            ): List<CurvePointState> {
                                val clampedY = target.y.coerceIn(0f, 255f)
                                val isEndPoint = index == 0 || index == pts.lastIndex
                                val clampedX =
                                    if (isEndPoint) {
                                        pts[index].x
                                    } else {
                                        val prevX = pts[index - 1].x
                                        val nextX = pts[index + 1].x
                                        target.x.coerceIn(prevX + 0.01f, nextX - 0.01f)
                                    }
                                val out = pts.toMutableList()
                                out[index] = CurvePointState(x = clampedX, y = clampedY)
                                return out
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var working = latestPoints
                                val downPos = down.position
                                var draggingIndex = closestPointIndex(downPos, working)

                                if (draggingIndex == null) {
                                    val slop = viewConfiguration.touchSlop
                                    var movedTooMuch = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if (!change.pressed) {
                                            if (!movedTooMuch) {
                                                if (working.size >= 16) return@awaitEachGesture
                                                val newPoint = toCurvePoint(change.position)
                                                val newPoints = (working + newPoint).sortedBy { it.x }
                                                working = newPoints
                                                onBeginEditInteraction()
                                                val updatedCurves = latestCurves.withPoints(activeChannel, working)
                                                latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
                                                onEndEditInteraction()
                                            }
                                            return@awaitEachGesture
                                        }
                                        val dx = change.position.x - downPos.x
                                        val dy = change.position.y - downPos.y
                                        if ((dx * dx + dy * dy) > slop * slop) {
                                            movedTooMuch = true
                                        }
                                    }
                                }

                                down.consume()
                                onBeginEditInteraction()
                                drag(down.id) { change ->
                                    change.consume()
                                    val target = toCurvePoint(change.position)
                                    working = movePoint(working, draggingIndex!!, target)
                                    val updatedCurves = latestCurves.withPoints(activeChannel, working)
                                    latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
                                }
                                onEndEditInteraction()
                            }
                        }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height

                    for (i in 1..3) {
                        val t = i / 4f
                        drawLine(color = gridColor, start = Offset(w * t, 0f), end = Offset(w * t, h), strokeWidth = 1f)
                        drawLine(color = gridColor, start = Offset(0f, h * t), end = Offset(w, h * t), strokeWidth = 1f)
                    }

                    histogram?.let { data ->
                        val maxVal = data.maxOrNull() ?: 0f
                        if (maxVal > 0f) {
                            val path =
                                Path().apply {
                                    moveTo(0f, h)
                                    for (i in data.indices) {
                                        val x = (i / 255f) * w
                                        val y = (data[i] / maxVal) * h
                                        lineTo(x, h - y)
                                    }
                                    lineTo(w, h)
                                    close()
                                }
                            drawPath(path, color = channelColor.copy(alpha = 0.18f))
                        }
                    }

                    if (points.size >= 2) {
                        val curvePath = CurvesMath.buildCurvePath(points, Size(w, h))
                        drawPath(curvePath, color = channelColor, style = Stroke(width = 7f))
                    }

                    points.forEach { p ->
                        val x = p.x / 255f * w
                        val y = (255f - p.y) / 255f * h
                        drawCircle(color = Color.White, radius = 11f, center = Offset(x, y))
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = 11f,
                            center = Offset(x, y),
                            style = Stroke(width = 6f)
                        )
                    }
                }
            }
        }
    }
}
