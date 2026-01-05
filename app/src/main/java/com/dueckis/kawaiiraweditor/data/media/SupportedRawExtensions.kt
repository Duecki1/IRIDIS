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
        val segment = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (segment.isBlank()) return false
        return rawExtensions.contains(segment.lowercase(Locale.US))
    }
}
