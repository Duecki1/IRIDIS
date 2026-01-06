package com.dueckis.kawaiiraweditor.ui.editor.controls

import com.dueckis.kawaiiraweditor.data.model.CropState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun computeMaxCropNormalized(params: AutoCropParams): CropState {
    if (params.aspectRatio == null) {
        return CropState(0f, 0f, 1f, 1f)
    }
    val baseAspect = params.baseAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val (w, h) = if (baseAspect >= 1f) {
        baseAspect to 1f
    } else {
        1f to (1f / baseAspect)
    }
    val ratio = params.aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (w / h)

    val angle = abs(params.rotationDegrees) % 180f
    val rad = (angle * Math.PI / 180.0).toFloat()
    val s = sin(rad).coerceAtLeast(0f)
    val c = cos(rad).coerceAtLeast(0f)

    val cropH = min(
        h / (ratio * s + c).coerceAtLeast(0.000001f),
        w / (ratio * c + s).coerceAtLeast(0.000001f)
    ).coerceIn(0f, h)
    val cropW = (ratio * cropH).coerceIn(0f, w)

    val x = ((w - cropW) / 2f).coerceIn(0f, (w - 1f).coerceAtLeast(0f))
    val y = ((h - cropH) / 2f).coerceIn(0f, (h - 1f).coerceAtLeast(0f))

    return CropState(
        x = (x / w).coerceIn(0f, 1f),
        y = (y / h).coerceIn(0f, 1f),
        width = (cropW / w).coerceIn(0f, 1f),
        height = (cropH / h).coerceIn(0f, 1f)
    ).normalized()
}
