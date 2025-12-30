package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.adjustmentSections
import com.dueckis.kawaiiraweditor.data.model.vignetteSection
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.ToneMapperSection
import com.dueckis.kawaiiraweditor.ui.components.doubleTapSliderThumbToReset
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskIcon
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskItemCard
import com.dueckis.kawaiiraweditor.ui.editor.masking.SubMaskItemChip
import com.dueckis.kawaiiraweditor.ui.editor.masking.duplicateMaskState
import com.dueckis.kawaiiraweditor.ui.editor.masking.newSubMaskState
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditorControlsContent(
    panelTab: EditorPanelTab,
    adjustments: AdjustmentState,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
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
    onDetectAiEnvironmentCategories: (() -> Unit)?
) {
    // Container for the entire control panel
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

            // Inner content wrapper
            Column(
                modifier = Modifier
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

                        // Adjustment Sections
                        adjustmentSections.forEach { (sectionTitle, controls) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveSectionContainer(title = sectionTitle) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                                    // Inject Exposure Slider INTO the Basics section group as the first item
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
                            histogramData = histogramData
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Header for ALL tabs to ensure alignment with the Masks tab.
 */
@Composable
private fun CommonHeader(
    title: String,
    content: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * A styled container for sections of the editor, using Material 3 Expressive shapes and elevations.
 */
@Composable
private fun ExpressiveSectionContainer(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp), // Expressive Large Shape
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}


// -----------------------------------------------------------------------------------------
// REFACTORED MASKING UI
// -----------------------------------------------------------------------------------------

@Composable
private fun MaskingUI(
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
    environmentMaskingEnabled: Boolean,
    isGeneratingAiMask: Boolean,
    onGenerateAiEnvironmentMask: (() -> Unit)?,
    detectedAiEnvironmentCategories: List<AiEnvironmentCategory>?,
    isDetectingAiEnvironmentCategories: Boolean,
    onDetectAiEnvironmentCategories: (() -> Unit)?,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    histogramData: HistogramData?
) {
    val availableSubMaskTypes = remember(environmentMaskingEnabled) {
        if (environmentMaskingEnabled) {
            listOf(SubMaskType.AiEnvironment, SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
        } else {
            listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
        }
    }

    fun assignMaskNumber(maskId: String): Int {
        if (maskId !in maskNumbers) {
            val next = (maskNumbers.values.maxOrNull() ?: 0) + 1
            maskNumbers[maskId] = next
        }
        return maskNumbers[maskId] ?: 0
    }

    // --- Header & Create Button (Aligned via CommonHeader) ---
    CommonHeader(title = "Masks") {
        // Visibility Toggle
        FilledTonalIconButton(
            enabled = masks.any { it.id == selectedMaskId },
            onClick = { onShowMaskOverlayChange(!showMaskOverlay) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if(showMaskOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if(showMaskOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = if (showMaskOverlay) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = "Toggle Overlay"
            )
        }

        // Add Mask Dropdown
        Box {
            var showCreateMenu by remember { mutableStateOf(false) }
            Button(
                onClick = { showCreateMenu = true },
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(100) // Pill shape
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New")
            }

            DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Create new mask...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                availableSubMaskTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label()) },
                        leadingIcon = { MaskIcon(type.id) },
                        onClick = {
                            showCreateMenu = false
                            onMaskTapModeChange(MaskTapMode.None)
                            val newMaskId = UUID.randomUUID().toString()
                            val newSubId = UUID.randomUUID().toString()
                            val newMask = MaskState(
                                id = newMaskId,
                                name = "Mask ${masks.size + 1}",
                                subMasks = listOf(newSubMaskState(newSubId, SubMaskMode.Additive, type))
                            )
                            assignMaskNumber(newMaskId)
                            onMasksChange(masks + newMask)
                            onSelectedMaskIdChange(newMaskId)
                            onSelectedSubMaskIdChange(newSubId)
                            onPaintingMaskChange(type == SubMaskType.Brush || type == SubMaskType.AiSubject)
                            onRequestMaskOverlayBlink(null)
                        }
                    )
                }
            }
        }
    }

    // --- Masks Carousel ---
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(masks, key = { _, m -> m.id }) { index, mask ->
            val isSelected = mask.id == selectedMaskId
            var showMenu by remember(mask.id) { mutableStateOf(false) }

            MaskItemCard(
                mask = mask,
                maskIndex = assignMaskNumber(mask.id).takeIf { it > 0 } ?: (index + 1),
                isSelected = isSelected,
                onClick = {
                    onMaskTapModeChange(MaskTapMode.None)
                    onSelectedMaskIdChange(mask.id)
                    val firstSub = mask.subMasks.firstOrNull()
                    onSelectedSubMaskIdChange(firstSub?.id)
                    val shouldPaint = firstSub?.type == SubMaskType.Brush.id || firstSub?.type == SubMaskType.AiSubject.id
                    onPaintingMaskChange(shouldPaint)
                    onRequestMaskOverlayBlink(null)
                },
                onMenuClick = { showMenu = true }
            )

            // Mask Actions Dropdown
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(if (mask.invert) "Uninvert Mask" else "Invert Mask") },
                    onClick = {
                        showMenu = false
                        onMasksChange(masks.map { m -> if (m.id == mask.id) m.copy(invert = !m.invert) else m })
                        if (isSelected) onRequestMaskOverlayBlink(null)
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = {
                        showMenu = false
                        val dup = duplicateMaskState(mask, false)
                        assignMaskNumber(dup.id)
                        onMasksChange(masks.toMutableList().apply { add(index + 1, dup) }.toList())
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        val remaining = masks.filterNot { it.id == mask.id }
                        onMasksChange(remaining)
                        if (isSelected) {
                            onPaintingMaskChange(false)
                            onMaskTapModeChange(MaskTapMode.None)
                            onSelectedMaskIdChange(remaining.firstOrNull()?.id)
                            onSelectedSubMaskIdChange(remaining.firstOrNull()?.subMasks?.firstOrNull()?.id)
                            onRequestMaskOverlayBlink(null)
                        }
                    }
                )
            }
        }
    }

    val selectedMask = masks.firstOrNull { it.id == selectedMaskId }

    // --- Active Mask Interface ---
    AnimatedVisibility(
        visible = selectedMask != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        if (selectedMask != null) {
            val selectedSubMask = selectedMask.subMasks.firstOrNull { it.id == selectedSubMaskId }

            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

                // Sub-mask Toolbar (Add/Subtract + Chips)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add/Subtract Button
                    Box {
                        var showAddSubMenu by remember { mutableStateOf(false) }
                        FilledTonalButton(
                            onClick = { showAddSubMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("/", modifier = Modifier.padding(horizontal = 2.dp))
                            Text("-", style = MaterialTheme.typography.titleLarge)
                        }

                        DropdownMenu(
                            expanded = showAddSubMenu,
                            onDismissRequest = { showAddSubMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            listOf("Add" to SubMaskMode.Additive, "Subtract" to SubMaskMode.Subtractive).forEach { (label, mode) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                                    onClick = {},
                                    enabled = false
                                )
                                availableSubMaskTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.label()) },
                                        leadingIcon = { MaskIcon(type.id) },
                                        onClick = {
                                            showAddSubMenu = false
                                            onMaskTapModeChange(MaskTapMode.None)
                                            val newSubId = UUID.randomUUID().toString()
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(subMasks = m.subMasks + newSubMaskState(newSubId, mode, type))
                                            }
                                            onMasksChange(updated)
                                            onSelectedSubMaskIdChange(newSubId)
                                            onPaintingMaskChange(type == SubMaskType.Brush || type == SubMaskType.AiSubject)
                                            onRequestMaskOverlayBlink(newSubId)
                                        }
                                    )
                                }
                                if (label == "Add") HorizontalDivider()
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // SubMask Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(selectedMask.subMasks, key = { _, s -> s.id }) { idx, sub ->
                            val isSubSelected = sub.id == selectedSubMaskId
                            var showSubMenu by remember(sub.id) { mutableStateOf(false) }

                            SubMaskItemChip(
                                subMask = sub,
                                isSelected = isSubSelected,
                                onClick = {
                                    onSelectedSubMaskIdChange(sub.id)
                                    onPaintingMaskChange(sub.type == SubMaskType.Brush.id || sub.type == SubMaskType.AiSubject.id)
                                    onMaskTapModeChange(MaskTapMode.None)
                                    onRequestMaskOverlayBlink(sub.id)
                                },
                                onMenuClick = { showSubMenu = true }
                            )

                            DropdownMenu(expanded = showSubMenu, onDismissRequest = { showSubMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (sub.mode == SubMaskMode.Additive) "Change to Subtract" else "Change to Add") },
                                    onClick = {
                                        showSubMenu = false
                                        val newMode = if (sub.mode == SubMaskMode.Additive) SubMaskMode.Subtractive else SubMaskMode.Additive
                                        val updated = masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(subMasks = m.subMasks.map { s -> if (s.id == sub.id) s.copy(mode = newMode) else s })
                                        }
                                        onMasksChange(updated)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showSubMenu = false
                                        val updated = masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(subMasks = m.subMasks.filterNot { it.id == sub.id })
                                        }
                                        onMasksChange(updated)
                                        if (isSubSelected) {
                                            onPaintingMaskChange(false)
                                            onSelectedSubMaskIdChange(updated.firstOrNull { it.id == selectedMask.id }?.subMasks?.firstOrNull()?.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // --- Selected SubMask Properties Panel ---
                if (selectedSubMask != null) {
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Header for props
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Properties", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }

                            // Specific Tool Controls
                            MaskToolControls(
                                selectedSubMask = selectedSubMask,
                                masks = masks,
                                selectedMask = selectedMask,
                                onMasksChange = onMasksChange,
                                onBeginEditInteraction = onBeginEditInteraction,
                                onEndEditInteraction = onEndEditInteraction,
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
                                onPaintingMaskChange = onPaintingMaskChange,
                                onShowMaskOverlayChange = onShowMaskOverlayChange,
                                environmentMaskingEnabled = environmentMaskingEnabled,
                                detectedAiEnvironmentCategories = detectedAiEnvironmentCategories,
                                isDetectingAiEnvironmentCategories = isDetectingAiEnvironmentCategories,
                                onDetectAiEnvironmentCategories = onDetectAiEnvironmentCategories,
                                isGeneratingAiMask = isGeneratingAiMask,
                                onGenerateAiEnvironmentMask = onGenerateAiEnvironmentMask
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Common Opacity
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Mask Opacity", style = MaterialTheme.typography.bodyMedium)
                                    Text("${selectedMask.opacity.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    modifier = Modifier.doubleTapSliderThumbToReset(
                                        value = selectedMask.opacity,
                                        valueRange = 0f..100f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            onMasksChange(masks.map { m -> if (m.id == selectedMask.id) m.copy(opacity = 100f) else m })
                                            onEndEditInteraction()
                                        }
                                    ),
                                    value = selectedMask.opacity.coerceIn(0f, 100f),
                                    onValueChange = { newValue ->
                                        onBeginEditInteraction()
                                        onMasksChange(masks.map { m -> if (m.id == selectedMask.id) m.copy(opacity = newValue) else m })
                                    },
                                    onValueChangeFinished = onEndEditInteraction,
                                    valueRange = 0f..100f
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // --- Inner Tabs for Mask Adjustments vs Color ---
                var selectedMaskTab by remember(selectedMask.id) { mutableIntStateOf(0) }
                val maskInnerTabs = listOf("Adjustments", "Color Grading")

                Column(Modifier.padding(horizontal = 16.dp)) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedMaskTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(selectedMaskTab),
                                height = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        maskInnerTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedMaskTab == index,
                                onClick = { selectedMaskTab = index },
                                text = {
                                    Text(
                                        title,
                                        fontWeight = if(selectedMaskTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    AnimatedContent(targetState = selectedMaskTab, label = "MaskTabs") { tabIndex ->
                        when(tabIndex) {
                            1 -> {
                                ColorTabControls(
                                    adjustments = selectedMask.adjustments,
                                    histogramData = histogramData,
                                    onAdjustmentsChange = { newAdj ->
                                        val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(adjustments = newAdj) }
                                        onMasksChange(updated)
                                    },
                                    onBeginEditInteraction = onBeginEditInteraction,
                                    onEndEditInteraction = onEndEditInteraction
                                )
                            }
                            else -> {
                                ExpressiveSectionContainer(title = "Local Adjustments") {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        // Exposure first
                                        AdjustmentSlider(
                                            label = "Exposure",
                                            value = selectedMask.adjustments.exposure,
                                            range = -5f..5f,
                                            step = 0.01f,
                                            defaultValue = 0f,
                                            formatter = { value -> String.format(Locale.US, "%.2f", value) },
                                            onValueChange = { snapped ->
                                                val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(adjustments = selectedMask.adjustments.copy(exposure = snapped)) }
                                                onMasksChange(updated)
                                            },
                                            onInteractionStart = onBeginEditInteraction,
                                            onInteractionEnd = onEndEditInteraction
                                        )

                                        adjustmentSections.forEach { (_, controls) ->
                                            controls.forEach { control ->
                                                val currentValue = selectedMask.adjustments.valueFor(control.field)
                                                AdjustmentSlider(
                                                    label = control.label,
                                                    value = currentValue,
                                                    range = control.range,
                                                    step = control.step,
                                                    defaultValue = control.defaultValue,
                                                    formatter = control.formatter,
                                                    onValueChange = { snapped ->
                                                        val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(adjustments = selectedMask.adjustments.withValue(control.field, snapped)) }
                                                        onMasksChange(updated)
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
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select or create a mask to start editing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// Helper for SubMask Type Labels
private fun SubMaskType.label(): String = when(this) {
    SubMaskType.AiEnvironment -> "AI Environment"
    SubMaskType.AiSubject -> "AI Subject"
    SubMaskType.Brush -> "Brush"
    SubMaskType.Linear -> "Linear Gradient"
    SubMaskType.Radial -> "Radial Gradient"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaskToolControls(
    selectedSubMask: SubMaskState,
    masks: List<MaskState>,
    selectedMask: MaskState,
    onMasksChange: (List<MaskState>) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
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
    onPaintingMaskChange: (Boolean) -> Unit,
    onShowMaskOverlayChange: (Boolean) -> Unit,
    environmentMaskingEnabled: Boolean,
    detectedAiEnvironmentCategories: List<AiEnvironmentCategory>?,
    isDetectingAiEnvironmentCategories: Boolean,
    onDetectAiEnvironmentCategories: (() -> Unit)?,
    isGeneratingAiMask: Boolean,
    onGenerateAiEnvironmentMask: (() -> Unit)?
) {
    // Mode Cancellation Button
    if (maskTapMode != MaskTapMode.None) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            onClick = { onMaskTapModeChange(MaskTapMode.None) }
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (maskTapMode) {
                        MaskTapMode.SetRadialCenter -> "Tap image to set center"
                        MaskTapMode.SetLinearStart -> "Tap image to set start"
                        MaskTapMode.SetLinearEnd -> "Tap image to set end"
                        MaskTapMode.None -> ""
                    },
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge
                )
                Text("Cancel", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }

    when (selectedSubMask.type) {
        SubMaskType.Brush.id -> {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = brushTool == BrushTool.Brush,
                    onClick = { onBrushToolChange(BrushTool.Brush) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Draw") }
                SegmentedButton(
                    selected = brushTool == BrushTool.Eraser,
                    onClick = { onBrushToolChange(BrushTool.Eraser) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Erase") }
            }

            Text("Size: ${brushSize.roundToInt()} px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = brushSize,
                onValueChange = { onBeginEditInteraction(); onBrushSizeChange(it) },
                onValueChangeFinished = onEndEditInteraction,
                valueRange = 2f..400f
            )

            val softness = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness
            Text("Softness: ${(softness * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = softness,
                onValueChange = {
                    onBeginEditInteraction()
                    if (brushTool == BrushTool.Eraser) onEraserSoftnessChange(it) else onBrushSoftnessChange(it)
                },
                onValueChangeFinished = onEndEditInteraction,
                valueRange = 0f..1f
            )
        }

        SubMaskType.AiSubject.id -> {
            Text("Draw loosely over subject to detect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val updated = masks.map { m ->
                        if (m.id != selectedMask.id) m
                        else m.copy(subMasks = m.subMasks.map { s -> if (s.id != selectedSubMask.id) s else s.copy(aiSubject = s.aiSubject.copy(maskDataBase64 = null)) })
                    }
                    onMasksChange(updated)
                    onShowMaskOverlayChange(true)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear & Redraw") }
        }

        SubMaskType.Radial.id -> {
            Button(
                onClick = { onPaintingMaskChange(false); onMaskTapModeChange(MaskTapMode.SetRadialCenter) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Set Center") }

            Text("Radius", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = selectedSubMask.radial.radiusX,
                onValueChange = { v ->
                    val updated = masks.map { m -> if(m.id!=selectedMask.id) m else m.copy(subMasks = m.subMasks.map { s -> if(s.id!=selectedSubMask.id) s else s.copy(radial = s.radial.copy(radiusX=v, radiusY=v)) }) }
                    onMasksChange(updated)
                },
                valueRange = 0.01f..1.5f
            )
        }

        SubMaskType.Linear.id -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPaintingMaskChange(false); onMaskTapModeChange(MaskTapMode.SetLinearStart) }, modifier = Modifier.weight(1f)) { Text("Start") }
                Button(onClick = { onPaintingMaskChange(false); onMaskTapModeChange(MaskTapMode.SetLinearEnd) }, modifier = Modifier.weight(1f)) { Text("End") }
            }
            Text("Feather", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = selectedSubMask.linear.range,
                onValueChange = { v ->
                    val updated = masks.map { m -> if(m.id!=selectedMask.id) m else m.copy(subMasks = m.subMasks.map { s -> if(s.id!=selectedSubMask.id) s else s.copy(linear = s.linear.copy(range=v)) }) }
                    onMasksChange(updated)
                },
                valueRange = 0.01f..1.5f
            )
        }

        SubMaskType.AiEnvironment.id -> {
            if (!environmentMaskingEnabled) {
                Text("Environment masking is disabled in settings.", color = MaterialTheme.colorScheme.error)
            } else {
                // Category Selector logic abbreviated for brevity but styled nicely
                val selectedCategory = AiEnvironmentCategory.fromId(selectedSubMask.aiEnvironment.category)
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = if (isDetectingAiEnvironmentCategories) "Detecting..." else selectedCategory.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        detectedAiEnvironmentCategories?.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = {
                                    expanded = false
                                    val updated = masks.map { m -> if(m.id!=selectedMask.id) m else m.copy(subMasks = m.subMasks.map { s -> if(s.id!=selectedSubMask.id) s else s.copy(aiEnvironment = s.aiEnvironment.copy(category=cat.id, maskDataBase64=null)) }) }
                                    onMasksChange(updated)
                                    onGenerateAiEnvironmentMask?.invoke()
                                }
                            )
                        }
                    }
                }

                // Trigger Detection if needed
                LaunchedEffect(expanded) {
                    if (expanded && detectedAiEnvironmentCategories == null && !isDetectingAiEnvironmentCategories) {
                        onDetectAiEnvironmentCategories?.invoke()
                    }
                }
            }
        }
    }
}