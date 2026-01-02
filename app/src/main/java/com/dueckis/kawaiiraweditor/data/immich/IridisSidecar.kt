package com.dueckis.kawaiiraweditor.data.immich

import android.util.Base64

internal data class IridisSidecar(
    val editsJson: String,
    val updatedAtMs: Long
)

internal fun buildIridisXmpSidecar(editsJson: String, updatedAtMs: Long): ByteArray {
    val encoded =
        Base64.encodeToString(editsJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.NO_PADDING)
    val safeUpdatedAt = updatedAtMs.coerceAtLeast(0L)
    val xml =
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="KawaiiRawEditor">
        |  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        |    <rdf:Description xmlns:krwe="https://kawaiiraweditor.dueckis.com/ns/1.0/"
        |      krwe:UpdatedAtMs="$safeUpdatedAt"
        |      krwe:EditsJsonBase64="$encoded" />
        |  </rdf:RDF>
        |</x:xmpmeta>
        |
        """.trimMargin()
    return xml.toByteArray(Charsets.UTF_8)
}

internal fun parseIridisXmpSidecar(bytes: ByteArray): IridisSidecar? {
    val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
    val updatedAtMatch = Regex("krwe:UpdatedAtMs\\s*=\\s*\"(\\d+)\"").find(text)
    val editsMatch = Regex("krwe:EditsJsonBase64\\s*=\\s*\"([^\"]+)\"").find(text)
    val updatedAtMs = updatedAtMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    val encoded = editsMatch?.groupValues?.getOrNull(1).orEmpty()
    if (encoded.isBlank()) return null
    val decoded =
        runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
    val editsJson = runCatching { decoded.toString(Charsets.UTF_8) }.getOrNull() ?: return null
    if (editsJson.isBlank()) return null
    return IridisSidecar(editsJson = editsJson, updatedAtMs = updatedAtMs)
}

