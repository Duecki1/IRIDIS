package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.ranges.ClosedFloatingPointRange

// --- Original Slider (Unchanged) ---
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
            modifier = Modifier.fillMaxWidth() // Logic for standard slider double tap usually handled in parent or here
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

// --- NEW: Compact Horizontal Slider ---
// Removes the large header row to save space.
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label on the left
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp)
        )

        // Slider
        Slider(
            modifier = Modifier.weight(1f),
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

        // Value on the right
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

// --- NEW: Vertical Slider (Custom Canvas) ---
// Designed for Luminance control next to color wheel
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

    // Calculate normalized position (0..1)
    // For range -100..100: 100 is top (0.0y), -100 is bottom (1.0y)
    // Compose Canvas Y increases downwards.
    // Let's map Max Value -> Top (0f), Min Value -> Bottom (Height)
    val rangeSize = range.endInclusive - range.start

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onInteractionStart()
                        onValueChange(0f) // Reset to 0
                        onInteractionEnd()
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onInteractionStart() },
                    onDragEnd = { onInteractionEnd() },
                    onDragCancel = { onInteractionEnd() }
                ) { change, _ ->
                    change.consume()
                    // Calculate new value based on Y position
                    // Y = 0 is Max, Y = Height is Min
                    val yRatio = (change.position.y / size.height).coerceIn(0f, 1f)
                    // Invert ratio because Y grows down (1.0 is bottom/min)
                    val newValue = range.endInclusive - (yRatio * rangeSize)
                    onValueChange(newValue.coerceIn(range.start, range.endInclusive))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2

            // Draw Track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackWidth / 2, 0f),
                size = Size(trackWidth, h),
                cornerRadius = CornerRadius(trackWidth / 2)
            )

            // Draw Center Marker (Zero point)
            val zeroRatio = (range.endInclusive - 0f) / rangeSize
            val zeroY = zeroRatio * h
            drawLine(
                color = trackColor.copy(alpha = 0.8f),
                start = Offset(centerX - trackWidth * 1.5f, zeroY),
                end = Offset(centerX + trackWidth * 1.5f, zeroY),
                strokeWidth = 2.dp.toPx()
            )

            // Draw Active Fill (from center to current)
            val currentRatio = (range.endInclusive - value) / rangeSize
            val currentY = currentRatio * h

            drawLine(
                color = activeColor.copy(alpha = 0.5f),
                start = Offset(centerX, zeroY),
                end = Offset(centerX, currentY),
                strokeWidth = trackWidth
            )

            // Draw Thumb
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(centerX, currentY)
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(centerX, currentY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

// Helper
internal fun Float.snapToStep(step: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (step == 0f) return this
    val stepped = Math.round(this / step) * step
    return stepped.coerceIn(range.start, range.endInclusive)
}

// Stub for modifier if not present in project
fun Modifier.doubleTapSliderThumbToReset(value: Float, valueRange: ClosedFloatingPointRange<Float>, onReset: () -> Unit): Modifier = this