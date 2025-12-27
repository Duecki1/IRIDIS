package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest

internal class AiEnvironmentMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext

    private val cityscapes = SegFormerOnnxSegmenter(
        context = context,
        modelUrl = "https://huggingface.co/Xenova/segformer-b0-finetuned-cityscapes-768-768/resolve/main/onnx/model_quantized.onnx?download=true",
        modelSha256 = "e90dc4112c066066110c5aac955990427095a27a88c7dbfd4fcc7d1bbf147147",
        modelFilename = "segformer_cityscapes_quant.onnx",
        inputSize = 512
    )

    private val ade20k = SegFormerOnnxSegmenter(
        context = context,
        modelUrl = "https://huggingface.co/Xenova/segformer-b0-finetuned-ade-512-512/resolve/main/onnx/model_quantized.onnx?download=true",
        modelSha256 = "9a98d6daf3d926869ab8cc4c2ed7374a2bc23b889bb7ca3b0915d15e3c4756bb",
        modelFilename = "segformer_ade20k_quant.onnx",
        inputSize = 512
    )

    suspend fun generateEnvironmentMaskDataUrl(
        previewBitmap: Bitmap,
        category: AiEnvironmentCategory
    ): String = withContext(Dispatchers.Default) {
        val safe = if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false) else previewBitmap

        val (segmenter, labelIds) = modelAndLabelsFor(category)
        val maskBytes = segmenter.segmentBinaryMask(safe, labelIds)

        val maskBitmap = grayscaleMaskToBitmap(maskBytes, safe.width, safe.height)
        val pngBytes = ByteArrayOutputStream().use { out ->
            maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        "data:image/png;base64,$b64"
    }

    private fun modelAndLabelsFor(category: AiEnvironmentCategory): Pair<SegFormerOnnxSegmenter, Set<Int>> {
        return when (category) {
            AiEnvironmentCategory.Planes ->
                ade20k to setOf(90) // airplane

            AiEnvironmentCategory.OldBuildings ->
                ade20k to setOf(
                    0, // wall
                    1, // building
                    25, // house
                    48, // skyscraper
                    61, // bridge
                    79, // hovel
                    84 // tower
                )

            AiEnvironmentCategory.Sky -> cityscapes to setOf(10)
            AiEnvironmentCategory.Ground -> cityscapes to setOf(0, 1, 8, 9) // road, sidewalk, vegetation, terrain
            AiEnvironmentCategory.Architecture -> cityscapes to setOf(2, 3, 4, 5, 6, 7) // building, wall, fence, pole, traffic light/sign
            AiEnvironmentCategory.Humans -> cityscapes to setOf(11, 12) // person, rider
            AiEnvironmentCategory.Cars -> cityscapes to setOf(13)
            AiEnvironmentCategory.Trains -> cityscapes to setOf(16)
            AiEnvironmentCategory.Vehicles -> cityscapes to setOf(13, 14, 15, 16, 17, 18) // car, truck, bus, train, motorcycle, bicycle
        }
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

private class SegFormerOnnxSegmenter(
    private val context: Context,
    private val modelUrl: String,
    private val modelSha256: String,
    private val modelFilename: String,
    private val inputSize: Int
) {
    private val lock = Any()
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

    suspend fun segmentBinaryMask(bitmap: Bitmap, labelIds: Set<Int>): ByteArray {
        val safe = if (bitmap.config != Bitmap.Config.ARGB_8888) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val ortSession = ensureSession()

        val resized = Bitmap.createScaledBitmap(safe, inputSize, inputSize, true)
        val inputTensor = createInputTensor(resized)

        val inputName = ortSession.inputNames.first()
        val segOut: SegFormerOutput = inputTensor.use { tensor ->
            ortSession.run(mapOf(inputName to tensor)).use { results ->
                val outTensor = results[0] as OnnxTensor
                val shape = outTensor.info.shape
                require(shape.size >= 4) { "Unexpected SegFormer output shape: ${shape.contentToString()}" }
                val numClasses = shape[1].toInt()
                val outH = shape[2].toInt()
                val outW = shape[3].toInt()

                val fb = outTensor.floatBuffer
                val logits = FloatArray(fb.remaining()).also { fb.get(it) }
                SegFormerOutput(labels = argmaxLabels(logits, numClasses, outH, outW), width = outW, height = outH)
            }
        }

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

    private data class SegFormerOutput(
        val labels: IntArray,
        val width: Int,
        val height: Int
    )

    private fun argmaxLabels(logits: FloatArray, numClasses: Int, height: Int, width: Int): IntArray {
        val hw = height * width
        require(logits.size >= numClasses * hw) { "Unexpected SegFormer output size: ${logits.size}" }
        val labels = IntArray(hw)
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
        return labels
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
