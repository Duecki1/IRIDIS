package com.dueckis.kawaiiraweditor.data.immich

internal const val IMMICH_OAUTH_APP_REDIRECT_URI = "kawaiiraweditor://oauth/immich"

internal fun buildImmichMobileRedirectUri(serverUrl: String): String {
    val base = ImmichConfig(serverUrl, ImmichAuthMode.Login).apiBaseUrl()
    if (base.isBlank()) return ""
    return "$base/oauth/mobile-redirect"
}
