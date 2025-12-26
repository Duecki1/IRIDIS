package com.dueckis.kawaiiraweditor.data.decoding

import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder

internal fun decodePreviewBytesForTagging(rawBytes: ByteArray, lowQualityPreviewEnabled: Boolean): ByteArray? {
    return if (lowQualityPreviewEnabled) {
        LibRawDecoder.lowdecode(rawBytes, "{}") ?: LibRawDecoder.lowlowdecode(rawBytes, "{}")
    } else {
        LibRawDecoder.decode(rawBytes, "{}")
    }
}
