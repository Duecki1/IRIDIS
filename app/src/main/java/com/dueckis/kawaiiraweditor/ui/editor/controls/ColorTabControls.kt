package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard

@Composable
internal fun ColorTabControls(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val colorTabs = remember {
        listOf(
            ColorTab(title = "Curves", subtitle = "Tap to add points / Drag to adjust / Double-tap to remove", compactLabel = "Curves"),
            ColorTab(title = "Color Grading", subtitle = "Shadows / Midtones / Highlights", compactLabel = "Grading"),
            ColorTab(title = "HSL Mixer", subtitle = "Hue / Saturation / Luminance", compactLabel = "HSL")
        )
    }
    var selectedColorTab by remember { mutableIntStateOf(0) }

    val selectedTab = colorTabs[selectedColorTab]

    PanelSectionCard(
        title = selectedTab.title,
        subtitle = selectedTab.subtitle,
        trailing = {
            ColorTabSelector(
                tabs = colorTabs,
                selectedIndex = selectedColorTab,
                onSelected = { selectedColorTab = it }
            )
        }
    ) {
        AnimatedContent(targetState = selectedColorTab, label = "ColorTabs") { tabIndex ->
            when (tabIndex) {
                1 -> {
                    ColorGradingEditor(
                        colorGrading = adjustments.colorGrading,
                        onColorGradingChange = { updated ->
                            onAdjustmentsChange(adjustments.copy(colorGrading = updated))
                        },
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction
                    )
                }

                2 -> {
                    HslEditor(
                        hsl = adjustments.hsl,
                        onHslChange = { updated -> onAdjustmentsChange(adjustments.copy(hsl = updated)) },
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction
                    )
                }

                else -> {
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

private data class ColorTab(val title: String, val subtitle: String, val compactLabel: String)

@Composable
private fun ColorTabSelector(
    tabs: List<ColorTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val containerColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "ColorTabContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "ColorTabContent"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "ColorTabBorder"
            )

            Surface(
                modifier = Modifier.clickable { onSelected(index) },
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    text = tab.compactLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
