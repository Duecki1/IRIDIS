package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorTabControls(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val colorTabs = listOf("Curves", "Color Grading", "HSL Mixer")
    var selectedColorTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            colorTabs.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedColorTab == index,
                    onClick = { selectedColorTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = colorTabs.size),
                    label = { Text(title) }
                )
            }
        }

        AnimatedContent(targetState = selectedColorTab, label = "ColorTabs") { tabIndex ->
            when (tabIndex) {
                1 -> {
                    PanelSectionCard(title = "Color Grading", subtitle = "Shadows / Midtones / Highlights") {
                        ColorGradingEditor(
                            colorGrading = adjustments.colorGrading,
                            onColorGradingChange = { updated ->
                                onAdjustmentsChange(adjustments.copy(colorGrading = updated))
                            },
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }
                }

                2 -> {
                    PanelSectionCard(title = "HSL Mixer", subtitle = "Hue / Saturation / Luminance") {
                        HslEditor(
                            hsl = adjustments.hsl,
                            onHslChange = { updated -> onAdjustmentsChange(adjustments.copy(hsl = updated)) },
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }
                }

                else -> {
                    PanelSectionCard(title = "Curves", subtitle = "Tap to add points / Drag to adjust") {
                        CurvesEditor(
                            adjustments = adjustments,
                            histogramData = histogramData,
                            onAdjustmentsChange = onAdjustmentsChange,
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }
                }
            }
        }
    }
}
