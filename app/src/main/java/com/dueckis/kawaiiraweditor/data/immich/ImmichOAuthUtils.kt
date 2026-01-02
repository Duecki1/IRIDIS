package com.dueckis.kawaiiraweditor.data.immich

import android.net.Uri

internal fun parseImmichOAuthParams(redirectUrl: String): ImmichOAuthParams {
    val trimmed = redirectUrl.trim()
    if (trimmed.isBlank()) return ImmichOAuthParams(null, null, null, null)
    val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
        ?: return ImmichOAuthParams(null, null, null, null)

    val fragmentParams =
        parsed.fragment?.takeIf { it.isNotBlank() }?.let { fragment ->
            runCatching { Uri.parse("dummy://oauth/?$fragment") }.getOrNull()
        }

    fun pickParam(name: String): String? {
        return parsed.getQueryParameter(name)?.takeIf { it.isNotBlank() }
            ?: fragmentParams?.getQueryParameter(name)?.takeIf { it.isNotBlank() }
    }

    return ImmichOAuthParams(
        code = pickParam("code"),
        state = pickParam("state"),
        error = pickParam("error"),
        errorDescription = pickParam("error_description")
    )
}

internal fun buildImmichCallbackUrl(serverUrl: String, redirectUrl: String): String {
    val baseUriString = "${serverUrl.trimEnd('/')}/api/oauth/mobile-redirect"
    val baseBuilder = Uri.parse(baseUriString).buildUpon()
    val intercepted = Uri.parse(redirectUrl)

    fun copyParams(source: Uri) {
        source.queryParameterNames?.forEach { key ->
            source.getQueryParameters(key)?.forEach { value ->
                baseBuilder.appendQueryParameter(key, value)
            }
        }
    }

    copyParams(intercepted)

    if (!intercepted.fragment.isNullOrBlank()) {
        val fragmentUri = Uri.parse("dummy://?${intercepted.fragment}")
        copyParams(fragmentUri)
    }

    return baseBuilder.build().toString()
}
