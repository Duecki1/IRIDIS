package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichLoginResult(
    val accessToken: String?,
    val userEmail: String? = null,
    val name: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)
