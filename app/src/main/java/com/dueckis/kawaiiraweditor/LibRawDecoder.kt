package com.dueckis.kawaiiraweditor

import android.graphics.Bitmap

object LibRawDecoder {
    init {
        System.loadLibrary("kawaiiraweditor")
    }

    external fun decode(rawData: ByteArray, exposure: Float): Bitmap?
}