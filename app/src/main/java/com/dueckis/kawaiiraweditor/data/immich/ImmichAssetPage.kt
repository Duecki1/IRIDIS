package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichAssetPage(
    val items: List<ImmichAsset>,
    val count: Int,
    val total: Int
)
