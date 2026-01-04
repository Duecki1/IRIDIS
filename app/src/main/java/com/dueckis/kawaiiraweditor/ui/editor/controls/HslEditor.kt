package com.dueckis.kawaiiraweditor.ui.editor.controls

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.GradientAdjustmentSlider
import kotlin.math.roundToInt

// --- Helper Functions ---
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

    // Brushes
    val hueBrush = remember(activeChannel) { hslHueTrackBrush(activeChannel) }
    val saturationBrush = remember(activeChannel) { hslSaturationTrackBrush(activeChannel) }
    val luminanceBrush = remember(activeChannel) { hslLuminanceTrackBrush(activeChannel) }

    // Main Card
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- LEFT COLUMN: Channel Selector ---
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Grid
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "COLOR MIX",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // 2-Column Grid of Color Dots
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HslChannel.entries.chunked(2).forEach { rowChannels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between columns
                            ) {
                                rowChannels.forEach { channel ->
                                    val isActive = channel == activeChannel
                                    ColorChannelDot(
                                        color = channel.swatch,
                                        isActive = isActive,
                                        onClick = { activeChannel = channel },
                                        modifier = Modifier.weight(1f) // Equal width distribution
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset Button
                IconButton(
                    onClick = {
                        onBeginEditInteraction()
                        onHslChange(hsl.withValue(activeChannel, HueSatLumState())) // Resets current channel
                        onEndEditInteraction()
                    },
                    modifier = Modifier.size(32.dp).align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Channel",
                        tint = activeChannel.swatch, // Icon takes channel color
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- RIGHT COLUMN: Sliders ---
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp), // Comfortable spacing
                    modifier = Modifier.fillMaxWidth()
                ) {
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
        }
    }
}

// --- Helper Component: Color Dot ---
@Composable
private fun ColorChannelDot(
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp) // Fixed height for touch target
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // If active, show a subtle background container
            .background(if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // The Dot
        Box(
            modifier = Modifier
                .size(20.dp) // Dot size
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isActive) {
                        // Active state border
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSecondaryContainer, CircleShape)
                    } else {
                        // Inactive border for visibility on dark backgrounds
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                    }
                )
        )
    }
}