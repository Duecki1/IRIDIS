package com.dueckis.kawaiiraweditor.data.decoding

import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder

internal fun decodePreviewBytesForTagging(rawBytes: ByteArray, lowQualityPreviewEnabled: Boolean): ByteArray? {
    val previewAdjustmentsJson = AdjustmentState().toJson()
    return if (lowQualityPreviewEnabled) {
        LibRawDecoder.lowdecode(rawBytes, previewAdjustmentsJson)
            ?: LibRawDecoder.lowlowdecode(rawBytes, previewAdjustmentsJson)
    } else {
        LibRawDecoder.decode(rawBytes, previewAdjustmentsJson)
    }
}
