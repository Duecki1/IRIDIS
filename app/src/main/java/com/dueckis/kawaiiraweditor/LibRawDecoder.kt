package com.dueckis.kawaiiraweditor

import android.graphics.Bitmap

object LibRawDecoder {
    init {
        System.loadLibrary("kawaiiraweditor")
    }

    external fun decode(rawData: ByteArray, exposure: Float, contrast: Float, whites: Float, blacks: Float): Bitmap?
    external fun decodeFullRes(rawData: ByteArray, exposure: Float, contrast: Float, whites: Float, blacks: Float): Bitmap?
}
