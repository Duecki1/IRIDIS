package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun ColorWheelControl(
    wheelSize: Dp,
    modifier: Modifier = Modifier,
    value: HueSatLumState,
    defaultValue: HueSatLumState,
    isHeaderCentered: Boolean? = null,
    onValueChange: (HueSatLumState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val handleHitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isHeaderCentered == true) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = "H ${value.hue.roundToInt()}  S ${value.saturation.roundToInt()}  L ${value.luminance.roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    onBeginEditInteraction()
                    onValueChange(defaultValue)
                    onEndEditInteraction()
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Reset", fontSize = 12.sp)
            }
        }

        Box(
            modifier =
                Modifier
                    .size(wheelSize)
                    .align(Alignment.CenterHorizontally)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
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
        ) {
            Canvas(modifier = Modifier.size(wheelSize)) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)
                val radius = minOf(w, h) / 2f

                val sweep =
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

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
                drawCircle(
                    color = Color.Black.copy(alpha = 0.15f),
                    radius = radius,
                    center = center
                )

                val angleRad = (value.hue / 180f) * Math.PI.toFloat()
                val satNorm = (value.saturation / 100f).coerceIn(0f, 1f)
                val px = center.x + cos(angleRad) * radius * satNorm
                val py = center.y + sin(angleRad) * radius * satNorm
                val pointerColor = if (value.saturation > 5f) Color.hsv(value.hue, satNorm, 1f) else Color.Transparent

                drawCircle(color = pointerColor, radius = 14f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 14f, center = Offset(px, py))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        AdjustmentSlider(
            label = "Luminance",
            value = value.luminance,
            range = -100f..100f,
            step = 1f,
            defaultValue = 0f,
            formatter = formatterInt,
            onValueChange = { onValueChange(value.copy(luminance = it)) }
        )
    }
}
