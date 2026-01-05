package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.*
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.CompactTabRow
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import com.dueckis.kawaiiraweditor.ui.components.ToneMapperSection
import kotlinx.coroutines.launch

@Composable
internal fun EditorControlsContent(
    panelTab: EditorPanelTab,
    adjustments: AdjustmentState,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    showToneCurveProfileSwitcher: Boolean,
    histogramData: HistogramData?,
    masks: List<MaskState>,
    onMasksChange: (List<MaskState>) -> Unit,
    maskNumbers: MutableMap<String, Int>,
    selectedMaskId: String?,
    onSelectedMaskIdChange: (String?) -> Unit,
    selectedSubMaskId: String?,
    onSelectedSubMaskIdChange: (String?) -> Unit,
    isPaintingMask: Boolean,
    onPaintingMaskChange: (Boolean) -> Unit,
    showMaskOverlay: Boolean,
    onShowMaskOverlayChange: (Boolean) -> Unit,
    onRequestMaskOverlayBlink: (String?) -> Unit,
    onCreateMask: (SubMaskType) -> Unit,
    onCreateSubMask: (SubMaskMode, SubMaskType) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushTool: BrushTool,
    onBrushToolChange: (BrushTool) -> Unit,
    brushSoftness: Float,
    onBrushSoftnessChange: (Float) -> Unit,
    eraserSoftness: Float,
    onEraserSoftnessChange: (Float) -> Unit,
    maskTapMode: MaskTapMode,
    onMaskTapModeChange: (MaskTapMode) -> Unit,
    cropBaseWidthPx: Int?,
    cropBaseHeightPx: Int?,
    rotationDraft: Float?,
    onRotationDraftChange: (Float?) -> Unit,
    isStraightenActive: Boolean,
    onStraightenActiveChange: (Boolean) -> Unit,
    aiMaskingEnabled: Boolean,
    isGeneratingAiMask: Boolean,
    onGenerateAiEnvironmentMask: (() -> Unit)?,
    detectedAiEnvironmentCategories: List<AiEnvironmentCategory>?,
    isDetectingAiEnvironmentCategories: Boolean,
    onDetectAiEnvironmentCategories: (() -> Unit)?,
    maskRenameTags: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val contentModifier = modifier.fillMaxSize()

    AnimatedContent(
        targetState = panelTab,
        transitionSpec = {
            val isMovingForward = targetState.ordinal > initialState.ordinal
            val direction = if (isMovingForward) 1 else -1
            (slideInVertically { h -> direction * h / 10 } + fadeIn())
                .togetherWith(slideOutVertically { h -> -direction * h / 10 } + fadeOut())
                .using(SizeTransform(clip = false))
        },
        modifier = contentModifier,
        label = "EditorControlsTransition"
    ) { targetTab ->
        when (targetTab) {
            EditorPanelTab.Adjustments -> {
                val sections = adjustmentSections.map { (title, controls) ->
                    LightToneSection(key = title, compactLabel = title, controls = controls)
                }

                LightToneTabbedContent(
                    sections = sections,
                    adjustments = adjustments,
                    onAdjustmentsChange = onAdjustmentsChange,
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction,
                    showToneCurveProfileSwitcher = showToneCurveProfileSwitcher,
                    modifier = contentModifier
                )
            }

            EditorPanelTab.Color -> {
                ColorTabbedContent(
                    adjustments = adjustments,
                    histogramData = histogramData,
                    onAdjustmentsChange = onAdjustmentsChange,
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction,
                    modifier = contentModifier
                )
            }

            EditorPanelTab.CropTransform -> {
                ScrollableControlsColumn(modifier = contentModifier) {
                    CommonHeader(title = "Geometry")
                    ExpressiveSectionContainer(title = "Crop & Rotate") {
                        CropTransformControls(
                            adjustments = adjustments,
                            baseImageWidthPx = cropBaseWidthPx,
                            baseImageHeightPx = cropBaseHeightPx,
                            rotationDraft = rotationDraft,
                            onRotationDraftChange = onRotationDraftChange,
                            isStraightenActive = isStraightenActive,
                            onStraightenActiveChange = onStraightenActiveChange,
                            onAdjustmentsChange = onAdjustmentsChange,
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }
                }
            }

            EditorPanelTab.Effects -> {
                ScrollableControlsColumn(modifier = contentModifier) {
                    ExpressiveSectionContainer(title = "Vignette", subtitle = "Post-crop styling") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            vignetteSection.forEach { control ->
                                AdjustmentSlider(
                                    label = control.label,
                                    value = adjustments.valueFor(control.field),
                                    range = control.range,
                                    step = control.step,
                                    defaultValue = control.defaultValue,
                                    formatter = control.formatter,
                                    onValueChange = { onAdjustmentsChange(adjustments.withValue(control.field, it)) },
                                    onInteractionStart = onBeginEditInteraction,
                                    onInteractionEnd = onEndEditInteraction
                                )
                            }
                        }
                    }
                }
            }

            EditorPanelTab.Masks -> {
                ScrollableControlsColumn(modifier = contentModifier) {
                    MaskingUI(
                        masks = masks,
                        onMasksChange = onMasksChange,
                        maskNumbers = maskNumbers,
                        selectedMaskId = selectedMaskId,
                        onSelectedMaskIdChange = onSelectedMaskIdChange,
                        selectedSubMaskId = selectedSubMaskId,
                        onSelectedSubMaskIdChange = onSelectedSubMaskIdChange,
                        isPaintingMask = isPaintingMask,
                        onPaintingMaskChange = onPaintingMaskChange,
                        showMaskOverlay = showMaskOverlay,
                        onShowMaskOverlayChange = onShowMaskOverlayChange,
                        onRequestMaskOverlayBlink = onRequestMaskOverlayBlink,
                        onCreateMask = onCreateMask,
                        onCreateSubMask = onCreateSubMask,
                        brushSize = brushSize,
                        onBrushSizeChange = onBrushSizeChange,
                        brushTool = brushTool,
                        onBrushToolChange = onBrushToolChange,
                        brushSoftness = brushSoftness,
                        onBrushSoftnessChange = onBrushSoftnessChange,
                        eraserSoftness = eraserSoftness,
                        onEraserSoftnessChange = onEraserSoftnessChange,
                        maskTapMode = maskTapMode,
                        onMaskTapModeChange = onMaskTapModeChange,
                        aiMaskingEnabled = aiMaskingEnabled,
                        isGeneratingAiMask = isGeneratingAiMask,
                        onGenerateAiEnvironmentMask = onGenerateAiEnvironmentMask,
                        detectedAiEnvironmentCategories = detectedAiEnvironmentCategories,
                        isDetectingAiEnvironmentCategories = isDetectingAiEnvironmentCategories,
                        onDetectAiEnvironmentCategories = onDetectAiEnvironmentCategories,
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction,
                        histogramData = histogramData,
                        maskRenameTags = maskRenameTags
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableControlsColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LightToneTabbedContent(
    sections: List<LightToneSection>,
    adjustments: AdjustmentState,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    showToneCurveProfileSwitcher: Boolean,
    modifier: Modifier
) {
    if (sections.isEmpty()) {
        ScrollableControlsColumn(modifier) {}
        return
    }

    val pagerState = rememberPagerState(pageCount = { sections.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompactTabRow(
            labels = sections.map { it.compactLabel },
            selectedIndex = pagerState.currentPage,
            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
            key = { it }
        ) { pageIndex ->
            val section = sections[pageIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (section.key.equals("Basic", ignoreCase = true) && showToneCurveProfileSwitcher) {
                    PanelSectionCard(title = "Tone Curve Profile") {
                        ToneMapperSection(
                            toneMapper = adjustments.toneMapper,
                            exposure = adjustments.exposure,
                            onToneMapperChange = { mapper -> onAdjustmentsChange(adjustments.withToneMapper(mapper)) },
                            onExposureChange = { value -> onAdjustmentsChange(adjustments.copy(exposure = value)) },
                            onInteractionStart = onBeginEditInteraction,
                            onInteractionEnd = onEndEditInteraction
                        )
                    }
                }

                PanelSectionCard(title = null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        section.controls.forEach { control ->
                            AdjustmentSlider(
                                label = control.label,
                                value = adjustments.valueFor(control.field),
                                range = control.range,
                                step = control.step,
                                defaultValue = control.defaultValue,
                                formatter = control.formatter,
                                onValueChange = { snapped -> onAdjustmentsChange(adjustments.withValue(control.field, snapped)) },
                                onInteractionStart = onBeginEditInteraction,
                                onInteractionEnd = onEndEditInteraction
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorTabbedContent(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    modifier: Modifier
) {
    val colorTabs = remember { listOf("Curves", "Grading", "HSL") }
    val pagerState = rememberPagerState(pageCount = { colorTabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompactTabRow(
            labels = colorTabs,
            selectedIndex = pagerState.currentPage,
            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
            key = { it }
        ) { pageIndex ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (pageIndex) {
                    1 -> {
                        PanelSectionCard(title = null) {
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
                        PanelSectionCard(title = null) {
                            HslEditor(
                                hsl = adjustments.hsl,
                                onHslChange = { updated -> onAdjustmentsChange(adjustments.copy(hsl = updated)) },
                                onBeginEditInteraction = onBeginEditInteraction,
                                onEndEditInteraction = onEndEditInteraction
                            )
                        }
                    }

                    else -> {
                        PanelSectionCard(title = null) {
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

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

private data class LightToneSection(
    val key: String,
    val compactLabel: String,
    val controls: List<AdjustmentControl>
)