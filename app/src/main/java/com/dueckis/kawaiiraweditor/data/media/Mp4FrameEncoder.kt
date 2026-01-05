package com.dueckis.kawaiiraweditor.data.media

import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor

internal class Mp4FrameEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val outFd: FileDescriptor
) {
    fun encode(frames: Sequence<PreparedFrame>, frameDurationUs: Long): Boolean {
        val iterator = frames.iterator()
        if (!iterator.hasNext() || width <= 0 || height <= 0 || fps <= 0) return false

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val colorConfig = selectColorConfig(codec)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorConfig.format)
            setInteger(MediaFormat.KEY_BIT_RATE, calculateBitRate())
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val muxer = MediaMuxer(outFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var inputDone = false
        var outputDone = false
        var nextFrame: PreparedFrame? = iterator.next()
        var lastPresentationUs = 0L
        val useFlexibleInput = colorConfig.mode == InputColorMode.FLEXIBLE
        val nv12Scratch = if (colorConfig.mode == InputColorMode.NV12) ByteArray(width * height * 3 / 2) else null

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val frame = nextFrame
                        if (frame != null) {
                            if (useFlexibleInput) {
                                val imageQueued = codec.queueFrame(frame, inputIndex, width, height)
                                if (!imageQueued) {
                                    throw IllegalStateException("Codec reported flexible YUV input but did not expose writable images")
                                }
                            } else {
                                val buffer = codec.getInputBuffer(inputIndex) ?: continue
                                buffer.clear()
                                val payload = when (colorConfig.mode) {
                                    InputColorMode.I420 -> frame.data
                                    InputColorMode.NV12 -> frame.asNv12(width, height, nv12Scratch!!)
                                    InputColorMode.FLEXIBLE -> frame.data
                                }
                                buffer.put(payload)
                                codec.queueInputBuffer(inputIndex, 0, payload.size, frame.presentationTimeUs, 0)
                            }
                            lastPresentationUs = frame.presentationTimeUs
                            nextFrame = if (iterator.hasNext()) iterator.next() else null
                        } else {
                            val endTimeUs = lastPresentationUs + frameDurationUs
                            codec.queueInputBuffer(inputIndex, 0, 0, endTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                while (outputIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outputIndex)
                    if (encoded != null && bufferInfo.size > 0) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        if (trackIndex >= 0) {
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                    }
                    val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (endOfStream) {
                        outputDone = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (trackIndex != -1) throw IllegalStateException("Output format changed twice")
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: IllegalStateException) {
            }
            codec.release()

            try {
                muxer.stop()
            } catch (_: IllegalStateException) {
            }
            muxer.release()
        }

        return true
    }

    private fun MediaCodec.queueFrame(frame: PreparedFrame, bufferIndex: Int, frameWidth: Int, frameHeight: Int): Boolean {
        var image: Image? = null
        return try {
            image = getInputImage(bufferIndex) ?: return false
            frame.copyToImage(image, frameWidth, frameHeight)
            queueInputBuffer(bufferIndex, 0, 0, frame.presentationTimeUs, 0)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.nio.ReadOnlyBufferException) {
            false
        } finally {
            try {
                image?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun calculateBitRate(): Int {
        val base = width.toLong() * height.toLong() * fps.toLong() * 6L
        val min = 1_500_000L
        val max = 50_000_000L
        return base.coerceAtLeast(min).coerceAtMost(max).toInt()
    }

    private companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}

private enum class InputColorMode {
    I420,
    NV12,
    FLEXIBLE
}

private data class ColorConfig(val format: Int, val mode: InputColorMode)

private fun selectColorConfig(codec: MediaCodec): ColorConfig {
    val capabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var planar: Int? = null
    var semiPlanar: Int? = null
    var flexible: Int? = null
    var fallback: Int? = null

    for (format in capabilities.colorFormats) {
        when (format) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> planar = format
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
            0x7f000789, // OMX_QCOM_COLOR_FormatYUV420SemiPlanar
            0x7f420888, // QCOM tiled NV12
            0x7f000100 -> if (semiPlanar == null) semiPlanar = format
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible -> flexible = format
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface -> Unit
            else -> if (fallback == null) fallback = format
        }
        if (planar != null && semiPlanar != null) break
    }

    return when {
        planar != null -> ColorConfig(planar, InputColorMode.I420)
        semiPlanar != null -> ColorConfig(semiPlanar, InputColorMode.NV12)
        flexible != null -> ColorConfig(flexible, InputColorMode.FLEXIBLE)
        fallback != null -> ColorConfig(fallback, InputColorMode.FLEXIBLE)
        else -> ColorConfig(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible, InputColorMode.FLEXIBLE)
    }
}

private fun PreparedFrame.copyToImage(image: Image, frameWidth: Int, frameHeight: Int) {
    val planes = image.planes
    val ySize = frameWidth * frameHeight
    val uvWidth = frameWidth / 2
    val uvHeight = frameHeight / 2
    val uOffset = ySize
    val vOffset = ySize + uvWidth * uvHeight
    val crop: Rect = image.cropRect ?: Rect(0, 0, frameWidth, frameHeight)
    val yRowOffset = crop.top
    val yColOffset = crop.left
    val uvRowOffset = crop.top / 2
    val uvColOffset = crop.left / 2

    copyPlane(
        plane = planes[0],
        src = data,
        offset = 0,
        width = frameWidth,
        height = frameHeight,
        rowOffset = yRowOffset,
        colOffset = yColOffset
    )

    when (planes.size) {
        1 -> error("Unexpected single-plane YUV image")
        2 -> copyInterleavedPlane(
            plane = planes[1],
            src = data,
            uOffset = uOffset,
            vOffset = vOffset,
            width = uvWidth,
            height = uvHeight,
            rowOffset = uvRowOffset,
            colOffset = uvColOffset
        )
        else -> {
            copyPlane(
                plane = planes[1],
                src = data,
                offset = uOffset,
                width = uvWidth,
                height = uvHeight,
                rowOffset = uvRowOffset,
                colOffset = uvColOffset
            )

            copyPlane(
                plane = planes[2],
                src = data,
                offset = vOffset,
                width = uvWidth,
                height = uvHeight,
                rowOffset = uvRowOffset,
                colOffset = uvColOffset
            )
        }
    }

}

private fun copyPlane(
    plane: Image.Plane,
    src: ByteArray,
    offset: Int,
    width: Int,
    height: Int,
    rowOffset: Int,
    colOffset: Int
) {
    val buffer = plane.buffer.duplicate()
    buffer.position(0)
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride.coerceAtLeast(1)

    var srcIndex = offset
    for (row in 0 until height) {
        val rowBase = (row + rowOffset) * rowStride + colOffset * pixelStride
        if (pixelStride == 1) {
            buffer.position(rowBase)
            buffer.put(src, srcIndex, width)
            srcIndex += width
        } else {
            var columnBase = rowBase
            for (col in 0 until width) {
                buffer.put(columnBase, src[srcIndex])
                columnBase += pixelStride
                srcIndex += 1
            }
        }
    }
}

private fun PreparedFrame.asNv12(width: Int, height: Int, scratch: ByteArray): ByteArray {
    val ySize = width * height
    val uvWidth = width / 2
    val uvHeight = height / 2
    val uvSize = uvWidth * uvHeight
    val expected = ySize + uvSize * 2
    require(scratch.size >= expected) { "NV12 scratch buffer too small" }

    System.arraycopy(data, 0, scratch, 0, ySize)

    var dstIndex = ySize
    var uIndex = ySize
    var vIndex = ySize + uvSize
    repeat(uvSize) {
        scratch[dstIndex] = data[uIndex]
        scratch[dstIndex + 1] = data[vIndex]
        dstIndex += 2
        uIndex += 1
        vIndex += 1
    }
    return scratch
}

private fun copyInterleavedPlane(
    plane: Image.Plane,
    src: ByteArray,
    uOffset: Int,
    vOffset: Int,
    width: Int,
    height: Int,
    rowOffset: Int,
    colOffset: Int
) {
    val buffer = plane.buffer.duplicate()
    buffer.position(0)
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride.coerceAtLeast(2)

    var uIndex = uOffset
    var vIndex = vOffset
    for (row in 0 until height) {
        var rowBase = (row + rowOffset) * rowStride + colOffset * pixelStride
        for (col in 0 until width) {
            buffer.put(rowBase, src[uIndex])
            uIndex += 1
            buffer.put(rowBase + 1, src[vIndex])
            vIndex += 1
            rowBase += pixelStride
        }
    }
}
