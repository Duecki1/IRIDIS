package com.dueckis.kawaiiraweditor.data.media

internal enum class ExportImageFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    Jpeg("JPEG", "jpg", "image/jpeg"),
    Png("PNG", "png", "image/png"),
    Webp("WebP", "webp", "image/webp")
}
