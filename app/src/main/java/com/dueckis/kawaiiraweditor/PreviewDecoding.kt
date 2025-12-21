package com.dueckis.kawaiiraweditor

internal fun decodePreviewBytesForTagging(rawBytes: ByteArray, lowQualityPreviewEnabled: Boolean): ByteArray? {
    return if (lowQualityPreviewEnabled) {
        LibRawDecoder.lowdecode(rawBytes, "{}") ?: LibRawDecoder.lowlowdecode(rawBytes, "{}")
    } else {
        LibRawDecoder.decode(rawBytes, "{}")
    }
}

