package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorWheelControl(
    title: String, // Kept for API compatibility, but unused inside as per request
    wheelSize: Dp,
    modifier: Modifier = Modifier,
    value: HueSatLumState,
    defaultValue: HueSatLumState,
    enabled: Boolean = true,
    onValueChange: (HueSatLumState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestDefaultValue by rememberUpdatedState(defaultValue)

    val density = LocalDensity.current
    val handleHitRadiusPx = with(density) { 28.dp.toPx() }
    val pointerRadiusPx = with(density) { 6.dp.toPx() }
    val pointerStrokePx = with(density) { 2.dp.toPx() }

    // Symmetric Layout: Weight 1 - Weight 2 - Weight 1
    // This ensures the middle element is perfectly centered.
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // 1. LEFT SIDE: Info & Reset
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart // Align content start, but box takes full space
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                // HSL Values
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelSmall) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(text = "H ${value.hue.roundToInt()}°", color = color, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "S ${value.saturation.roundToInt()}", color = color, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "L ${value.luminance.roundToInt()}", color = color, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reset Icon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onBeginEditInteraction()
                            onValueChange(defaultValue)
                            onEndEditInteraction()
                        }
                        // Negative padding trick to make the visual icon flush left with text
                        // while keeping a good touch target
                        .padding(4.dp)
                        .offset(x = (-4).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. CENTER: The Wheel
        // State for Double Click
        var lastClickTime by remember { mutableLongStateOf(0L) }

        Box(
            modifier = Modifier
                .weight(2f) // Takes double the space of sides
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.95f) // Use most of the height
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(100))
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput

                        fun calcHueSat(pos: Offset): HueSatLumState {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val cx = w / 2f
                            val cy = h / 2f
                            val dx = pos.x - cx
                            val dy = pos.y - cy
                            val radius = sqrt(dx * dx + dy * dy)
                            val maxRadius = minOf(cx, cy).coerceAtLeast(1f)
                            val sat = ((radius / maxRadius).coerceIn(0f, 1f) * 100f)
                            var hue = (atan2(dy, dx) * 180.0 / Math.PI).toFloat()
                            if (hue < 0f) hue += 360f
                            return latestValue.copy(hue = hue, saturation = sat)
                        }

                        fun handleOffsetFor(v: HueSatLumState): Offset {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val cx = w / 2f
                            val cy = h / 2f
                            val radius = minOf(cx, cy)
                            val angleRad = (v.hue / 180f) * Math.PI.toFloat()
                            val satNorm = (v.saturation / 100f).coerceIn(0f, 1f)
                            val x = cx + cos(angleRad) * radius * satNorm
                            val y = cy + sin(angleRad) * radius * satNorm
                            return Offset(x, y)
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val now = System.currentTimeMillis()

                            if (now - lastClickTime < 300) {
                                down.consume()
                                onBeginEditInteraction()
                                // Reset H/S, keep L
                                latestOnValueChange(latestDefaultValue.copy(luminance = latestValue.luminance))
                                onEndEditInteraction()
                                lastClickTime = 0L
                            } else {
                                lastClickTime = now
                                val slop = viewConfiguration.touchSlop
                                val handlePos = handleOffsetFor(latestValue)
                                val dist = (handlePos - downPos).getDistance()

                                if (dist <= handleHitRadiusPx) {
                                    down.consume()
                                    onBeginEditInteraction()
                                    drag(down.id) { change ->
                                        change.consume()
                                        latestOnValueChange(calcHueSat(change.position))
                                    }
                                    onEndEditInteraction()
                                } else {
                                    var movedTooMuch = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if (!change.pressed) {
                                            if (!movedTooMuch) {
                                                onBeginEditInteraction()
                                                latestOnValueChange(calcHueSat(change.position))
                                                onEndEditInteraction()
                                            }
                                            break
                                        }
                                        val dx = change.position.x - downPos.x
                                        val dy = change.position.y - downPos.y
                                        if ((dx * dx + dy * dy) > slop * slop) {
                                            movedTooMuch = true
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    val radius = minOf(w, h) / 2f

                    val sweep = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    drawCircle(brush = Brush.sweepGradient(sweep), radius = radius, center = center)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                    drawCircle(color = Color.Black.copy(alpha = 0.1f), radius = radius, center = center)

                    val angleRad = (value.hue / 180f) * Math.PI.toFloat()
                    val satNorm = (value.saturation / 100f).coerceIn(0f, 1f)
                    val px = center.x + cos(angleRad) * radius * satNorm
                    val py = center.y + sin(angleRad) * radius * satNorm
                    val pointerFill = if (value.saturation > 2f) Color.hsv(value.hue, satNorm, 1f) else Color.Transparent

                    drawCircle(color = pointerFill, radius = pointerRadiusPx, center = Offset(px, py))
                    drawCircle(color = Color.White, radius = pointerRadiusPx, center = Offset(px, py), style = Stroke(width = pointerStrokePx))
                }
            }
        }

        // 3. RIGHT SIDE: Vertical Slider
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd // Slider aligned towards the end, or Center
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(32.dp)
                    .fillMaxHeight(0.7f), // Slider is 70% of container height
                contentAlignment = Alignment.Center
            ) {
                VerticalValueSlider(
                    value = value.luminance,
                    onValueChange = { if (enabled) onValueChange(value.copy(luminance = it)) },
                    onInteractionStart = onBeginEditInteraction,
                    onInteractionEnd = onEndEditInteraction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}