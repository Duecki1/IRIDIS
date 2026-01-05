package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.CompactTabRow
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
            ColorTab(title = "Curves", compactLabel = "Curves"),
            ColorTab(title = "Color Grading", compactLabel = "Grading"),
            ColorTab(title = "HSL Mixer", compactLabel = "HSL")
        )
    }
    var selectedColorTab by remember { mutableIntStateOf(0) }

    val safeIndex = selectedColorTab.coerceIn(0, colorTabs.lastIndex)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CompactTabRow(
            labels = colorTabs.map { it.compactLabel },
            selectedIndex = safeIndex,
            onSelected = { selectedColorTab = it },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedContent(
            targetState = safeIndex,
            label = "ColorTabs",
            transitionSpec = {
                // Determine if we are moving forward (to the right) or backward (to the left)
                val isMovingForward = targetState > initialState
                val direction = if (isMovingForward) 1 else -1
                val animSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 350)

                // New content slides in from the direction (bottom/top)
                // Old content slides out to the opposite direction
                (slideInVertically(animationSpec = animSpec) { height -> direction * height / 4 } + fadeIn())
                    .togetherWith(slideOutVertically(animationSpec = animSpec) { height -> -direction * height / 4 } + fadeOut())
                    .using(SizeTransform(clip = false))
            }
        ) { tabIndex ->
            val tab = colorTabs[tabIndex]

            PanelSectionCard(title = tab.title) {
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
}

private data class ColorTab(val title: String, val compactLabel: String)