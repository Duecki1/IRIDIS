package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichOAuthStartResult(
    val authorizationUrl: String?,
    val state: String? = null,
    val codeVerifier: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)
