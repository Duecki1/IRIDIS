package com.dueckis.kawaiiraweditor.data.model

internal fun AdjustmentState.toMaskTransformState(): MaskTransformState {
    return MaskTransformState(
        rotation = rotation,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        orientationSteps = orientationSteps,
        crop = crop?.normalized()
    )
}

internal fun MaskTransformState.toAdjustmentState(): AdjustmentState {
    val normalized = normalized()
    return AdjustmentState(
        rotation = normalized.rotation,
        flipHorizontal = normalized.flipHorizontal,
        flipVertical = normalized.flipVertical,
        orientationSteps = normalized.orientationSteps,
        crop = normalized.crop
    )
}

internal fun AdjustmentState.isNeutralForMask(): Boolean {
    fun nearZero(v: Float) = kotlin.math.abs(v) <= 0.000001f
    fun near(v: Float, target: Float) = kotlin.math.abs(v - target) <= 0.000001f
    return nearZero(rotation) &&
        !flipHorizontal &&
        !flipVertical &&
        orientationSteps == 0 &&
        aspectRatio == null &&
        crop == null &&
        nearZero(exposure) &&
        nearZero(brightness) &&
        nearZero(contrast) &&
        nearZero(highlights) &&
        nearZero(shadows) &&
        nearZero(whites) &&
        nearZero(blacks) &&
        nearZero(saturation) &&
        nearZero(temperature) &&
        nearZero(tint) &&
        nearZero(vibrance) &&
        nearZero(clarity) &&
        nearZero(dehaze) &&
        nearZero(structure) &&
        nearZero(centre) &&
        nearZero(vignetteAmount) &&
        near(vignetteMidpoint, 50f) &&
        nearZero(vignetteRoundness) &&
        near(vignetteFeather, 50f) &&
        nearZero(sharpness) &&
        nearZero(lumaNoiseReduction) &&
        nearZero(colorNoiseReduction) &&
        nearZero(chromaticAberrationRedCyan) &&
        nearZero(chromaticAberrationBlueYellow) &&
        curves.isDefault() &&
        colorGrading.isDefault() &&
        hsl.isDefault()
}
