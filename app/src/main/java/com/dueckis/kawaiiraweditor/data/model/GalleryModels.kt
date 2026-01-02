package com.dueckis.kawaiiraweditor.data.model

import android.graphics.Bitmap

internal data class GalleryItem(
    val projectId: String,
    val fileName: String,
    val thumbnail: Bitmap? = null,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val rawMetadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val immichAssetId: String? = null,
    val immichAlbumId: String? = null,
    val editsUpdatedAtMs: Long = 0L,
    val immichSidecarUpdatedAtMs: Long = 0L
)

internal enum class Screen {
    Gallery,
    Settings,
    Editor
}
