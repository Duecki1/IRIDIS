package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichAssetInfo(
    val id: String,
    val originalFileName: String,
    val description: String? = null,
    val updatedAt: String? = null
)
