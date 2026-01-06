package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichOAuthParams(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?
)
