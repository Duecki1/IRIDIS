package com.dueckis.kawaiiraweditor.data.model

import android.graphics.Bitmap

internal data class GalleryItem(
    val projectId: String,
    val fileName: String,
    val thumbnail: Bitmap? = null,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val rawMetadata: Map<String, String> = emptyMap()
)

internal enum class Screen {
    Gallery,
    Settings,
    Editor
}

