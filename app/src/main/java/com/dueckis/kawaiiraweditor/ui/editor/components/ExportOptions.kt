package com.dueckis.kawaiiraweditor.ui.editor.components

import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat

internal data class ExportOptions(
    val format: ExportImageFormat,
    val quality: Int,
    val resizeLongEdgePx: Int?,
    val dontEnlarge: Boolean,
    val lowRamMode: Boolean = false
)
