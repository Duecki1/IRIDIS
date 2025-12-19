package com.dueckis.kawaiiraweditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Base64
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.nio.FloatBuffer
import kotlin.math.roundToInt

data class NormalizedPoint(val x: Float, val y: Float)

class AiSubjectMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext
    private val u2net = U2NetOnnxSegmenter(context)

    suspend fun generateSubjectMaskDataUrl(
        previewBitmap: Bitmap,
        lassoPoints: List<NormalizedPoint>,
        paddingFraction: Float = 0.08f
    ): String = withContext(Dispatchers.Default) {
        require(lassoPoints.size >= 3) { "Need at least 3 points for lasso" }
        val width = previewBitmap.width.coerceAtLeast(1)
        val height = previewBitmap.height.coerceAtLeast(1)

        val minX = lassoPoints.minOf { it.x }.coerceIn(0f, 1f)
        val minY = lassoPoints.minOf { it.y }.coerceIn(0f, 1f)
        val maxX = lassoPoints.maxOf { it.x }.coerceIn(0f, 1f)
        val maxY = lassoPoints.maxOf { it.y }.coerceIn(0f, 1f)

        val padX = ((maxX - minX) * paddingFraction).coerceAtLeast(0.02f)
        val padY = ((maxY - minY) * paddingFraction).coerceAtLeast(0.02f)

        val left = ((minX - padX) * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val top = ((minY - padY) * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val right = ((maxX + padX) * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val bottom = ((maxY + padY) * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val cropRect = Rect(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))

        val crop = Bitmap.createBitmap(
            previewBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        ).copy(Bitmap.Config.ARGB_8888, false)

        val cropMask = u2net.segmentForegroundMask(crop) // 0..255, size cropW*cropH

        val lassoMask = rasterizeLassoMask(
            lassoPoints = lassoPoints,
            fullWidth = width,
            fullHeight = height,
            cropRect = cropRect
        )

        val fullMask = ByteArray(width * height)
        val cropW = cropRect.width()
        val cropH = cropRect.height()
        for (y in 0 until cropH) {
            val outRow = (cropRect.top + y) * width + cropRect.left
            val cropRow = y * cropW
            val lassoRow = y * cropW
            for (x in 0 until cropW) {
                val cropVal = cropMask[cropRow + x].toInt() and 0xFF
                val lassoVal = lassoMask[lassoRow + x].toInt() and 0xFF
                val finalVal = (cropVal * lassoVal / 255).coerceIn(0, 255)
                fullMask[outRow + x] = finalVal.toByte()
            }
        }

        val maskBitmap = grayscaleMaskToBitmap(fullMask, width, height)
        val pngBytes = ByteArrayOutputStream().use { out ->
            maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        "data:image/png;base64,$b64"
    }

    private fun rasterizeLassoMask(
        lassoPoints: List<NormalizedPoint>,
        fullWidth: Int,
        fullHeight: Int,
        cropRect: Rect
    ): ByteArray {
        val cropW = cropRect.width().coerceAtLeast(1)
        val cropH = cropRect.height().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        val maxX = (fullWidth - 1).coerceAtLeast(1)
        val maxY = (fullHeight - 1).coerceAtLeast(1)
        val path = Path()
        val first = lassoPoints.first()
        path.moveTo(first.x * maxX - cropRect.left, first.y * maxY - cropRect.top)
        lassoPoints.drop(1).forEach { p ->
            path.lineTo(p.x * maxX - cropRect.left, p.y * maxY - cropRect.top)
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawPath(path, paint)

        val pixels = IntArray(cropW * cropH)
        bmp.getPixels(pixels, 0, cropW, 0, 0, cropW, cropH)
        val mask = ByteArray(cropW * cropH)
        for (i in pixels.indices) {
            mask[i] = if ((pixels[i] and 0x00FFFFFF) != 0) 0xFF.toByte() else 0
        }
        return mask
    }

    private fun grayscaleMaskToBitmap(mask: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = mask[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

private class U2NetOnnxSegmenter(private val context: Context) {
    companion object {
        private const val INPUT_SIZE = 320
        private const val MODEL_URL =
            "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/u2net.onnx?download=true"
        private const val MODEL_SHA256 =
            "8d10d2f3bb75ae3b6d527c77944fc5e7dcd94b29809d47a739a7a728a912b491"
        private const val MODEL_FILENAME = "u2net.onnx"
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

    private suspend fun ensureModelOnDisk(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val dest = File(dir, MODEL_FILENAME)
        if (dest.exists() && sha256Hex(dest) == MODEL_SHA256) return@withContext dest
        if (dest.exists()) dest.delete()

        URL(MODEL_URL).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        check(sha256Hex(dest) == MODEL_SHA256) { "U2Net model hash mismatch after download" }
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
