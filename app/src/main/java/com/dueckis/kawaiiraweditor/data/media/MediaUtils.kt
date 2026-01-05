package com.dueckis.kawaiiraweditor.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.FileDescriptor
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun displayNameForUri(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "Imported RAW"
}

internal fun saveJpegToPictures(
    context: Context,
    jpegBytes: ByteArray,
    relativePath: String? = null
): Uri? {
    val filename = "IRIDIS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath?.trim().takeIf { !it.isNullOrBlank() } ?: "Pictures/IRIDIS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { it.write(jpegBytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}

internal fun saveBitmapToPictures(
    context: Context,
    bitmap: Bitmap,
    format: ExportImageFormat,
    quality: Int,
    relativePath: String? = null
): Uri? {
    val filename = "IRIDIS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.${format.extension}"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath?.trim().takeIf { !it.isNullOrBlank() } ?: "Pictures/IRIDIS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { out ->
                val compressFormat =
                    when (format) {
                        ExportImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
                        ExportImageFormat.Png -> Bitmap.CompressFormat.PNG
                        ExportImageFormat.Webp ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    }
                val ok = bitmap.compress(compressFormat, quality.coerceIn(1, 100), out)
                if (!ok) throw IllegalStateException("Bitmap.compress returned false")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}

internal fun saveMp4ToMovies(
    context: Context,
    displayName: String,
    relativePath: String? = null,
    durationMs: Long = 0L,
    write: (FileDescriptor) -> Boolean
): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.VideoColumns.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                relativePath?.takeIf { !it.isNullOrBlank() } ?: "Movies/IRIDIS"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    val pfd = resolver.openFileDescriptor(uri, "rw")
    if (pfd == null) {
        resolver.delete(uri, null, null)
        return null
    }

    val success = pfd.use { descriptor ->
        runCatching { write(descriptor.fileDescriptor) }.getOrElse {
            Log.e("MediaUtils", "Failed to write MP4", it)
            false
        }
    }

    if (!success) {
        resolver.delete(uri, null, null)
        return null
    }

    val updateValues = ContentValues().apply {
        if (durationMs > 0) {
            put(MediaStore.Video.VideoColumns.DURATION, durationMs)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
    }
    resolver.update(uri, updateValues, null, null)
    return uri
}

internal fun ByteArray.decodeToBitmap(): Bitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)

internal fun ByteArray.decodeJpegToBitmapSampled(
    targetLongEdgePx: Int?,
    dontEnlarge: Boolean
): Bitmap? {
    if (targetLongEdgePx == null) return decodeToBitmap()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    val srcLongEdge = maxOf(srcW, srcH).coerceAtLeast(1)
    val targetLongEdge = targetLongEdgePx.coerceAtLeast(1)
    val scale =
        if (dontEnlarge) minOf(1f, targetLongEdge.toFloat() / srcLongEdge.toFloat())
        else targetLongEdge.toFloat() / srcLongEdge.toFloat()

    val reqW = (srcW * scale).toInt().coerceAtLeast(1)
    val reqH = (srcH * scale).toInt().coerceAtLeast(1)

    val decodeOptions =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(srcW, srcH, reqW, reqH)
        }
    val decoded = BitmapFactory.decodeByteArray(this, 0, size, decodeOptions) ?: return null
    if (decoded.width == reqW && decoded.height == reqH) return decoded
    return Bitmap.createScaledBitmap(decoded, reqW, reqH, true).also { scaled ->
        if (scaled != decoded) decoded.recycle()
    }
}

private fun calculateInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
    var inSampleSize = 1
    if (srcH > reqH || srcW > reqW) {
        var halfH = srcH / 2
        var halfW = srcW / 2
        while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

internal fun parseRawMetadataForSearch(metadataJson: String?): Map<String, String> {
    val json = metadataJson?.takeIf { it.isNotBlank() } ?: return emptyMap()
    return runCatching {
        val obj = JSONObject(json)
        mapOf(
            "make" to obj.optString("make"),
            "model" to obj.optString("model"),
            "lens" to obj.optString("lens"),
            "iso" to obj.optString("iso"),
            "exposure" to obj.optString("exposureTime"),
            "aperture" to obj.optString("fNumber"),
            "focalLength" to obj.optString("focalLength"),
            "date" to obj.optString("dateTimeOriginal")
        ).filterValues { it.isNotBlank() && it != "null" }
    }.getOrDefault(emptyMap())
}
