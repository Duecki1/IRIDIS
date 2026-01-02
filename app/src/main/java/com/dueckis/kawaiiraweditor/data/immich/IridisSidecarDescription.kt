package com.dueckis.kawaiiraweditor.data.immich

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object IridisSidecarDescription {
    private const val begin = "[KRWE:BEGIN]"
    private const val end = "[KRWE:END]"

    internal fun upsert(description: String?, editsJson: String, updatedAtMs: Long): String {
        val base = description.orEmpty()
        val stripped = remove(base).trimEnd()
        val encoded = encodeGzipBase64(editsJson)
        val block =
            buildString {
                append(begin).append('\n')
                append("updatedAtMs=").append(updatedAtMs.coerceAtLeast(0L)).append('\n')
                append("encoding=gzip+base64").append('\n')
                append("edits=").append(encoded).append('\n')
                append(end)
            }
        return if (stripped.isBlank()) block else stripped + "\n\n" + block
    }

    internal fun parse(description: String?): IridisSidecarDescriptionParsed? {
        val base = description.orEmpty()
        val block = extractBlock(base) ?: return null
        val map =
            block.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim()
                    if (k.isBlank() || v.isBlank()) return@mapNotNull null
                    k to v
                }
                .toMap()
        val updatedAtMs = map["updatedAtMs"]?.toLongOrNull() ?: 0L
        val encoding = map["encoding"].orEmpty()
        val editsEncoded = map["edits"].orEmpty()
        if (editsEncoded.isBlank()) return null
        val editsJson =
            when (encoding.lowercase()) {
                "gzip+base64" -> decodeGzipBase64(editsEncoded)
                "base64" -> decodeBase64(editsEncoded)
                else -> decodeGzipBase64(editsEncoded) ?: decodeBase64(editsEncoded)
            } ?: return null
        val original = remove(base).trim()
        return IridisSidecarDescriptionParsed(
            sidecar = IridisSidecar(editsJson = editsJson, updatedAtMs = updatedAtMs),
            originalDescription = original
        )
    }

    internal fun remove(description: String): String {
        val start = description.indexOf(begin)
        val endIdx = description.indexOf(end)
        if (start < 0 || endIdx < 0 || endIdx <= start) return description
        val afterEnd = endIdx + end.length
        val before = description.substring(0, start)
        val after = description.substring(afterEnd)
        return (before + after).trim()
    }

    private fun extractBlock(description: String): String? {
        val start = description.indexOf(begin)
        val endIdx = description.indexOf(end)
        if (start < 0 || endIdx < 0 || endIdx <= start) return null
        val innerStart = start + begin.length
        return description.substring(innerStart, endIdx).trim()
    }

    private fun encodeGzipBase64(text: String): String {
        val raw = text.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun decodeGzipBase64(encoded: String): String? {
        val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        val input = ByteArrayInputStream(decoded)
        val out = ByteArrayOutputStream()
        return runCatching {
            GZIPInputStream(input).use { it.copyTo(out) }
            out.toString(Charsets.UTF_8.name())
        }.getOrNull()
    }

    private fun decodeBase64(encoded: String): String? {
        val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        return runCatching { decoded.toString(Charsets.UTF_8) }.getOrNull()
    }
}
