package com.dueckis.kawaiiraweditor.data.immich

internal data class ImmichConfig(
    val serverUrl: String,
    val authMode: ImmichAuthMode,
    val apiKey: String = "",
    val accessToken: String = ""
) {
    fun apiBaseUrl(): String {
        val trimmed = serverUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }
}
