package com.dueckis.kawaiiraweditor.data.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlin.math.roundToInt

internal enum class MaskOverlayMode {
    Composite,
    Result,
}

internal fun buildMaskOverlayBitmap(
    mask: MaskState,
    targetWidth: Int,
    targetHeight: Int,
    overlayMode: MaskOverlayMode,
    highlightSubMaskId: String? = null
): Bitmap {
    data class BrushEvent(
        val order: Long,
        val mode: SubMaskMode,
        val brushSize: Float,
        val feather: Float,
        val points: List<MaskPoint>
    )

    val width = targetWidth.coerceAtLeast(1)
    val height = targetHeight.coerceAtLeast(1)
    val baseDim = minOf(width, height).toFloat()

    fun denorm(value: Float, max: Int): Float {
        val maxCoord = (max - 1).coerceAtLeast(1).toFloat()
        return if (value <= 1.5f) (value * maxCoord).coerceIn(0f, maxCoord) else value
    }

    fun lenPx(value: Float): Float {
        return if (value <= 1.5f) (value * baseDim).coerceAtLeast(0f) else value
    }

    fun screenBlend(current: Int, intensity: Int): Int {
        val c = current.coerceIn(0, 255)
        val i = intensity.coerceIn(0, 255)
        val invC = 255 - c
        val invI = 255 - i
        return 255 - ((invC * invI + 127) / 255)
    }

    fun subtractBlend(current: Int, intensity: Int): Int {
        val c = current.coerceIn(0, 255)
        val i = intensity.coerceIn(0, 255)
        val invI = 255 - i
        return ((c * invI + 127) / 255).coerceIn(0, 255)
    }

    fun alphaFor(intensity: Int): Int = (intensity.coerceIn(0, 255) * 170 / 255).coerceIn(0, 255)

    fun argb(alpha: Int, r: Int, g: Int, b: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    fun over(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        val da = (dst ushr 24) and 0xFF

        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF

        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF

        val invSa = 255 - sa
        val outA = (sa + (da * invSa + 127) / 255).coerceIn(0, 255)
        val outR = (sr + (dr * invSa + 127) / 255).coerceIn(0, 255)
        val outG = (sg + (dg * invSa + 127) / 255).coerceIn(0, 255)
        val outB = (sb + (db * invSa + 127) / 255).coerceIn(0, 255)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    fun applyFeatheredCircleScreen(target: IntArray, cx: Float, cy: Float, radius: Float, feather: Float) {
        if (radius <= 0.5f) return
        val featherAmount = feather.coerceIn(0f, 1f)
        val innerRadius = radius * (1f - featherAmount)
        val outerRadius = radius
        val outerSq = outerRadius * outerRadius

        val x0 = kotlin.math.floor(cx - outerRadius).toInt().coerceAtLeast(0)
        val y0 = kotlin.math.floor(cy - outerRadius).toInt().coerceAtLeast(0)
        val x1 = kotlin.math.ceil(cx + outerRadius).toInt().coerceAtMost(width - 1)
        val y1 = kotlin.math.ceil(cy + outerRadius).toInt().coerceAtMost(height - 1)

        for (y in y0..y1) {
            val row = y * width
            val dy = y.toFloat() - cy
            for (x in x0..x1) {
                val dx = x.toFloat() - cx
                val distSq = dx * dx + dy * dy
                if (distSq > outerSq) continue
                val dist = kotlin.math.sqrt(distSq)
                val intensityF =
                    if (dist <= innerRadius) {
                        1f
                    } else if (outerRadius > innerRadius) {
                        1f - ((dist - innerRadius) / (outerRadius - innerRadius)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                if (intensityF <= 0f) continue
                val intensity = (intensityF * 255f).roundToInt().coerceIn(0, 255)
                if (intensity == 0) continue
                val idx = row + x
                target[idx] = screenBlend(target[idx], intensity)
            }
        }
    }

    fun applyFeatheredCircleSub(target: IntArray, cx: Float, cy: Float, radius: Float, feather: Float) {
        if (radius <= 0.5f) return
        val featherAmount = feather.coerceIn(0f, 1f)
        val innerRadius = radius * (1f - featherAmount)
        val outerRadius = radius
        val outerSq = outerRadius * outerRadius

        val x0 = kotlin.math.floor(cx - outerRadius).toInt().coerceAtLeast(0)
        val y0 = kotlin.math.floor(cy - outerRadius).toInt().coerceAtLeast(0)
        val x1 = kotlin.math.ceil(cx + outerRadius).toInt().coerceAtMost(width - 1)
        val y1 = kotlin.math.ceil(cy + outerRadius).toInt().coerceAtMost(height - 1)

        for (y in y0..y1) {
            val row = y * width
            val dy = y.toFloat() - cy
            for (x in x0..x1) {
                val dx = x.toFloat() - cx
                val distSq = dx * dx + dy * dy
                if (distSq > outerSq) continue
                val dist = kotlin.math.sqrt(distSq)
                val intensityF =
                    if (dist <= innerRadius) {
                        1f
                    } else if (outerRadius > innerRadius) {
                        1f - ((dist - innerRadius) / (outerRadius - innerRadius)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                if (intensityF <= 0f) continue
                val intensity = (intensityF * 255f).roundToInt().coerceIn(0, 255)
                if (intensity == 0) continue
                val idx = row + x
                target[idx] = subtractBlend(target[idx], intensity)
            }
        }
    }

    fun brushRadiusPx(brushSizeRaw: Float): Float {
        val px = if (brushSizeRaw <= 1.5f) brushSizeRaw * baseDim else brushSizeRaw
        return (px / 2f).coerceAtLeast(1f)
    }

    fun pressureScale(pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return 0.2f + (0.8f * p)
    }

    fun brushRadiusPx(brushSizeRaw: Float, pressure: Float): Float {
        return (brushRadiusPx(brushSizeRaw) * pressureScale(pressure)).coerceAtLeast(1f)
    }

    fun denormPoint(p: MaskPoint): Pair<Float, Float> {
        return denorm(p.x, width) to denorm(p.y, height)
    }

    fun decodeMaskDataUrlToBitmap(dataUrl: String): Bitmap? {
        val idx = dataUrl.indexOf("base64,")
        if (idx < 0) return null
        return try {
            val b64 = dataUrl.substring(idx + "base64,".length)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun boxBlurU8(src: IntArray, radius: Int): IntArray {
        if (radius <= 0) return src
        val r = radius
        val denom = (2 * r + 1).toFloat()
        val tmp = IntArray(src.size)
        val dst = IntArray(src.size)

        for (y in 0 until height) {
            val row = y * width
            var sum = 0
            sum += src[row] * (r + 1)
            for (ix in 1..minOf(r, width - 1)) sum += src[row + ix]
            for (ix in (minOf(r, width - 1) + 1)..r) sum += src[row + (width - 1)]
            tmp[row] = (sum / denom).roundToInt().coerceIn(0, 255)
            for (x in 1 until width) {
                val addX = minOf(x + r, width - 1)
                val subX = maxOf(x - r - 1, 0)
                sum += src[row + addX]
                sum -= src[row + subX]
                tmp[row + x] = (sum / denom).roundToInt().coerceIn(0, 255)
            }
        }

        for (x in 0 until width) {
            var sum = 0
            sum += tmp[x] * (r + 1)
            for (iy in 1..minOf(r, height - 1)) sum += tmp[iy * width + x]
            for (iy in (minOf(r, height - 1) + 1)..r) sum += tmp[(height - 1) * width + x]
            dst[x] = (sum / denom).roundToInt().coerceIn(0, 255)
            for (y in 1 until height) {
                val addY = minOf(y + r, height - 1)
                val subY = maxOf(y - r - 1, 0)
                sum += tmp[addY * width + x]
                sum -= tmp[subY * width + x]
                dst[y * width + x] = (sum / denom).roundToInt().coerceIn(0, 255)
            }
        }
        return dst
    }

    fun brushEvents(sub: SubMaskState): List<BrushEvent> {
        return sub.lines.mapNotNull { line ->
            if (line.points.isEmpty()) return@mapNotNull null
            val effectiveMode = if (line.tool == "eraser") SubMaskMode.Subtractive else sub.mode
            BrushEvent(
                order = line.order,
                mode = effectiveMode,
                brushSize = line.brushSize,
                feather = line.feather,
                points = line.points
            )
        }.sortedBy { it.order }
    }

    fun applyBrushStamps(event: BrushEvent, applyStamp: (cx: Float, cy: Float, radius: Float, feather: Float) -> Unit) {
        if (event.points.isEmpty()) return
        val feather = event.feather.coerceIn(0f, 1f)
        if (event.points.size == 1) {
            val (x, y) = denormPoint(event.points[0])
            val radius = brushRadiusPx(event.brushSize, event.points[0].pressure)
            applyStamp(x, y, radius, feather)
            return
        }

        event.points.windowed(2, 1, false).forEach { (p0, p1) ->
            val (x0, y0) = denormPoint(p0)
            val (x1, y1) = denormPoint(p1)
            val dx = x1 - x0
            val dy = y1 - y0
            val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val r0 = brushRadiusPx(event.brushSize, p0.pressure)
            val r1 = brushRadiusPx(event.brushSize, p1.pressure)
            val step = (maxOf(r0, r1) * 0.5f).coerceAtLeast(0.75f)
            val steps = kotlin.math.ceil(dist / step).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val radius = r0 + (r1 - r0) * t
                applyStamp(x0 + dx * t, y0 + dy * t, radius, feather)
            }
        }
    }

    fun applyRadialMask(apply: (idx: Int, intensity: Int) -> Unit, sub: SubMaskState) {
        val cx = denorm(sub.radial.centerX, width)
        val cy = denorm(sub.radial.centerY, height)
        val rx = lenPx(sub.radial.radiusX).coerceAtLeast(0.01f)
        val ry = lenPx(sub.radial.radiusY).coerceAtLeast(0.01f)
        val feather = sub.radial.feather.coerceIn(0f, 1f)
        val innerBound = (1f - feather).coerceIn(0f, 1f)
        val rotation = sub.radial.rotation * (Math.PI.toFloat() / 180f)
        val cosRot = kotlin.math.cos(rotation)
        val sinRot = kotlin.math.sin(rotation)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val dx = x.toFloat() - cx
                val dy = y.toFloat() - cy
                val rotDx = dx * cosRot + dy * sinRot
                val rotDy = -dx * sinRot + dy * cosRot
                val nx = rotDx / rx
                val ny = rotDy / ry
                val dist = kotlin.math.sqrt(nx * nx + ny * ny)
                val intensityF =
                    if (dist <= innerBound) {
                        1f
                    } else {
                        1f - (dist - innerBound) / (1f - innerBound).coerceAtLeast(0.01f)
                    }
                val intensity = (intensityF.coerceIn(0f, 1f) * 255f).roundToInt()
                if (intensity == 0) continue
                apply(row + x, intensity)
            }
        }
    }

    fun applyLinearMask(apply: (idx: Int, intensity: Int) -> Unit, sub: SubMaskState) {
        val sx = denorm(sub.linear.startX, width)
        val sy = denorm(sub.linear.startY, height)
        val ex = denorm(sub.linear.endX, width)
        val ey = denorm(sub.linear.endY, height)
        val rangePx = lenPx(sub.linear.range).coerceAtLeast(0.01f)
        val vx = ex - sx
        val vy = ey - sy
        val len = kotlin.math.sqrt(vx * vx + vy * vy)
        if (len <= 0.01f) return
        val invLen = 1f / len
        val nx = -vy * invLen
        val ny = vx * invLen

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val px = x.toFloat() - sx
                val py = y.toFloat() - sy
                val distPerp = px * nx + py * ny
                val t = distPerp / rangePx
                val intensityF = (0.5f - t * 0.5f).coerceIn(0f, 1f)
                val intensity = (intensityF * 255f).roundToInt()
                if (intensity == 0) continue
                apply(row + x, intensity)
            }
        }
    }

    fun applyAiMask(apply: (idx: Int, intensity: Int) -> Unit, dataUrl: String?, softness: Float) {
        val url = dataUrl ?: return
        val decoded = decodeMaskDataUrlToBitmap(url) ?: return
        val scaled =
            if (decoded.width != width || decoded.height != height) {
                Bitmap.createScaledBitmap(decoded, width, height, true)
            } else {
                decoded
            }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val maskU8 = IntArray(width * height) { i -> (pixels[i] shr 16) and 0xFF }
        val radius = (softness.coerceIn(0f, 1f) * 10f).roundToInt()
        val softened = if (radius >= 1) boxBlurU8(maskU8, radius) else maskU8
        for (i in softened.indices) {
            val v = softened[i]
            if (v == 0) continue
            apply(i, v)
        }
    }

    val highlightSubMask =
        highlightSubMaskId?.let { id -> mask.subMasks.firstOrNull { it.id == id && it.visible } }

    if (highlightSubMask != null) {
        val layer = IntArray(width * height)
        when (highlightSubMask.type) {
            SubMaskType.Brush.id -> {
                val events = brushEvents(highlightSubMask)
                if (highlightSubMask.mode == SubMaskMode.Additive) {
                    events.forEach { event ->
                        applyBrushStamps(event) { cx, cy, radius, feather ->
                            if (event.mode == SubMaskMode.Additive) {
                                applyFeatheredCircleScreen(layer, cx, cy, radius, feather)
                            } else {
                                applyFeatheredCircleSub(layer, cx, cy, radius, feather)
                            }
                        }
                    }
                } else {
                    events.forEach { event ->
                        applyBrushStamps(event) { cx, cy, radius, feather ->
                            applyFeatheredCircleScreen(layer, cx, cy, radius, feather)
                        }
                    }
                }
            }

            SubMaskType.Radial.id ->
                applyRadialMask({ idx, intensity -> layer[idx] = maxOf(layer[idx], intensity) }, highlightSubMask)

            SubMaskType.Linear.id ->
                applyLinearMask({ idx, intensity -> layer[idx] = maxOf(layer[idx], intensity) }, highlightSubMask)

            SubMaskType.AiSubject.id ->
                applyAiMask({ idx, intensity -> layer[idx] = maxOf(layer[idx], intensity) }, highlightSubMask.aiSubject.maskDataBase64, highlightSubMask.aiSubject.softness)

            SubMaskType.AiEnvironment.id ->
                applyAiMask({ idx, intensity -> layer[idx] = maxOf(layer[idx], intensity) }, highlightSubMask.aiEnvironment.maskDataBase64, highlightSubMask.aiEnvironment.softness)
        }

        val overlayPixels = IntArray(width * height)
        val (r, g, b) =
            if (highlightSubMask.mode == SubMaskMode.Additive) {
                Triple(255, 0, 0)
            } else {
                Triple(0, 120, 255)
            }
        for (i in overlayPixels.indices) {
            val v = layer[i]
            if (v == 0) continue
            overlayPixels[i] = argb(alphaFor(v), r, g, b)
        }
        return Bitmap.createBitmap(overlayPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    return when (overlayMode) {
        MaskOverlayMode.Composite -> {
            val addLayer = IntArray(width * height)
            val subLayer = IntArray(width * height)

            mask.subMasks.forEach { sub ->
                if (!sub.visible) return@forEach
                when (sub.type) {
                    SubMaskType.Brush.id -> {
                        val events = brushEvents(sub)
                        events.forEach { event ->
                            applyBrushStamps(event) { cx, cy, radius, feather ->
                                if (event.mode == SubMaskMode.Additive) {
                                    applyFeatheredCircleScreen(addLayer, cx, cy, radius, feather)
                                } else {
                                    applyFeatheredCircleScreen(subLayer, cx, cy, radius, feather)
                                }
                            }
                        }
                    }

                    SubMaskType.Radial.id -> {
                        applyRadialMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    addLayer[idx] = maxOf(addLayer[idx], intensity)
                                } else {
                                    subLayer[idx] = screenBlend(subLayer[idx], intensity)
                                }
                            },
                            sub = sub
                        )
                    }

                    SubMaskType.Linear.id -> {
                        applyLinearMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    addLayer[idx] = maxOf(addLayer[idx], intensity)
                                } else {
                                    subLayer[idx] = screenBlend(subLayer[idx], intensity)
                                }
                            },
                            sub = sub
                        )
                    }

                    SubMaskType.AiSubject.id ->
                        applyAiMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    addLayer[idx] = maxOf(addLayer[idx], intensity)
                                } else {
                                    subLayer[idx] = screenBlend(subLayer[idx], intensity)
                                }
                            },
                            dataUrl = sub.aiSubject.maskDataBase64,
                            softness = sub.aiSubject.softness
                        )

                    SubMaskType.AiEnvironment.id ->
                        applyAiMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    addLayer[idx] = maxOf(addLayer[idx], intensity)
                                } else {
                                    subLayer[idx] = screenBlend(subLayer[idx], intensity)
                                }
                            },
                            dataUrl = sub.aiEnvironment.maskDataBase64,
                            softness = sub.aiEnvironment.softness
                        )
                }
            }

            val overlayPixels = IntArray(width * height)
            for (i in overlayPixels.indices) {
                val add = addLayer[i]
                val sub = subLayer[i]
                if (add == 0 && sub == 0) continue
                var out = 0
                if (add > 0) out = over(out, argb(alphaFor(add), 255, 0, 0))
                if (sub > 0) out = over(out, argb(alphaFor(sub), 0, 120, 255))
                overlayPixels[i] = out
            }
            Bitmap.createBitmap(overlayPixels, width, height, Bitmap.Config.ARGB_8888)
        }

        MaskOverlayMode.Result -> {
            val resultMask = IntArray(width * height)

            mask.subMasks.forEach { sub ->
                if (!sub.visible) return@forEach
                when (sub.type) {
                    SubMaskType.Brush.id -> {
                        val events = brushEvents(sub)
                        events.forEach { event ->
                            applyBrushStamps(event) { cx, cy, radius, feather ->
                                if (event.mode == SubMaskMode.Additive) {
                                    applyFeatheredCircleScreen(resultMask, cx, cy, radius, feather)
                                } else {
                                    applyFeatheredCircleSub(resultMask, cx, cy, radius, feather)
                                }
                            }
                        }
                    }

                    SubMaskType.Radial.id ->
                        applyRadialMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    resultMask[idx] = maxOf(resultMask[idx], intensity)
                                } else {
                                    resultMask[idx] = subtractBlend(resultMask[idx], intensity)
                                }
                            },
                            sub = sub
                        )

                    SubMaskType.Linear.id ->
                        applyLinearMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    resultMask[idx] = maxOf(resultMask[idx], intensity)
                                } else {
                                    resultMask[idx] = subtractBlend(resultMask[idx], intensity)
                                }
                            },
                            sub = sub
                        )

                    SubMaskType.AiSubject.id ->
                        applyAiMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    resultMask[idx] = maxOf(resultMask[idx], intensity)
                                } else {
                                    resultMask[idx] = subtractBlend(resultMask[idx], intensity)
                                }
                            },
                            dataUrl = sub.aiSubject.maskDataBase64,
                            softness = sub.aiSubject.softness
                        )

                    SubMaskType.AiEnvironment.id ->
                        applyAiMask(
                            apply = { idx, intensity ->
                                if (sub.mode == SubMaskMode.Additive) {
                                    resultMask[idx] = maxOf(resultMask[idx], intensity)
                                } else {
                                    resultMask[idx] = subtractBlend(resultMask[idx], intensity)
                                }
                            },
                            dataUrl = sub.aiEnvironment.maskDataBase64,
                            softness = sub.aiEnvironment.softness
                        )
                }
            }

            if (mask.invert) {
                for (i in resultMask.indices) {
                    resultMask[i] = 255 - resultMask[i].coerceIn(0, 255)
                }
            }

            val overlayPixels = IntArray(width * height)
            for (i in overlayPixels.indices) {
                val v = resultMask[i]
                if (v == 0) continue
                overlayPixels[i] = argb(alphaFor(v), 255, 0, 0)
            }
            Bitmap.createBitmap(overlayPixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
