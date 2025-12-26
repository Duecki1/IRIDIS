package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class AiSceneMaskResult(
    val label: String,
    val maskDataUrl: String,
    val confidence: Float,
    val coverage: Float
)

class AiSceneMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext
    private val clip = ClipAutoTagger(context)

    suspend fun generateSceneMasks(
        previewBitmap: Bitmap,
        maxMasks: Int = 6,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<AiSceneMaskResult> = withContext(Dispatchers.Default) {
        fun progress(p: Float, msg: String) = onProgress?.invoke(p.coerceIn(0f, 1f), msg)

        val safe =
            if (previewBitmap.config != Bitmap.Config.ARGB_8888) previewBitmap.copy(Bitmap.Config.ARGB_8888, false)
            else previewBitmap

        progress(0.02f, "Tagging…")
        val tags =
            runCatching {
                clip.generateTags(safe) { p -> progress(0.02f + 0.18f * p, "Tagging…") }
            }.getOrDefault(emptyList())

        val candidateLabels = chooseCandidateLabels(tags)
        progress(0.22f, "Proposing regions…")

        val down = scaleDownKeepAspect(safe, maxDimension = 240)
        val w = down.width.coerceAtLeast(1)
        val h = down.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        down.getPixels(pixels, 0, w, 0, 0, w, h)

        val k = 7.coerceAtMost((w * h / 1500).coerceIn(3, 7))
        val assignments = kMeans(pixels, w, h, k, iterations = 8)

        val clusterCounts = IntArray(k)
        val clusterSumX = FloatArray(k)
        val clusterSumY = FloatArray(k)
        for (i in assignments.indices) {
            val c = assignments[i]
            clusterCounts[c]++
            val x = (i % w).toFloat() / (w - 1).coerceAtLeast(1)
            val y = (i / w).toFloat() / (h - 1).coerceAtLeast(1)
            clusterSumX[c] += x
            clusterSumY[c] += y
        }

        val clusterCentroidY = FloatArray(k) { idx ->
            val denom = clusterCounts[idx].coerceAtLeast(1).toFloat()
            (clusterSumY[idx] / denom).coerceIn(0f, 1f)
        }

        val minClusterFrac = 0.025f
        val clusterLabel = Array<String?>(k) { null }
        val clusterConf = FloatArray(k) { 0f }

        val usableClusters = (0 until k).filter { (clusterCounts[it].toFloat() / (w * h).toFloat()) >= minClusterFrac }
        for ((idx, clusterId) in usableClusters.withIndex()) {
            progress(0.22f + 0.48f * (idx.toFloat() / usableClusters.size.coerceAtLeast(1).toFloat()), "Classifying…")

            val masked = maskedClusterBitmap(pixels, assignments, w, h, clusterId)
            val scores =
                clip.scoreCandidates(masked, candidateLabels) { p ->
                    progress(0.70f + 0.20f * p, "Classifying…")
                }

            val centroidY = clusterCentroidY[clusterId]
            var bestLabel: String? = null
            var bestScore = 0f
            for ((label, score) in scores) {
                val adjusted = (score * labelPrior(label, centroidY)).coerceAtLeast(0f)
                if (adjusted > bestScore) {
                    bestScore = adjusted
                    bestLabel = label
                }
            }

            val acceptThreshold = 0.18f
            if (bestLabel != null && bestScore >= acceptThreshold) {
                clusterLabel[clusterId] = bestLabel
                clusterConf[clusterId] = bestScore
            }
        }

        progress(0.92f, "Building masks…")

        val labelCounts = HashMap<String, Int>()
        for (i in assignments.indices) {
            val label = clusterLabel[assignments[i]] ?: continue
            labelCounts[label] = (labelCounts[label] ?: 0) + 1
        }
        val labelsInUse =
            labelCounts.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(maxMasks)
        val results = mutableListOf<AiSceneMaskResult>()

        for (label in labelsInUse) {
            val maskSmall = ByteArray(w * h)
            var count = 0
            var confSum = 0f
            for (i in assignments.indices) {
                val c = assignments[i]
                if (clusterLabel[c] == label) {
                    maskSmall[i] = 0xFF.toByte()
                    count++
                    confSum += clusterConf[c]
                }
            }
            val coverage = count.toFloat() / (w * h).toFloat()
            if (coverage < 0.01f) continue

            val scaledMask = scaleMaskNearest(maskSmall, w, h, safe.width, safe.height)
            val dataUrl = maskToPngDataUrl(scaledMask, safe.width, safe.height)
            val confidence = (confSum / count.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            results += AiSceneMaskResult(label = label, maskDataUrl = dataUrl, confidence = confidence, coverage = coverage)
        }

        progress(1f, "Done")
        results.sortedByDescending { it.coverage }
    }

    private fun chooseCandidateLabels(tags: List<String>): List<String> {
        val normalized = tags.map { it.lowercase() }

        val out = linkedSetOf("sky", "grass", "tree", "water", "building", "mountain", "snow", "sand")
        fun seenAny(vararg needles: String): Boolean =
            normalized.any { t -> needles.any { n -> t.contains(n) } }

        if (seenAny("cloud", "sky", "sunset", "sunrise")) out += "sky"
        if (seenAny("grass", "meadow", "field", "lawn", "pasture", "vegetation", "green")) out += "grass"
        if (seenAny("tree", "forest", "woods")) out += "tree"
        if (seenAny("water", "sea", "ocean", "lake", "river", "beach")) out += "water"
        if (seenAny("building", "architecture", "house", "city", "tower")) out += "building"
        if (seenAny("mountain", "hill", "cliff")) out += "mountain"
        if (seenAny("snow", "ice", "winter")) out += "snow"
        if (seenAny("sand", "desert", "dune")) out += "sand"

        return out.toList()
    }

    private fun labelPrior(label: String, centroidY: Float): Float {
        val y = centroidY.coerceIn(0f, 1f)
        return when (label) {
            "sky" -> (0.35f + 0.95f * (1f - y)).coerceIn(0.2f, 1.3f)
            "grass" -> (0.35f + 0.95f * y).coerceIn(0.2f, 1.3f)
            else -> 1f
        }
    }

    private fun maskedClusterBitmap(
        pixels: IntArray,
        assignments: IntArray,
        width: Int,
        height: Int,
        clusterId: Int
    ): Bitmap {
        val out = IntArray(width * height)
        val gray = Color.rgb(127, 127, 127)
        for (i in out.indices) {
            out[i] = if (assignments[i] == clusterId) pixels[i] else gray
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
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

    private fun kMeans(pixels: IntArray, width: Int, height: Int, k: Int, iterations: Int): IntArray {
        val n = pixels.size
        val centers = Array(k) { FloatArray(5) }
        for (c in 0 until k) {
            val idx = (c.toLong() * (n - 1).toLong() / (k - 1).coerceAtLeast(1)).toInt()
            val p = pixels[idx]
            centers[c][0] = ((p shr 16) and 0xFF) / 255f
            centers[c][1] = ((p shr 8) and 0xFF) / 255f
            centers[c][2] = (p and 0xFF) / 255f
            centers[c][3] = (idx % width).toFloat() / (width - 1).coerceAtLeast(1)
            centers[c][4] = (idx / width).toFloat() / (height - 1).coerceAtLeast(1)
        }

        val assignments = IntArray(n)
        val sums = Array(k) { FloatArray(5) }
        val counts = IntArray(k)
        val spatialWeight = 0.35f

        repeat(iterations.coerceAtLeast(1)) {
            for (c in 0 until k) {
                java.util.Arrays.fill(sums[c], 0f)
                counts[c] = 0
            }

            for (i in 0 until n) {
                val p = pixels[i]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val x = (i % width).toFloat() / (width - 1).coerceAtLeast(1)
                val y = (i / width).toFloat() / (height - 1).coerceAtLeast(1)

                var best = 0
                var bestDist = Float.POSITIVE_INFINITY
                for (c in 0 until k) {
                    val dx0 = r - centers[c][0]
                    val dx1 = g - centers[c][1]
                    val dx2 = b - centers[c][2]
                    val dx3 = (x - centers[c][3]) * spatialWeight
                    val dx4 = (y - centers[c][4]) * spatialWeight
                    val dist = dx0 * dx0 + dx1 * dx1 + dx2 * dx2 + dx3 * dx3 + dx4 * dx4
                    if (dist < bestDist) {
                        bestDist = dist
                        best = c
                    }
                }
                assignments[i] = best
                sums[best][0] += r
                sums[best][1] += g
                sums[best][2] += b
                sums[best][3] += x
                sums[best][4] += y
                counts[best]++
            }

            for (c in 0 until k) {
                val cnt = counts[c].coerceAtLeast(1).toFloat()
                centers[c][0] = sums[c][0] / cnt
                centers[c][1] = sums[c][1] / cnt
                centers[c][2] = sums[c][2] / cnt
                centers[c][3] = sums[c][3] / cnt
                centers[c][4] = sums[c][4] / cnt
            }
        }

        return assignments
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
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }
}
