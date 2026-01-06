package com.dueckis.kawaiiraweditor.data.model

import java.util.Locale

internal val basicSection = listOf(
    AdjustmentControl(
        field = AdjustmentField.Exposure,
        label = "Exposure",
        range = -5f..5f,
        step = 0.01f,
        defaultValue = 0f,
        formatter = { value -> String.format(Locale.US, "%.2f", value) }
    ),
    AdjustmentControl(
        field = AdjustmentField.Brightness,
        label = "Brightness",
        range = -5f..5f,
        step = 0.01f,
        defaultValue = 0f,
        formatter = { value -> String.format(Locale.US, "%.2f", value) }
    ),
    AdjustmentControl(field = AdjustmentField.Contrast, label = "Contrast", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Highlights, label = "Highlights", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Shadows, label = "Shadows", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Whites, label = "Whites", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Blacks, label = "Blacks", range = -100f..100f, step = 1f)
)

internal val colorSection = listOf(
    AdjustmentControl(field = AdjustmentField.Saturation, label = "Saturation", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Temperature, label = "Temperature", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Tint, label = "Tint", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Vibrance, label = "Vibrance", range = -100f..100f, step = 1f)
)

internal val detailsSection = listOf(
    AdjustmentControl(field = AdjustmentField.Clarity, label = "Clarity", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Dehaze, label = "Dehaze", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Structure, label = "Structure", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Centre, label = "Centré", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Sharpness, label = "Sharpness", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.ChromaticAberrationRedCyan, label = "CA Red/Cyan", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.ChromaticAberrationBlueYellow, label = "CA Blue/Yellow", range = -100f..100f, step = 1f)
)

internal val vignetteSection = listOf(
    AdjustmentControl(field = AdjustmentField.VignetteAmount, label = "Amount", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteMidpoint, label = "Midpoint", range = 0f..100f, step = 1f, defaultValue = 50f),
    AdjustmentControl(field = AdjustmentField.VignetteRoundness, label = "Roundness", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteFeather, label = "Feather", range = 0f..100f, step = 1f, defaultValue = 50f)
)

internal val adjustmentSections = listOf(
    "Basic" to basicSection,
    "Color" to colorSection,
    "Details" to detailsSection
)
