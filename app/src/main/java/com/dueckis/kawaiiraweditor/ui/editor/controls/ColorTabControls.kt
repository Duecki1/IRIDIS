package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.CompactTabRow
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ColorTabControls(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val colorTabs = remember {
        listOf("Curves", "Grading", "HSL")
    }

    val pagerState = rememberPagerState(pageCount = { colorTabs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CompactTabRow(
            labels = colorTabs,
            selectedIndex = pagerState.currentPage,
            onSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 16.dp,
            // FIX: Force content to stay at the top so it doesn't jump vertically
            verticalAlignment = Alignment.Top,
            // FIX: Provide a stable key based on the index
            key = { it },
            userScrollEnabled = true
        ) { pageIndex ->
            PanelSectionCard(title = null) {
                when (pageIndex) {
                    0 -> {
                        CurvesEditor(
                            adjustments = adjustments,
                            histogramData = histogramData,
                            onAdjustmentsChange = onAdjustmentsChange,
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }
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
                }
            }
        }
    }
}