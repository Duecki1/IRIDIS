package com.dueckis.kawaiiraweditor.data.immich

import android.util.Base64
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object IridisSidecarDescription {
    private const val begin = "[KRWE:BEGIN]"
    private const val end = "[KRWE:END]"
    private const val defaultMaxEntries = 25

    internal data class IridisSidecarHistoryEntry(
        val editsJson: String,
        val updatedAtMs: Long,
        val revisionId: String,
        val parentRevisionId: String? = null
    )

    internal data class IridisSidecarHistoryParsed(
        val entries: List<IridisSidecarHistoryEntry>,
        val originalDescription: String
    )

    internal fun upsert(description: String?, editsJson: String, updatedAtMs: Long): String {
        return appendHistory(description, editsJson, updatedAtMs, maxEntries = defaultMaxEntries)
    }

    internal fun parse(description: String?): IridisSidecarDescriptionParsed? {
        val parsed = parseHistory(description) ?: return null
        val latest = parsed.entries.maxByOrNull { it.updatedAtMs } ?: return null
        return IridisSidecarDescriptionParsed(
            sidecar = IridisSidecar(editsJson = latest.editsJson, updatedAtMs = latest.updatedAtMs),
            originalDescription = parsed.originalDescription
        )
    }

    internal fun parseHistory(description: String?): IridisSidecarHistoryParsed? {
        val base = description.orEmpty()
        val blocks = extractBlocks(base)
        if (blocks.isEmpty()) return null
        val entries = blocks.mapNotNull { parseBlock(it) }
        if (entries.isEmpty()) return null
        val original = remove(base).trim()
        return IridisSidecarHistoryParsed(entries = entries, originalDescription = original)
    }

    internal fun appendHistory(
        description: String?,
        editsJson: String,
        updatedAtMs: Long,
        parentRevisionId: String? = null,
        maxEntries: Int = defaultMaxEntries
    ): String {
        val base = description.orEmpty()
        val current = parseHistory(base)?.entries.orEmpty()
        val revisionId = buildRevisionId(updatedAtMs, editsJson)
        val entry =
            IridisSidecarHistoryEntry(
                editsJson = editsJson,
                updatedAtMs = updatedAtMs.coerceAtLeast(0L),
                revisionId = revisionId,
                parentRevisionId = parentRevisionId
            )
        val filtered = current.filterNot { it.revisionId == revisionId }
        val merged = (filtered + entry).takeLast(maxEntries.coerceAtLeast(1))
        val stripped = remove(base).trimEnd()
        val blocks = merged.joinToString(separator = "\n\n") { buildBlock(it) }
        return if (stripped.isBlank()) blocks else stripped + "\n\n" + blocks
    }

    internal fun remove(description: String): String {
        var working = description
        var start = working.indexOf(begin)
        while (start >= 0) {
            val endIdx = working.indexOf(end, start + begin.length)
            if (endIdx < 0 || endIdx <= start) break
            val afterEnd = endIdx + end.length
            val before = working.substring(0, start)
            val after = working.substring(afterEnd)
            working = (before + after).trim()
            start = working.indexOf(begin)
        }
        return working
    }

    internal fun buildRevisionId(updatedAtMs: Long, editsJson: String): String {
        val safeUpdatedAt = updatedAtMs.coerceAtLeast(0L)
        val hash = sha256Hex(editsJson)
        return "$safeUpdatedAt-$hash"
    }

    private fun extractBlocks(description: String): List<String> {
        val blocks = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val start = description.indexOf(begin, searchFrom)
            if (start < 0) break
            val endIdx = description.indexOf(end, start + begin.length)
            if (endIdx < 0 || endIdx <= start) break
            val innerStart = start + begin.length
            blocks.add(description.substring(innerStart, endIdx).trim())
            searchFrom = endIdx + end.length
        }
        return blocks
    }

    private fun parseBlock(block: String): IridisSidecarHistoryEntry? {
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
        val revisionId = map["rev"].takeIf { !it.isNullOrBlank() } ?: buildRevisionId(updatedAtMs, editsJson)
        val parentId = map["parent"]?.takeIf { it.isNotBlank() }
        return IridisSidecarHistoryEntry(
            editsJson = editsJson,
            updatedAtMs = updatedAtMs,
            revisionId = revisionId,
            parentRevisionId = parentId
        )
    }

    private fun buildBlock(entry: IridisSidecarHistoryEntry): String {
        val encoded = encodeGzipBase64(entry.editsJson)
        return buildString {
            append(begin).append('\n')
            append("updatedAtMs=").append(entry.updatedAtMs.coerceAtLeast(0L)).append('\n')
            append("rev=").append(entry.revisionId).append('\n')
            entry.parentRevisionId?.let { append("parent=").append(it).append('\n') }
            append("encoding=gzip+base64").append('\n')
            append("edits=").append(encoded).append('\n')
            append(end)
        }
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

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
