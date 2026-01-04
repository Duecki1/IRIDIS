package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.ranges.ClosedFloatingPointRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity


@Composable
internal fun GradientAdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    formatter: (Float) -> String,
    trackBrush: Brush,
    onValueChange: (Float) -> Unit,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionEnd: (() -> Unit)? = null
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        disabledActiveTrackColor = Color.Transparent,
        disabledInactiveTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
        thumbColor = MaterialTheme.colorScheme.primary,
        disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                color = MaterialTheme.colorScheme.onSurface
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
                    ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackBrush)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50)
                    )
            )

            Slider(
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
internal fun RoundGradientAdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    formatter: (Float) -> String,
    trackBrush: Brush,
    onValueChange: (Float) -> Unit,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionEnd: (() -> Unit)? = null
) {
    // Uses local private helper to avoid conflicts
    val snappedDefault = snapValueToStep(defaultValue, step, range)
    val density = LocalDensity.current

    // Dimensions
    val trackHeight = 28.dp
    val thumbStrokeWidth = 3.dp

    val trackHeightPx = with(density) { trackHeight.toPx() }
    val thumbStrokePx = with(density) { thumbStrokeWidth.toPx() }

    // Radius is half height so it fits exactly
    val thumbRadiusPx = trackHeightPx / 2

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Canvas Slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onInteractionStart?.invoke()
                            onValueChange(snappedDefault)
                            onInteractionEnd?.invoke()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onInteractionStart?.invoke() },
                        onDragEnd = { onInteractionEnd?.invoke() },
                        onDragCancel = { onInteractionEnd?.invoke() }
                    ) { change, _ ->
                        change.consume()
                        val width = size.width
                        val usableWidth = width - (thumbRadiusPx * 2)
                        val xPos = (change.position.x - thumbRadiusPx).coerceIn(0f, usableWidth)

                        val rangeSize = range.endInclusive - range.start
                        val fraction = xPos / usableWidth
                        val rawValue = range.start + (fraction * rangeSize)

                        val snapped = snapValueToStep(rawValue, step, range)
                        onValueChange(snapped)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val centerY = size.height / 2

                // 1. Gradient Track
                drawRoundRect(
                    brush = trackBrush,
                    size = Size(w, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2)
                )

                // 2. Track Border
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    size = Size(w, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Calculate Position
                val rangeSize = range.endInclusive - range.start
                val fraction = ((value - range.start) / rangeSize).coerceIn(0f, 1f)
                val availableWidth = w - (thumbRadiusPx * 2)
                val thumbX = thumbRadiusPx + (availableWidth * fraction)

                // 3. Thumb Ring (Transparent Center)
                val ringRadius = thumbRadiusPx - (thumbStrokePx / 2)

                // Shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = ringRadius,
                    center = Offset(thumbX, centerY),
                    style = Stroke(width = thumbStrokePx + 2f)
                )

                // White Ring
                drawCircle(
                    color = Color.White,
                    radius = ringRadius,
                    center = Offset(thumbX, centerY),
                    style = Stroke(width = thumbStrokePx)
                )
            }
        }
    }
}

// Local helper to ensure self-containment
private fun snapValueToStep(value: Float, step: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (step == 0f) return value
    val stepped = Math.round(value / step) * step
    return stepped.coerceIn(range.start, range.endInclusive)
}