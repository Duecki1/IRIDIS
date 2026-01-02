package com.dueckis.kawaiiraweditor.data.model

internal data class BrushLineState(
    val tool: String = "brush",
    val brushSize: Float,
    val feather: Float = 0.5f,
    val order: Long = 0L,
    val points: List<MaskPoint>
)
