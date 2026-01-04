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
import androidx.compose.foundation.layout.padding
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
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.adjustmentSections
import com.dueckis.kawaiiraweditor.data.model.vignetteSection
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import com.dueckis.kawaiiraweditor.ui.components.ToneMapperSection


@OptIn(ExperimentalMaterial3Api::class)
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
    environmentMaskingEnabled: Boolean,
    isGeneratingAiMask: Boolean,
    onGenerateAiEnvironmentMask: (() -> Unit)?,
    detectedAiEnvironmentCategories: List<AiEnvironmentCategory>?,
    isDetectingAiEnvironmentCategories: Boolean,
    onDetectAiEnvironmentCategories: (() -> Unit)?,
    maskRenameTags: List<String> = emptyList()
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = panelTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideInVertically { height -> height / 10 })
                    .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { height -> -height / 10 })
                    .using(SizeTransform(clip = false))
            },
            label = "EditorControlsTransition"
        ) { targetTab ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 80.dp)
            ) {
                when (targetTab) {
                    EditorPanelTab.CropTransform -> {
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

                    EditorPanelTab.Adjustments -> {
                        CommonHeader(title = "Light & Tone")

                        val lightToneTabs = adjustmentSections.map { it.first }
                        var selectedLightToneTab by remember { mutableIntStateOf(0) }

                        if (lightToneTabs.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    lightToneTabs.forEachIndexed { index, title ->
                                        SegmentedButton(
                                            selected = selectedLightToneTab == index,
                                            onClick = { selectedLightToneTab = index },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = lightToneTabs.size
                                            ),
                                            label = { Text(title) }
                                        )
                                    }
                                }

                                AnimatedContent(targetState = selectedLightToneTab, label = "LightToneTabs") { tabIndex ->
                                    val safeIndex = tabIndex.coerceIn(0, lightToneTabs.lastIndex)
                                    val (sectionTitle, controls) = adjustmentSections[safeIndex]

                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (sectionTitle.equals("Basic", ignoreCase = true) && showToneCurveProfileSwitcher) {
                                            PanelSectionCard(title = "Tone Curve Profile") {
                                                ToneMapperSection(
                                                    toneMapper = adjustments.toneMapper,
                                                    exposure = adjustments.exposure,
                                                    onToneMapperChange = { mapper ->
                                                        onAdjustmentsChange(adjustments.withToneMapper(mapper))
                                                    },
                                                    onExposureChange = { value ->
                                                        onAdjustmentsChange(adjustments.copy(exposure = value))
                                                    },
                                                    onInteractionStart = onBeginEditInteraction,
                                                    onInteractionEnd = onEndEditInteraction
                                                )
                                            }
                                        }

                                        PanelSectionCard(title = sectionTitle) {
                                            controls.forEach { control ->
                                                val currentValue = adjustments.valueFor(control.field)
                                                AdjustmentSlider(
                                                    label = control.label,
                                                    value = currentValue,
                                                    range = control.range,
                                                    step = control.step,
                                                    defaultValue = control.defaultValue,
                                                    formatter = control.formatter,
                                                    onValueChange = { snapped ->
                                                        onAdjustmentsChange(adjustments.withValue(control.field, snapped))
                                                    },
                                                    onInteractionStart = onBeginEditInteraction,
                                                    onInteractionEnd = onEndEditInteraction
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EditorPanelTab.Color -> {
                        CommonHeader(title = "Color Grading")
                        ColorTabControls(
                            adjustments = adjustments,
                            histogramData = histogramData,
                            onAdjustmentsChange = onAdjustmentsChange,
                            onBeginEditInteraction = onBeginEditInteraction,
                            onEndEditInteraction = onEndEditInteraction
                        )
                    }

                    EditorPanelTab.Effects -> {
                        CommonHeader(title = "Effects")
                        ExpressiveSectionContainer(title = "Vignette", subtitle = "Post-crop styling") {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                vignetteSection.forEach { control ->
                                    val currentValue = adjustments.valueFor(control.field)
                                    AdjustmentSlider(
                                        label = control.label,
                                        value = currentValue,
                                        range = control.range,
                                        step = control.step,
                                        defaultValue = control.defaultValue,
                                        formatter = control.formatter,
                                        onValueChange = { snapped ->
                                            onAdjustmentsChange(adjustments.withValue(control.field, snapped))
                                        },
                                        onInteractionStart = onBeginEditInteraction,
                                        onInteractionEnd = onEndEditInteraction
                                    )
                                }
                            }
                        }
                    }

                    EditorPanelTab.Masks -> {
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
                            environmentMaskingEnabled = environmentMaskingEnabled,
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
}
