package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.adjustmentSections
import com.dueckis.kawaiiraweditor.data.model.vignetteSection
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
import com.dueckis.kawaiiraweditor.ui.components.ToneMapperSection
import com.dueckis.kawaiiraweditor.ui.components.doubleTapSliderThumbToReset
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskIcon
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskItemCard
import com.dueckis.kawaiiraweditor.ui.editor.masking.SubMaskItemChip
import com.dueckis.kawaiiraweditor.ui.editor.masking.duplicateMaskState
import com.dueckis.kawaiiraweditor.ui.editor.masking.newSubMaskState
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
    val maskTabsByMaskId = remember { mutableStateMapOf<String, Int>() }

    when (panelTab) {
        EditorPanelTab.CropTransform -> {
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

        EditorPanelTab.Adjustments -> {
            ToneMapperSection(
                toneMapper = adjustments.toneMapper,
                onToneMapperChange = { mapper -> onAdjustmentsChange(adjustments.withToneMapper(mapper)) }
            )

            adjustmentSections.forEach { (sectionTitle, controls) ->
                PanelSectionCard(title = sectionTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            ColorTabControls(
                adjustments = adjustments,
                histogramData = histogramData,
                onAdjustmentsChange = onAdjustmentsChange,
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction
            )
        }

        EditorPanelTab.Effects -> {
            PanelSectionCard(title = "Vignette", subtitle = "Post-crop vignette") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            val availableSubMaskTypes =
                if (environmentMaskingEnabled) {
                    listOf(SubMaskType.AiEnvironment, SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
                } else {
                    listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
                }

            fun assignMaskNumber(maskId: String): Int {
                if (maskId !in maskNumbers) {
                    val next = (maskNumbers.values.maxOrNull() ?: 0) + 1
                    maskNumbers[maskId] = next
                }
                return maskNumbers[maskId] ?: 0
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Masks", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    var showCreateMenu by remember { mutableStateOf(false) }
                    FilledTonalButton(
                        onClick = { showCreateMenu = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Mask", style = MaterialTheme.typography.labelSmall)
                    }

                    DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
                        availableSubMaskTypes.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            SubMaskType.AiEnvironment -> "Select Environment"
                                            SubMaskType.AiSubject -> "Select Subject"
                                            SubMaskType.Brush -> "Brush"
                                            SubMaskType.Linear -> "Linear Gradient"
                                            SubMaskType.Radial -> "Radial Gradient"
                                        }
                                    )
                                },
                                leadingIcon = { MaskIcon(type.id) },
                                onClick = {
                                    showCreateMenu = false
                                    onMaskTapModeChange(MaskTapMode.None)
                                    val newMaskId = UUID.randomUUID().toString()
                                    val newSubId = UUID.randomUUID().toString()
                                    val newMask =
                                        MaskState(
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

                    IconButton(
                        enabled = masks.any { it.id == selectedMaskId },
                        onClick = { onShowMaskOverlayChange(!showMaskOverlay) }
                    ) {
                        Icon(
                            imageVector = if (showMaskOverlay) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle Overlay",
                            tint =
                                if (showMaskOverlay) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(masks, key = { _, m -> m.id }) { index, mask ->
                    val isSelected = mask.id == selectedMaskId
                    var showMenu by remember(mask.id) { mutableStateOf(false) }

                    MaskItemCard(
                        mask = mask,
                        maskIndex = assignMaskNumber(mask.id).takeIf { it > 0 } ?: (index + 1),
                        isSelected = isSelected,
                        onClick = {
                            onPaintingMaskChange(false)
                            onMaskTapModeChange(MaskTapMode.None)
                            onSelectedMaskIdChange(mask.id)
                            onSelectedSubMaskIdChange(mask.subMasks.firstOrNull()?.id)
                            onRequestMaskOverlayBlink(null)
                        },
                        onMenuClick = { showMenu = true }
                    )

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (mask.invert) "Uninvert" else "Invert") },
                            onClick = {
                                showMenu = false
                                onMasksChange(masks.map { m -> if (m.id == mask.id) m.copy(invert = !m.invert) else m })
                                if (isSelected) onRequestMaskOverlayBlink(null)
                            }
                        )
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
                            text = { Text("Duplicate and invert") },
                            onClick = {
                                showMenu = false
                                val dup = duplicateMaskState(mask, true)
                                assignMaskNumber(dup.id)
                                onMasksChange(masks.toMutableList().apply { add(index + 1, dup) }.toList())
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            enabled = index > 0,
                            onClick = {
                                showMenu = false
                                if (index > 0) {
                                    val reordered = masks.toMutableList()
                                    val tmp = reordered[index - 1]
                                    reordered[index - 1] = reordered[index]
                                    reordered[index] = tmp
                                    onMasksChange(reordered.toList())
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            enabled = index < masks.lastIndex,
                            onClick = {
                                showMenu = false
                                if (index < masks.lastIndex) {
                                    val reordered = masks.toMutableList()
                                    val tmp = reordered[index + 1]
                                    reordered[index + 1] = reordered[index]
                                    reordered[index] = tmp
                                    onMasksChange(reordered.toList())
                                }
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
            if (selectedMask == null) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
                    Text(
                        "Select or create a mask to start editing",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                return
            }

            val selectedSubMask = selectedMask.subMasks.firstOrNull { it.id == selectedSubMaskId }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))

                    var showAddSubMenu by remember { mutableStateOf(false) }
                    FilledTonalButton(
                        onClick = { showAddSubMenu = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add / Subtract", style = MaterialTheme.typography.labelSmall)
                    }

                    DropdownMenu(expanded = showAddSubMenu, onDismissRequest = { showAddSubMenu = false }) {
                        listOf("Add" to SubMaskMode.Additive, "Subtract" to SubMaskMode.Subtractive).forEach { (label, mode) ->
                            DropdownMenuItem(text = { Text(label, fontWeight = FontWeight.SemiBold) }, onClick = {}, enabled = false)
                            availableSubMaskTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "  " +
                                                when (type) {
                                                    SubMaskType.AiEnvironment -> "Environment"
                                                    SubMaskType.AiSubject -> "Subject"
                                                    SubMaskType.Brush -> "Brush"
                                                    SubMaskType.Linear -> "Gradient"
                                                    SubMaskType.Radial -> "Radial"
                                                }
                                        )
                                    },
                                    leadingIcon = { MaskIcon(type.id) },
                                    onClick = {
                                        showAddSubMenu = false
                                        onMaskTapModeChange(MaskTapMode.None)
                                        val newSubId = UUID.randomUUID().toString()
                                        val updated =
                                            masks.map { m ->
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
                        }
                    }
                }

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
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(subMasks = m.subMasks.map { s -> if (s.id == sub.id) s.copy(mode = newMode) else s })
                                        }
                                    onMasksChange(updated)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = {
                                    showSubMenu = false
                                    val newId = UUID.randomUUID().toString()
                                    val copy = sub.copy(id = newId)
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else {
                                                val list = m.subMasks.toMutableList()
                                                list.add(idx + 1, copy)
                                                m.copy(subMasks = list.toList())
                                            }
                                        }
                                    onMasksChange(updated)
                                    onSelectedSubMaskIdChange(newId)
                                    onPaintingMaskChange(copy.type == SubMaskType.Brush.id || copy.type == SubMaskType.AiSubject.id)
                                    onMaskTapModeChange(MaskTapMode.None)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showSubMenu = false
                                    val updated =
                                        masks.map { m ->
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

            if (selectedSubMask == null) {
                Text("Select a submask to edit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Properties", style = MaterialTheme.typography.labelLarge)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Opacity", style = MaterialTheme.typography.bodySmall)
                        Text("${selectedMask.opacity.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        modifier =
                            Modifier.doubleTapSliderThumbToReset(
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Invert Mask")
                        Switch(
                            checked = selectedMask.invert,
                            onCheckedChange = { inv ->
                                onBeginEditInteraction()
                                onMasksChange(masks.map { m -> if (m.id == selectedMask.id) m.copy(invert = inv) else m })
                                onEndEditInteraction()
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Overlay")
                        Switch(checked = showMaskOverlay, onCheckedChange = { checked -> onShowMaskOverlayChange(checked) })
                    }

                    if (maskTapMode != MaskTapMode.None) {
                        Text(
                            text =
                                when (maskTapMode) {
                                    MaskTapMode.SetRadialCenter -> "Tap the image to set radial center"
                                    MaskTapMode.SetLinearStart -> "Tap the image to set gradient start"
                                    MaskTapMode.SetLinearEnd -> "Tap the image to set gradient end"
                                    MaskTapMode.None -> ""
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = { onMaskTapModeChange(MaskTapMode.None) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel")
                        }
                    }

                    when (selectedSubMask.type) {
                        SubMaskType.Brush.id -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { onBrushToolChange(BrushTool.Brush) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                if (brushTool == BrushTool.Brush) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                ) {
                                    Text("Brush")
                                }
                                FilledTonalButton(
                                    onClick = { onBrushToolChange(BrushTool.Eraser) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                if (brushTool == BrushTool.Eraser) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                ) {
                                    Text("Eraser")
                                }
                            }

                            Text("Brush Size: ${brushSize.roundToInt()} px")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = brushSize,
                                        valueRange = 2f..400f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            onBrushSizeChange(60f)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = brushSize.coerceIn(2f, 400f),
                                onValueChange = {
                                    onBeginEditInteraction()
                                    onBrushSizeChange(it)
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 2f..400f
                            )

                            val softness = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness
                            Text("Softness: ${(softness * 100f).roundToInt()}%")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = softness,
                                        valueRange = 0f..1f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            if (brushTool == BrushTool.Eraser) onEraserSoftnessChange(0.5f) else onBrushSoftnessChange(0.5f)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = softness.coerceIn(0f, 1f),
                                onValueChange = { newValue ->
                                    onBeginEditInteraction()
                                    if (brushTool == BrushTool.Eraser) onEraserSoftnessChange(newValue.coerceIn(0f, 1f))
                                    else onBrushSoftnessChange(newValue.coerceIn(0f, 1f))
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 0f..1f
                            )
                        }

                        SubMaskType.AiSubject.id -> {
                            Text("Draw around your subject to generate an AI mask.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Text("Softness: ${(selectedSubMask.aiSubject.softness.coerceIn(0f, 1f) * 100f).roundToInt()}%")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = selectedSubMask.aiSubject.softness,
                                        valueRange = 0f..1f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            val updated =
                                                masks.map { m ->
                                                    if (m.id != selectedMask.id) m
                                                    else m.copy(
                                                        subMasks =
                                                            m.subMasks.map { s ->
                                                                if (s.id != selectedSubMask.id) s
                                                                else s.copy(aiSubject = s.aiSubject.copy(softness = 0.25f))
                                                            }
                                                    )
                                                }
                                            onMasksChange(updated)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = selectedSubMask.aiSubject.softness.coerceIn(0f, 1f),
                                onValueChange = { newValue ->
                                    onBeginEditInteraction()
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(
                                                subMasks =
                                                    m.subMasks.map { s ->
                                                        if (s.id != selectedSubMask.id) s
                                                        else s.copy(aiSubject = s.aiSubject.copy(softness = newValue.coerceIn(0f, 1f)))
                                                    }
                                            )
                                        }
                                    onMasksChange(updated)
                                    onShowMaskOverlayChange(true)
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 0f..1f
                            )

                            FilledTonalButton(
                                onClick = {
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(
                                                subMasks =
                                                    m.subMasks.map { s ->
                                                        if (s.id != selectedSubMask.id) s
                                                        else s.copy(aiSubject = s.aiSubject.copy(maskDataBase64 = null))
                                                    }
                                            )
                                        }
                                    onMasksChange(updated)
                                    onShowMaskOverlayChange(true)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear AI Mask")
                            }
                        }

                        SubMaskType.AiEnvironment.id -> {
                            if (!environmentMaskingEnabled) {
                                Text(
                                    "Environment AI masks are disabled in Settings and won't be applied.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val hasMask = selectedSubMask.aiEnvironment.maskDataBase64 != null
                                FilledTonalButton(
                                    enabled = hasMask,
                                    onClick = {
                                        val updated =
                                            masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(
                                                    subMasks =
                                                        m.subMasks.map { s ->
                                                            if (s.id != selectedSubMask.id) s
                                                            else s.copy(aiEnvironment = s.aiEnvironment.copy(maskDataBase64 = null))
                                                        }
                                                )
                                            }
                                        onMasksChange(updated)
                                        onShowMaskOverlayChange(true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Clear AI Mask")
                                }
                            } else {
                                Text("Auto-detect a scene element and generate an AI mask.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                                val selectedCategory = AiEnvironmentCategory.fromId(selectedSubMask.aiEnvironment.category)
                                var showCategoryMenu by remember(selectedSubMask.id) { mutableStateOf(false) }
                                var envGenerateRequestKey by remember(selectedSubMask.id) { mutableStateOf(0L) }

                                LaunchedEffect(showCategoryMenu) {
                                    if (showCategoryMenu && detectedAiEnvironmentCategories == null && !isDetectingAiEnvironmentCategories) {
                                        onDetectAiEnvironmentCategories?.invoke()
                                    }
                                }

                                LaunchedEffect(envGenerateRequestKey) {
                                    if (envGenerateRequestKey == 0L) return@LaunchedEffect
                                    if (isGeneratingAiMask) return@LaunchedEffect
                                    onGenerateAiEnvironmentMask?.invoke()
                                }

                                LaunchedEffect(detectedAiEnvironmentCategories, selectedSubMask.id) {
                                    val detected = detectedAiEnvironmentCategories
                                    if (detected.isNullOrEmpty()) return@LaunchedEffect
                                    if (selectedCategory !in detected) {
                                        val fallback = detected.first()
                                        val updated =
                                            masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(
                                                    subMasks =
                                                        m.subMasks.map { s ->
                                                            if (s.id != selectedSubMask.id) s
                                                            else s.copy(aiEnvironment = s.aiEnvironment.copy(category = fallback.id, maskDataBase64 = null))
                                                        }
                                                )
                                            }
                                        onMasksChange(updated)
                                        onShowMaskOverlayChange(true)
                                        envGenerateRequestKey = System.nanoTime()
                                    }
                                }

                                val detected = detectedAiEnvironmentCategories
                                val categoryDisplayLabel =
                                    when {
                                        detected == null -> "Select"
                                        isDetectingAiEnvironmentCategories -> "Select"
                                        detected.isEmpty() -> "No categories detected"
                                        else -> selectedCategory.label
                                    }

                                ExposedDropdownMenuBox(
                                    expanded = showCategoryMenu,
                                    onExpandedChange = { showCategoryMenu = !showCategoryMenu },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = categoryDisplayLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        singleLine = true,
                                        label = { Text("Category") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu) },
                                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = showCategoryMenu,
                                        onDismissRequest = { showCategoryMenu = false }
                                    ) {
                                        val detectedOrEmpty = detectedAiEnvironmentCategories.orEmpty()

                                        if (detectedAiEnvironmentCategories == null || isDetectingAiEnvironmentCategories) {
                                            DropdownMenuItem(text = { Text("Detecting…") }, onClick = {}, enabled = false)
                                        }

                                        if (detectedAiEnvironmentCategories != null && !isDetectingAiEnvironmentCategories && detectedOrEmpty.isEmpty()) {
                                            DropdownMenuItem(text = { Text("No categories detected") }, onClick = {}, enabled = false)
                                        }

                                        detectedOrEmpty.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat.label) },
                                                onClick = {
                                                    showCategoryMenu = false
                                                    val updated =
                                                        masks.map { m ->
                                                            if (m.id != selectedMask.id) m
                                                            else m.copy(
                                                                subMasks =
                                                                    m.subMasks.map { s ->
                                                                        if (s.id != selectedSubMask.id) s
                                                                        else s.copy(aiEnvironment = s.aiEnvironment.copy(category = cat.id, maskDataBase64 = null))
                                                                    }
                                                            )
                                                        }
                                                    onMasksChange(updated)
                                                    onShowMaskOverlayChange(true)
                                                    envGenerateRequestKey = System.nanoTime()
                                                }
                                            )
                                        }
                                    }
                                }

                                Text("Softness: ${(selectedSubMask.aiEnvironment.softness.coerceIn(0f, 1f) * 100f).roundToInt()}%")
                                Slider(
                                    modifier =
                                        Modifier.doubleTapSliderThumbToReset(
                                            value = selectedSubMask.aiEnvironment.softness,
                                            valueRange = 0f..1f,
                                            onReset = {
                                                onBeginEditInteraction()
                                                val updated =
                                                    masks.map { m ->
                                                        if (m.id != selectedMask.id) m
                                                        else m.copy(
                                                            subMasks =
                                                                m.subMasks.map { s ->
                                                                    if (s.id != selectedSubMask.id) s
                                                                    else s.copy(aiEnvironment = s.aiEnvironment.copy(softness = 0.25f))
                                                                }
                                                        )
                                                    }
                                                onMasksChange(updated)
                                                onEndEditInteraction()
                                            }
                                        ),
                                    value = selectedSubMask.aiEnvironment.softness.coerceIn(0f, 1f),
                                    onValueChange = { newValue ->
                                        onBeginEditInteraction()
                                        val updated =
                                            masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(
                                                    subMasks =
                                                        m.subMasks.map { s ->
                                                            if (s.id != selectedSubMask.id) s
                                                            else s.copy(aiEnvironment = s.aiEnvironment.copy(softness = newValue.coerceIn(0f, 1f)))
                                                        }
                                                )
                                            }
                                        onMasksChange(updated)
                                        onShowMaskOverlayChange(true)
                                    },
                                    onValueChangeFinished = onEndEditInteraction,
                                    valueRange = 0f..1f
                                )

                                val hasMask = selectedSubMask.aiEnvironment.maskDataBase64 != null
                                FilledTonalButton(
                                    enabled = hasMask,
                                    onClick = {
                                        val updated =
                                            masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(
                                                    subMasks =
                                                        m.subMasks.map { s ->
                                                            if (s.id != selectedSubMask.id) s
                                                            else s.copy(aiEnvironment = s.aiEnvironment.copy(maskDataBase64 = null))
                                                        }
                                                )
                                            }
                                        onMasksChange(updated)
                                        onShowMaskOverlayChange(true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Clear AI Mask")
                                }
                            }
                        }

                        SubMaskType.Radial.id -> {
                            FilledTonalButton(
                                onClick = {
                                    onPaintingMaskChange(false)
                                    onMaskTapModeChange(MaskTapMode.SetRadialCenter)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Set Center (Tap Image)")
                            }

                            Text("Radius: ${(selectedSubMask.radial.radiusX * 100f).roundToInt()}%")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = selectedSubMask.radial.radiusX,
                                        valueRange = 0.01f..1.5f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            val updated =
                                                masks.map { m ->
                                                    if (m.id != selectedMask.id) m
                                                    else m.copy(
                                                        subMasks =
                                                            m.subMasks.map { s ->
                                                                if (s.id != selectedSubMask.id) s
                                                                else s.copy(radial = s.radial.copy(radiusX = 0.35f, radiusY = 0.35f))
                                                            }
                                                    )
                                                }
                                            onMasksChange(updated)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = selectedSubMask.radial.radiusX.coerceIn(0.01f, 1.5f),
                                onValueChange = { newValue ->
                                    onBeginEditInteraction()
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(
                                                subMasks =
                                                    m.subMasks.map { s ->
                                                        if (s.id != selectedSubMask.id) s
                                                        else s.copy(radial = s.radial.copy(radiusX = newValue, radiusY = newValue))
                                                    }
                                            )
                                        }
                                    onMasksChange(updated)
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 0.01f..1.5f
                            )

                            Text("Softness: ${(selectedSubMask.radial.feather * 100f).roundToInt()}%")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = selectedSubMask.radial.feather,
                                        valueRange = 0f..1f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            val updated =
                                                masks.map { m ->
                                                    if (m.id != selectedMask.id) m
                                                    else m.copy(
                                                        subMasks =
                                                            m.subMasks.map { s ->
                                                                if (s.id != selectedSubMask.id) s else s.copy(radial = s.radial.copy(feather = 0.5f))
                                                            }
                                                    )
                                                }
                                            onMasksChange(updated)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = selectedSubMask.radial.feather.coerceIn(0f, 1f),
                                onValueChange = { newValue ->
                                    onBeginEditInteraction()
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(
                                                subMasks =
                                                    m.subMasks.map { s ->
                                                        if (s.id != selectedSubMask.id) s else s.copy(radial = s.radial.copy(feather = newValue))
                                                    }
                                            )
                                        }
                                    onMasksChange(updated)
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 0f..1f
                            )
                        }

                        SubMaskType.Linear.id -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        onPaintingMaskChange(false)
                                        onMaskTapModeChange(MaskTapMode.SetLinearStart)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Set Start")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onPaintingMaskChange(false)
                                        onMaskTapModeChange(MaskTapMode.SetLinearEnd)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Set End")
                                }
                            }

                            Text("Softness: ${(selectedSubMask.linear.range * 100f).roundToInt()}%")
                            Slider(
                                modifier =
                                    Modifier.doubleTapSliderThumbToReset(
                                        value = selectedSubMask.linear.range,
                                        valueRange = 0.01f..1.5f,
                                        onReset = {
                                            onBeginEditInteraction()
                                            val updated =
                                                masks.map { m ->
                                                    if (m.id != selectedMask.id) m
                                                    else m.copy(
                                                        subMasks =
                                                            m.subMasks.map { s ->
                                                                if (s.id != selectedSubMask.id) s else s.copy(linear = s.linear.copy(range = 0.25f))
                                                            }
                                                    )
                                                }
                                            onMasksChange(updated)
                                            onEndEditInteraction()
                                        }
                                    ),
                                value = selectedSubMask.linear.range.coerceIn(0.01f, 1.5f),
                                onValueChange = { newValue ->
                                    onBeginEditInteraction()
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(
                                                subMasks =
                                                    m.subMasks.map { s ->
                                                        if (s.id != selectedSubMask.id) s else s.copy(linear = s.linear.copy(range = newValue))
                                                    }
                                            )
                                        }
                                    onMasksChange(updated)
                                },
                                onValueChangeFinished = onEndEditInteraction,
                                valueRange = 0.01f..1.5f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val maskInnerTabs = listOf("Adjust", "Color")
            val selectedMaskTab = maskTabsByMaskId.getOrPut(selectedMask.id) { 0 }
            TabRow(selectedTabIndex = selectedMaskTab) {
                Tab(selected = selectedMaskTab == 0, onClick = { maskTabsByMaskId[selectedMask.id] = 0 }, text = { Text(maskInnerTabs[0]) })
                Tab(selected = selectedMaskTab == 1, onClick = { maskTabsByMaskId[selectedMask.id] = 1 }, text = { Text(maskInnerTabs[1]) })
            }

            fun updateSelectedMaskAdjustments(updatedAdjustments: AdjustmentState) {
                val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(adjustments = updatedAdjustments) }
                onMasksChange(updated)
            }

            when (selectedMaskTab) {
                1 -> {
                    ColorTabControls(
                        adjustments = selectedMask.adjustments,
                        histogramData = histogramData,
                        onAdjustmentsChange = ::updateSelectedMaskAdjustments,
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction
                    )
                }

                else -> {
                    PanelSectionCard(title = "Mask Adjustments", subtitle = "Edits inside this mask") {
                        adjustmentSections.forEach { (sectionTitle, controls) ->
                            Text(text = sectionTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                            updateSelectedMaskAdjustments(selectedMask.adjustments.withValue(control.field, snapped))
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
