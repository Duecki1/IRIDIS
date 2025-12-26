package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AiSceneLabelSuggestion(
    val label: String,
    val confidence: Float
)

class AiSceneMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext
    private val clip = ClipAutoTagger(context)

    suspend fun suggestLabels(
        previewBitmap: Bitmap,
        maxLabels: Int = 8,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<AiSceneLabelSuggestion> = withContext(Dispatchers.Default) {
        fun progress(p: Float, msg: String) = onProgress?.invoke(p.coerceIn(0f, 1f), msg)

        val safe =
            if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false)
            else previewBitmap

        val candidates = baseCandidateLabels()
        progress(0.05f, "Analyzing…")
        val scores =
            runCatching {
                clip.scoreCandidates(safe, candidates) { p -> progress(0.05f + 0.85f * p, "Analyzing…") }
            }.getOrDefault(emptyList())

        val down = scaleDownKeepAspect(safe, maxDimension = 240)
        val w = down.width.coerceAtLeast(1)
        val h = down.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        down.getPixels(pixels, 0, w, 0, 0, w, h)

        fun available(label: String): Boolean {
            return when (label) {
                "sky" -> estimateSkyFraction(pixels, w, h) >= 0.03f
                "grass" -> estimateGrassFraction(pixels, w, h) >= 0.03f
                "water" -> estimateWaterFraction(pixels, w, h) >= 0.02f
                "snow" -> estimateSnowFraction(pixels, w, h) >= 0.01f
                else -> true
            }
        }

        val minConfidence = 0.18f
        val out =
            scores
                .filter { (label, conf) -> conf >= minConfidence && available(label) }
                .take(maxLabels)
                .map { (label, conf) -> AiSceneLabelSuggestion(label = label, confidence = conf.coerceIn(0f, 1f)) }

        progress(1f, "Done")
        out
    }

    suspend fun generateMaskDataUrl(
        previewBitmap: Bitmap,
        label: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): String? = withContext(Dispatchers.Default) {
        fun progress(p: Float, msg: String) = onProgress?.invoke(p.coerceIn(0f, 1f), msg)

        val safe =
            if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false)
            else previewBitmap

        val normalizedLabel = label.lowercase(Locale.US)
        progress(0.05f, "Generating ${normalizedLabel} mask…")
        val down = scaleDownKeepAspect(safe, maxDimension = 320)
        val w = down.width.coerceAtLeast(1)
        val h = down.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        down.getPixels(pixels, 0, w, 0, 0, w, h)

        val scores =
            when (normalizedLabel) {
                "sky" -> skyScores(pixels, w, h)
                "grass" -> grassScores(pixels, w, h)
                "water" -> waterScores(pixels, w, h)
                "snow" -> snowScores(pixels, w, h)
                else -> return@withContext null
            }

        val maxScore = scores.maxOrNull() ?: 0f
        if (maxScore < 0.20f) return@withContext null

        val threshold = max(0.25f, 0.55f * maxScore)
        val maskSmall = ByteArray(w * h)
        var nonZero = 0
        for (i in scores.indices) {
            val s = scores[i]
            if (s < threshold) continue
            val v = ((s / maxScore).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            if (v > 0) {
                maskSmall[i] = v.toByte()
                nonZero++
            }
        }

        val coverage = nonZero.toFloat() / (w * h).toFloat()
        if (coverage < 0.01f || coverage > 0.92f) return@withContext null

        val scaledMask = scaleMaskNearest(maskSmall, w, h, safe.width, safe.height)
        progress(0.95f, "Encoding…")
        val dataUrl = maskToPngDataUrl(scaledMask, safe.width, safe.height)
        progress(1f, "Done")
        dataUrl
    }

    private fun baseCandidateLabels(): List<String> = listOf("sky", "grass", "water", "snow")

    private fun skyScores(pixels: IntArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0f else y.toFloat() / (height - 1).toFloat()
            val topPrior = (1f - ny).coerceIn(0f, 1f).pow(1.25f)
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                val r = Color.red(p) / 255f
                val g = Color.green(p) / 255f
                val b = Color.blue(p) / 255f
                val hsv = FloatArray(3)
                Color.RGBToHSV((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt(), hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]

                val blueScore =
                    if (hue in 185f..260f && sat >= 0.12f && v >= 0.18f) {
                        val hueCenter = 220f
                        val hueWidth = 55f
                        val hueDist = abs(hue - hueCenter) / hueWidth
                        val hueFactor = (1f - hueDist).coerceIn(0f, 1f)
                        (0.35f + 0.65f * hueFactor) * sat.coerceIn(0f, 1f) * v.coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                val cloudScore =
                    if (sat <= 0.12f && v >= 0.78f) {
                        val whiteish = (min(min(r, g), b) / max(max(r, g), b).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                        0.55f * whiteish * v
                    } else {
                        0f
                    }

                out[row + x] = max(blueScore, cloudScore) * topPrior
            }
        }
        return out
    }

    private fun grassScores(pixels: IntArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0f else y.toFloat() / (height - 1).toFloat()
            val bottomPrior = ny.coerceIn(0f, 1f).pow(1.15f)
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                val hsv = FloatArray(3)
                Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]

                val score =
                    if (hue in 70f..170f && sat >= 0.14f && v >= 0.16f) {
                        val hueCenter = 115f
                        val hueWidth = 65f
                        val hueDist = abs(hue - hueCenter) / hueWidth
                        val hueFactor = (1f - hueDist).coerceIn(0f, 1f)
                        (0.35f + 0.65f * hueFactor) * sat * v
                    } else {
                        0f
                    }
                out[row + x] = score * (0.55f + 0.45f * bottomPrior)
            }
        }
        return out
    }

    private fun waterScores(pixels: IntArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0f else y.toFloat() / (height - 1).toFloat()
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                val hsv = FloatArray(3)
                Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]

                val score =
                    if (hue in 160f..255f && sat >= 0.10f && v >= 0.12f) {
                        val hueCenter = 200f
                        val hueWidth = 70f
                        val hueDist = abs(hue - hueCenter) / hueWidth
                        val hueFactor = (1f - hueDist).coerceIn(0f, 1f)
                        (0.35f + 0.65f * hueFactor) * sat * v
                    } else {
                        0f
                    }
                out[row + x] = score * (0.70f + 0.30f * ny)
            }
        }
        return out
    }

    private fun snowScores(pixels: IntArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                val r = Color.red(p) / 255f
                val g = Color.green(p) / 255f
                val b = Color.blue(p) / 255f
                val hsv = FloatArray(3)
                Color.RGBToHSV((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt(), hsv)
                val sat = hsv[1]
                val v = hsv[2]

                val whiteness = min(min(r, g), b)
                val channelSpread = (max(max(r, g), b) - min(min(r, g), b)).coerceIn(0f, 1f)
                val neutral = (1f - (channelSpread / 0.18f)).coerceIn(0f, 1f)
                val score =
                    if (sat <= 0.14f && v >= 0.80f) {
                        whiteness * v * neutral
                    } else {
                        0f
                    }
                out[row + x] = score
            }
        }
        return out
    }

    private fun estimateSkyFraction(pixels: IntArray, width: Int, height: Int): Float {
        val scores = skyScores(pixels, width, height)
        val m = scores.maxOrNull() ?: 0f
        if (m <= 0f) return 0f
        val t = 0.45f * m
        var count = 0
        for (s in scores) if (s >= t) count++
        return count.toFloat() / scores.size.toFloat()
    }

    private fun estimateGrassFraction(pixels: IntArray, width: Int, height: Int): Float {
        val scores = grassScores(pixels, width, height)
        val m = scores.maxOrNull() ?: 0f
        if (m <= 0f) return 0f
        val t = 0.45f * m
        var count = 0
        for (s in scores) if (s >= t) count++
        return count.toFloat() / scores.size.toFloat()
    }

    private fun estimateWaterFraction(pixels: IntArray, width: Int, height: Int): Float {
        val scores = waterScores(pixels, width, height)
        val m = scores.maxOrNull() ?: 0f
        if (m <= 0f) return 0f
        val t = 0.45f * m
        var count = 0
        for (s in scores) if (s >= t) count++
        return count.toFloat() / scores.size.toFloat()
    }

    private fun estimateSnowFraction(pixels: IntArray, width: Int, height: Int): Float {
        val scores = snowScores(pixels, width, height)
        val m = scores.maxOrNull() ?: 0f
        if (m <= 0f) return 0f
        val t = 0.55f * m
        var count = 0
        for (s in scores) if (s >= t) count++
        return count.toFloat() / scores.size.toFloat()
    }

    private fun scaleDownKeepAspect(src: Bitmap, maxDimension: Int): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val maxDim = maxOf(w, h)
        if (maxDim <= maxDimension) return src
        val scale = maxDimension.toFloat() / maxDim.toFloat()
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun scaleMaskNearest(src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
        val out = ByteArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y.toLong() * srcH.toLong() / dstH.toLong()).toInt().coerceIn(0, srcH - 1)
            val dstRow = y * dstW
            val srcRow = sy * srcW
            for (x in 0 until dstW) {
                val sx = (x.toLong() * srcW.toLong() / dstW.toLong()).toInt().coerceIn(0, srcW - 1)
                out[dstRow + x] = src[srcRow + sx]
            }
        }
        return out
    }

    private fun maskToPngDataUrl(mask: ByteArray, width: Int, height: Int): String {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = mask[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val pngBytes =
            ByteArrayOutputStream().use { outStream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.toByteArray()
            }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }
}

