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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.dueckis.kawaiiraweditor.ui.components.ToneMapperSection
import java.util.Locale

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

                        if (showToneCurveProfileSwitcher) {
                            ExpressiveSectionContainer(title = "Tone Curve Profile") {
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

                        adjustmentSections.forEach { (sectionTitle, controls) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveSectionContainer(title = sectionTitle) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (sectionTitle.equals("Basics", ignoreCase = true)) {
                                        AdjustmentSlider(
                                            label = "Exposure",
                                            value = adjustments.exposure,
                                            range = -5f..5f,
                                            step = 0.01f,
                                            defaultValue = 0f,
                                            formatter = { value -> String.format(Locale.US, "%.2f", value) },
                                            onValueChange = { snapped ->
                                                onAdjustmentsChange(adjustments.copy(exposure = snapped))
                                            },
                                            onInteractionStart = onBeginEditInteraction,
                                            onInteractionEnd = onEndEditInteraction
                                        )
                                    }

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
