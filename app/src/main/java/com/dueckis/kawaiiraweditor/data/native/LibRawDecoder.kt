package com.dueckis.kawaiiraweditor.data.native

import android.util.Log

object LibRawDecoder {
    private const val TAG = "LibRawDecoder"
    @Volatile private var loadError: Throwable? = null

    init {
        runCatching { System.loadLibrary("kawaiiraweditor") }.onFailure { err ->
            loadError = err
            Log.e(TAG, "Failed to load native library", err)
        }
    }

    fun isAvailable(): Boolean = loadError == null
    fun loadErrorOrNull(): Throwable? = loadError

    external fun createSession(rawData: ByteArray): Long
    external fun releaseSession(handle: Long)

    external fun decodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun lowlowdecodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun lowdecodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun decodeFullResFromSession(handle: Long, adjustmentsJson: String): ByteArray?

    external fun exportFromSession(
        handle: Long,
        adjustmentsJson: String,
        maxDimension: Int,
        lowRamMode: Boolean
    ): ByteArray?

    external fun getMetadataJsonFromSession(handle: Long): String?

    external fun decode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun lowlowdecode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun lowdecode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun decodeFullRes(rawData: ByteArray, adjustmentsJson: String): ByteArray?
}
