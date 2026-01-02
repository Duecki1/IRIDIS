package com.dueckis.kawaiiraweditor.ui.editor.controls

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.GradientAdjustmentSlider
import kotlin.math.roundToInt

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
            items(HslChannel.entries) { channel ->
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
