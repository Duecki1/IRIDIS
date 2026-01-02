package com.dueckis.kawaiiraweditor.domain

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

object HistogramUtils {
    fun calculateHistogram(bitmap: Bitmap): HistogramData {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val redCounts = IntArray(256)
        val greenCounts = IntArray(256)
        val blueCounts = IntArray(256)
        val lumaCounts = IntArray(256)

        for (p in pixels) {
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            redCounts[r]++
            greenCounts[g]++
            blueCounts[b]++
            val lumaVal = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)
            lumaCounts[lumaVal]++
        }

        val red = FloatArray(256) { idx -> redCounts[idx].toFloat() }
        val green = FloatArray(256) { idx -> greenCounts[idx].toFloat() }
        val blue = FloatArray(256) { idx -> blueCounts[idx].toFloat() }
        val luma = FloatArray(256) { idx -> lumaCounts[idx].toFloat() }

        val sigma = 2.5f
        applyGaussianSmoothing(red, sigma)
        applyGaussianSmoothing(green, sigma)
        applyGaussianSmoothing(blue, sigma)
        applyGaussianSmoothing(luma, sigma)

        normalizeHistogramRange(red, 0.99f)
        normalizeHistogramRange(green, 0.99f)
        normalizeHistogramRange(blue, 0.99f)
        normalizeHistogramRange(luma, 0.99f)

        return HistogramData(red = red, green = green, blue = blue, luma = luma)
    }

    fun applyGaussianSmoothing(histogram: FloatArray, sigma: Float) {
        if (sigma <= 0f) return
        val kernelRadius = ceil(sigma * 3f).toInt()
        if (kernelRadius <= 0 || kernelRadius >= histogram.size) return

        val kernelSize = 2 * kernelRadius + 1
        val kernel = FloatArray(kernelSize)
        var kernelSum = 0f

        val twoSigmaSq = 2f * sigma * sigma
        for (i in 0 until kernelSize) {
            val x = (i - kernelRadius).toFloat()
            val v = exp((-x * x / twoSigmaSq).toDouble()).toFloat()
            kernel[i] = v
            kernelSum += v
        }

        if (kernelSum > 0f) {
            for (i in kernel.indices) {
                kernel[i] /= kernelSum
            }
        }

        val original = histogram.copyOf()
        val len = histogram.size
        for (i in 0 until len) {
            var smoothed = 0f
            for (k in 0 until kernelSize) {
                val offset = k - kernelRadius
                val sampleIndex = (i + offset).coerceIn(0, len - 1)
                smoothed += original[sampleIndex] * kernel[k]
            }
            histogram[i] = smoothed
        }
    }

    fun normalizeHistogramRange(histogram: FloatArray, percentileClip: Float) {
        if (histogram.isEmpty()) return
        val sorted = histogram.copyOf()
        sorted.sort()
        val clipIndex = ((sorted.size - 1) * percentileClip).roundToInt().coerceIn(0, sorted.size - 1)
        val maxVal = sorted[clipIndex]

        if (maxVal > 1e-6f) {
            val scale = 1f / maxVal
            for (i in histogram.indices) {
                histogram[i] = (histogram[i] * scale).coerceAtMost(1f)
            }
        } else {
            for (i in histogram.indices) {
                histogram[i] = 0f
            }
        }
    }
}
