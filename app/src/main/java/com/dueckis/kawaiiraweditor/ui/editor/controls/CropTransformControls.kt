package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.ui.components.doubleTapSliderThumbToReset
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BASE_RATIO = 1.618f

@Composable
internal fun CropTransformControls(
    adjustments: AdjustmentState,
    baseImageWidthPx: Int?,
    baseImageHeightPx: Int?,
    rotationDraft: Float?,
    onRotationDraftChange: (Float?) -> Unit,
    isStraightenActive: Boolean,
    onStraightenActiveChange: (Boolean) -> Unit,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    // --- Logic Setup ---
    val baseW = baseImageWidthPx
    val baseH = baseImageHeightPx
    val originalRatio = remember(baseW, baseH) {
        if (baseW == null || baseH == null || baseW <= 0 || baseH <= 0) null
        else baseW.toFloat() / baseH.toFloat()
    }

    fun isApprox(a: Float, b: Float): Boolean = abs(a - b) < 0.001f

    val activePreset = remember(adjustments.aspectRatio, originalRatio) {
        val ar = adjustments.aspectRatio
        if (ar == null) return@remember cropPresets.first { it.ratio == null }
        val numericPreset = cropPresets.firstOrNull { preset ->
            val r = preset.ratio
            r != null && r != 0f && (isApprox(ar, r) || isApprox(ar, 1f / r))
        }
        if (numericPreset != null) return@remember numericPreset
        if (originalRatio != null && isApprox(ar, originalRatio)) {
            return@remember cropPresets.first { it.ratio == 0f }
        }
        null
    }

    val isCustomActive = adjustments.aspectRatio != null && activePreset == null

    fun requestAutoCrop(next: AdjustmentState): AdjustmentState {
        val ratio = originalRatio ?: return next
        val crop = computeMaxCropNormalized(
            AutoCropParams(
                baseAspectRatio = ratio,
                rotationDegrees = next.rotation,
                aspectRatio = next.aspectRatio
            )
        )
        return next.copy(crop = crop)
    }

    // --- Vertical Stack Layout ---
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Aspect Ratio Card
        AspectRatioCard(
            adjustments = adjustments,
            originalRatio = originalRatio,
            activePreset = activePreset,
            isCustomActive = isCustomActive,
            onAdjustmentsChange = onAdjustmentsChange,
            requestAutoCrop = ::requestAutoCrop
        )

        // 2. Rotation Card
        RotationCard(
            adjustments = adjustments,
            rotationDraft = rotationDraft,
            isStraightenActive = isStraightenActive,
            onRotationDraftChange = onRotationDraftChange,
            onAdjustmentsChange = onAdjustmentsChange,
            onStraightenActiveChange = onStraightenActiveChange,
            requestAutoCrop = ::requestAutoCrop,
            onBeginEditInteraction = onBeginEditInteraction,
            onEndEditInteraction = onEndEditInteraction
        )

        // 3. Geometry Card
        GeometryCard(
            adjustments = adjustments,
            originalRatio = originalRatio,
            onAdjustmentsChange = onAdjustmentsChange
        )
    }
}

// --- Card 1: Aspect Ratio ---
@Composable
private fun AspectRatioCard(
    adjustments: AdjustmentState,
    originalRatio: Float?,
    activePreset: CropPreset?,
    isCustomActive: Boolean,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    requestAutoCrop: (AdjustmentState) -> AdjustmentState
) {
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }

    LaunchedEffect(isCustomActive, adjustments.aspectRatio) {
        if (isCustomActive) {
            val ar = adjustments.aspectRatio ?: return@LaunchedEffect
            val h = 100f
            val w = ar * h
            customW = w.toString().removeSuffix(".0")
            customH = h.roundToInt().toString()
        } else {
            customW = ""
            customH = ""
        }
    }

    SectionCard(
        title = "ASPECT RATIO",
        onReset = {
            onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = null, crop = null)))
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Presets Grid
            val rows = cropPresets.chunked(4) // 4 items per row for compactness
            rows.forEach { rowPresets ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPresets.forEach { preset ->
                        val selected = preset == activePreset
                        CompactToggleButton(
                            text = preset.name,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val ar = when {
                                    selected && preset.ratio != null && preset.ratio != 0f && preset.ratio != 1f -> {
                                        val current = adjustments.aspectRatio
                                        if (current != null && current.isFinite() && current != 0f) 1f / current else current
                                    }
                                    else -> when (preset.ratio) {
                                        null -> null
                                        0f -> originalRatio
                                        else -> {
                                            val imageRatio = originalRatio
                                            val base = preset.ratio
                                            if (imageRatio != null && imageRatio < 1f && base > 1f) 1f / base else base
                                        }
                                    }
                                }
                                onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = ar, crop = null)))
                            }
                        )
                    }
                    repeat(4 - rowPresets.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }

            // Bottom Actions (Orientation & Custom)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Swap Orientation
                FilledTonalButton(
                    onClick = {
                        val current = adjustments.aspectRatio ?: return@FilledTonalButton
                        onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = 1f / current, crop = null)))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = adjustments.aspectRatio != null && adjustments.aspectRatio != 1f && activePreset?.ratio != 0f,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(Icons.Default.Cached, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Flip Ratio", style = MaterialTheme.typography.bodySmall)
                }

                // Custom Logic
                if (!isCustomActive) {
                    FilledTonalButton(
                        onClick = {
                            val imageRatio = originalRatio
                            val target = if (imageRatio != null && imageRatio < 1f) 1f / BASE_RATIO else BASE_RATIO
                            onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = target, crop = null)))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Text("Custom", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactNumInput(customW, { customW = it }, Modifier.weight(1f))
                        Text(":", style = MaterialTheme.typography.labelLarge)
                        CompactNumInput(customH, { customH = it }, Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val w = customW.toFloatOrNull()
                                val h = customH.toFloatOrNull()
                                if (w != null && h != null && w > 0f && h > 0f) {
                                    onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = w / h, crop = null)))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- Card 2: Rotation ---
@Composable
private fun RotationCard(
    adjustments: AdjustmentState,
    rotationDraft: Float?,
    isStraightenActive: Boolean,
    onRotationDraftChange: (Float?) -> Unit,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    onStraightenActiveChange: (Boolean) -> Unit,
    requestAutoCrop: (AdjustmentState) -> AdjustmentState,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val rotationValue = rotationDraft ?: adjustments.rotation

    SectionCard(
        title = "ROTATION",
        onReset = {
            onBeginEditInteraction()
            onRotationDraftChange(null)
            onStraightenActiveChange(false)
            onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = 0f, crop = null)))
            onEndEditInteraction()
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Readout
            Text(
                text = "${rotationValue.coerceIn(-45f, 45f).toString().take(5)}°",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slider
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .doubleTapSliderThumbToReset(
                        value = rotationValue,
                        valueRange = -45f..45f,
                        onReset = {
                            onBeginEditInteraction()
                            onRotationDraftChange(null)
                            onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = 0f, crop = null)))
                            onEndEditInteraction()
                        }
                    ),
                value = rotationValue.coerceIn(-45f, 45f),
                onValueChange = {
                    onBeginEditInteraction()
                    onRotationDraftChange(it)
                },
                onValueChangeFinished = {
                    val committed = (rotationDraft ?: adjustments.rotation).coerceIn(-45f, 45f)
                    onRotationDraftChange(null)
                    onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = committed, crop = null)))
                    onEndEditInteraction()
                },
                valueRange = -45f..45f
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Straighten Button
            FilledTonalButton(
                onClick = {
                    val next = !isStraightenActive
                    onStraightenActiveChange(next)
                    if (next) {
                        onRotationDraftChange(null)
                        onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = 0f, crop = null)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isStraightenActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isStraightenActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStraightenActive) "Cancel Straighten" else "Straighten Tool")
            }
        }
    }
}

// --- Card 3: Geometry ---
@Composable
private fun GeometryCard(
    adjustments: AdjustmentState,
    originalRatio: Float?,
    onAdjustmentsChange: (AdjustmentState) -> Unit
) {
    SectionCard(
        title = "GEOMETRY",
        onReset = {
            // Reset orientation steps and flips
            rotate90(adjustments.copy(orientationSteps = 0, flipHorizontal = false, flipVertical = false), originalRatio, 0, onAdjustmentsChange)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Rotate 90
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rotate 90°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeometryButton(Icons.Default.RotateLeft, "Left") {
                        rotate90(adjustments, originalRatio, 3, onAdjustmentsChange)
                    }
                    GeometryButton(Icons.Default.RotateRight, "Right") {
                        rotate90(adjustments, originalRatio, 1, onAdjustmentsChange)
                    }
                }
            }

            // Flip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Flip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeometryButton(Icons.Default.Flip, "Horizontal", adjustments.flipHorizontal) {
                        onAdjustmentsChange(adjustments.copy(flipHorizontal = !adjustments.flipHorizontal))
                    }
                    GeometryButton(Icons.Default.Flip, "Vertical", adjustments.flipVertical) {
                        onAdjustmentsChange(adjustments.copy(flipVertical = !adjustments.flipVertical))
                    }
                }
            }
        }
    }
}

// --- Shared Components ---

@Composable
private fun SectionCard(
    title: String,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Small Reset Icon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onReset)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Content
            content()
        }
    }
}

@Composable
private fun RowScope.CompactToggleButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun RowScope.GeometryButton(
    icon: ImageVector,
    text: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompactNumInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun rotate90(
    state: AdjustmentState,
    originalRatio: Float?,
    stepsToAdd: Int,
    onChange: (AdjustmentState) -> Unit
) {
    val nextSteps = ((state.orientationSteps % 4) + stepsToAdd) % 4
    val nextAspect = state.aspectRatio?.takeIf { it.isFinite() && it != 0f }?.let { 1f / it }

    val nextState = state.copy(
        orientationSteps = nextSteps,
        rotation = 0f,
        crop = null,
        aspectRatio = nextAspect
    )
    val nextBaseRatio = originalRatio?.takeIf { it.isFinite() && it > 0f }?.let { 1f / it }

    if (nextBaseRatio == null) {
        onChange(nextState)
    } else {
        val crop = computeMaxCropNormalized(
            AutoCropParams(
                baseAspectRatio = nextBaseRatio,
                rotationDegrees = nextState.rotation,
                aspectRatio = nextState.aspectRatio
            )
        )
        onChange(nextState.copy(crop = crop))
    }
}