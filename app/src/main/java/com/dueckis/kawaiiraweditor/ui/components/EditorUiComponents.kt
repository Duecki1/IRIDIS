package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange

@Composable
internal fun ToneMapperSection(
    toneMapper: String,
    onToneMapperChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Tone Mapper",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = { onToneMapperChange("basic") },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor =
                            if (toneMapper == "basic") MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor =
                            if (toneMapper == "basic") MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Basic")
                }
                FilledTonalButton(
                    onClick = { onToneMapperChange("agx") },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor =
                            if (toneMapper == "agx") MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor =
                            if (toneMapper == "agx") MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("AgX")
                }
            }
        }
    }
}

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
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
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

internal fun snapToStep(
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (step <= 0f) return clamped
    val steps = ((clamped - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}

@Composable
internal fun PanelSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable (() -> Unit))? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}
