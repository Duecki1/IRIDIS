package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichAlbum(
    val id: String,
    val name: String,
    val assetCount: Int,
    val thumbnailAssetId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastModifiedAssetTimestamp: String? = null
)
