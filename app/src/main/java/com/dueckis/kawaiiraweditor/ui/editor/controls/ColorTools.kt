package com.dueckis.kawaiiraweditor.ui.editor.controls

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.ColorGradingState
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import com.dueckis.kawaiiraweditor.data.model.CurvesState
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints
import com.dueckis.kawaiiraweditor.domain.CurvesMath
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.GradientAdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.ColorWheelControl
import kotlin.math.roundToInt

private enum class CurveChannel(val label: String) {
    Luma("L"),
    Red("R"),
    Green("G"),
    Blue("B"),
}

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

        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val graphSize = minOf(maxWidth, 340.dp)
            Box(
                modifier =
                    Modifier
                        .size(graphSize)
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
                                return if (best != null && kotlin.math.sqrt(bestDist) <= pointHitRadiusPx) best else null
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
                Canvas(modifier = Modifier.fillMaxWidth().height(graphSize)) {
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

@Composable
internal fun ColorTabControls(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val isPhoneLayout = LocalConfiguration.current.screenWidthDp < 600

    if (isPhoneLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            PanelSectionCard(
                title = "Curves",
                subtitle = "Tap to add points • Drag to adjust",
                modifier = Modifier.weight(1f)
            ) {
                CurvesEditor(
                    adjustments = adjustments,
                    histogramData = histogramData,
                    onAdjustmentsChange = onAdjustmentsChange,
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }

            PanelSectionCard(title = "Midtones", modifier = Modifier.weight(1f)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val wheelSize = minOf(maxWidth, 170.dp)
                    ColorWheelControl(
                        label = "Midtones",
                        wheelSize = wheelSize,
                        modifier = Modifier.fillMaxWidth(),
                        value = adjustments.colorGrading.midtones,
                        defaultValue = HueSatLumState(),
                        onValueChange = { updated ->
                            onAdjustmentsChange(adjustments.copy(colorGrading = adjustments.colorGrading.copy(midtones = updated)))
                        },
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction
                    )
                }
            }
        }

        PanelSectionCard(title = "Color Grading", subtitle = "Shadows / Highlights") {
            ColorGradingEditor(
                colorGrading = adjustments.colorGrading,
                onColorGradingChange = { updated -> onAdjustmentsChange(adjustments.copy(colorGrading = updated)) },
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction,
                showMidtones = false
            )
        }
    } else {
        PanelSectionCard(title = "Curves", subtitle = "Tap to add points • Drag to adjust") {
            CurvesEditor(
                adjustments = adjustments,
                histogramData = histogramData,
                onAdjustmentsChange = onAdjustmentsChange,
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction
            )
        }

        PanelSectionCard(title = "Color Grading", subtitle = "Shadows / Midtones / Highlights") {
            ColorGradingEditor(
                colorGrading = adjustments.colorGrading,
                onColorGradingChange = { updated -> onAdjustmentsChange(adjustments.copy(colorGrading = updated)) },
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction
            )
        }
    }

    PanelSectionCard(title = "Color Mixer", subtitle = "Hue / Saturation / Luminance") {
        HslEditor(
            hsl = adjustments.hsl,
            onHslChange = { updated -> onAdjustmentsChange(adjustments.copy(hsl = updated)) },
            onBeginEditInteraction = onBeginEditInteraction,
            onEndEditInteraction = onEndEditInteraction
        )
    }
}

@Composable
internal fun ColorGradingEditor(
    colorGrading: ColorGradingState,
    onColorGradingChange: (ColorGradingState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    showMidtones: Boolean = true
) {
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val midWheelSize = if (isWide) 240.dp else minOf(maxWidth, 220.dp)
        val sideWheelSize = minOf((maxWidth / 2) - 10.dp, if (isWide) 220.dp else 170.dp)

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showMidtones) {
                ColorWheelControl(
                    label = "Midtones",
                    wheelSize = midWheelSize,
                    modifier = Modifier.fillMaxWidth(),
                    value = colorGrading.midtones,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(midtones = it)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorWheelControl(
                    label = "Shadows",
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.shadows,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(shadows = it)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
                ColorWheelControl(
                    label = "Highlights",
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.highlights,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(highlights = it)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }

            AdjustmentSlider(
                label = "Blending",
                value = colorGrading.blending,
                range = 0f..100f,
                step = 1f,
                defaultValue = 50f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(blending = it)) },
                onInteractionStart = onBeginEditInteraction,
                onInteractionEnd = onEndEditInteraction
            )
            AdjustmentSlider(
                label = "Balance",
                value = colorGrading.balance,
                range = -100f..100f,
                step = 1f,
                defaultValue = 0f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(balance = it)) },
                onInteractionStart = onBeginEditInteraction,
                onInteractionEnd = onEndEditInteraction
            )
        }
    }
}

private enum class HslChannel(val label: String, val swatch: Color) {
    Reds("Reds", Color(0xFFF87171)),
    Oranges("Oranges", Color(0xFFFB923C)),
    Yellows("Yellows", Color(0xFFFACC15)),
    Greens("Greens", Color(0xFF4ADE80)),
    Aquas("Aquas", Color(0xFF2DD4BF)),
    Blues("Blues", Color(0xFF60A5FA)),
    Purples("Purples", Color(0xFFA78BFA)),
    Magentas("Magentas", Color(0xFFF472B6)),
}

private fun HslState.valueFor(channel: HslChannel): HueSatLumState {
    return when (channel) {
        HslChannel.Reds -> reds
        HslChannel.Oranges -> oranges
        HslChannel.Yellows -> yellows
        HslChannel.Greens -> greens
        HslChannel.Aquas -> aquas
        HslChannel.Blues -> blues
        HslChannel.Purples -> purples
        HslChannel.Magentas -> magentas
    }
}

private fun HslState.withValue(channel: HslChannel, value: HueSatLumState): HslState {
    return when (channel) {
        HslChannel.Reds -> copy(reds = value)
        HslChannel.Oranges -> copy(oranges = value)
        HslChannel.Yellows -> copy(yellows = value)
        HslChannel.Greens -> copy(greens = value)
        HslChannel.Aquas -> copy(aquas = value)
        HslChannel.Blues -> copy(blues = value)
        HslChannel.Purples -> copy(purples = value)
        HslChannel.Magentas -> copy(magentas = value)
    }
}

private fun hslChannelHueDegrees(channel: HslChannel): Float {
    val hsv = FloatArray(3)
    val r = (channel.swatch.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (channel.swatch.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (channel.swatch.blue * 255f).roundToInt().coerceIn(0, 255)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    return hsv[0]
}

private fun hslHueTrackBrush(channel: HslChannel): Brush {
    val channels = HslChannel.entries
    val idx = channels.indexOf(channel)
    val prev = channels[(idx - 1 + channels.size) % channels.size]
    val next = channels[(idx + 1) % channels.size]
    val v = 0.95f
    return Brush.horizontalGradient(
        listOf(
            Color.hsv(hslChannelHueDegrees(prev), 1f, v),
            Color.hsv(hslChannelHueDegrees(channel), 1f, v),
            Color.hsv(hslChannelHueDegrees(next), 1f, v)
        )
    )
}

private fun hslSaturationTrackBrush(channel: HslChannel): Brush {
    val hue = hslChannelHueDegrees(channel)
    val v = 0.85f
    return Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, v), Color.hsv(hue, 1f, v)))
}

private fun hslLuminanceTrackBrush(channel: HslChannel): Brush {
    val hue = hslChannelHueDegrees(channel)
    return Brush.horizontalGradient(
        listOf(Color.hsv(hue, 0.9f, 0.12f), Color.hsv(hue, 0.9f, 0.85f), Color.hsv(hue, 0.05f, 1f))
    )
}

@Composable
internal fun HslEditor(
    hsl: HslState,
    onHslChange: (HslState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    var activeChannel by remember { mutableStateOf(HslChannel.Reds) }
    val current = hsl.valueFor(activeChannel)
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }
    val hueBrush = remember(activeChannel) { hslHueTrackBrush(activeChannel) }
    val saturationBrush = remember(activeChannel) { hslSaturationTrackBrush(activeChannel) }
    val luminanceBrush = remember(activeChannel) { hslLuminanceTrackBrush(activeChannel) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = activeChannel.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(HslChannel.entries.size) { index ->
                val channel = HslChannel.entries[index]
                val isActive = channel == activeChannel
                val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(channel.swatch)
                            .border(width = 2.dp, color = borderColor, shape = CircleShape)
                            .clickable { activeChannel = channel }
                )
            }
        }

        GradientAdjustmentSlider(
            label = "Hue",
            value = current.hue,
            range = -100f..100f,
            step = 1f,
            defaultValue = 0f,
            formatter = formatterInt,
            trackBrush = hueBrush,
            onValueChange = { onHslChange(hsl.withValue(activeChannel, current.copy(hue = it))) },
            onInteractionStart = onBeginEditInteraction,
            onInteractionEnd = onEndEditInteraction
        )
        GradientAdjustmentSlider(
            label = "Saturation",
            value = current.saturation,
            range = -100f..100f,
            step = 1f,
            defaultValue = 0f,
            formatter = formatterInt,
            trackBrush = saturationBrush,
            onValueChange = { onHslChange(hsl.withValue(activeChannel, current.copy(saturation = it))) },
            onInteractionStart = onBeginEditInteraction,
            onInteractionEnd = onEndEditInteraction
        )
        GradientAdjustmentSlider(
            label = "Luminance",
            value = current.luminance,
            range = -100f..100f,
            step = 1f,
            defaultValue = 0f,
            formatter = formatterInt,
            trackBrush = luminanceBrush,
            onValueChange = { onHslChange(hsl.withValue(activeChannel, current.copy(luminance = it))) },
            onInteractionStart = onBeginEditInteraction,
            onInteractionEnd = onEndEditInteraction
        )
    }
}
