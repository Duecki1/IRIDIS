package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichAsset(
    val id: String,
    val fileName: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val type: String = "IMAGE"
)
