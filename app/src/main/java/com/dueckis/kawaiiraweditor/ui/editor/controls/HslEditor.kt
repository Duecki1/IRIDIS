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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.RoundGradientAdjustmentSlider
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
    var activeChannel by rememberSaveable { mutableStateOf(HslChannel.Reds) }
    val current = hsl.valueFor(activeChannel)
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }

    val hueBrush = remember(activeChannel) { hslHueTrackBrush(activeChannel) }
    val saturationBrush = remember(activeChannel) { hslSaturationTrackBrush(activeChannel) }
    val luminanceBrush = remember(activeChannel) { hslLuminanceTrackBrush(activeChannel) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT COLUMN: Channel Selector
            Column(
                modifier = Modifier.weight(0.3f).fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "COLOR MIX",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HslChannel.entries.chunked(2).forEach { rowChannels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowChannels.forEach { channel ->
                                    ColorChannelDot(
                                        color = channel.swatch,
                                        isActive = channel == activeChannel,
                                        onClick = { activeChannel = channel },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        onBeginEditInteraction()
                        onHslChange(hsl.withValue(activeChannel, HueSatLumState()))
                        onEndEditInteraction()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                }
            }

            // RIGHT COLUMN: Sliders
            Box(
                modifier = Modifier.weight(0.7f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundGradientAdjustmentSlider(
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
                    RoundGradientAdjustmentSlider(
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
                    RoundGradientAdjustmentSlider(
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

@Composable
private fun ColorChannelDot(
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        )
    }
}