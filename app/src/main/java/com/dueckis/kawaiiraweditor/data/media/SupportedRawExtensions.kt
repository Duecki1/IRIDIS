package com.dueckis.kawaiiraweditor.data.media

import java.util.Locale

/**
 * Mirrors the extension list exposed by RapidRAW/rawler's `supported_extensions()`.
 * Keep the entries lowercase to make case-insensitive comparisons cheap.
 */
internal object SupportedRawExtensions {
    private val rawExtensions = setOf(
        "ari",
        "arw",
        "cr2",
        "cr3",
        "crm",
        "crw",
        "dcr",
        "dcs",
        "dng",
        "erf",
        "iiq",
        "kdc",
        "mef",
        "mos",
        "mrw",
        "nef",
        "nrw",
        "orf",
        "ori",
        "pef",
        "raf",
        "raw",
        "rw2",
        "rwl",
        "srw",
        "3fr",
        "fff",
        "x3f",
        "qtk"
    )

    fun hasSupportedExtension(fileName: String): Boolean {
        return extractExtension(fileName)?.let { rawExtensions.contains(it) } ?: false
    }

    fun isSupported(fileName: String?, mimeType: String?): Boolean {
        return normalizeFileName(fileName, mimeType) != null
    }

    fun normalizeFileName(fileName: String?, mimeType: String?): String? {
        val trimmed = fileName?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val currentExt = extractExtension(trimmed)
        if (currentExt != null && rawExtensions.contains(currentExt)) return trimmed

        val mimeExt = extractExtensionFromMime(mimeType)
        if (mimeExt != null) {
            val base = if (currentExt == null) trimmed else trimmed.substringBeforeLast('.')
            return if (base.isBlank()) null else "$base.$mimeExt"
        }

        return null
    }

    private fun extractExtension(fileName: String): String? {
        val segment = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (segment.isBlank()) return null
        return segment.lowercase(Locale.US)
    }

    private fun extractExtensionFromMime(mimeType: String?): String? {
        val typePart = mimeType?.substringAfter('/', missingDelimiterValue = "")?.lowercase(Locale.US)
            ?.takeUnless { it.isBlank() }
            ?: return null
        val candidates = typePart.split('-', '_', '.').asReversed()
        return candidates.firstOrNull { rawExtensions.contains(it) }
    }
}
