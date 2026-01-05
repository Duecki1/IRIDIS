package com.dueckis.kawaiiraweditor.data.media

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
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
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

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        val frame = nextFrame
                        if (frame != null) {
                            buffer.clear()
                            buffer.put(frame.data)
                            codec.queueInputBuffer(inputIndex, 0, frame.data.size, frame.presentationTimeUs, 0)
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

    private fun calculateBitRate(): Int {
        val base = width.toLong() * height.toLong() * fps.toLong() * 6L
        val min = 1_500_000L
        val max = 25_000_000L
        return base.coerceAtLeast(min).coerceAtMost(max).toInt()
    }

    private companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
