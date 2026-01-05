package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.ranges.ClosedFloatingPointRange

@Composable
internal fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionEnd: (() -> Unit)? = null
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
        thumbColor = MaterialTheme.colorScheme.primary
    )
    val snappedDefault = snapToStep(defaultValue, step, range)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(label, defaultValue) {
                    detectTapGestures(
                        onDoubleTap = {
                            onInteractionStart?.invoke()
                            onValueChange(defaultValue)
                            onInteractionEnd?.invoke()
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .doubleTapSliderThumbToReset(
                        value = value,
                        valueRange = range,
                        onReset = {
                            onInteractionStart?.invoke()
                            onValueChange(snappedDefault)
                            onInteractionEnd?.invoke()
                        }
                    )
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = { newValue ->
                    val snapped = snapToStep(newValue, step, range)
                    onInteractionStart?.invoke()
                    onValueChange(snapped)
                },
                onValueChangeFinished = { onInteractionEnd?.invoke() },
                valueRange = range,
                colors = colors
            )
        }
    }
}

@Composable
internal fun CompactAdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionEnd: (() -> Unit)? = null
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        thumbColor = MaterialTheme.colorScheme.primary
    )

    var lastResetTime by remember { mutableLongStateOf(0L) }
    val snappedDefault = snapValueToStepLocal(defaultValue, step, range)

    val performReset = {
        lastResetTime = System.currentTimeMillis()
        onInteractionStart?.invoke()
        onValueChange(snappedDefault)
        onInteractionEnd?.invoke()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().interceptDoubleTapToReset { performReset() }
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))

        Box(modifier = Modifier.weight(1f)) {
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = { newValue ->
                    if (System.currentTimeMillis() - lastResetTime > 250) {
                        val snapped = snapValueToStepLocal(newValue, step, range)
                        onInteractionStart?.invoke()
                        onValueChange(snapped)
                    }
                },
                onValueChangeFinished = { onInteractionEnd?.invoke() },
                valueRange = range,
                colors = colors
            )
        }

        Text(text = value.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

/**
 * HIGH-PRIORITY GESTURE INTERCEPTOR
 * This looks at the touch events in the "Initial" pass, meaning it sees them
 * before the Slider logic can consume them.
 */
fun Modifier.interceptDoubleTapToReset(onReset: () -> Unit): Modifier = this.pointerInput(Unit) {
    var lastClickTime = 0L
    awaitEachGesture {
        // Look at the "Down" event before the Slider consumes it
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastClickTime < 300) {
            // Double tap detected!
            onReset()
            // Consuming the event stops the slider from seeking to the second tap position
            down.consume()

            // Also consume all following move/up events for this specific gesture
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.all { !it.pressed }) break
            }
            lastClickTime = 0L
        } else {
            lastClickTime = currentTime
        }
    }
}

@Composable
internal fun VerticalValueSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float> = -100f..100f,
    onValueChange: (Float) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { 8.dp.toPx() }
    val trackWidth = with(density) { 4.dp.toPx() }
    val rangeSize = range.endInclusive - range.start

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    onInteractionStart()
                    onValueChange(0f)
                    onInteractionEnd()
                })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onInteractionStart() },
                    onDragEnd = { onInteractionEnd() },
                    onDragCancel = { onInteractionEnd() }
                ) { change, _ ->
                    change.consume()
                    val yRatio = (change.position.y / size.height).coerceIn(0f, 1f)
                    val newValue = range.endInclusive - (yRatio * rangeSize)
                    onValueChange(newValue.coerceIn(range.start, range.endInclusive))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2
            drawRoundRect(color = trackColor, topLeft = Offset(centerX - trackWidth / 2, 0f), size = Size(trackWidth, h), cornerRadius = CornerRadius(trackWidth / 2))
            val zeroRatio = (range.endInclusive - 0f) / rangeSize
            val zeroY = zeroRatio * h
            drawLine(color = trackColor.copy(alpha = 0.8f), start = Offset(centerX - trackWidth * 1.5f, zeroY), end = Offset(centerX + trackWidth * 1.5f, zeroY), strokeWidth = 2.dp.toPx())
            val currentRatio = (range.endInclusive - value) / rangeSize
            val currentY = currentRatio * h
            drawLine(color = activeColor.copy(alpha = 0.5f), start = Offset(centerX, zeroY), end = Offset(centerX, currentY), strokeWidth = trackWidth)
            drawCircle(color = Color.White, radius = thumbRadius, center = Offset(centerX, currentY))
            drawCircle(color = activeColor, radius = thumbRadius, center = Offset(centerX, currentY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun snapValueToStepLocal(value: Float, step: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (step == 0f) return value
    val stepped = Math.round(value / step) * step
    return stepped.coerceIn(range.start, range.endInclusive)
}