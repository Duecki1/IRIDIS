package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.nio.FloatBuffer
import kotlin.math.roundToInt

internal class U2NetOnnxSegmenter(private val context: Context) {
    companion object {
        private const val INPUT_SIZE = 320
        internal const val MODEL_URL =
            "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/u2net.onnx?download=true"
        internal const val MODEL_SHA256 =
            "8d10d2f3bb75ae3b6d527c77944fc5e7dcd94b29809d47a739a7a728a912b491"
        internal const val MODEL_FILENAME = "u2net.onnx"
        private const val MODEL_NOTIFICATION_ID = 0xA11001
    }

    private val lock = Any()
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

    suspend fun segmentForegroundMask(bitmap: Bitmap): ByteArray {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val ortSession = ensureSession()
        val (square, resizedW, resizedH, pasteX, pasteY) = prepareSquareInput(safe, INPUT_SIZE)
        val inputTensor = createInputTensor(square)

        val inputName = ortSession.inputNames.first()
        val output: FloatArray = inputTensor.use { tensor ->
            ortSession.run(mapOf(inputName to tensor)).use { results ->
                val outTensor = results[0] as OnnxTensor
                val fb = outTensor.floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        require(output.size >= INPUT_SIZE * INPUT_SIZE) { "Unexpected U2Net output size: ${output.size}" }

        val squareMask = normalizeToByteMask(output)
        val cropped = cropByteMask(squareMask, INPUT_SIZE, INPUT_SIZE, pasteX, pasteY, resizedW, resizedH)
        return scaleByteMaskToBitmapSize(cropped, resizedW, resizedH, safe.width, safe.height)
    }

    private suspend fun ensureSession(): OrtSession = withContext(Dispatchers.IO) {
        val existing = synchronized(lock) { session }
        if (existing != null) return@withContext existing

        val modelFile = ensureModelOnDisk()
        val newEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val opts = OrtSession.SessionOptions()
        val created = newEnv.createSession(modelFile.absolutePath, opts)

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

    internal suspend fun ensureModelOnDisk(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val dest = File(dir, MODEL_FILENAME)
        if (dest.exists() && sha256Hex(dest) == MODEL_SHA256) return@withContext dest
        if (dest.exists()) dest.delete()

        ModelDownloadHelper.downloadFileWithProgress(
            context = context,
            url = MODEL_URL,
            finalDest = dest,
            expectedSha256 = MODEL_SHA256,
            notificationTitle = "Downloading subject AI model",
            toastStart = "Downloading subject AI model...",
            toastDone = "Subject AI model ready.",
            toastFailure = "Subject AI model download failed.",
            notificationId = MODEL_NOTIFICATION_ID
        )
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

    private data class SquareInput(
        val bitmap: Bitmap,
        val resizedW: Int,
        val resizedH: Int,
        val pasteX: Int,
        val pasteY: Int
    )

    private fun prepareSquareInput(src: Bitmap, size: Int): SquareInput {
        val srcW = src.width.coerceAtLeast(1)
        val srcH = src.height.coerceAtLeast(1)
        val scale = if (srcW >= srcH) size.toFloat() / srcW else size.toFloat() / srcH
        val resizedW = (srcW * scale).roundToInt().coerceIn(1, size)
        val resizedH = (srcH * scale).roundToInt().coerceIn(1, size)
        val resized = Bitmap.createScaledBitmap(src, resizedW, resizedH, true)

        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.BLACK)
        val pasteX = (size - resizedW) / 2
        val pasteY = (size - resizedH) / 2
        canvas.drawBitmap(resized, pasteX.toFloat(), pasteY.toFloat(), null)
        return SquareInput(square, resizedW, resizedH, pasteX, pasteY)
    }

    private fun createInputTensor(square: Bitmap): OnnxTensor {
        val ortEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        square.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val chw = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            val row = y * INPUT_SIZE
            for (x in 0 until INPUT_SIZE) {
                val p = pixels[row + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                val idx = row + x
                chw[idx] = (r - mean[0]) / std[0]
                chw[INPUT_SIZE * INPUT_SIZE + idx] = (g - mean[1]) / std[1]
                chw[2 * INPUT_SIZE * INPUT_SIZE + idx] = (b - mean[2]) / std[2]
            }
        }

        val fb = FloatBuffer.wrap(chw)
        return OnnxTensor.createTensor(ortEnv, fb, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
    }

    private fun normalizeToByteMask(output: FloatArray): ByteArray {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val v = output[i]
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = (max - min).takeIf { it > 1e-6f } ?: 1f
        val out = ByteArray(INPUT_SIZE * INPUT_SIZE)
        for (i in out.indices) {
            val norm = ((output[i] - min) / range * 255f).roundToInt().coerceIn(0, 255)
            out[i] = norm.toByte()
        }
        return out
    }

    private fun cropByteMask(
        mask: ByteArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        cropW: Int,
        cropH: Int
    ): ByteArray {
        val out = ByteArray(cropW * cropH)
        for (row in 0 until cropH) {
            val srcOff = (y + row) * width + x
            val dstOff = row * cropW
            System.arraycopy(mask, srcOff, out, dstOff, cropW)
        }
        return out
    }

    private fun scaleByteMaskToBitmapSize(mask: ByteArray, maskW: Int, maskH: Int, outW: Int, outH: Int): ByteArray {
        val pixels = IntArray(maskW * maskH)
        for (i in pixels.indices) {
            val v = mask[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(pixels, maskW, maskH, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(bmp, outW, outH, true)
        val scaledPixels = IntArray(outW * outH)
        scaled.getPixels(scaledPixels, 0, outW, 0, 0, outW, outH)
        val out = ByteArray(outW * outH)
        for (i in out.indices) {
            out[i] = ((scaledPixels[i] shr 16) and 0xFF).toByte()
        }
        return out
    }
}
