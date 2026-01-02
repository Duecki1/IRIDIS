package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichUploadResult(
    val assetId: String?,
    val errorMessage: String? = null,
    val statusCode: Int? = null,
    val responseBody: String? = null
)
