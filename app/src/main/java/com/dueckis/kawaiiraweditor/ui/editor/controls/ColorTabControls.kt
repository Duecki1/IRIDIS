package com.dueckis.kawaiiraweditor.ui.editor.controls

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.ColorWheelControl
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import com.dueckis.kawaiiraweditor.ui.components.PanelTwoTitleSectionCard

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun ColorTabControls(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val isPhoneLayout = LocalConfiguration.current.screenWidthDp < 600

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isPhoneLayout) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val totalWidth = maxWidth
                val spacing = 12.dp
                val cardInternalPadding = 24.dp
                val columnWidth = (totalWidth - spacing) / 2
                val wheelSize = minOf(columnWidth - cardInternalPadding, 170.dp)

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Top
                ) {
                    PanelSectionCard(
                        title = "Curves",
                        subtitle = "Tap to add points • Drag to adjust",
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                    ) {
                        CurvesEditor(
                            adjustments = adjustments,
                            histogramData = histogramData,
                            onAdjustmentsChange = onAdjustmentsChange,
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }

                    PanelSectionCard(
                        title = "Midtones",
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ColorWheelControl(
                                wheelSize = wheelSize,
                                modifier = Modifier.fillMaxWidth(),
                                value = adjustments.colorGrading.midtones,
                                defaultValue = HueSatLumState(),
                                onValueChange = { updated ->
                                    onAdjustmentsChange(adjustments.copy(colorGrading = adjustments.colorGrading.copy(midtones = updated)))
                                },
                                onBeginEditInteraction = onBeginEditInteraction,
                                onEndEditInteraction = onEndEditInteraction
                            )
                        }
                    }
                }
            }

            PanelTwoTitleSectionCard(title = "Shadows", subtitle = "Highlights") {
                ColorGradingEditor(
                    colorGrading = adjustments.colorGrading,
                    onColorGradingChange = { updated -> onAdjustmentsChange(adjustments.copy(colorGrading = updated)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction,
                    showMidtones = false
                )
            }
        } else {
            PanelSectionCard(title = "Curves", subtitle = "Tap to add points • Drag to adjust") {
                CurvesEditor(
                    adjustments = adjustments,
                    histogramData = histogramData,
                    onAdjustmentsChange = onAdjustmentsChange,
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }

            PanelSectionCard(title = "Color Grading", subtitle = "Shadows / Midtones / Highlights") {
                ColorGradingEditor(
                    colorGrading = adjustments.colorGrading,
                    onColorGradingChange = { updated -> onAdjustmentsChange(adjustments.copy(colorGrading = updated)) },
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }
        }

        PanelSectionCard(title = "Color Mixer", subtitle = "Hue / Saturation / Luminance") {
            HslEditor(
                hsl = adjustments.hsl,
                onHslChange = { updated -> onAdjustmentsChange(adjustments.copy(hsl = updated)) },
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction
            )
        }
    }
}
