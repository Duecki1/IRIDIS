package com.dueckis.kawaiiraweditor.data.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange

internal enum class AdjustmentField {
    Brightness,
    Contrast,
    Highlights,
    Shadows,
    Whites,
    Blacks,
    Saturation,
    Temperature,
    Tint,
    Vibrance,
    Clarity,
    Dehaze,
    Structure,
    Centre,
    VignetteAmount,
    VignetteMidpoint,
    VignetteRoundness,
    VignetteFeather,
    Sharpness,
    LumaNoiseReduction,
    ColorNoiseReduction,
    ChromaticAberrationRedCyan,
    ChromaticAberrationBlueYellow,
    ToneMapper
}

internal data class CropState(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun normalized(): CropState {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        val nWidth = width.coerceIn(0f, 1f - nx)
        val nHeight = height.coerceIn(0f, 1f - ny)
        return copy(x = nx, y = ny, width = nWidth, height = nHeight)
    }

    fun toJsonObject(): JSONObject {
        val crop = normalized()
        return JSONObject().apply {
            put("x", crop.x)
            put("y", crop.y)
            put("width", crop.width)
            put("height", crop.height)
        }
    }
}

internal data class CurvePointState(
    val x: Float,
    val y: Float
)

internal fun defaultCurvePoints(): List<CurvePointState> {
    return listOf(CurvePointState(0f, 0f), CurvePointState(255f, 255f))
}

internal data class CurvesState(
    val luma: List<CurvePointState> = defaultCurvePoints(),
    val red: List<CurvePointState> = defaultCurvePoints(),
    val green: List<CurvePointState> = defaultCurvePoints(),
    val blue: List<CurvePointState> = defaultCurvePoints()
) {
    fun toJsonObject(): JSONObject {
        fun channel(points: List<CurvePointState>): JSONArray {
            return JSONArray().apply {
                points.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("x", p.x)
                            put("y", p.y)
                        }
                    )
                }
            }
        }

        return JSONObject().apply {
            put("luma", channel(luma))
            put("red", channel(red))
            put("green", channel(green))
            put("blue", channel(blue))
        }
    }

    fun isDefault(): Boolean {
        fun isDefaultChannel(points: List<CurvePointState>): Boolean {
            if (points.size != 2) return false
            fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= 0.1f
            return near(points[0].y, 0f) && near(points[1].y, 255f)
        }
        return isDefaultChannel(luma) && isDefaultChannel(red) && isDefaultChannel(green) && isDefaultChannel(blue)
    }
}

internal data class HueSatLumState(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("hue", hue)
            put("saturation", saturation)
            put("luminance", luminance)
        }
    }

    fun isDefault(): Boolean {
        fun nearZero(v: Float) = kotlin.math.abs(v) <= 0.000001f
        return nearZero(hue) && nearZero(saturation) && nearZero(luminance)
    }
}

internal data class ColorGradingState(
    val shadows: HueSatLumState = HueSatLumState(),
    val midtones: HueSatLumState = HueSatLumState(),
    val highlights: HueSatLumState = HueSatLumState(),
    val blending: Float = 50f,
    val balance: Float = 0f
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("shadows", shadows.toJsonObject())
            put("midtones", midtones.toJsonObject())
            put("highlights", highlights.toJsonObject())
            put("blending", blending)
            put("balance", balance)
        }
    }

    fun isDefault(): Boolean {
        fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= 0.000001f
        return shadows.isDefault() &&
            midtones.isDefault() &&
            highlights.isDefault() &&
            near(blending, 50f) &&
            near(balance, 0f)
    }
}

internal data class HslState(
    val reds: HueSatLumState = HueSatLumState(),
    val oranges: HueSatLumState = HueSatLumState(),
    val yellows: HueSatLumState = HueSatLumState(),
    val greens: HueSatLumState = HueSatLumState(),
    val aquas: HueSatLumState = HueSatLumState(),
    val blues: HueSatLumState = HueSatLumState(),
    val purples: HueSatLumState = HueSatLumState(),
    val magentas: HueSatLumState = HueSatLumState()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("reds", reds.toJsonObject())
            put("oranges", oranges.toJsonObject())
            put("yellows", yellows.toJsonObject())
            put("greens", greens.toJsonObject())
            put("aquas", aquas.toJsonObject())
            put("blues", blues.toJsonObject())
            put("purples", purples.toJsonObject())
            put("magentas", magentas.toJsonObject())
        }
    }

    fun isDefault(): Boolean {
        return reds.isDefault() &&
            oranges.isDefault() &&
            yellows.isDefault() &&
            greens.isDefault() &&
            aquas.isDefault() &&
            blues.isDefault() &&
            purples.isDefault() &&
            magentas.isDefault()
    }
}

internal data class AdjustmentState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val orientationSteps: Int = 0,
    val aspectRatio: Float? = null,
    val crop: CropState? = null,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val sharpness: Float = 0f,
    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,
    val toneMapper: String = "basic",
    val curves: CurvesState = CurvesState(),
    val colorGrading: ColorGradingState = ColorGradingState(),
    val hsl: HslState = HslState()
) {
    fun toJsonObject(includeToneMapper: Boolean = true): JSONObject {
        return JSONObject().apply {
            if (includeToneMapper) {
                put("rotation", rotation)
                put("flipHorizontal", flipHorizontal)
                put("flipVertical", flipVertical)
                put("orientationSteps", orientationSteps)
                put("aspectRatio", aspectRatio ?: JSONObject.NULL)
                put("crop", crop?.toJsonObject() ?: JSONObject.NULL)
            }
            put("brightness", brightness)
            put("contrast", contrast)
            put("highlights", highlights)
            put("shadows", shadows)
            put("whites", whites)
            put("blacks", blacks)
            put("saturation", saturation)
            put("temperature", temperature)
            put("tint", tint)
            put("vibrance", vibrance)
            put("clarity", clarity)
            put("dehaze", dehaze)
            put("structure", structure)
            put("centre", centre)
            put("vignetteAmount", vignetteAmount)
            put("vignetteMidpoint", vignetteMidpoint)
            put("vignetteRoundness", vignetteRoundness)
            put("vignetteFeather", vignetteFeather)
            put("sharpness", sharpness)
            put("lumaNoiseReduction", lumaNoiseReduction)
            put("colorNoiseReduction", colorNoiseReduction)
            put("chromaticAberrationRedCyan", chromaticAberrationRedCyan)
            put("chromaticAberrationBlueYellow", chromaticAberrationBlueYellow)
            put("curves", curves.toJsonObject())
            put("colorGrading", colorGrading.toJsonObject())
            put("hsl", hsl.toJsonObject())
            if (includeToneMapper) {
                put("toneMapper", toneMapper)
            }
        }
    }

    fun valueFor(field: AdjustmentField): Float {
        return when (field) {
            AdjustmentField.Brightness -> brightness
            AdjustmentField.Contrast -> contrast
            AdjustmentField.Highlights -> highlights
            AdjustmentField.Shadows -> shadows
            AdjustmentField.Whites -> whites
            AdjustmentField.Blacks -> blacks
            AdjustmentField.Saturation -> saturation
            AdjustmentField.Temperature -> temperature
            AdjustmentField.Tint -> tint
            AdjustmentField.Vibrance -> vibrance
            AdjustmentField.Clarity -> clarity
            AdjustmentField.Dehaze -> dehaze
            AdjustmentField.Structure -> structure
            AdjustmentField.Centre -> centre
            AdjustmentField.VignetteAmount -> vignetteAmount
            AdjustmentField.VignetteMidpoint -> vignetteMidpoint
            AdjustmentField.VignetteRoundness -> vignetteRoundness
            AdjustmentField.VignetteFeather -> vignetteFeather
            AdjustmentField.Sharpness -> sharpness
            AdjustmentField.LumaNoiseReduction -> lumaNoiseReduction
            AdjustmentField.ColorNoiseReduction -> colorNoiseReduction
            AdjustmentField.ChromaticAberrationRedCyan -> chromaticAberrationRedCyan
            AdjustmentField.ChromaticAberrationBlueYellow -> chromaticAberrationBlueYellow
            AdjustmentField.ToneMapper -> 0f // ToneMapper is a string, not a float
        }
    }

    fun withValue(field: AdjustmentField, value: Float): AdjustmentState {
        return when (field) {
            AdjustmentField.Brightness -> copy(brightness = value)
            AdjustmentField.Contrast -> copy(contrast = value)
            AdjustmentField.Highlights -> copy(highlights = value)
            AdjustmentField.Shadows -> copy(shadows = value)
            AdjustmentField.Whites -> copy(whites = value)
            AdjustmentField.Blacks -> copy(blacks = value)
            AdjustmentField.Saturation -> copy(saturation = value)
            AdjustmentField.Temperature -> copy(temperature = value)
            AdjustmentField.Tint -> copy(tint = value)
            AdjustmentField.Vibrance -> copy(vibrance = value)
            AdjustmentField.Clarity -> copy(clarity = value)
            AdjustmentField.Dehaze -> copy(dehaze = value)
            AdjustmentField.Structure -> copy(structure = value)
            AdjustmentField.Centre -> copy(centre = value)
            AdjustmentField.VignetteAmount -> copy(vignetteAmount = value)
            AdjustmentField.VignetteMidpoint -> copy(vignetteMidpoint = value)
            AdjustmentField.VignetteRoundness -> copy(vignetteRoundness = value)
            AdjustmentField.VignetteFeather -> copy(vignetteFeather = value)
            AdjustmentField.Sharpness -> copy(sharpness = value)
            AdjustmentField.LumaNoiseReduction -> copy(lumaNoiseReduction = value)
            AdjustmentField.ColorNoiseReduction -> copy(colorNoiseReduction = value)
            AdjustmentField.ChromaticAberrationRedCyan -> copy(chromaticAberrationRedCyan = value)
            AdjustmentField.ChromaticAberrationBlueYellow -> copy(chromaticAberrationBlueYellow = value)
            AdjustmentField.ToneMapper -> this
        }
    }

    fun withToneMapper(mapper: String): AdjustmentState {
        return copy(toneMapper = mapper)
    }

    fun toJson(masks: List<MaskState> = emptyList()): String {
        val payload = toJsonObject(includeToneMapper = true).apply {
            put(
                "masks",
                JSONArray().apply {
                    masks.forEach { put(it.toJsonObject()) }
                }
            )
        }
        return payload.toString()
    }
}

internal enum class SubMaskMode {
    Additive,
    Subtractive,
}

internal fun SubMaskMode.inverted(): SubMaskMode {
    return when (this) {
        SubMaskMode.Additive -> SubMaskMode.Subtractive
        SubMaskMode.Subtractive -> SubMaskMode.Additive
    }
}

internal enum class SubMaskType(val id: String) {
    Brush("brush"),
    Linear("linear"),
    Radial("radial"),
    AiSubject("ai-subject"),
}

internal enum class MaskTapMode {
    None,
    SetRadialCenter,
    SetLinearStart,
    SetLinearEnd,
}

internal enum class MaskHandle {
    RadialCenter,
    LinearStart,
    LinearEnd,
}

internal enum class BrushTool {
    Brush,
    Eraser,
}

internal data class LinearMaskParametersState(
    val startX: Float = 0.5f,
    val startY: Float = 0.2f,
    val endX: Float = 0.5f,
    val endY: Float = 0.8f,
    val range: Float = 0.25f
)

internal data class RadialMaskParametersState(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radiusX: Float = 0.35f,
    val radiusY: Float = 0.35f,
    val rotation: Float = 0f,
    val feather: Float = 0.5f
)

internal data class AiSubjectMaskParametersState(
    val maskDataBase64: String? = null,
    val softness: Float = 0.25f
)

internal enum class EditorPanelTab(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
) {
    CropTransform("Crop", Icons.Outlined.Crop, Icons.Filled.Crop),
    Adjustments("Adjust", Icons.Outlined.Tune, Icons.Filled.Tune),
    Color("Color", Icons.Outlined.Palette, Icons.Filled.Palette),
    Effects("Effects", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Masks("Masking", Icons.Outlined.Layers, Icons.Filled.Layers),
}

internal fun AdjustmentState.isNeutralForMask(): Boolean {
    fun nearZero(v: Float) = kotlin.math.abs(v) <= 0.000001f
    fun near(v: Float, target: Float) = kotlin.math.abs(v - target) <= 0.000001f
    return nearZero(rotation) &&
        !flipHorizontal &&
        !flipVertical &&
        orientationSteps == 0 &&
        aspectRatio == null &&
        crop == null &&
        nearZero(brightness) &&
        nearZero(contrast) &&
        nearZero(highlights) &&
        nearZero(shadows) &&
        nearZero(whites) &&
        nearZero(blacks) &&
        nearZero(saturation) &&
        nearZero(temperature) &&
        nearZero(tint) &&
        nearZero(vibrance) &&
        nearZero(clarity) &&
        nearZero(dehaze) &&
        nearZero(structure) &&
        nearZero(centre) &&
        nearZero(vignetteAmount) &&
        near(vignetteMidpoint, 50f) &&
        nearZero(vignetteRoundness) &&
        near(vignetteFeather, 50f) &&
        nearZero(sharpness) &&
        nearZero(lumaNoiseReduction) &&
        nearZero(colorNoiseReduction) &&
        nearZero(chromaticAberrationRedCyan) &&
        nearZero(chromaticAberrationBlueYellow) &&
        curves.isDefault() &&
        colorGrading.isDefault() &&
        hsl.isDefault()
}

internal data class MaskPoint(
    val x: Float,
    val y: Float
)

internal data class BrushLineState(
    val tool: String = "brush",
    val brushSize: Float,
    val feather: Float = 0.5f,
    val order: Long = 0L,
    val points: List<MaskPoint>
)

internal data class SubMaskState(
    val id: String,
    val type: String = SubMaskType.Brush.id,
    val visible: Boolean = true,
    val mode: SubMaskMode = SubMaskMode.Additive,
    val lines: List<BrushLineState> = emptyList(),
    val linear: LinearMaskParametersState = LinearMaskParametersState(),
    val radial: RadialMaskParametersState = RadialMaskParametersState(),
    val aiSubject: AiSubjectMaskParametersState = AiSubjectMaskParametersState()
)

internal data class MaskState(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 100f,
    val adjustments: AdjustmentState = AdjustmentState(),
    val subMasks: List<SubMaskState> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("visible", visible)
            put("invert", invert)
            put("opacity", opacity)
            put("adjustments", adjustments.toJsonObject(includeToneMapper = false))
            put(
                "subMasks",
                JSONArray().apply {
                    subMasks.forEach { put(it.toJsonObject()) }
                }
            )
        }
    }
}

internal fun SubMaskState.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("type", type)
        put("visible", visible)
        put("mode", mode.name.lowercase(Locale.US))
        put(
            "parameters",
            when (type) {
                SubMaskType.Brush.id -> JSONObject().apply {
                    put(
                        "lines",
                        JSONArray().apply {
                            lines.forEach { line ->
                                put(
                                    JSONObject().apply {
                                        put("tool", line.tool)
                                        put("brushSize", line.brushSize)
                                        put("feather", line.feather)
                                        put("order", line.order)
                                        put(
                                            "points",
                                            JSONArray().apply {
                                                line.points.forEach { point ->
                                                    put(
                                                        JSONObject().apply {
                                                            put("x", point.x)
                                                            put("y", point.y)
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }

                SubMaskType.Linear.id -> JSONObject().apply {
                    put("startX", linear.startX)
                    put("startY", linear.startY)
                    put("endX", linear.endX)
                    put("endY", linear.endY)
                    put("range", linear.range)
                }

                SubMaskType.Radial.id -> JSONObject().apply {
                    put("centerX", radial.centerX)
                    put("centerY", radial.centerY)
                    put("radiusX", radial.radiusX)
                    put("radiusY", radial.radiusY)
                    put("rotation", radial.rotation)
                    put("feather", radial.feather)
                }

                SubMaskType.AiSubject.id -> JSONObject().apply {
                    aiSubject.maskDataBase64?.let { put("maskDataBase64", it) }
                    put("softness", aiSubject.softness.coerceIn(0f, 1f))
                }

                else -> JSONObject()
            }
        )
    }
}

private data class BrushEvent(
    val order: Long,
    val mode: SubMaskMode,
    val brushSize: Float,
    val feather: Float,
    val points: List<MaskPoint>
)

internal fun buildMaskOverlayBitmap(
    mask: MaskState,
    targetWidth: Int,
    targetHeight: Int,
    highlightSubMaskId: String? = null
): Bitmap {
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

    fun applyPixel(mode: SubMaskMode, current: Int, intensity: Int): Int {
        return when (mode) {
            SubMaskMode.Additive -> {
                val c = current / 255f
                val i = intensity / 255f
                ((1f - (1f - c) * (1f - i)).coerceIn(0f, 1f) * 255f).roundToInt()
            }
            SubMaskMode.Subtractive -> {
                val currentF = current / 255f
                val intensityF = intensity / 255f
                ((currentF * (1f - intensityF)).coerceIn(0f, 1f) * 255f).roundToInt()
            }
        }
    }

    fun circleIntensity(dist: Float, radius: Float, feather: Float): Int {
        if (radius <= 0.5f) return 0
        val featherClamped = feather.coerceIn(0f, 1f)
        if (featherClamped <= 0.0001f) {
            return if (dist <= radius) 255 else 0
        }
        val inner = radius * (1f - featherClamped)
        if (dist <= inner) return 255
        if (dist >= radius) return 0
        val t = (dist - inner) / (radius - inner).coerceAtLeast(0.001f)
        return ((1f - t).coerceIn(0f, 1f) * 255f).roundToInt()
    }

    val additive = IntArray(width * height) { 0 }
    val subtractive = IntArray(width * height) { 0 }
    val selection = IntArray(width * height) { 0 }

    fun plot(mode: SubMaskMode, idx: Int, intensity: Int) {
        if (intensity <= 0) return
        val target = if (mode == SubMaskMode.Subtractive) subtractive else additive
        if (intensity > target[idx]) target[idx] = intensity
    }

    fun applyToSelection(mode: SubMaskMode, idx: Int, intensity: Int) {
        if (intensity <= 0) return
        selection[idx] = applyPixel(mode, selection[idx], intensity)
    }

    fun plotAndApply(mode: SubMaskMode, idx: Int, intensity: Int) {
        plot(mode, idx, intensity)
        applyToSelection(mode, idx, intensity)
    }

    fun applyCircle(mode: SubMaskMode, cx: Float, cy: Float, radius: Float, feather: Float) {
        val x0 = (cx - radius - 1f).toInt().coerceAtLeast(0)
        val y0 = (cy - radius - 1f).toInt().coerceAtLeast(0)
        val x1 = (cx + radius + 1f).toInt().coerceAtMost(width - 1)
        val y1 = (cy + radius + 1f).toInt().coerceAtMost(height - 1)
        for (y in y0..y1) {
            val dy = y + 0.5f - cy
            val row = y * width
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val intensity = circleIntensity(dist, radius, feather)
                if (intensity == 0) continue
                val idx = row + x
                plotAndApply(mode, idx, intensity)
            }
        }
    }

    fun brushRadiusPx(brushSizeRaw: Float): Float {
        val px = if (brushSizeRaw <= 1.5f) brushSizeRaw * baseDim else brushSizeRaw
        return (px / 2f).coerceAtLeast(1f)
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

        // Horizontal
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

        // Vertical
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

    mask.subMasks.forEach { sub ->
        if (!sub.visible) return@forEach
        if (highlightSubMaskId != null && sub.id != highlightSubMaskId) return@forEach
        when (sub.type) {
            SubMaskType.Brush.id -> {
                val events =
                    sub.lines.mapNotNull { line ->
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

                events.forEach { event ->
                    if (event.points.isEmpty()) return@forEach
                    val radius = brushRadiusPx(event.brushSize)
                    val feather = event.feather.coerceIn(0f, 1f)
                    if (event.points.size == 1) {
                        val (x, y) = denormPoint(event.points[0])
                        applyCircle(event.mode, x, y, radius, feather)
                        return@forEach
                    }

                    val step = (radius * 0.5f).coerceAtLeast(0.75f)
                    event.points.windowed(2, 1, false).forEach { (p0, p1) ->
                        val (x0, y0) = denormPoint(p0)
                        val (x1, y1) = denormPoint(p1)
                        val dx = x1 - x0
                        val dy = y1 - y0
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                        val steps = (dist / step).roundToInt().coerceAtLeast(1)
                        for (i in 0..steps) {
                            val t = i.toFloat() / steps.toFloat()
                            applyCircle(event.mode, x0 + dx * t, y0 + dy * t, radius, feather)
                        }
                    }
                }
            }

            SubMaskType.Radial.id -> {
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
                        val dx = x + 0.5f - cx
                        val dy = y + 0.5f - cy
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
                        val idx = row + x
                        plotAndApply(sub.mode, idx, intensity)
                    }
                }
            }

            SubMaskType.Linear.id -> {
                val sx = denorm(sub.linear.startX, width)
                val sy = denorm(sub.linear.startY, height)
                val ex = denorm(sub.linear.endX, width)
                val ey = denorm(sub.linear.endY, height)
                val rangePx = lenPx(sub.linear.range).coerceAtLeast(0.01f)
                val vx = ex - sx
                val vy = ey - sy
                val len = kotlin.math.sqrt(vx * vx + vy * vy)
                if (len <= 0.01f) return@forEach
                val invLen = 1f / len
                val nx = -vy * invLen
                val ny = vx * invLen

                for (y in 0 until height) {
                    val row = y * width
                    for (x in 0 until width) {
                        val px = x + 0.5f - sx
                        val py = y + 0.5f - sy
                        val distPerp = px * nx + py * ny
                        val t = distPerp / rangePx
                        val intensityF = (0.5f - t * 0.5f).coerceIn(0f, 1f)
                        val intensity = (intensityF * 255f).roundToInt()
                        if (intensity == 0) continue
                        val idx = row + x
                        plotAndApply(sub.mode, idx, intensity)
                    }
                }
            }

            SubMaskType.AiSubject.id -> {
                val dataUrl = sub.aiSubject.maskDataBase64 ?: return@forEach
                val decoded = decodeMaskDataUrlToBitmap(dataUrl) ?: return@forEach
                val scaled = if (decoded.width != width || decoded.height != height) {
                    Bitmap.createScaledBitmap(decoded, width, height, true)
                } else {
                    decoded
                }
                val pixels = IntArray(width * height)
                scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                val maskU8 = IntArray(width * height) { i -> (pixels[i] shr 16) and 0xFF }
                val radius = (sub.aiSubject.softness.coerceIn(0f, 1f) * 10f).roundToInt()
                val softened = if (radius >= 1) boxBlurU8(maskU8, radius) else maskU8
                for (i in softened.indices) {
                    val v = softened[i]
                    if (v == 0) continue
                    plotAndApply(sub.mode, i, v)
                }
            }
        }
    }

    val overlayPixels = IntArray(width * height)
    val invertSelection = highlightSubMaskId == null && mask.invert
    for (i in overlayPixels.indices) {
        var add = additive[i].coerceIn(0, 255)
        var sub = subtractive[i].coerceIn(0, 255)
        var sel = selection[i].coerceIn(0, 255)
        if (invertSelection) {
            sel = 255 - sel
            val tmp = add
            add = sub
            sub = tmp
        }
        if (add == 0 && sub == 0 && sel == 0) continue

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

        fun alphaFor(intensity: Int): Int = (intensity * 170 / 255).coerceIn(0, 255)

        var out = 0
        if (sel > 0) out = argb(alphaFor(sel), 255, 155, 0) // selection (also shows invert)
        if (add > 0) out = over(out, argb(alphaFor(add), 255, 155, 0)) // additive strokes
        if (sub > 0) out = over(out, argb(alphaFor(sub), 0, 140, 255)) // subtractive strokes
        overlayPixels[i] = out
    }
    return Bitmap.createBitmap(overlayPixels, width, height, Bitmap.Config.ARGB_8888)
}

internal data class AdjustmentControl(
    val field: AdjustmentField,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val defaultValue: Float = 0f,
    val formatter: (Float) -> String = { value -> String.format(Locale.US, "%.0f", value) }
)

internal data class RenderRequest(
    val version: Long,
    val adjustmentsJson: String,
    val target: RenderTarget = RenderTarget.Edited,
    val rotationDegrees: Float = 0f,
    val previewRoi: CropState? = null
)

internal enum class RenderTarget {
    Edited,
    Original,
    UncroppedEdited,
}

internal val basicSection = listOf(
    AdjustmentControl(
        field = AdjustmentField.Brightness,
        label = "Brightness",
        range = -5f..5f,
        step = 0.01f,
        defaultValue = 0f,
        formatter = { value -> String.format(Locale.US, "%.2f", value) }
    ),
    AdjustmentControl(field = AdjustmentField.Contrast, label = "Contrast", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Highlights, label = "Highlights", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Shadows, label = "Shadows", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Whites, label = "Whites", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Blacks, label = "Blacks", range = -100f..100f, step = 1f)
)

internal val colorSection = listOf(
    AdjustmentControl(field = AdjustmentField.Saturation, label = "Saturation", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Temperature, label = "Temperature", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Tint, label = "Tint", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Vibrance, label = "Vibrance", range = -100f..100f, step = 1f)
)

internal val detailsSection = listOf(
    AdjustmentControl(field = AdjustmentField.Clarity, label = "Clarity", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Dehaze, label = "Dehaze", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Structure, label = "Structure", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Centre, label = "Centré", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Sharpness, label = "Sharpness", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.LumaNoiseReduction, label = "Luminance NR", range = 0f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.ColorNoiseReduction, label = "Color NR", range = 0f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.ChromaticAberrationRedCyan, label = "CA Red/Cyan", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.ChromaticAberrationBlueYellow, label = "CA Blue/Yellow", range = -100f..100f, step = 1f)
)

internal val vignetteSection = listOf(
    AdjustmentControl(field = AdjustmentField.VignetteAmount, label = "Amount", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteMidpoint, label = "Midpoint", range = 0f..100f, step = 1f, defaultValue = 50f),
    AdjustmentControl(field = AdjustmentField.VignetteRoundness, label = "Roundness", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteFeather, label = "Feather", range = 0f..100f, step = 1f, defaultValue = 50f)
)

internal val adjustmentSections = listOf(
    "Basic" to basicSection,
    "Color" to colorSection,
    "Details" to detailsSection
)
