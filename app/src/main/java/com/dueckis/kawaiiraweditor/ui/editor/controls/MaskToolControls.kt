package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaskToolControls(
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
                    text =
                        when (maskTapMode) {
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

            Text("Size: ${brushSize.roundToInt()} px", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = brushSize,
                onValueChange = { onBeginEditInteraction(); onBrushSizeChange(it) },
                onValueChangeFinished = onEndEditInteraction,
                valueRange = 2f..400f
            )

            val softness = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness
            Text("Softness: ${(softness * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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

            Text("Radius", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = selectedSubMask.radial.radiusX,
                onValueChange = { v ->
                    val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(subMasks = m.subMasks.map { s -> if (s.id != selectedSubMask.id) s else s.copy(radial = s.radial.copy(radiusX = v, radiusY = v)) }) }
                    onMasksChange(updated)
                },
                valueRange = 0.01f..1.5f
            )

            Text("Softness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = selectedSubMask.radial.feather.coerceIn(0f, 1f),
                onValueChange = { v ->
                    val updated =
                        masks.map { m ->
                            if (m.id != selectedMask.id) m
                            else
                                m.copy(
                                    subMasks =
                                        m.subMasks.map { s ->
                                            if (s.id != selectedSubMask.id) s else s.copy(radial = s.radial.copy(feather = v))
                                        }
                                )
                        }
                    onMasksChange(updated)
                },
                valueRange = 0f..1f
            )
        }

        SubMaskType.Linear.id -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPaintingMaskChange(false); onMaskTapModeChange(MaskTapMode.SetLinearStart) }, modifier = Modifier.weight(1f)) { Text("Start") }
                Button(onClick = { onPaintingMaskChange(false); onMaskTapModeChange(MaskTapMode.SetLinearEnd) }, modifier = Modifier.weight(1f)) { Text("End") }
            }
            Text("Feather", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = selectedSubMask.linear.range,
                onValueChange = { v ->
                    val updated = masks.map { m -> if (m.id != selectedMask.id) m else m.copy(subMasks = m.subMasks.map { s -> if (s.id != selectedSubMask.id) s else s.copy(linear = s.linear.copy(range = v)) }) }
                    onMasksChange(updated)
                },
                valueRange = 0.01f..1.5f
            )
        }

        SubMaskType.AiEnvironment.id -> {
            if (!environmentMaskingEnabled) {
                Text("Environment masking is disabled in settings.", color = MaterialTheme.colorScheme.error)
            } else {
                val selectedCategory = AiEnvironmentCategory.fromId(selectedSubMask.aiEnvironment.category)
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = if (isDetectingAiEnvironmentCategories) "Detecting..." else selectedCategory.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        detectedAiEnvironmentCategories?.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    expanded = false
                                    val currentName = selectedMask.name.trim()
                                    val previousLabel = selectedCategory.label
                                    val isAutoEnvironmentName =
                                        Regex(
                                            "^${Regex.escape(previousLabel)}(\\s+Copy(\\s*\\d+)?)?$",
                                            RegexOption.IGNORE_CASE
                                        ).matches(currentName)
                                    val shouldAutoRename =
                                        currentName.isBlank() ||
                                            Regex("^Mask\\s*\\d+$", RegexOption.IGNORE_CASE).matches(currentName) ||
                                            currentName.equals("Environment", ignoreCase = true) ||
                                            isAutoEnvironmentName
                                    val updated =
                                        masks.map { m ->
                                            if (m.id != selectedMask.id) m
                                            else
                                                m.copy(
                                                    name = if (shouldAutoRename) cat.label else m.name,
                                                    subMasks =
                                                        m.subMasks.map { s ->
                                                            if (s.id != selectedSubMask.id) s
                                                            else s.copy(aiEnvironment = s.aiEnvironment.copy(category = cat.id, maskDataBase64 = null))
                                                        }
                                                )
                                        }
                                    onMasksChange(updated)
                                    onGenerateAiEnvironmentMask?.invoke()
                                }
                            )
                        }
                    }
                }

                LaunchedEffect(expanded) {
                    if (expanded && detectedAiEnvironmentCategories == null && !isDetectingAiEnvironmentCategories) {
                        onDetectAiEnvironmentCategories?.invoke()
                    }
                }
            }
        }
    }
}
