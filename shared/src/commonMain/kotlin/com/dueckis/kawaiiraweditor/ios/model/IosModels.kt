package com.dueckis.kawaiiraweditor.ios.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable

data class IosGalleryItem(
    val projectId: String,
    val fileName: String,
    val thumbnail: ImageBitmap? = null,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val rawMetadata: Map<String, String> = emptyMap()
)

@Serializable
data class IosProjectMetadata(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val rawMetadata: Map<String, String> = emptyMap()
)
