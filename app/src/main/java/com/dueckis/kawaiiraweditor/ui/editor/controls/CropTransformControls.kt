package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.ui.components.PanelSectionCard
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

    PanelSectionCard(
        title = "Crop & Transform",
        trailing = {
            IconButton(
                onClick = {
                    onRotationDraftChange(null)
                    onStraightenActiveChange(false)
                    val effectiveRatioNow = originalRatio
                    val ratioAfterReset =
                        if ((adjustments.orientationSteps % 2) != 0) effectiveRatioNow?.let { 1f / it } else effectiveRatioNow
                    onAdjustmentsChange(
                        requestAutoCrop(
                            adjustments.copy(
                                rotation = 0f,
                                flipHorizontal = false,
                                flipVertical = false,
                                orientationSteps = 0,
                                aspectRatio = ratioAfterReset,
                                crop = null
                            )
                        )
                    )
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset")
            }
        }
    ) {
        val isOrientationToggleDisabled =
            adjustments.aspectRatio == null || adjustments.aspectRatio == 1f || activePreset?.ratio == 0f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                enabled = !isOrientationToggleDisabled,
                onClick = {
                    val current = adjustments.aspectRatio ?: return@IconButton
                    onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = 1f / current, crop = null)))
                }
            ) {
                Icon(Icons.Filled.Cached, contentDescription = "Swap orientation")
            }
        }

        cropPresets.chunked(3).forEach { rowPresets ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPresets.forEach { preset ->
                    val selected = preset == activePreset
                    FilledTonalButton(
                        onClick = {
                            val ar = when {
                                selected && preset.ratio != null && preset.ratio != 0f && preset.ratio != 1f -> {
                                    val current = adjustments.aspectRatio
                                    if (current != null && current.isFinite() && current != 0f) 1f / current else current
                                }

                                else ->
                                    when (preset.ratio) {
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
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(preset.name)
                    }
                }
                repeat((3 - rowPresets.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }

        FilledTonalButton(
            onClick = {
                val imageRatio = originalRatio
                val target = if (imageRatio != null && imageRatio < 1f) 1f / BASE_RATIO else BASE_RATIO
                onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = target, crop = null)))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isCustomActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Custom")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customW,
                onValueChange = { customW = it },
                enabled = isCustomActive,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("W") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = customH,
                onValueChange = { customH = it },
                enabled = isCustomActive,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("H") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            IconButton(
                enabled = isCustomActive,
                onClick = {
                    val w = customW.toFloatOrNull()
                    val h = customH.toFloatOrNull()
                    if (w != null && h != null && w > 0f && h > 0f) {
                        onAdjustmentsChange(requestAutoCrop(adjustments.copy(aspectRatio = w / h, crop = null)))
                    }
                }
            ) {
                Icon(Icons.Filled.Cached, contentDescription = "Apply ratio")
            }
        }

        Text(
            text = "Rotation",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )

        val rotationValue = rotationDraft ?: adjustments.rotation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${rotationValue.coerceIn(-45f, 45f).toString().take(6)}°",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    onRotationDraftChange(null)
                    onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = 0f, crop = null)))
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset rotation")
            }
        }

        Slider(
            modifier =
                Modifier.doubleTapSliderThumbToReset(
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
            onValueChange = { newValue ->
                onBeginEditInteraction()
                onRotationDraftChange(newValue)
            },
            onValueChangeFinished = {
                val committed = (rotationDraft ?: adjustments.rotation).coerceIn(-45f, 45f)
                onRotationDraftChange(null)
                onAdjustmentsChange(requestAutoCrop(adjustments.copy(rotation = committed, crop = null)))
                onEndEditInteraction()
            },
            valueRange = -45f..45f,
            steps = 0
        )

        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val nextSteps = ((adjustments.orientationSteps % 4) + 3) % 4
                    val nextAspect =
                        adjustments.aspectRatio?.takeIf { it.isFinite() && it != 0f }?.let { 1f / it }
                    val nextState = adjustments.copy(
                        orientationSteps = nextSteps,
                        rotation = 0f,
                        crop = null,
                        aspectRatio = nextAspect
                    )
                    val nextBaseRatio = originalRatio?.takeIf { it.isFinite() && it > 0f }?.let { 1f / it }
                    if (nextBaseRatio == null) {
                        onAdjustmentsChange(nextState)
                    } else {
                        onAdjustmentsChange(
                            nextState.copy(
                                crop = computeMaxCropNormalized(
                                    AutoCropParams(
                                        baseAspectRatio = nextBaseRatio,
                                        rotationDegrees = nextState.rotation,
                                        aspectRatio = nextState.aspectRatio
                                    )
                                )
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Left")
            }

            FilledTonalButton(
                onClick = {
                    val nextSteps = ((adjustments.orientationSteps % 4) + 1) % 4
                    val nextAspect =
                        adjustments.aspectRatio?.takeIf { it.isFinite() && it != 0f }?.let { 1f / it }
                    val nextState = adjustments.copy(
                        orientationSteps = nextSteps,
                        rotation = 0f,
                        crop = null,
                        aspectRatio = nextAspect
                    )
                    val nextBaseRatio = originalRatio?.takeIf { it.isFinite() && it > 0f }?.let { 1f / it }
                    if (nextBaseRatio == null) {
                        onAdjustmentsChange(nextState)
                    } else {
                        onAdjustmentsChange(
                            nextState.copy(
                                crop = computeMaxCropNormalized(
                                    AutoCropParams(
                                        baseAspectRatio = nextBaseRatio,
                                        rotationDegrees = nextState.rotation,
                                        aspectRatio = nextState.aspectRatio
                                    )
                                )
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Right")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    onAdjustmentsChange(adjustments.copy(flipHorizontal = !adjustments.flipHorizontal))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (adjustments.flipHorizontal) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(Icons.Filled.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Flip H")
            }

            FilledTonalButton(
                onClick = {
                    onAdjustmentsChange(adjustments.copy(flipVertical = !adjustments.flipVertical))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (adjustments.flipVertical) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    Icons.Filled.Flip,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Flip V")
            }
        }

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
                containerColor = if (isStraightenActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isStraightenActive) "Straighten (Tap to cancel)" else "Straighten")
        }
    }
}
