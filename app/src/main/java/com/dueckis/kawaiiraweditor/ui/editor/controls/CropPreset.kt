package com.dueckis.kawaiiraweditor.ui.editor.controls

internal data class CropPreset(
    val name: String,
    val ratio: Float?
)

internal val cropPresets = listOf(
    CropPreset("Free", null),
    CropPreset("Original", 0f),
    CropPreset("1:1", 1f),
    CropPreset("5:4", 5f / 4f),
    CropPreset("4:3", 4f / 3f),
    CropPreset("3:2", 3f / 2f),
    CropPreset("16:9", 16f / 9f),
    CropPreset("21:9", 21f / 9f),
    CropPreset("65:24", 65f / 24f)
)
