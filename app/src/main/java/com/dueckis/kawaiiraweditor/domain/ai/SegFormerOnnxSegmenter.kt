package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

internal class SegFormerOnnxSegmenter(
    private val context: Context,
    private val modelUrl: String,
    private val modelSha256: String,
    private val modelFilename: String,
    private val inputSize: Int
) {
    private val lock = Any()
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

    suspend fun segmentLabelMap(bitmap: Bitmap): SegFormerOutput {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val logitsOut = segmentLogits(safe)
        return SegFormerOutput(
            labels = argmaxLabels(logitsOut.logits, logitsOut.numClasses, logitsOut.height, logitsOut.width, logitsOut.layout),
            width = logitsOut.width,
            height = logitsOut.height
        )
    }

    suspend fun segmentBinaryMask(bitmap: Bitmap, labelIds: Set<Int>): ByteArray {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val segOut = segmentLabelMap(safe)

        val maskSmall = ByteArray(segOut.labels.size)
        for (i in segOut.labels.indices) {
            maskSmall[i] = if (segOut.labels[i] in labelIds) 0xFF.toByte() else 0
        }

        val maskPixels = IntArray(segOut.width * segOut.height) { i ->
            val v = maskSmall[i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val maskBitmap = Bitmap.createBitmap(maskPixels, segOut.width, segOut.height, Bitmap.Config.ARGB_8888)
        val scaled = if (safe.width == segOut.width && safe.height == segOut.height) maskBitmap else Bitmap.createScaledBitmap(maskBitmap, safe.width, safe.height, true)

        val scaledPixels = IntArray(safe.width * safe.height)
        scaled.getPixels(scaledPixels, 0, safe.width, 0, 0, safe.width, safe.height)
        val out = ByteArray(safe.width * safe.height)
        for (i in out.indices) {
            out[i] = ((scaledPixels[i] shr 16) and 0xFF).toByte()
        }
        return out
    }

    suspend fun segmentSoftMask(bitmap: Bitmap, labelIds: Set<Int>): ByteArray {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val logitsOut = segmentLogits(safe)
        val maskSmall = softmaxMaskU8(logitsOut.logits, logitsOut.numClasses, logitsOut.height, logitsOut.width, logitsOut.layout, labelIds)

        val maskPixels = IntArray(logitsOut.width * logitsOut.height) { i ->
            val v = maskSmall[i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val maskBitmap = Bitmap.createBitmap(maskPixels, logitsOut.width, logitsOut.height, Bitmap.Config.ARGB_8888)
        val scaled = if (safe.width == logitsOut.width && safe.height == logitsOut.height) maskBitmap else Bitmap.createScaledBitmap(maskBitmap, safe.width, safe.height, true)

        val scaledPixels = IntArray(safe.width * safe.height)
        scaled.getPixels(scaledPixels, 0, safe.width, 0, 0, safe.width, safe.height)
        val out = ByteArray(safe.width * safe.height)
        for (i in out.indices) {
            out[i] = ((scaledPixels[i] shr 16) and 0xFF).toByte()
        }
        return out
    }

    internal data class SegFormerOutput(
        val labels: IntArray,
        val width: Int,
        val height: Int
    )

    private enum class SegFormerLayout { NCHW, NHWC }

    private data class SegFormerLogits(
        val logits: FloatArray,
        val numClasses: Int,
        val width: Int,
        val height: Int,
        val layout: SegFormerLayout
    )

    private suspend fun segmentLogits(bitmap: Bitmap): SegFormerLogits {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val ortSession = ensureSession()

        val resized = Bitmap.createScaledBitmap(safe, inputSize, inputSize, true)
        val inputTensor = createInputTensor(resized)

        val inputName = ortSession.inputNames.first()
        return inputTensor.use { tensor ->
            ortSession.run(mapOf(inputName to tensor)).use { results ->
                val outTensor = results[0] as OnnxTensor
                val shape = outTensor.info.shape
                require(shape.size >= 4) { "Unexpected SegFormer output shape: ${shape.contentToString()}" }

                val fb = outTensor.floatBuffer
                val logits = FloatArray(fb.remaining()).also { fb.get(it) }

                val axis1 = shape[1].toInt()
                val axis2 = shape[2].toInt()
                val axis3 = shape[3].toInt()

                val knownClassCounts = setOf(19, 150)
                val layout: SegFormerLayout
                val numClasses: Int
                val outH: Int
                val outW: Int
                when {
                    axis1 in knownClassCounts -> {
                        layout = SegFormerLayout.NCHW
                        numClasses = axis1
                        outH = axis2
                        outW = axis3
                    }
                    axis3 in knownClassCounts -> {
                        layout = SegFormerLayout.NHWC
                        numClasses = axis3
                        outH = axis1
                        outW = axis2
                    }
                    axis3 in 2..256 && axis1 > axis3 && axis2 > axis3 -> {
                        layout = SegFormerLayout.NHWC
                        numClasses = axis3
                        outH = axis1
                        outW = axis2
                    }
                    else -> {
                        layout = SegFormerLayout.NCHW
                        numClasses = axis1
                        outH = axis2
                        outW = axis3
                    }
                }

                SegFormerLogits(logits = logits, numClasses = numClasses, width = outW, height = outH, layout = layout)
            }
        }
    }

    private fun softmaxMaskU8(
        logits: FloatArray,
        numClasses: Int,
        height: Int,
        width: Int,
        layout: SegFormerLayout,
        labelIds: Set<Int>
    ): ByteArray {
        val hw = height * width
        val out = ByteArray(hw)
        if (hw == 0 || numClasses <= 0 || labelIds.isEmpty()) return out

        when (layout) {
            SegFormerLayout.NCHW -> {
                for (i in 0 until hw) {
                    var max = Float.NEGATIVE_INFINITY
                    for (c in 0 until numClasses) {
                        val v = logits[c * hw + i]
                        if (v > max) max = v
                    }
                    var denom = 0.0
                    var num = 0.0
                    for (c in 0 until numClasses) {
                        val e = kotlin.math.exp((logits[c * hw + i] - max).toDouble())
                        denom += e
                        if (c in labelIds) num += e
                    }
                    val p = if (denom <= 0.0) 0.0 else (num / denom).coerceIn(0.0, 1.0)
                    out[i] = (p * 255.0).roundToInt().coerceIn(0, 255).toByte()
                }
            }
            SegFormerLayout.NHWC -> {
                for (i in 0 until hw) {
                    val base = i * numClasses
                    var max = Float.NEGATIVE_INFINITY
                    for (c in 0 until numClasses) {
                        val v = logits[base + c]
                        if (v > max) max = v
                    }
                    var denom = 0.0
                    var num = 0.0
                    for (c in 0 until numClasses) {
                        val e = kotlin.math.exp((logits[base + c] - max).toDouble())
                        denom += e
                        if (c in labelIds) num += e
                    }
                    val p = if (denom <= 0.0) 0.0 else (num / denom).coerceIn(0.0, 1.0)
                    out[i] = (p * 255.0).roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }

    private fun argmaxLabels(logits: FloatArray, numClasses: Int, height: Int, width: Int, layout: SegFormerLayout): IntArray {
        val hw = height * width
        require(logits.size >= numClasses * hw) { "Unexpected SegFormer output size: ${logits.size}" }
        val labels = IntArray(hw)
        when (layout) {
            SegFormerLayout.NCHW -> {
                for (i in 0 until hw) {
                    var bestClass = 0
                    var bestVal = logits[i]
                    var c = 1
                    while (c < numClasses) {
                        val v = logits[c * hw + i]
                        if (v > bestVal) {
                            bestVal = v
                            bestClass = c
                        }
                        c++
                    }
                    labels[i] = bestClass
                }
            }
            SegFormerLayout.NHWC -> {
                for (i in 0 until hw) {
                    val base = i * numClasses
                    var bestClass = 0
                    var bestVal = logits[base]
                    var c = 1
                    while (c < numClasses) {
                        val v = logits[base + c]
                        if (v > bestVal) {
                            bestVal = v
                            bestClass = c
                        }
                        c++
                    }
                    labels[i] = bestClass
                }
            }
        }
        return labels
    }

    private suspend fun ensureSession(): OrtSession = withContext(Dispatchers.IO) {
        val existing = synchronized(lock) { session }
        if (existing != null) return@withContext existing

        val modelFile = ensureModelOnDisk()
        val newEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        fun createWith(level: OrtSession.SessionOptions.OptLevel): OrtSession {
            val opts = OrtSession.SessionOptions()
            try {
                opts.setOptimizationLevel(level)
                opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                opts.setIntraOpNumThreads(1)
                opts.setInterOpNumThreads(1)
                return newEnv.createSession(modelFile.absolutePath, opts)
            } finally {
                opts.close()
            }
        }

        val created =
            runCatching { createWith(OrtSession.SessionOptions.OptLevel.BASIC_OPT) }
                .getOrElse { createWith(OrtSession.SessionOptions.OptLevel.NO_OPT) }

        synchronized(lock) {
            val race = session
            if (race != null) {
                created.close()
                return@withContext race
            }
            session = created
            created
        }
    }

    private suspend fun ensureModelOnDisk(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val dest = File(dir, modelFilename)
        if (dest.exists() && sha256Hex(dest) == modelSha256) return@withContext dest
        if (dest.exists()) dest.delete()

        URL(modelUrl).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        check(sha256Hex(dest) == modelSha256) { "SegFormer model hash mismatch after download ($modelFilename)" }
        dest
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createInputTensor(bitmap: Bitmap): OnnxTensor {
        val ortEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val chw = FloatArray(3 * w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = pixels[row + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                val idx = row + x
                chw[idx] = (r - mean[0]) / std[0]
                chw[w * h + idx] = (g - mean[1]) / std[1]
                chw[2 * w * h + idx] = (b - mean[2]) / std[2]
            }
        }

        val fb = FloatBuffer.wrap(chw)
        return OnnxTensor.createTensor(ortEnv, fb, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }
}
