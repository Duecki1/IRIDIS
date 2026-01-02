package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.ColorGradingState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.ColorWheelControl
import kotlin.math.roundToInt

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

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (showMidtones) {
                ColorWheelControl(
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
                    isHeaderCentered = true,
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.shadows,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(shadows = it)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
                ColorWheelControl(
                    isHeaderCentered = true,
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.highlights,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(highlights = it)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
}
