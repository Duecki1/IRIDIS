package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.adjustmentSections
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.doubleTapSliderThumbToReset
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskIcon
import com.dueckis.kawaiiraweditor.ui.editor.masking.MaskItemCard
import com.dueckis.kawaiiraweditor.ui.editor.masking.SubMaskItemChip
import com.dueckis.kawaiiraweditor.ui.editor.masking.duplicateMaskState
import com.dueckis.kawaiiraweditor.ui.editor.masking.duplicateSubMaskState
import com.dueckis.kawaiiraweditor.ui.editor.masking.newSubMaskState
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaskingUI(
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
    histogramData: HistogramData?,
    maskRenameTags: List<String>
) {
    val availableSubMaskTypes = remember(environmentMaskingEnabled) {
        if (environmentMaskingEnabled) {
            listOf(SubMaskType.AiEnvironment, SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
        } else {
            listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial)
        }
    }
    var renamingMaskId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    fun assignMaskNumber(maskId: String): Int {
        if (maskId !in maskNumbers) {
            val next = (maskNumbers.values.maxOrNull() ?: 0) + 1
            maskNumbers[maskId] = next
        }
        return maskNumbers[maskId] ?: 0
    }

    CommonMaskHeader(title = "Masks") {
        FilledTonalIconButton(
            enabled = masks.any { it.id == selectedMaskId },
            onClick = { onShowMaskOverlayChange(!showMaskOverlay) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (showMaskOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (showMaskOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = if (showMaskOverlay) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = "Toggle Overlay"
            )
        }

        Box {
            var showCreateMenu by remember { mutableStateOf(false) }
            Button(
                onClick = { showCreateMenu = true },
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(100)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New")
            }

            DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    "Create new mask...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            val subMask = newSubMaskState(newSubId, SubMaskMode.Additive, type)
                            val newMask = MaskState(
                                id = newMaskId,
                                name =
                                    when (type) {
                                        SubMaskType.AiEnvironment -> AiEnvironmentCategory.fromId(subMask.aiEnvironment.category).label
                                        SubMaskType.AiSubject -> "Subject"
                                        SubMaskType.Brush -> "Brush"
                                        SubMaskType.Linear -> "Gradient"
                                        SubMaskType.Radial -> "Radial"
                                    },
                                subMasks = listOf(subMask)
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

    LazyRow(
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

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                DropdownMenuItem(
                    text = { Text(if (mask.invert) "Uninvert Mask" else "Invert Mask") },
                    onClick = {
                        showMenu = false
                        onMasksChange(masks.map { m -> if (m.id == mask.id) m.copy(invert = !m.invert) else m })
                        if (isSelected) onRequestMaskOverlayBlink(null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        showMenu = false
                        renamingMaskId = mask.id
                        showRenameDialog = true
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
                    text = { Text("Duplicate & Invert") },
                    onClick = {
                        showMenu = false
                        val dup = duplicateMaskState(mask, true)
                        assignMaskNumber(dup.id)
                        onMasksChange(masks.toMutableList().apply { add(index + 1, dup) }.toList())
                    }
                )

                HorizontalDivider()

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
    val maskToRename = masks.firstOrNull { it.id == renamingMaskId }

    if (showRenameDialog && maskToRename != null) {
        RenameMaskDialog(
            currentName = maskToRename.name,
            tagPresets = maskRenameTags,
            onDismiss = {
                showRenameDialog = false
                renamingMaskId = null
            },
            onConfirm = { newName ->
                onMasksChange(
                    masks.map { m ->
                        if (m.id == maskToRename.id) m.copy(name = newName) else m
                    }
                )
                showRenameDialog = false
                renamingMaskId = null
            }
        )
    }

    val selectedMask = masks.firstOrNull { it.id == selectedMaskId }

    AnimatedVisibility(
        visible = selectedMask != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        if (selectedMask != null) {
            val selectedSubMask = selectedMask.subMasks.firstOrNull { it.id == selectedSubMaskId }

            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        var showAddSubModeMenu by remember { mutableStateOf(false) }
                        var showAddSubTypeMenu by remember { mutableStateOf(false) }
                        var pendingSubMaskMode by remember { mutableStateOf<SubMaskMode?>(null) }
                        FilledTonalButton(
                            onClick = { showAddSubModeMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("/", modifier = Modifier.padding(horizontal = 2.dp))
                            Text("-", style = MaterialTheme.typography.titleLarge)
                        }

                        DropdownMenu(
                            expanded = showAddSubModeMenu,
                            onDismissRequest = { showAddSubModeMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add", style = MaterialTheme.typography.labelLarge) },
                                onClick = {
                                    showAddSubModeMenu = false
                                    pendingSubMaskMode = SubMaskMode.Additive
                                    showAddSubTypeMenu = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtract", style = MaterialTheme.typography.labelLarge) },
                                onClick = {
                                    showAddSubModeMenu = false
                                    pendingSubMaskMode = SubMaskMode.Subtractive
                                    showAddSubTypeMenu = true
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = showAddSubTypeMenu,
                            onDismissRequest = {
                                showAddSubTypeMenu = false
                                pendingSubMaskMode = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            val mode = pendingSubMaskMode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (mode) {
                                            SubMaskMode.Additive -> "Add…"
                                            SubMaskMode.Subtractive -> "Subtract…"
                                            null -> "Add…"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            HorizontalDivider()

                            availableSubMaskTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label()) },
                                    leadingIcon = { MaskIcon(type.id) },
                                    enabled = mode != null,
                                    onClick = {
                                        val selectedMode = mode ?: return@DropdownMenuItem
                                        showAddSubTypeMenu = false
                                        pendingSubMaskMode = null
                                        onMaskTapModeChange(MaskTapMode.None)
                                        val newSubId = UUID.randomUUID().toString()
                                        val updated = masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(subMasks = m.subMasks + newSubMaskState(newSubId, selectedMode, type))
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

                    Spacer(Modifier.width(12.dp))

                    val subMaskChipsState = rememberLazyListState()
                    val latestMasks by rememberUpdatedState(masks)
                    val latestOnMasksChange by rememberUpdatedState(onMasksChange)
                    val haptic = LocalHapticFeedback.current
                    val latestHaptic by rememberUpdatedState(haptic)
                    var draggingSubMaskId by remember(selectedMask.id) { mutableStateOf<String?>(null) }
                    var draggingSubMaskOffsetX by remember(selectedMask.id) { mutableStateOf(0f) }
                    var lastHapticReorderIndex by remember(selectedMask.id) { mutableStateOf<Int?>(null) }

                    LazyRow(
                        state = subMaskChipsState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(selectedMask.subMasks, key = { _, s -> s.id }) { idx, sub ->
                            val isSubSelected = sub.id == selectedSubMaskId
                            val isDragging = draggingSubMaskId == sub.id
                            val dragScale by androidx.compose.animation.core.animateFloatAsState(if (isDragging) 1.06f else 1f, label = "SubMaskDragScale")
                            val dragAlpha by androidx.compose.animation.core.animateFloatAsState(if (isDragging) 0.9f else 1f, label = "SubMaskDragAlpha")
                            var showSubMenu by remember(sub.id) { mutableStateOf(false) }

                            SubMaskItemChip(
                                subMask = sub,
                                isSelected = isSubSelected,
                                modifier =
                                    Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer(
                                            translationX = if (isDragging) draggingSubMaskOffsetX else 0f,
                                            scaleX = dragScale,
                                            scaleY = dragScale,
                                            alpha = dragAlpha
                                        )
                                        .pointerInput(selectedMask.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingSubMaskId = sub.id
                                                    draggingSubMaskOffsetX = 0f
                                                    lastHapticReorderIndex = idx
                                                    latestHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragCancel = {
                                                    if (draggingSubMaskId == sub.id) {
                                                        draggingSubMaskId = null
                                                        draggingSubMaskOffsetX = 0f
                                                        lastHapticReorderIndex = null
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (draggingSubMaskId == sub.id) {
                                                        draggingSubMaskId = null
                                                        draggingSubMaskOffsetX = 0f
                                                        lastHapticReorderIndex = null
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (draggingSubMaskId != sub.id) return@detectDragGesturesAfterLongPress

                                                    draggingSubMaskOffsetX += dragAmount.x

                                                    val currentMaskId = selectedMask.id
                                                    val currentMask =
                                                        latestMasks.firstOrNull { it.id == currentMaskId }
                                                            ?: return@detectDragGesturesAfterLongPress
                                                    val fromIndex = currentMask.subMasks.indexOfFirst { it.id == sub.id }
                                                    if (fromIndex == -1) return@detectDragGesturesAfterLongPress

                                                    val layoutInfo = subMaskChipsState.layoutInfo
                                                    val draggedInfo =
                                                        layoutInfo.visibleItemsInfo.firstOrNull { it.index == fromIndex }
                                                            ?: return@detectDragGesturesAfterLongPress
                                                    val draggedCenterX = draggedInfo.offset + draggingSubMaskOffsetX + (draggedInfo.size / 2f)

                                                    val targetInfo =
                                                        layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                            info.index != fromIndex &&
                                                                draggedCenterX >= info.offset &&
                                                                draggedCenterX <= (info.offset + info.size)
                                                        } ?: return@detectDragGesturesAfterLongPress

                                                    val toIndex = targetInfo.index
                                                    if (fromIndex == toIndex) return@detectDragGesturesAfterLongPress
                                                    if (lastHapticReorderIndex != toIndex) {
                                                        latestHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastHapticReorderIndex = toIndex
                                                    }

                                                    val movedSubMasks =
                                                        currentMask.subMasks.toMutableList().apply {
                                                            add(toIndex, removeAt(fromIndex))
                                                        }.toList()

                                                    latestOnMasksChange(
                                                        latestMasks.map { m ->
                                                            if (m.id != currentMaskId) m else m.copy(subMasks = movedSubMasks)
                                                        }
                                                    )

                                                    draggingSubMaskOffsetX += (draggedInfo.offset - targetInfo.offset).toFloat()
                                                }
                                            )
                                        },
                                onClick = {
                                    onSelectedSubMaskIdChange(sub.id)
                                    onPaintingMaskChange(sub.type == SubMaskType.Brush.id || sub.type == SubMaskType.AiSubject.id)
                                    onMaskTapModeChange(MaskTapMode.None)
                                    onRequestMaskOverlayBlink(sub.id)
                                },
                                onMenuClick = { showSubMenu = true }
                            )

                            DropdownMenu(
                                expanded = showSubMenu,
                                onDismissRequest = { showSubMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = {
                                        showSubMenu = false
                                        onMaskTapModeChange(MaskTapMode.None)
                                        val dup = duplicateSubMaskState(sub)
                                        val updated = masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else m.copy(subMasks = m.subMasks.toMutableList().apply { add(idx + 1, dup) }.toList())
                                        }
                                        onMasksChange(updated)
                                        onSelectedSubMaskIdChange(dup.id)
                                        onPaintingMaskChange(dup.type == SubMaskType.Brush.id || dup.type == SubMaskType.AiSubject.id)
                                        onRequestMaskOverlayBlink(dup.id)
                                    }
                                )
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

                if (selectedSubMask != null) {
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Properties", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }

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

                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Mask Opacity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${selectedMask.opacity.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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

                var selectedMaskTab by remember(selectedMask.id) { mutableIntStateOf(0) }
                val maskInnerTabs = listOf("Adjustments", "Color Grading")

                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        maskInnerTabs.forEachIndexed { index, title ->
                            SegmentedButton(
                                selected = selectedMaskTab == index,
                                onClick = { selectedMaskTab = index },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = maskInnerTabs.size
                                ),
                                label = { Text(title) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    AnimatedContent(targetState = selectedMaskTab, label = "MaskTabs") { tabIndex ->
                        when (tabIndex) {
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
            Box(
                modifier =
                    Modifier
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
