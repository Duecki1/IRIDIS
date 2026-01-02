package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal class AiEnvironmentMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext

    private data class CategoryScore(val category: AiEnvironmentCategory, val fraction: Float)
    private data class LabelSource(val segmenter: SegFormerOnnxSegmenter, val labelIds: Set<Int>)

    private val cityscapes = SegFormerOnnxSegmenter(
        context = context,
        modelUrl = "https://huggingface.co/Xenova/segformer-b2-finetuned-cityscapes-1024-1024/resolve/main/onnx/model_fp16.onnx?download=true",
        modelSha256 = "323d74b5e4150b93d2682059c40ce227a6effeabc9a7926776b6e8499c2b2761",
        modelFilename = "segformer_cityscapes_b2_fp16.onnx",
        inputSize = 512
    )

    private val ade20k = SegFormerOnnxSegmenter(
        context = context,
        modelUrl = "https://huggingface.co/Xenova/segformer-b2-finetuned-ade-512-512/resolve/main/onnx/model_fp16.onnx?download=true",
        modelSha256 = "79209c2663c66b35af907b9bfeab79570d396ab7c4d0c22a54814288538a8d1b",
        modelFilename = "segformer_ade20k_b2_fp16.onnx",
        inputSize = 256
    )

    suspend fun generateEnvironmentMaskDataUrl(
        previewBitmap: Bitmap,
        category: AiEnvironmentCategory
    ): String = withContext(Dispatchers.Default) {
        val safe = if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false) else previewBitmap

        val sources = labelSourcesFor(category)
        require(sources.isNotEmpty()) { "No label mapping for ${category.id}" }
        val maskBytes = ByteArray(safe.width * safe.height)
        var anySucceeded = false
        var lastError: Throwable? = null
        for ((segmenter, labelIds) in sources) {
            val m =
                runCatching { segmenter.segmentSoftMask(safe, labelIds) }
                    .onFailure { lastError = it }
                    .getOrNull()
                    ?: continue
            anySucceeded = true
            for (i in maskBytes.indices) {
                val v = m[i].toInt() and 0xFF
                if (v > (maskBytes[i].toInt() and 0xFF)) maskBytes[i] = v.toByte()
            }
        }
        if (!anySucceeded) throw (lastError ?: IllegalStateException("Segmentation failed"))

        val maskBitmap = grayscaleMaskToBitmap(maskBytes, safe.width, safe.height)
        val pngBytes = ByteArrayOutputStream().use { out ->
            maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        "data:image/png;base64,$b64"
    }

    suspend fun detectAvailableCategories(previewBitmap: Bitmap): List<AiEnvironmentCategory> = withContext(Dispatchers.Default) {
        val safe = if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false) else previewBitmap
        val city = cityscapes.segmentLabelMap(safe)
        val ade = ade20k.segmentLabelMap(safe)

        fun fraction(labels: IntArray, labelIds: Set<Int>): Float {
            if (labels.isEmpty()) return 0f
            var hit = 0
            for (v in labels) if (v in labelIds) hit++
            return hit.toFloat() / labels.size.toFloat()
        }

        fun thresholdFor(category: AiEnvironmentCategory): Float {
            return when (category) {
                AiEnvironmentCategory.Floor -> 0.005f
                AiEnvironmentCategory.Sky -> 0.02f
                AiEnvironmentCategory.Water -> 0.004f
                AiEnvironmentCategory.People -> 0.002f
                AiEnvironmentCategory.Animals,
                AiEnvironmentCategory.Dogs,
                AiEnvironmentCategory.Cats -> 0.001f
                AiEnvironmentCategory.Plants -> 0.01f
                AiEnvironmentCategory.Food -> 0.001f
                AiEnvironmentCategory.Vehicles,
                AiEnvironmentCategory.Cars,
                AiEnvironmentCategory.Trains,
                AiEnvironmentCategory.Planes -> 0.001f
            }
        }

        val scores = mutableListOf<CategoryScore>()
        for (cat in AiEnvironmentCategory.entries) {
            val sources = labelSourcesFor(cat)
            if (sources.isEmpty()) continue
            var f = 0f
            for ((segmenter, labelIds) in sources) {
                val src = if (segmenter === cityscapes) city.labels else ade.labels
                f = maxOf(f, fraction(src, labelIds))
            }
            if (f >= thresholdFor(cat)) scores += CategoryScore(cat, f)
        }

        scores.sortedByDescending { it.fraction }.map { it.category }
    }

    private fun labelSourcesFor(category: AiEnvironmentCategory): List<LabelSource> {
        fun city(vararg ids: Int) = LabelSource(cityscapes, ids.toSet())
        fun ade(vararg ids: Int) = LabelSource(ade20k, ids.toSet())

        return when (category) {
            AiEnvironmentCategory.Sky -> listOf(city(10))
            AiEnvironmentCategory.Water -> listOf(ade(21, 26, 60, 128, 113))
            AiEnvironmentCategory.Floor -> listOf(ade(3))

            AiEnvironmentCategory.People -> listOf(city(11, 12), ade(12))
            AiEnvironmentCategory.Animals -> listOf(ade(126))
            AiEnvironmentCategory.Dogs -> listOf(ade(126))
            AiEnvironmentCategory.Cats -> listOf(ade(126))
            AiEnvironmentCategory.Plants -> listOf(city(8), ade(4, 9, 17, 66, 72))
            AiEnvironmentCategory.Food -> listOf(ade(120))

            AiEnvironmentCategory.Vehicles -> listOf(city(13, 14, 15, 16, 17, 18), ade(20, 80, 83, 90, 76, 103, 102))
            AiEnvironmentCategory.Cars -> listOf(city(13), ade(20))
            AiEnvironmentCategory.Trains -> listOf(city(16))
            AiEnvironmentCategory.Planes -> listOf(ade(90))
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
