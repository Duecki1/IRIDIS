package com.dueckis.kawaiiraweditor

object LibRawDecoder {
    init {
        System.loadLibrary("kawaiiraweditor")
    }

    external fun createSession(rawData: ByteArray): Long
    external fun releaseSession(handle: Long)

    external fun decodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun lowlowdecodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun lowdecodeFromSession(handle: Long, adjustmentsJson: String): ByteArray?
    external fun decodeFullResFromSession(handle: Long, adjustmentsJson: String): ByteArray?

    external fun getMetadataJsonFromSession(handle: Long): String?

    external fun decode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun lowlowdecode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun lowdecode(rawData: ByteArray, adjustmentsJson: String): ByteArray?
    external fun decodeFullRes(rawData: ByteArray, adjustmentsJson: String): ByteArray?
}
