@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange

private data class GalleryItem(
    val projectId: String,
    val fileName: String,
    val thumbnail: Bitmap? = null,
    val rating: Int = 0
)

private enum class Screen {
    Gallery,
    Editor
}

private enum class AdjustmentField {
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

private data class CurvePointState(
    val x: Float,
    val y: Float
)

private fun defaultCurvePoints(): List<CurvePointState> {
    return listOf(CurvePointState(0f, 0f), CurvePointState(255f, 255f))
}

private data class CurvesState(
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

private data class HueSatLumState(
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

private data class ColorGradingState(
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

private data class AdjustmentState(
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
    val colorGrading: ColorGradingState = ColorGradingState()
) {
    fun toJsonObject(includeToneMapper: Boolean = true): JSONObject {
        return JSONObject().apply {
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

private enum class SubMaskMode {
    Additive,
    Subtractive,
}

private fun SubMaskMode.inverted(): SubMaskMode {
    return when (this) {
        SubMaskMode.Additive -> SubMaskMode.Subtractive
        SubMaskMode.Subtractive -> SubMaskMode.Additive
    }
}

private enum class SubMaskType(val id: String) {
    Brush("brush"),
    Linear("linear"),
    Radial("radial"),
    AiSubject("ai-subject"),
}

private enum class MaskTapMode {
    None,
    SetRadialCenter,
    SetLinearStart,
    SetLinearEnd,
}

private enum class MaskHandle {
    RadialCenter,
    LinearStart,
    LinearEnd,
}

private enum class BrushTool {
    Brush,
    Eraser,
}

private data class LinearMaskParametersState(
    val startX: Float = 0.5f,
    val startY: Float = 0.2f,
    val endX: Float = 0.5f,
    val endY: Float = 0.8f,
    val range: Float = 0.25f
)

private data class RadialMaskParametersState(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radiusX: Float = 0.35f,
    val radiusY: Float = 0.35f,
    val rotation: Float = 0f,
    val feather: Float = 0.5f
)

private data class AiSubjectMaskParametersState(
    val maskDataBase64: String? = null,
    val softness: Float = 0.25f
)

private enum class EditorPanelTab {
    Adjustments,
    Color,
    Effects,
    Masks,
}

private fun AdjustmentState.isNeutralForMask(): Boolean {
    fun nearZero(v: Float) = kotlin.math.abs(v) <= 0.000001f
    fun near(v: Float, target: Float) = kotlin.math.abs(v - target) <= 0.000001f
    return nearZero(brightness) &&
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
        colorGrading.isDefault()
}

private data class MaskPoint(
    val x: Float,
    val y: Float
)

private data class BrushLineState(
    val tool: String = "brush",
    val brushSize: Float,
    val feather: Float = 0.5f,
    val order: Long = 0L,
    val points: List<MaskPoint>
)

private data class SubMaskState(
    val id: String,
    val type: String = SubMaskType.Brush.id,
    val visible: Boolean = true,
    val mode: SubMaskMode = SubMaskMode.Additive,
    val lines: List<BrushLineState> = emptyList(),
    val linear: LinearMaskParametersState = LinearMaskParametersState(),
    val radial: RadialMaskParametersState = RadialMaskParametersState(),
    val aiSubject: AiSubjectMaskParametersState = AiSubjectMaskParametersState()
)

private data class MaskState(
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

private fun SubMaskState.toJsonObject(): JSONObject {
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

private fun buildMaskOverlayBitmap(mask: MaskState, targetWidth: Int, targetHeight: Int): Bitmap {
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

    val selection = IntArray(width * height) { 0 }

    val events = buildList {
        mask.subMasks.forEach { sub ->
            if (!sub.visible) return@forEach
            if (sub.type != SubMaskType.Brush.id) return@forEach
            sub.lines.forEach { line ->
                val effectiveMode =
                    if (line.tool == "eraser") SubMaskMode.Subtractive else sub.mode
                add(
                    BrushEvent(
                        order = line.order,
                        mode = effectiveMode,
                        brushSize = line.brushSize,
                        feather = line.feather,
                        points = line.points
                    )
                )
            }
        }
    }.sortedBy { it.order }

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
                selection[idx] = applyPixel(mode, selection[idx], intensity)
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

    mask.subMasks.forEach { sub ->
        if (!sub.visible) return@forEach
        when (sub.type) {
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
                        val intensityF = if (dist <= innerBound) {
                            1f
                        } else {
                            1f - (dist - innerBound) / (1f - innerBound).coerceAtLeast(0.01f)
                        }
                        val intensity = (intensityF.coerceIn(0f, 1f) * 255f).roundToInt()
                        if (intensity == 0) continue
                        val idx = row + x
                        selection[idx] = applyPixel(sub.mode, selection[idx], intensity)
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
                        selection[idx] = applyPixel(sub.mode, selection[idx], intensity)
                    }
                }
            }
        }
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
        if (sub.type != SubMaskType.AiSubject.id) return@forEach
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
            selection[i] = applyPixel(sub.mode, selection[i], v)
        }
    }

    val overlayPixels = IntArray(width * height)
    for (i in overlayPixels.indices) {
        val v = selection[i].coerceIn(0, 255)
        if (v == 0) continue
        val a = (v * 140 / 255).coerceIn(0, 255)
        overlayPixels[i] = (a shl 24) or (255 shl 16) or (23 shl 8) or 68
    }
    return Bitmap.createBitmap(overlayPixels, width, height, Bitmap.Config.ARGB_8888)
}

private data class AdjustmentControl(
    val field: AdjustmentField,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val defaultValue: Float = 0f,
    val formatter: (Float) -> String = { value -> String.format(Locale.US, "%.0f", value) }
)

private data class RenderRequest(
    val version: Long,
    val adjustmentsJson: String
)

private val basicSection = listOf(
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

private val colorSection = listOf(
    AdjustmentControl(field = AdjustmentField.Saturation, label = "Saturation", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Temperature, label = "Temperature", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Tint, label = "Tint", range = -100f..100f, step = 1f),
    AdjustmentControl(field = AdjustmentField.Vibrance, label = "Vibrance", range = -100f..100f, step = 1f)
)

private val detailsSection = listOf(
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

private val vignetteSection = listOf(
    AdjustmentControl(field = AdjustmentField.VignetteAmount, label = "Amount", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteMidpoint, label = "Midpoint", range = 0f..100f, step = 1f, defaultValue = 50f),
    AdjustmentControl(field = AdjustmentField.VignetteRoundness, label = "Roundness", range = -100f..100f, step = 1f, defaultValue = 0f),
    AdjustmentControl(field = AdjustmentField.VignetteFeather, label = "Feather", range = 0f..100f, step = 1f, defaultValue = 50f)
)

private val adjustmentSections = listOf(
    "Basic" to basicSection,
    "Color" to colorSection,
    "Details" to detailsSection
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KawaiiRawEditor)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawaiiRawEditorTheme {
                var showStartupSplash by remember { mutableStateOf(true) }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RapidRawEditorScreen()
                        if (showStartupSplash) {
                            StartupSplash(onFinished = { showStartupSplash = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RapidRawEditorScreen() {
    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var currentScreen by remember { mutableStateOf(Screen.Gallery) }
    var galleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Load existing projects on startup and when returning from editor
    LaunchedEffect(refreshTrigger) {
        val projects = storage.getAllProjects()
        galleryItems = withContext(Dispatchers.IO) {
            projects.map { metadata ->
                val thumbnailBytes = storage.loadThumbnail(metadata.id)
                val thumbnail = thumbnailBytes?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                GalleryItem(
                    projectId = metadata.id,
                    fileName = metadata.fileName,
                    thumbnail = thumbnail,
                    rating = metadata.rating
                )
            }
        }
    }

    when (currentScreen) {
        Screen.Gallery -> GalleryScreen(
            items = galleryItems,
            onOpenItem = { item ->
                selectedItem = item
                currentScreen = Screen.Editor
            },
            onAddClick = { newItem ->
                galleryItems = galleryItems + newItem
            },
            onRatingChangeMany = { projectIds, rating ->
                coroutineScope.launch {
                    val ids = projectIds.toSet()
                    withContext(Dispatchers.IO) {
                        ids.forEach { id -> storage.setRating(id, rating) }
                    }
                    galleryItems = galleryItems.map { item ->
                        if (item.projectId !in ids) item else item.copy(rating = rating.coerceIn(0, 5))
                    }
                }
            }
        )
        Screen.Editor -> EditorScreen(
            galleryItem = selectedItem,
            onBackClick = {
                currentScreen = Screen.Gallery
                refreshTrigger++
            }
        )
    }
}

@Composable
private fun StartupSplash(
    onFinished: () -> Unit
) {
    val bladePath = remember {
        Path().apply {
            moveTo(-10f, -150f)
            lineTo(80f, -150f)
            lineTo(140f, -40f)
            lineTo(0f, -40f)
            close()
        }
    }
    val centerHexPath = remember {
        Path().apply {
            moveTo(0f, -40f)
            lineTo(35f, -20f)
            lineTo(35f, 20f)
            lineTo(0f, 40f)
            lineTo(-35f, 20f)
            lineTo(-35f, -20f)
            close()
        }
    }

    val blades = remember {
        listOf(
            0f to Color(0xFFFF5252),
            60f to Color(0xFFFFD740),
            120f to Color(0xFF69F0AE),
            180f to Color(0xFF40C4FF),
            240f to Color(0xFF536DFE),
            300f to Color(0xFFE040FB)
        )
    }

    val bladeTravel = remember { blades.map { Animatable(1f) } }
    val centerScale = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val jobs = bladeTravel.mapIndexed { idx, anim ->
                launch {
                    delay(idx * 60L)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                    )
                }
            }
            jobs.joinAll()
        }

        centerScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
        delay(140)
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .graphicsLayer(alpha = overlayAlpha.value.coerceIn(0f, 1f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val scale = size.minDimension / 420f
            withTransform({
                scale(scaleX = scale, scaleY = scale)
                translate(left = size.width / 2f, top = size.height / 2f)
            }) {
                blades.forEachIndexed { idx, (rotationDeg, bladeColor) ->
                    val dist = bladeTravel[idx].value.coerceIn(0f, 1f) * 240f
                    rotate(degrees = rotationDeg, pivot = Offset.Zero) {
                        translate(top = -dist) {
                            drawPath(
                                path = bladePath,
                                color = bladeColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                val cScale = centerScale.value.coerceIn(0f, 1f)
                if (cScale > 0.001f) {
                    scale(scale = cScale, pivot = Offset.Zero) {
                        drawPath(path = centerHexPath, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryScreen(
    items: List<GalleryItem>,
    onOpenItem: (GalleryItem) -> Unit,
    onAddClick: (GalleryItem) -> Unit,
    onRatingChangeMany: (List<String>, Int) -> Unit
) {
    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    // Calculate columns based on screen width (each item ~150-200dp)
    val columns = when {
        screenWidthDp >= 900 -> 5  // Large tablets
        screenWidthDp >= 600 -> 4  // Small tablets
        screenWidthDp >= 400 -> 3  // Large phones
        else -> 2  // Small phones
    }
    
    val mimeTypes = arrayOf("image/x-sony-arw", "image/*")
    val pickRaw = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes != null) {
                val name = displayNameForUri(context, uri)
                // Import the file into app storage
                val projectId = withContext(Dispatchers.IO) {
                    storage.importRawFile(name, bytes)
                }
                val item = GalleryItem(
                    projectId = projectId,
                    fileName = name,
                    thumbnail = null
                )
                onAddClick(item)
            }
        }
    }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBulkExporting by remember { mutableStateOf(false) }
    var bulkExportDone by remember { mutableIntStateOf(0) }
    var bulkExportTotal by remember { mutableIntStateOf(0) }
    var bulkExportStatus by remember { mutableStateOf<String?>(null) }
    val bulkExportProgressAnim = remember { Animatable(0f) }
    val selectedItems = remember(items, selectedIds) { items.filter { it.projectId in selectedIds } }
    val uniformRating = remember(selectedItems) {
        selectedItems.map { it.rating }.distinct().singleOrNull()
    }

    fun startBulkExport(projectIds: List<String>) {
        if (isBulkExporting) return
        if (projectIds.isEmpty()) return

        isBulkExporting = true
        bulkExportDone = 0
        bulkExportTotal = projectIds.size

        coroutineScope.launch {
            var successCount = 0
            var failureCount = 0
            bulkExportProgressAnim.snapTo(0f)

            for ((idx, projectId) in projectIds.withIndex()) {
                val total = bulkExportTotal.coerceAtLeast(1)
                val start = idx.toFloat() / total.toFloat()
                val end = (idx + 1).toFloat() / total.toFloat()
                val slot = (end - start).coerceAtLeast(0f)

                if (bulkExportProgressAnim.value < start - 0.0001f) {
                    bulkExportProgressAnim.animateTo(
                        start,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                }

                val cap = (start + slot * 0.985f).coerceIn(0f, 1f)
                val fakeJob = launch {
                    val t0 = SystemClock.uptimeMillis()
                    while (isActive) {
                        val tSec = (SystemClock.uptimeMillis() - t0).toFloat() / 1000f
                        val frac = 1f - exp(-tSec / 1.4f)
                        val target = (start + (cap - start) * frac).coerceIn(start, cap)
                        val current = bulkExportProgressAnim.value
                        val next = current + (target - current) * 0.22f
                        bulkExportProgressAnim.snapTo(next.coerceIn(start, cap))
                        delay(16)
                    }
                }

                val (rawBytes, adjustmentsJson) = withContext(Dispatchers.IO) {
                    val raw = storage.loadRawBytes(projectId)
                    val adj = storage.loadAdjustments(projectId)
                    raw to adj
                }
                if (rawBytes == null) {
                    failureCount++
                    fakeJob.cancel()
                    fakeJob.join()
                    bulkExportDone = idx + 1
                    bulkExportProgressAnim.animateTo(
                        end,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                    continue
                }

                val jpegBytes = withContext(Dispatchers.Default) {
                    runCatching { LibRawDecoder.decodeFullRes(rawBytes, adjustmentsJson) }.getOrNull()
                }
                if (jpegBytes == null) {
                    failureCount++
                    fakeJob.cancel()
                    fakeJob.join()
                    bulkExportDone = idx + 1
                    bulkExportProgressAnim.animateTo(
                        end,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                    continue
                }

                val saved = withContext(Dispatchers.IO) { saveJpegToPictures(context, jpegBytes) }
                if (saved == null) failureCount++ else successCount++

                fakeJob.cancel()
                fakeJob.join()
                bulkExportDone = idx + 1
                bulkExportProgressAnim.animateTo(
                    end,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }

            isBulkExporting = false
            bulkExportStatus =
                if (failureCount == 0) "Exported $successCount JPEG(s)."
                else "Exported $successCount JPEG(s), $failureCount failed."
        }
    }

    LaunchedEffect(bulkExportStatus) {
        if (bulkExportStatus == null) return@LaunchedEffect
        delay(2500)
        bulkExportStatus = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            Column {
                CenterAlignedTopAppBar(title = { Text("Gallery") })
                if (isBulkExporting) {
                    LinearWavyProgressIndicator(
                        progress = { bulkExportProgressAnim.value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedIds.isEmpty() && !isBulkExporting) {
                FloatingActionButton(
                    onClick = { pickRaw.launch(mimeTypes) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add RAW file")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val gridBottomPadding = if (selectedIds.isNotEmpty()) 112.dp else 16.dp

            Column(modifier = Modifier.fillMaxSize()) {
                bulkExportStatus?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }

                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No RAW files yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button to add RAW files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize(),
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = gridBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items.size) { index ->
                            val item = items[index]
                            val isSelected = item.projectId in selectedIds
                            GalleryItemCard(
                                item = item,
                                selected = isSelected,
                                onClick = {
                                    if (isBulkExporting) return@GalleryItemCard
                                    if (selectedIds.isEmpty()) {
                                        onOpenItem(item)
                                    } else {
                                        selectedIds =
                                            if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
                                    }
                                },
                                onLongClick = {
                                    if (isBulkExporting) return@GalleryItemCard
                                    selectedIds =
                                        if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
                                }
                            )
                        }
                    }
                }
            }

            if (selectedIds.isNotEmpty()) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp) // Floating distance from screen bottom
                        .padding(horizontal = 16.dp) // Horizontal safety
                        .zIndex(1f),

                    // Use specific 'toolbar' prefix for colors in Expressive defaults
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        toolbarContentColor = MaterialTheme.colorScheme.onSurface
                    ),

                    // The Primary Action (Export) lives in the FAB slot
                    floatingActionButton = {
                        FloatingToolbarDefaults.StandardFloatingActionButton(
                            onClick = {
                                if (!isBulkExporting) {
                                    startBulkExport(selectedIds.toList())
                                }
                            },
                            // Manually handle disabled visual state since 'enabled' param doesn't exist
                            containerColor = if (isBulkExporting)
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isBulkExporting)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export Selection")
                        }
                    }
                ) {
                    // --- Toolbar Content ---

                    // 1. Navigation: Exit Selection Mode (Leading)
                    IconButton(
                        onClick = { selectedIds = emptySet() }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                    }

                    // 2. Context: Selection Count
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Optional Divider to separate Context from Secondary Actions
                    VerticalDivider(
                        modifier = Modifier
                            .height(16.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 3. Secondary Action: Rating
                    IconButton(
                        enabled = !isBulkExporting,
                        onClick = {
                            val next = when (uniformRating) {
                                null -> 1
                                5 -> 0
                                else -> (uniformRating + 1).coerceIn(0, 5)
                            }
                            onRatingChangeMany(selectedIds.toList(), next)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if ((uniformRating ?: 0) > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if ((uniformRating ?: 0) > 0) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Cycle rating"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryItemCard(
    item: GalleryItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (selected) 0.95f else 1f, label = "scale")
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (selected) 3.dp else 0.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(borderWidth, borderColor, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.thumbnail != null) {
                    Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "RAW",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.rating > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(item.rating.toString(), style = MaterialTheme.typography.labelSmall)
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EditorScreen(
    galleryItem: GalleryItem?,
    onBackClick: () -> Unit
) {
    if (galleryItem == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var sessionHandle by remember { mutableStateOf(0L) }
    var adjustments by remember { mutableStateOf(AdjustmentState()) }
    var masks by remember { mutableStateOf<List<MaskState>>(emptyList()) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var histogramData by remember { mutableStateOf<HistogramData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isGeneratingAiMask by remember { mutableStateOf(false) }
    var isDraggingMaskHandle by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var metadataJson by remember { mutableStateOf<String?>(null) }
    var showAiSubjectOverrideDialog by remember { mutableStateOf(false) }
    var aiSubjectOverrideTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    var panelTab by remember { mutableStateOf(EditorPanelTab.Adjustments) }
    var selectedMaskId by remember { mutableStateOf<String?>(null) }
    var selectedSubMaskId by remember { mutableStateOf<String?>(null) }
    var isPaintingMask by remember { mutableStateOf(false) }
    var maskTapMode by remember { mutableStateOf(MaskTapMode.None) }
    var brushSize by remember { mutableStateOf(60f) }
    var brushTool by remember { mutableStateOf(BrushTool.Brush) }
    var brushSoftness by remember { mutableStateOf(0.5f) }
    var eraserSoftness by remember { mutableStateOf(0.5f) }
    var showMaskOverlay by remember { mutableStateOf(true) }
    var maskOverlayBlinkKey by remember { mutableStateOf(0L) }
    val strokeOrder = remember { AtomicLong(0L) }

    val renderVersion = remember { AtomicLong(0L) }
    val lastPreviewVersion = remember { AtomicLong(0L) }
    val renderRequests = remember { Channel<RenderRequest>(capacity = Channel.CONFLATED) }
    val renderDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(renderDispatcher) {
        onDispose { renderDispatcher.close() }
    }

    DisposableEffect(sessionHandle) {
        val handleToRelease = sessionHandle
        onDispose {
            if (handleToRelease != 0L) {
                LibRawDecoder.releaseSession(handleToRelease)
            }
        }
    }

    // Load RAW file and adjustments from storage
    LaunchedEffect(galleryItem.projectId) {
        val raw = withContext(Dispatchers.IO) {
            storage.loadRawBytes(galleryItem.projectId)
        }
        if (raw == null) {
            sessionHandle = 0L
            errorMessage = "Failed to load RAW file."
            return@LaunchedEffect
        }
        sessionHandle = withContext(renderDispatcher) {
            runCatching { LibRawDecoder.createSession(raw) }.getOrDefault(0L)
        }
        if (sessionHandle == 0L) {
            errorMessage = "Failed to initialize native decoder."
            return@LaunchedEffect
        }
        val savedAdjustmentsJson = withContext(Dispatchers.IO) {
            storage.loadAdjustments(galleryItem.projectId)
        }
        // Parse saved adjustments if they exist
        if (savedAdjustmentsJson != "{}") {
            try {
                val json = org.json.JSONObject(savedAdjustmentsJson)

                fun parseCurvePoints(curvesObj: JSONObject?, key: String): List<CurvePointState> {
                    val arr = curvesObj?.optJSONArray(key) ?: return defaultCurvePoints()
                    val points = (0 until arr.length()).mapNotNull { idx ->
                        val pObj = arr.optJSONObject(idx) ?: return@mapNotNull null
                        CurvePointState(
                            x = pObj.optDouble("x", 0.0).toFloat(),
                            y = pObj.optDouble("y", 0.0).toFloat()
                        )
                    }
                    return if (points.size >= 2) points else defaultCurvePoints()
                }

                fun parseCurves(curvesObj: JSONObject?): CurvesState {
                    return CurvesState(
                        luma = parseCurvePoints(curvesObj, "luma"),
                        red = parseCurvePoints(curvesObj, "red"),
                        green = parseCurvePoints(curvesObj, "green"),
                        blue = parseCurvePoints(curvesObj, "blue")
                    )
                }

                fun parseHueSatLum(parent: JSONObject?, key: String): HueSatLumState {
                    val obj = parent?.optJSONObject(key) ?: return HueSatLumState()
                    return HueSatLumState(
                        hue = obj.optDouble("hue", 0.0).toFloat(),
                        saturation = obj.optDouble("saturation", 0.0).toFloat(),
                        luminance = obj.optDouble("luminance", 0.0).toFloat()
                    )
                }

                fun parseColorGrading(obj: JSONObject?): ColorGradingState {
                    if (obj == null) return ColorGradingState()
                    return ColorGradingState(
                        shadows = parseHueSatLum(obj, "shadows"),
                        midtones = parseHueSatLum(obj, "midtones"),
                        highlights = parseHueSatLum(obj, "highlights"),
                        blending = obj.optDouble("blending", 50.0).toFloat(),
                        balance = obj.optDouble("balance", 0.0).toFloat()
                    )
                }

                val parsedCurves = parseCurves(json.optJSONObject("curves"))
                val parsedColorGrading = parseColorGrading(json.optJSONObject("colorGrading"))
                adjustments = AdjustmentState(
                    brightness = json.optDouble("brightness", 0.0).toFloat(),
                    contrast = json.optDouble("contrast", 0.0).toFloat(),
                    highlights = json.optDouble("highlights", 0.0).toFloat(),
                    shadows = json.optDouble("shadows", 0.0).toFloat(),
                    whites = json.optDouble("whites", 0.0).toFloat(),
                    blacks = json.optDouble("blacks", 0.0).toFloat(),
                    saturation = json.optDouble("saturation", 0.0).toFloat(),
                    temperature = json.optDouble("temperature", 0.0).toFloat(),
                    tint = json.optDouble("tint", 0.0).toFloat(),
                    vibrance = json.optDouble("vibrance", 0.0).toFloat(),
                    clarity = json.optDouble("clarity", 0.0).toFloat(),
                    dehaze = json.optDouble("dehaze", 0.0).toFloat(),
                    structure = json.optDouble("structure", 0.0).toFloat(),
                    centre = json.optDouble("centre", 0.0).toFloat(),
                    vignetteAmount = json.optDouble("vignetteAmount", 0.0).toFloat(),
                    vignetteMidpoint = json.optDouble("vignetteMidpoint", 50.0).toFloat(),
                    vignetteRoundness = json.optDouble("vignetteRoundness", 0.0).toFloat(),
                    vignetteFeather = json.optDouble("vignetteFeather", 50.0).toFloat(),
                    sharpness = json.optDouble("sharpness", 0.0).toFloat(),
                    lumaNoiseReduction = json.optDouble("lumaNoiseReduction", 0.0).toFloat(),
                    colorNoiseReduction = json.optDouble("colorNoiseReduction", 0.0).toFloat(),
                    chromaticAberrationRedCyan = json.optDouble("chromaticAberrationRedCyan", 0.0).toFloat(),
                    chromaticAberrationBlueYellow = json.optDouble("chromaticAberrationBlueYellow", 0.0).toFloat(),
                    toneMapper = json.optString("toneMapper", "basic"),
                    curves = parsedCurves,
                    colorGrading = parsedColorGrading
                )

                val masksArr = json.optJSONArray("masks") ?: JSONArray()
                val parsedMasks = (0 until masksArr.length()).mapNotNull { idx ->
                    val maskObj = masksArr.optJSONObject(idx) ?: return@mapNotNull null
                    val maskId = maskObj.optString("id").takeIf { it.isNotBlank() }
                        ?: java.util.UUID.randomUUID().toString()
                    val maskAdjustmentsObj = maskObj.optJSONObject("adjustments") ?: JSONObject()
                    val maskCurves = parseCurves(maskAdjustmentsObj.optJSONObject("curves"))
                    val maskColorGrading = parseColorGrading(maskAdjustmentsObj.optJSONObject("colorGrading"))
                    val maskAdjustments = AdjustmentState(
                        brightness = maskAdjustmentsObj.optDouble("brightness", 0.0).toFloat(),
                        contrast = maskAdjustmentsObj.optDouble("contrast", 0.0).toFloat(),
                        highlights = maskAdjustmentsObj.optDouble("highlights", 0.0).toFloat(),
                        shadows = maskAdjustmentsObj.optDouble("shadows", 0.0).toFloat(),
                        whites = maskAdjustmentsObj.optDouble("whites", 0.0).toFloat(),
                        blacks = maskAdjustmentsObj.optDouble("blacks", 0.0).toFloat(),
                        saturation = maskAdjustmentsObj.optDouble("saturation", 0.0).toFloat(),
                        temperature = maskAdjustmentsObj.optDouble("temperature", 0.0).toFloat(),
                        tint = maskAdjustmentsObj.optDouble("tint", 0.0).toFloat(),
                        vibrance = maskAdjustmentsObj.optDouble("vibrance", 0.0).toFloat(),
                        clarity = maskAdjustmentsObj.optDouble("clarity", 0.0).toFloat(),
                        dehaze = maskAdjustmentsObj.optDouble("dehaze", 0.0).toFloat(),
                        structure = maskAdjustmentsObj.optDouble("structure", 0.0).toFloat(),
                        centre = maskAdjustmentsObj.optDouble("centre", 0.0).toFloat(),
                        vignetteAmount = maskAdjustmentsObj.optDouble("vignetteAmount", 0.0).toFloat(),
                        vignetteMidpoint = maskAdjustmentsObj.optDouble("vignetteMidpoint", 50.0).toFloat(),
                        vignetteRoundness = maskAdjustmentsObj.optDouble("vignetteRoundness", 0.0).toFloat(),
                        vignetteFeather = maskAdjustmentsObj.optDouble("vignetteFeather", 50.0).toFloat(),
                        sharpness = maskAdjustmentsObj.optDouble("sharpness", 0.0).toFloat(),
                        lumaNoiseReduction = maskAdjustmentsObj.optDouble("lumaNoiseReduction", 0.0).toFloat(),
                        colorNoiseReduction = maskAdjustmentsObj.optDouble("colorNoiseReduction", 0.0).toFloat(),
                        chromaticAberrationRedCyan = maskAdjustmentsObj.optDouble("chromaticAberrationRedCyan", 0.0).toFloat(),
                        chromaticAberrationBlueYellow = maskAdjustmentsObj.optDouble("chromaticAberrationBlueYellow", 0.0).toFloat(),
                        toneMapper = adjustments.toneMapper,
                        curves = maskCurves,
                        colorGrading = maskColorGrading
                    )

                    val subMasksArr = maskObj.optJSONArray("subMasks") ?: JSONArray()
                    val subMasks = (0 until subMasksArr.length()).mapNotNull { sIdx ->
                        val subObj = subMasksArr.optJSONObject(sIdx) ?: return@mapNotNull null
                        val subId = subObj.optString("id").takeIf { it.isNotBlank() }
                            ?: java.util.UUID.randomUUID().toString()
                        val subType = subObj.optString("type", SubMaskType.Brush.id).lowercase(Locale.US)
                        val modeStr = subObj.optString("mode", "additive").lowercase(Locale.US)
                        val mode = if (modeStr == "subtractive") SubMaskMode.Subtractive else SubMaskMode.Additive
                        val paramsObj = subObj.optJSONObject("parameters") ?: JSONObject()
                        val visible = subObj.optBoolean("visible", true)

                        when (subType) {
                            SubMaskType.Radial.id -> {
                                SubMaskState(
                                    id = subId,
                                    type = SubMaskType.Radial.id,
                                    visible = visible,
                                    mode = mode,
                                    radial = RadialMaskParametersState(
                                        centerX = paramsObj.optDouble("centerX", 0.5).toFloat(),
                                        centerY = paramsObj.optDouble("centerY", 0.5).toFloat(),
                                        radiusX = paramsObj.optDouble("radiusX", 0.35).toFloat(),
                                        radiusY = paramsObj.optDouble("radiusY", 0.35).toFloat(),
                                        rotation = paramsObj.optDouble("rotation", 0.0).toFloat(),
                                        feather = paramsObj.optDouble("feather", 0.5).toFloat()
                                    )
                                )
                            }

                            SubMaskType.Linear.id -> {
                                SubMaskState(
                                    id = subId,
                                    type = SubMaskType.Linear.id,
                                    visible = visible,
                                    mode = mode,
                                    linear = LinearMaskParametersState(
                                        startX = paramsObj.optDouble("startX", 0.5).toFloat(),
                                        startY = paramsObj.optDouble("startY", 0.2).toFloat(),
                                        endX = paramsObj.optDouble("endX", 0.5).toFloat(),
                                        endY = paramsObj.optDouble("endY", 0.8).toFloat(),
                                        range = paramsObj.optDouble("range", 0.25).toFloat()
                                    )
                                )
                            }

                            SubMaskType.AiSubject.id -> {
                                SubMaskState(
                                    id = subId,
                                    type = SubMaskType.AiSubject.id,
                                    visible = visible,
                                    mode = mode,
                                    aiSubject = AiSubjectMaskParametersState(
                                        maskDataBase64 = paramsObj.optString("maskDataBase64")
                                            .takeIf { it.isNotBlank() },
                                        softness = paramsObj.optDouble("softness", 0.25).toFloat().coerceIn(0f, 1f)
                                    )
                                )
                            }

                            else -> {
                                val linesArr = paramsObj.optJSONArray("lines") ?: JSONArray()
                                val lines = (0 until linesArr.length()).mapNotNull { lIdx ->
                                    val lineObj = linesArr.optJSONObject(lIdx) ?: return@mapNotNull null
                                    val pointsArr = lineObj.optJSONArray("points") ?: JSONArray()
                                    val points = (0 until pointsArr.length()).mapNotNull { pIdx ->
                                        val pObj = pointsArr.optJSONObject(pIdx) ?: return@mapNotNull null
                                        MaskPoint(
                                            x = pObj.optDouble("x", 0.0).toFloat(),
                                            y = pObj.optDouble("y", 0.0).toFloat()
                                        )
                                    }
                                    BrushLineState(
                                        tool = lineObj.optString("tool", "brush"),
                                        brushSize = lineObj.optDouble("brushSize", 50.0).toFloat(),
                                        feather = lineObj.optDouble("feather", 0.5).toFloat(),
                                        order = lineObj.optLong("order", 0L),
                                        points = points
                                    )
                                }
                                SubMaskState(
                                    id = subId,
                                    type = SubMaskType.Brush.id,
                                    visible = visible,
                                    mode = mode,
                                    lines = lines
                                )
                            }
                        }
                    }

                    MaskState(
                        id = maskId,
                        name = maskObj.optString("name", "Mask"),
                        visible = maskObj.optBoolean("visible", true),
                        invert = maskObj.optBoolean("invert", false),
                        opacity = maskObj.optDouble("opacity", 100.0).toFloat(),
                        adjustments = maskAdjustments,
                        subMasks = subMasks
                    )
                }
                val maxOrder = parsedMasks
                    .flatMap { it.subMasks }
                    .flatMap { it.lines }
                    .maxOfOrNull { it.order } ?: 0L
                strokeOrder.set(maxOf(strokeOrder.get(), maxOrder))
                masks = parsedMasks
                if (selectedMaskId == null && parsedMasks.isNotEmpty()) {
                    selectedMaskId = parsedMasks.first().id
                    selectedSubMaskId = parsedMasks.first().subMasks.firstOrNull()?.id
                }
            } catch (e: Exception) {
                // Keep default adjustments if parsing fails
            }
        }
    }

    LaunchedEffect(sessionHandle) {
        metadataJson = null
    }

    LaunchedEffect(previewBitmap) {
        val bmp = previewBitmap ?: run {
            histogramData = null
            return@LaunchedEffect
        }

        // Avoid recomputing the histogram for super-low / low previews while dragging.
        if (bmp.width < 512 && bmp.height < 512) {
            return@LaunchedEffect
        }

        delay(80)
        if (previewBitmap !== bmp) {
            return@LaunchedEffect
        }
        histogramData = withContext(Dispatchers.Default) { calculateHistogram(bmp) }
    }

    LaunchedEffect(adjustments, masks, isDraggingMaskHandle) {
        if (isDraggingMaskHandle) return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { adjustments.toJson(masks) }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(RenderRequest(version = version, adjustmentsJson = json))

        // Debounce persisting adjustments (I/O) for slider drags + mask edits.
        delay(350)
        withContext(Dispatchers.IO) {
            storage.saveAdjustments(galleryItem.projectId, json)
        }
    }

    LaunchedEffect(sessionHandle) {
        val handle = sessionHandle
        if (handle == 0L) return@LaunchedEffect

        // Ensure we always start with a render request for the current state.
        renderRequests.trySend(
            RenderRequest(
                version = renderVersion.incrementAndGet(),
                adjustmentsJson = adjustments.toJson(masks)
            )
        )

        var currentRequest = renderRequests.receive()
        while (true) {
            while (true) {
                val next = renderRequests.tryReceive().getOrNull() ?: break
                currentRequest = next
            }

            val requestVersion = currentRequest.version
            val requestJson = currentRequest.adjustmentsJson

            // Stage 1: super-low quality (fast feedback while dragging).
            val superLowBitmap = withContext(renderDispatcher) {
                val bytes = runCatching { LibRawDecoder.lowlowdecodeFromSession(handle, requestJson) }.getOrNull()
                bytes?.decodeToBitmap()
            }
            if (superLowBitmap != null && requestVersion > lastPreviewVersion.get()) {
                previewBitmap = superLowBitmap
                lastPreviewVersion.set(requestVersion)
            }

            // If the user is still moving the slider, keep updating the super-low preview only.
            val maybeUpdatedAfterSuperLow = withTimeoutOrNull(60) { renderRequests.receive() }
            if (maybeUpdatedAfterSuperLow != null) {
                currentRequest = maybeUpdatedAfterSuperLow
                continue
            }

            // Stage 2: low quality (still fast, but clearer).
            val lowBitmap = withContext(renderDispatcher) {
                val bytes = runCatching { LibRawDecoder.lowdecodeFromSession(handle, requestJson) }.getOrNull()
                bytes?.decodeToBitmap()
            }
            if (lowBitmap != null && requestVersion > lastPreviewVersion.get()) {
                previewBitmap = lowBitmap
                lastPreviewVersion.set(requestVersion)
            }

            // Debounce before running the expensive preview render.
            val maybeUpdatedAfterLow = withTimeoutOrNull(180) { renderRequests.receive() }
            if (maybeUpdatedAfterLow != null) {
                currentRequest = maybeUpdatedAfterLow
                continue
            }

            isLoading = true
            val fullBitmap = withContext(renderDispatcher) {
                val bytes = runCatching { LibRawDecoder.decodeFromSession(handle, requestJson) }.getOrNull()
                bytes?.decodeToBitmap()
            }
            isLoading = false

            if (requestVersion == renderVersion.get()) {
                errorMessage = if (fullBitmap == null) "Failed to render preview." else null
                if (fullBitmap != null) {
                    previewBitmap = fullBitmap
                    lastPreviewVersion.set(requestVersion)
                }

                // Generate and save thumbnail only for the latest completed render.
                fullBitmap?.let { bmp ->
                    withContext(Dispatchers.IO) {
                        val maxSize = 512
                        val scale = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height)
                        val scaledWidth = (bmp.width * scale).toInt()
                        val scaledHeight = (bmp.height * scale).toInt()
                        val thumbnail = Bitmap.createScaledBitmap(bmp, scaledWidth, scaledHeight, true)

                        val outputStream = java.io.ByteArrayOutputStream()
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val thumbnailBytes = outputStream.toByteArray()

                        storage.saveThumbnail(galleryItem.projectId, thumbnailBytes)

                        if (thumbnail != bmp) {
                            thumbnail.recycle()
                        }
                    }
                }
            }

            currentRequest = renderRequests.receive()
        }
    }

    val selectedMaskForOverlay = masks.firstOrNull { it.id == selectedMaskId }
    val selectedSubMaskForEdit = selectedMaskForOverlay?.subMasks?.firstOrNull { it.id == selectedSubMaskId }
    val isMaskMode = panelTab == EditorPanelTab.Masks
    val isInteractiveMaskingEnabled =
        isMaskMode && isPaintingMask && selectedMaskId != null && selectedSubMaskId != null &&
            (selectedSubMaskForEdit?.type == SubMaskType.Brush.id || selectedSubMaskForEdit?.type == SubMaskType.AiSubject.id)

    val onMaskTap: ((MaskPoint) -> Unit)? =
        if (!isMaskMode || maskTapMode == MaskTapMode.None) null
        else tap@{ point ->
            val maskId = selectedMaskId
            val subId = selectedSubMaskId
            if (maskId == null || subId == null) {
                maskTapMode = MaskTapMode.None
                return@tap
            }
            masks = masks.map { mask ->
                if (mask.id != maskId) return@map mask
                mask.copy(
                    subMasks = mask.subMasks.map { sub ->
                        if (sub.id != subId) return@map sub
                        when (maskTapMode) {
                            MaskTapMode.SetRadialCenter ->
                                if (sub.type != SubMaskType.Radial.id) sub
                                else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))

                            MaskTapMode.SetLinearStart ->
                                if (sub.type != SubMaskType.Linear.id) sub
                                else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))

                            MaskTapMode.SetLinearEnd ->
                                if (sub.type != SubMaskType.Linear.id) sub
                                else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))

                            MaskTapMode.None -> sub
                        }
                    }
                )
            }
            maskTapMode = MaskTapMode.None
        }

    val onBrushStrokeFinished: (List<MaskPoint>, Float) -> Unit = onBrush@{ points, brushSizeNorm ->
        val maskId = selectedMaskId ?: return@onBrush
        val subId = selectedSubMaskId ?: return@onBrush
        if (points.isEmpty()) return@onBrush
        val newLine = BrushLineState(
            tool = if (brushTool == BrushTool.Eraser) "eraser" else "brush",
            brushSize = brushSizeNorm,
            feather = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness,
            order = strokeOrder.incrementAndGet(),
            points = points
        )
        masks = masks.map { mask ->
            if (mask.id != maskId) return@map mask
            mask.copy(
                subMasks = mask.subMasks.map { sub ->
                    if (sub.id != subId) sub else sub.copy(lines = sub.lines + newLine)
                }
            )
        }
        showMaskOverlay = true
    }

    val onSubMaskHandleDrag: (MaskHandle, MaskPoint) -> Unit = onDrag@{ handle, point ->
        val maskId = selectedMaskId ?: return@onDrag
        val subId = selectedSubMaskId ?: return@onDrag
        masks = masks.map { mask ->
            if (mask.id != maskId) return@map mask
            mask.copy(
                subMasks = mask.subMasks.map { sub ->
                    if (sub.id != subId) return@map sub
                    when (handle) {
                        MaskHandle.RadialCenter ->
                            if (sub.type != SubMaskType.Radial.id) sub
                            else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))

                        MaskHandle.LinearStart ->
                            if (sub.type != SubMaskType.Linear.id) sub
                            else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))

                        MaskHandle.LinearEnd ->
                            if (sub.type != SubMaskType.Linear.id) sub
                            else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))
                    }
                }
            )
        }
        showMaskOverlay = true
    }

    val aiSubjectMaskGenerator = remember { AiSubjectMaskGenerator(context) }
    val onLassoFinished: (List<MaskPoint>) -> Unit = onLasso@{ points ->
        val maskId = selectedMaskId ?: return@onLasso
        val subId = selectedSubMaskId ?: return@onLasso
        val bmp = previewBitmap ?: return@onLasso
        if (points.size < 3) return@onLasso

        coroutineScope.launch {
            isGeneratingAiMask = true
            statusMessage = "Generating subject mask\u2026"
            val dataUrl = runCatching {
                aiSubjectMaskGenerator.generateSubjectMaskDataUrl(
                    previewBitmap = bmp,
                    lassoPoints = points.map { NormalizedPoint(it.x, it.y) }
                )
            }.getOrNull()

            if (dataUrl == null) {
                statusMessage = "Failed to generate subject mask."
            } else {
                masks = masks.map { mask ->
                    if (mask.id != maskId) return@map mask
                    mask.copy(
                        subMasks = mask.subMasks.map { sub ->
                            if (sub.id != subId) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = dataUrl))
                        }
                    )
                }
                showMaskOverlay = true
                statusMessage = "Subject mask added."
            }
            isGeneratingAiMask = false
            delay(1500)
            if (statusMessage == "Subject mask added.") statusMessage = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isTablet) {
                // Tablet layout: Full screen content with top bars overlaid
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                        // Left column: Preview (3/4 width)
                        Box(
                            modifier = Modifier
                                .weight(3f)
                                .fillMaxHeight()
                        ) {
                            ImagePreview(
                                bitmap = previewBitmap,
                                isLoading = isLoading || isGeneratingAiMask,
                                maskOverlay = selectedMaskForOverlay,
                                activeSubMask = selectedSubMaskForEdit,
                                isMaskMode = isMaskMode,
                                showMaskOverlay = showMaskOverlay,
                                maskOverlayBlinkKey = maskOverlayBlinkKey,
                                isPainting = isInteractiveMaskingEnabled,
                                brushSize = brushSize,
                                maskTapMode = maskTapMode,
                                onMaskTap = onMaskTap,
                                onBrushStrokeFinished = onBrushStrokeFinished,
                                onLassoFinished = onLassoFinished,
                                onSubMaskHandleDrag = onSubMaskHandleDrag,
                                onSubMaskHandleDragStateChange = { isDraggingMaskHandle = it },
                                onRequestAiSubjectOverride = {
                                    val maskId = selectedMaskId
                                    val subId = selectedSubMaskId
                                    if (maskId != null && subId != null) {
                                        aiSubjectOverrideTarget = maskId to subId
                                        showAiSubjectOverrideDialog = true
                                    }
                                }
                            )
                        }

                        // Right column: Controls (with top padding for status bar + app bar)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                            Spacer(modifier = Modifier.height(56.dp)) // Top app bar height
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TabbedEditorControls(
                                    panelTab = panelTab,
                                    onPanelTabChange = { panelTab = it },
                                    adjustments = adjustments,
                                    onAdjustmentsChange = { adjustments = it },
                                    histogramData = histogramData,
                                    masks = masks,
                                    onMasksChange = { masks = it },
                                    selectedMaskId = selectedMaskId,
                                    onSelectedMaskIdChange = { selectedMaskId = it },
                                    selectedSubMaskId = selectedSubMaskId,
                                    onSelectedSubMaskIdChange = { selectedSubMaskId = it },
                                    isPaintingMask = isPaintingMask,
                                    onPaintingMaskChange = { isPaintingMask = it },
                                    showMaskOverlay = showMaskOverlay,
                                    onShowMaskOverlayChange = { showMaskOverlay = it },
                                    onRequestMaskOverlayBlink = { maskOverlayBlinkKey++ },
                                    brushSize = brushSize,
                                    onBrushSizeChange = { brushSize = it },
                                    brushTool = brushTool,
                                    onBrushToolChange = { brushTool = it },
                                    brushSoftness = brushSoftness,
                                    onBrushSoftnessChange = { brushSoftness = it },
                                    eraserSoftness = eraserSoftness,
                                    onEraserSoftnessChange = { eraserSoftness = it },
                                    maskTapMode = maskTapMode,
                                    onMaskTapModeChange = { maskTapMode = it }
                                )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Export button at bottom
                            ExportButton(
                                sessionHandle = sessionHandle,
                                adjustments = adjustments,
                                masks = masks,
                                isExporting = isExporting,
                                nativeDispatcher = renderDispatcher,
                                context = context,
                                onExportStart = { isExporting = true },
                                onExportComplete = { success, message ->
                                    isExporting = false
                                    if (success) {
                                        statusMessage = message
                                        errorMessage = null
                                    } else {
                                        errorMessage = message
                                        statusMessage = null
                                    }
                                }
                            )
                            
                            errorMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            statusMessage?.let {
                                Text(
                                    text = it,
                                    color = Color(0xFF1B5E20),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                
                // Overlay status bar and top app bar on top
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Status bar background
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    
                    // Top app bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back to gallery",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = galleryItem.fileName,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                enabled = sessionHandle != 0L,
                                onClick = {
                                    showMetadataDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Image info",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                // Phone layout: fixed preview on top, fixed-height scrollable controls at bottom (like Lightroom)
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Status bar spacer
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    
                    // Top app bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back to gallery",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = galleryItem.fileName,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                enabled = sessionHandle != 0L,
                                onClick = {
                                    showMetadataDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Image info",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Fixed preview - fills remaining space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ImagePreview(
                            bitmap = previewBitmap,
                            isLoading = isLoading || isGeneratingAiMask,
                            maskOverlay = selectedMaskForOverlay,
                            activeSubMask = selectedSubMaskForEdit,
                            isMaskMode = isMaskMode,
                            showMaskOverlay = showMaskOverlay,
                            maskOverlayBlinkKey = maskOverlayBlinkKey,
                            isPainting = isInteractiveMaskingEnabled,
                            brushSize = brushSize,
                            maskTapMode = maskTapMode,
                            onMaskTap = onMaskTap,
                            onBrushStrokeFinished = onBrushStrokeFinished,
                            onLassoFinished = onLassoFinished,
                            onSubMaskHandleDrag = onSubMaskHandleDrag,
                            onSubMaskHandleDragStateChange = { isDraggingMaskHandle = it },
                            onRequestAiSubjectOverride = {
                                val maskId = selectedMaskId
                                val subId = selectedSubMaskId
                                if (maskId != null && subId != null) {
                                    aiSubjectOverrideTarget = maskId to subId
                                    showAiSubjectOverrideDialog = true
                                }
                            }
                        )
                    }

                    // Fixed-height scrollable controls panel at bottom
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Export button at top of controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                    ExportButton(
                                        sessionHandle = sessionHandle,
                                        adjustments = adjustments,
                                        masks = masks,
                                        isExporting = isExporting,
                                        nativeDispatcher = renderDispatcher,
                                        context = context,
                                        onExportStart = { isExporting = true },
                                    onExportComplete = { success, message ->
                                        isExporting = false
                                        if (success) {
                                            statusMessage = message
                                            errorMessage = null
                                        } else {
                                            errorMessage = message
                                            statusMessage = null
                                        }
                                    }
                                )
                            }
                            
                            errorMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            statusMessage?.let {
                                Text(
                                    text = it,
                                    color = Color(0xFF1B5E20),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            TabbedEditorControls(
                                panelTab = panelTab,
                                onPanelTabChange = { panelTab = it },
                                adjustments = adjustments,
                                onAdjustmentsChange = { adjustments = it },
                                histogramData = histogramData,
                                masks = masks,
                                onMasksChange = { masks = it },
                                selectedMaskId = selectedMaskId,
                                onSelectedMaskIdChange = { selectedMaskId = it },
                                selectedSubMaskId = selectedSubMaskId,
                                onSelectedSubMaskIdChange = { selectedSubMaskId = it },
                                isPaintingMask = isPaintingMask,
                                onPaintingMaskChange = { isPaintingMask = it },
                                showMaskOverlay = showMaskOverlay,
                                onShowMaskOverlayChange = { showMaskOverlay = it },
                                onRequestMaskOverlayBlink = { maskOverlayBlinkKey++ },
                                brushSize = brushSize,
                                onBrushSizeChange = { brushSize = it },
                                brushTool = brushTool,
                                onBrushToolChange = { brushTool = it },
                                brushSoftness = brushSoftness,
                                onBrushSoftnessChange = { brushSoftness = it },
                                eraserSoftness = eraserSoftness,
                                onEraserSoftnessChange = { eraserSoftness = it },
                                maskTapMode = maskTapMode,
                                onMaskTapModeChange = { maskTapMode = it }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
            
            // Status bar background overlay to prevent image showing through
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.surface
            ) {}
        }
    }

    if (showMetadataDialog) {
        val handle = sessionHandle
        LaunchedEffect(handle) {
            if (handle != 0L && metadataJson == null) {
                metadataJson = withContext(renderDispatcher) {
                    runCatching { LibRawDecoder.getMetadataJsonFromSession(handle) }.getOrNull()
                }
            }
        }

        val pairs = remember(metadataJson) {
            val json = metadataJson ?: return@remember emptyList()
            runCatching {
                val obj = JSONObject(json)
                listOf(
                    "Make" to obj.optString("make"),
                    "Model" to obj.optString("model"),
                    "Lens" to obj.optString("lens"),
                    "ISO" to obj.optString("iso"),
                    "Exposure" to obj.optString("exposureTime"),
                    "Aperture" to obj.optString("fNumber"),
                    "Focal Length" to obj.optString("focalLength"),
                    "Date" to obj.optString("dateTimeOriginal"),
                ).filter { it.second.isNotBlank() && it.second != "null" }
            }.getOrDefault(emptyList())
        }

        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            confirmButton = {
                TextButton(onClick = { showMetadataDialog = false }) { Text("Close") }
            },
            title = { Text("RAW Metadata") },
            text = {
                if (metadataJson == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Reading metadata...")
                    }
                } else if (pairs.isEmpty()) {
                    Text("No metadata available.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairs.forEach { (k, v) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        )
    }

    if (showAiSubjectOverrideDialog) {
        AlertDialog(
            onDismissRequest = {
                showAiSubjectOverrideDialog = false
                aiSubjectOverrideTarget = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = aiSubjectOverrideTarget
                        if (target != null) {
                            val (maskId, subId) = target
                            masks = masks.map { mask ->
                                if (mask.id != maskId) return@map mask
                                mask.copy(
                                    subMasks = mask.subMasks.map { sub ->
                                        if (sub.id != subId) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = null))
                                    }
                                )
                            }
                            showMaskOverlay = true
                            statusMessage = "Cleared subject mask. Draw a new one."
                        }
                        showAiSubjectOverrideDialog = false
                        aiSubjectOverrideTarget = null
                    }
                ) { Text("Replace") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAiSubjectOverrideDialog = false
                        aiSubjectOverrideTarget = null
                    }
                ) { Text("Cancel") }
            },
            title = { Text("Replace subject mask?") },
            text = { Text("This will delete the current subject mask so you can draw a new one.") }
        )
    }
}

@Composable
private fun ExportButton(
    sessionHandle: Long,
    adjustments: AdjustmentState,
    masks: List<MaskState>,
    isExporting: Boolean,
    nativeDispatcher: CoroutineDispatcher,
    context: Context,
    onExportStart: () -> Unit,
    onExportComplete: (Boolean, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    Button(
        onClick = {
            if (isExporting || sessionHandle == 0L) return@Button
            val currentAdjustments = adjustments
            val currentMasks = masks
            onExportStart()
            coroutineScope.launch {
                val currentJson = withContext(Dispatchers.Default) { currentAdjustments.toJson(currentMasks) }
                val fullBytes = withContext(nativeDispatcher) {
                    runCatching { LibRawDecoder.decodeFullResFromSession(sessionHandle, currentJson) }.getOrNull()
                }
                if (fullBytes == null) {
                    onExportComplete(false, "Export failed.")
                } else {
                    val savedUri = withContext(Dispatchers.IO) {
                        saveJpegToPictures(context, fullBytes)
                    }
                    if (savedUri != null) {
                        onExportComplete(true, "Saved to $savedUri")
                    } else {
                        onExportComplete(false, "Export failed: could not save JPEG.")
                    }
                }
            }
        },
        enabled = sessionHandle != 0L && !isExporting
    ) {
        if (isExporting) {
            LoadingIndicator(
                modifier = Modifier
                    .size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text("Export JPEG")
    }
}

@Composable
private fun TabbedEditorControls(
    panelTab: EditorPanelTab,
    onPanelTabChange: (EditorPanelTab) -> Unit,
    adjustments: AdjustmentState,
    onAdjustmentsChange: (AdjustmentState) -> Unit,
    histogramData: HistogramData?,
    masks: List<MaskState>,
    onMasksChange: (List<MaskState>) -> Unit,
    selectedMaskId: String?,
    onSelectedMaskIdChange: (String?) -> Unit,
    selectedSubMaskId: String?,
    onSelectedSubMaskIdChange: (String?) -> Unit,
    isPaintingMask: Boolean,
    onPaintingMaskChange: (Boolean) -> Unit,
    showMaskOverlay: Boolean,
    onShowMaskOverlayChange: (Boolean) -> Unit,
    onRequestMaskOverlayBlink: () -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushTool: BrushTool,
    onBrushToolChange: (BrushTool) -> Unit,
    brushSoftness: Float,
    onBrushSoftnessChange: (Float) -> Unit,
    eraserSoftness: Float,
    onEraserSoftnessChange: (Float) -> Unit,
    maskTapMode: MaskTapMode,
    onMaskTapModeChange: (MaskTapMode) -> Unit
) {
    val tabs = listOf(EditorPanelTab.Adjustments, EditorPanelTab.Color, EditorPanelTab.Effects, EditorPanelTab.Masks)
    val maskTabsByMaskId = remember { mutableStateMapOf<String, Int>() }
    TabRow(selectedTabIndex = tabs.indexOf(panelTab).coerceAtLeast(0)) {
        Tab(
            selected = panelTab == EditorPanelTab.Adjustments,
            onClick = {
                onPaintingMaskChange(false)
                onMaskTapModeChange(MaskTapMode.None)
                onPanelTabChange(EditorPanelTab.Adjustments)
            },
            text = { Text("Adjust") }
        )
        Tab(
            selected = panelTab == EditorPanelTab.Color,
            onClick = {
                onPaintingMaskChange(false)
                onMaskTapModeChange(MaskTapMode.None)
                onPanelTabChange(EditorPanelTab.Color)
            },
            text = { Text("Color") }
        )
        Tab(
            selected = panelTab == EditorPanelTab.Effects,
            onClick = {
                onPaintingMaskChange(false)
                onMaskTapModeChange(MaskTapMode.None)
                onPanelTabChange(EditorPanelTab.Effects)
            },
            text = { Text("Effects") }
        )
        Tab(
            selected = panelTab == EditorPanelTab.Masks,
            onClick = {
                onShowMaskOverlayChange(true)
                onMaskTapModeChange(MaskTapMode.None)
                onPanelTabChange(EditorPanelTab.Masks)
            },
            text = { Text("Mask") }
        )
    }

    when (panelTab) {
        EditorPanelTab.Adjustments -> {
            ToneMapperSection(
                toneMapper = adjustments.toneMapper,
                onToneMapperChange = { mapper -> onAdjustmentsChange(adjustments.withToneMapper(mapper)) }
            )

            adjustmentSections.forEach { (sectionTitle, controls) ->
                PanelSectionCard(title = sectionTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        controls.forEach { control ->
                            val currentValue = adjustments.valueFor(control.field)
                            AdjustmentSlider(
                                label = control.label,
                                value = currentValue,
                                range = control.range,
                                step = control.step,
                                defaultValue = control.defaultValue,
                                formatter = control.formatter,
                                onValueChange = { snapped ->
                                    onAdjustmentsChange(adjustments.withValue(control.field, snapped))
                                }
                            )
                        }
                    }
                }
            }
        }

        EditorPanelTab.Color -> {
            PanelSectionCard(
                title = "Curves",
                subtitle = "Tap to add points • Drag to adjust"
            ) {
                CurvesEditor(
                    adjustments = adjustments,
                    histogramData = histogramData,
                    onAdjustmentsChange = onAdjustmentsChange
                )
            }

            PanelSectionCard(
                title = "Color Grading",
                subtitle = "Shadows / Midtones / Highlights"
            ) {
                ColorGradingEditor(
                    colorGrading = adjustments.colorGrading,
                    onColorGradingChange = { updated ->
                        onAdjustmentsChange(adjustments.copy(colorGrading = updated))
                    }
                )
            }
        }

        EditorPanelTab.Effects -> {
            PanelSectionCard(
                title = "Vignette",
                subtitle = "Post-crop vignette"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    vignetteSection.forEach { control ->
                        val currentValue = adjustments.valueFor(control.field)
                        AdjustmentSlider(
                            label = control.label,
                            value = currentValue,
                            range = control.range,
                            step = control.step,
                            defaultValue = control.defaultValue,
                            formatter = control.formatter,
                            onValueChange = { snapped ->
                                onAdjustmentsChange(adjustments.withValue(control.field, snapped))
                            }
                        )
                    }
                }
            }
        }

        EditorPanelTab.Masks -> {
            val selectedMask = masks.firstOrNull { it.id == selectedMaskId }
            val selectedSubMask = selectedMask?.subMasks?.firstOrNull { it.id == selectedSubMaskId }

            fun newSubMaskState(id: String, mode: SubMaskMode, type: SubMaskType): SubMaskState {
                return when (type) {
                    SubMaskType.Brush -> SubMaskState(id = id, type = type.id, mode = mode)
                    SubMaskType.Linear -> SubMaskState(id = id, type = type.id, mode = mode, linear = LinearMaskParametersState())
                    SubMaskType.Radial -> SubMaskState(id = id, type = type.id, mode = mode, radial = RadialMaskParametersState())
                    SubMaskType.AiSubject -> SubMaskState(id = id, type = type.id, mode = mode, aiSubject = AiSubjectMaskParametersState())
                }
            }

            fun duplicateMaskState(mask: MaskState, invertDuplicate: Boolean): MaskState {
                val newId = java.util.UUID.randomUUID().toString()
                fun copySubMask(sub: SubMaskState): SubMaskState = sub.copy(id = java.util.UUID.randomUUID().toString())
                val newName = if (mask.name.endsWith(" Copy")) "${mask.name} 2" else "${mask.name} Copy"
                return mask.copy(
                    id = newId,
                    name = newName,
                    invert = if (invertDuplicate) !mask.invert else mask.invert,
                    subMasks = mask.subMasks.map(::copySubMask)
                )
            }

            var showAddMaskMenu by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(
                    onClick = { showAddMaskMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Mask")
                }
                DropdownMenu(
                    expanded = showAddMaskMenu,
                    onDismissRequest = { showAddMaskMenu = false }
                ) {
                    listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial).forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (type) {
                                        SubMaskType.AiSubject -> "Subject"
                                        SubMaskType.Brush -> "Brush"
                                        SubMaskType.Linear -> "Gradient"
                                        SubMaskType.Radial -> "Radial"
                                    }
                                )
                            },
                            onClick = {
                                showAddMaskMenu = false
                                onMaskTapModeChange(MaskTapMode.None)
                                val newMaskId = java.util.UUID.randomUUID().toString()
                                val newSubId = java.util.UUID.randomUUID().toString()
                                val newMask = MaskState(
                                    id = newMaskId,
                                    name = "Mask ${masks.size + 1}",
                                    subMasks = listOf(newSubMaskState(newSubId, SubMaskMode.Additive, type))
                                )
                                onMasksChange(masks + newMask)
                                onSelectedMaskIdChange(newMaskId)
                                onSelectedSubMaskIdChange(newSubId)
                                onPaintingMaskChange(type == SubMaskType.Brush || type == SubMaskType.AiSubject)
                                onRequestMaskOverlayBlink()
                            }
                        )
                    }
                }
            }

            if (masks.isEmpty()) {
                Text(
                    text = "Create a mask to start painting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                masks.forEachIndexed { index, mask ->
                    val isSelected = mask.id == selectedMaskId
                    var showMaskMenu by remember(mask.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = mask.visible,
                                onCheckedChange = { checked ->
                                    onMasksChange(
                                        masks.map { m -> if (m.id == mask.id) m.copy(visible = checked) else m }
                                    )
                                }
                            )
                            Text(
                                text = mask.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onPaintingMaskChange(false)
                                        onShowMaskOverlayChange(mask.adjustments.isNeutralForMask())
                                        onSelectedMaskIdChange(mask.id)
                                        onSelectedSubMaskIdChange(mask.subMasks.firstOrNull()?.id)
                                        onRequestMaskOverlayBlink()
                                    },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            IconButton(
                                onClick = {
                                    if (index <= 0) return@IconButton
                                    val reordered = masks.toMutableList()
                                    val tmp = reordered[index - 1]
                                    reordered[index - 1] = reordered[index]
                                    reordered[index] = tmp
                                    onMasksChange(reordered.toList())
                                },
                                enabled = index > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move mask up"
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index >= masks.lastIndex) return@IconButton
                                    val reordered = masks.toMutableList()
                                    val tmp = reordered[index + 1]
                                    reordered[index + 1] = reordered[index]
                                    reordered[index] = tmp
                                    onMasksChange(reordered.toList())
                                },
                                enabled = index < masks.lastIndex
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move mask down"
                                )
                            }
                            Box {
                                IconButton(onClick = { showMaskMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Mask options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMaskMenu,
                                    onDismissRequest = { showMaskMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        onClick = {
                                            showMaskMenu = false
                                            val duplicated = duplicateMaskState(mask, invertDuplicate = false)
                                            val updated = masks.toMutableList().apply { add(index + 1, duplicated) }.toList()
                                            onMasksChange(updated)
                                            onPaintingMaskChange(false)
                                            onShowMaskOverlayChange(duplicated.adjustments.isNeutralForMask())
                                            onSelectedMaskIdChange(duplicated.id)
                                            onSelectedSubMaskIdChange(duplicated.subMasks.firstOrNull()?.id)
                                            onRequestMaskOverlayBlink()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate and invert") },
                                        onClick = {
                                            showMaskMenu = false
                                            val duplicated = duplicateMaskState(mask, invertDuplicate = true)
                                            val updated = masks.toMutableList().apply { add(index + 1, duplicated) }.toList()
                                            onMasksChange(updated)
                                            onPaintingMaskChange(false)
                                            onShowMaskOverlayChange(duplicated.adjustments.isNeutralForMask())
                                            onSelectedMaskIdChange(duplicated.id)
                                            onSelectedSubMaskIdChange(duplicated.subMasks.firstOrNull()?.id)
                                            onRequestMaskOverlayBlink()
                                        }
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val remaining = masks.filterNot { it.id == mask.id }
                                    onMasksChange(remaining)
                                    if (selectedMaskId == mask.id) {
                                        onPaintingMaskChange(false)
                                        onSelectedMaskIdChange(remaining.firstOrNull()?.id)
                                        onSelectedSubMaskIdChange(remaining.firstOrNull()?.subMasks?.firstOrNull()?.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete mask"
                                )
                            }
                        }
                    }
                }
            }

            if (selectedMask == null) return

            PanelSectionCard(title = "Mask Settings") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Invert", color = MaterialTheme.colorScheme.onSurface)
                    Checkbox(
                        checked = selectedMask.invert,
                        onCheckedChange = { checked ->
                            onMasksChange(
                                masks.map { m -> if (m.id == selectedMask.id) m.copy(invert = checked) else m }
                            )
                        }
                    )
                }

                AdjustmentSlider(
                    label = "Opacity",
                    value = selectedMask.opacity.coerceIn(0f, 100f),
                    range = 0f..100f,
                    step = 1f,
                    defaultValue = 100f,
                    formatter = { "${it.roundToInt()}%" },
                    onValueChange = { newValue ->
                        onMasksChange(
                            masks.map { m -> if (m.id == selectedMask.id) m.copy(opacity = newValue) else m }
                        )
                    }
                )
            }

            PanelSectionCard(title = "Submasks", subtitle = "Brush / gradient / radial") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var showAddSubMenu by remember { mutableStateOf(false) }
                var showSubSubMenu by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { showAddSubMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add +") }
                    DropdownMenu(
                        expanded = showAddSubMenu,
                        onDismissRequest = { showAddSubMenu = false }
                    ) {
                        listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial).forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            SubMaskType.AiSubject -> "Subject"
                                            SubMaskType.Brush -> "Brush"
                                            SubMaskType.Linear -> "Gradient"
                                            SubMaskType.Radial -> "Radial"
                                        }
                                    )
                                },
                                onClick = {
                                    showAddSubMenu = false
                                    onMaskTapModeChange(MaskTapMode.None)
                                    val newSubId = java.util.UUID.randomUUID().toString()
                                    val updated = masks.map { m ->
                                        if (m.id != selectedMask.id) m
                                        else m.copy(subMasks = m.subMasks + newSubMaskState(newSubId, SubMaskMode.Additive, type))
                                    }
                                    onMasksChange(updated)
                                    onSelectedSubMaskIdChange(newSubId)
                                    onPaintingMaskChange(type == SubMaskType.Brush || type == SubMaskType.AiSubject)
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { showSubSubMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Subtract -") }
                    DropdownMenu(
                        expanded = showSubSubMenu,
                        onDismissRequest = { showSubSubMenu = false }
                    ) {
                        listOf(SubMaskType.AiSubject, SubMaskType.Brush, SubMaskType.Linear, SubMaskType.Radial).forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            SubMaskType.AiSubject -> "Subject"
                                            SubMaskType.Brush -> "Brush"
                                            SubMaskType.Linear -> "Gradient"
                                            SubMaskType.Radial -> "Radial"
                                        }
                                    )
                                },
                                onClick = {
                                    showSubSubMenu = false
                                    onMaskTapModeChange(MaskTapMode.None)
                                    val newSubId = java.util.UUID.randomUUID().toString()
                                    val updated = masks.map { m ->
                                        if (m.id != selectedMask.id) m
                                        else m.copy(subMasks = m.subMasks + newSubMaskState(newSubId, SubMaskMode.Subtractive, type))
                                    }
                                    onMasksChange(updated)
                                    onSelectedSubMaskIdChange(newSubId)
                                    onPaintingMaskChange(type == SubMaskType.Brush || type == SubMaskType.AiSubject)
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedMask.subMasks.forEach { sub ->
                    val isSelected = sub.id == selectedSubMaskId
                    var showSubMaskMenu by remember(sub.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = sub.visible,
                                onCheckedChange = { checked ->
                                    val updated = masks.map { m ->
                                        if (m.id != selectedMask.id) m
                                        else m.copy(
                                            subMasks = m.subMasks.map { s -> if (s.id == sub.id) s.copy(visible = checked) else s }
                                        )
                                    }
                                    onMasksChange(updated)
                                }
                            )
                            Text(
                                text = buildString {
                                    append(if (sub.mode == SubMaskMode.Additive) "Add" else "Subtract")
                                    append(" ")
                                    append(
                                        when (sub.type) {
                                            SubMaskType.AiSubject.id -> "Subject"
                                            SubMaskType.Linear.id -> "Gradient"
                                            SubMaskType.Radial.id -> "Radial"
                                            else -> "Brush"
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onMaskTapModeChange(MaskTapMode.None)
                                        onSelectedSubMaskIdChange(sub.id)
                                        onPaintingMaskChange(sub.type == SubMaskType.Brush.id || sub.type == SubMaskType.AiSubject.id)
                                    },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Box {
                                IconButton(onClick = { showSubMaskMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Submask options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSubMaskMenu,
                                    onDismissRequest = { showSubMaskMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Set to Add") },
                                        onClick = {
                                            showSubMaskMenu = false
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(subMasks = m.subMasks.map { s ->
                                                    if (s.id != sub.id) s else s.copy(mode = SubMaskMode.Additive)
                                                })
                                            }
                                            onMasksChange(updated)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Set to Subtract") },
                                        onClick = {
                                            showSubMaskMenu = false
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(subMasks = m.subMasks.map { s ->
                                                    if (s.id != sub.id) s else s.copy(mode = SubMaskMode.Subtractive)
                                                })
                                            }
                                            onMasksChange(updated)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Invert (toggle add/subtract)") },
                                        onClick = {
                                            showSubMaskMenu = false
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else m.copy(subMasks = m.subMasks.map { s ->
                                                    if (s.id != sub.id) s else s.copy(mode = s.mode.inverted())
                                                })
                                            }
                                            onMasksChange(updated)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        onClick = {
                                            showSubMaskMenu = false
                                            val newId = java.util.UUID.randomUUID().toString()
                                            val copy = sub.copy(id = newId)
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else {
                                                    val out = m.subMasks.toMutableList()
                                                    val idx = out.indexOfFirst { it.id == sub.id }.coerceAtLeast(0)
                                                    out.add(idx + 1, copy)
                                                    m.copy(subMasks = out.toList())
                                                }
                                            }
                                            onMasksChange(updated)
                                            onMaskTapModeChange(MaskTapMode.None)
                                            onSelectedSubMaskIdChange(newId)
                                            onPaintingMaskChange(copy.type == SubMaskType.Brush.id || copy.type == SubMaskType.AiSubject.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate and invert") },
                                        onClick = {
                                            showSubMaskMenu = false
                                            val newId = java.util.UUID.randomUUID().toString()
                                            val copy = sub.copy(id = newId, mode = sub.mode.inverted())
                                            val updated = masks.map { m ->
                                                if (m.id != selectedMask.id) m
                                                else {
                                                    val out = m.subMasks.toMutableList()
                                                    val idx = out.indexOfFirst { it.id == sub.id }.coerceAtLeast(0)
                                                    out.add(idx + 1, copy)
                                                    m.copy(subMasks = out.toList())
                                                }
                                            }
                                            onMasksChange(updated)
                                            onMaskTapModeChange(MaskTapMode.None)
                                            onSelectedSubMaskIdChange(newId)
                                            onPaintingMaskChange(copy.type == SubMaskType.Brush.id || copy.type == SubMaskType.AiSubject.id)
                                        }
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val updated = masks.map { m ->
                                        if (m.id != selectedMask.id) m
                                        else m.copy(subMasks = m.subMasks.filterNot { it.id == sub.id })
                                    }
                                    onMasksChange(updated)
                                    if (selectedSubMaskId == sub.id) {
                                        onPaintingMaskChange(false)
                                        onSelectedSubMaskIdChange(updated.firstOrNull { it.id == selectedMask.id }?.subMasks?.firstOrNull()?.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete submask"
                                )
                            }
                        }
                    }
                }
            }
            }

            PanelSectionCard(title = "Tool", subtitle = "Paint / overlay / shape controls") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedSubMask?.type) {
                    SubMaskType.Brush.id -> {
                        Text("Paint", color = MaterialTheme.colorScheme.onSurface)
                        Checkbox(
                            checked = isPaintingMask,
                            onCheckedChange = { checked ->
                                onMaskTapModeChange(MaskTapMode.None)
                                onPaintingMaskChange(checked)
                                if (checked) onShowMaskOverlayChange(true)
                            }
                        )
                    }

                    SubMaskType.AiSubject.id -> {
                        Text("Lasso", color = MaterialTheme.colorScheme.onSurface)
                        Checkbox(
                            checked = isPaintingMask,
                            onCheckedChange = { checked ->
                                onMaskTapModeChange(MaskTapMode.None)
                                onPaintingMaskChange(checked)
                                if (checked) onShowMaskOverlayChange(true)
                            }
                        )
                    }

                    else -> {
                        Text(
                            text = when (selectedSubMask?.type) {
                                SubMaskType.Linear.id -> "Gradient"
                                SubMaskType.Radial.id -> "Radial"
                                else -> "Mask"
                            },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Overlay", color = MaterialTheme.colorScheme.onSurface)
                Checkbox(
                    checked = showMaskOverlay,
                    onCheckedChange = { checked -> onShowMaskOverlayChange(checked) },
                    enabled = selectedMask.adjustments.isNeutralForMask()
                )
            }

            if (selectedSubMask == null) {
                Text(
                    text = "Select a submask to paint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
            if (maskTapMode != MaskTapMode.None) {
                Text(
                    text = when (maskTapMode) {
                        MaskTapMode.SetRadialCenter -> "Tap the image to set radial center"
                        MaskTapMode.SetLinearStart -> "Tap the image to set gradient start"
                        MaskTapMode.SetLinearEnd -> "Tap the image to set gradient end"
                        MaskTapMode.None -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { onMaskTapModeChange(MaskTapMode.None) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel") }
            }

            when (selectedSubMask.type) {
                SubMaskType.Brush.id -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onBrushToolChange(BrushTool.Brush) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (brushTool == BrushTool.Brush)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) { Text("Brush") }
                        FilledTonalButton(
                            onClick = { onBrushToolChange(BrushTool.Eraser) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (brushTool == BrushTool.Eraser)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) { Text("Eraser") }
                    }

                    Text("Brush Size: ${brushSize.roundToInt()} px", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = brushSize.coerceIn(2f, 400f),
                        onValueChange = { onBrushSizeChange(it) },
                        valueRange = 2f..400f
                    )

                    val softness = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness
                    Text("Softness: ${(softness * 100f).roundToInt()}%", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = softness.coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            if (brushTool == BrushTool.Eraser) onEraserSoftnessChange(newValue.coerceIn(0f, 1f))
                            else onBrushSoftnessChange(newValue.coerceIn(0f, 1f))
                        },
                        valueRange = 0f..1f
                    )
                }

                SubMaskType.AiSubject.id -> {
                    Text(
                        text = "Draw around your subject to generate an AI mask.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Softness: ${(selectedSubMask.aiSubject.softness.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = selectedSubMask.aiSubject.softness.coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            val updated = masks.map { m ->
                                if (m.id != selectedMask.id) m
                                else m.copy(
                                    subMasks = m.subMasks.map { s ->
                                        if (s.id != selectedSubMask.id) s else s.copy(aiSubject = s.aiSubject.copy(softness = newValue.coerceIn(0f, 1f)))
                                    }
                                )
                            }
                            onMasksChange(updated)
                            onShowMaskOverlayChange(true)
                        },
                        valueRange = 0f..1f
                    )

                    FilledTonalButton(
                        onClick = {
                            val updated = masks.map { m ->
                                if (m.id != selectedMask.id) m
                                else m.copy(
                                    subMasks = m.subMasks.map { s ->
                                        if (s.id != selectedSubMask.id) s else s.copy(aiSubject = s.aiSubject.copy(maskDataBase64 = null))
                                    }
                                )
                            }
                            onMasksChange(updated)
                            onShowMaskOverlayChange(true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear AI Mask") }
                }

                SubMaskType.Radial.id -> {
                    FilledTonalButton(
                        onClick = {
                            onPaintingMaskChange(false)
                            onMaskTapModeChange(MaskTapMode.SetRadialCenter)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Set Center (Tap Image)") }

                    Text("Radius: ${(selectedSubMask.radial.radiusX * 100f).roundToInt()}%", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = selectedSubMask.radial.radiusX.coerceIn(0.01f, 1.5f),
                        onValueChange = { newValue ->
                            val updated = masks.map { m ->
                                if (m.id != selectedMask.id) m
                                else m.copy(
                                    subMasks = m.subMasks.map { s ->
                                        if (s.id != selectedSubMask.id) s
                                        else s.copy(radial = s.radial.copy(radiusX = newValue, radiusY = newValue))
                                    }
                                )
                            }
                            onMasksChange(updated)
                        },
                        valueRange = 0.01f..1.5f
                    )

                    Text("Softness: ${(selectedSubMask.radial.feather * 100f).roundToInt()}%", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = selectedSubMask.radial.feather.coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            val updated = masks.map { m ->
                                if (m.id != selectedMask.id) m
                                else m.copy(
                                    subMasks = m.subMasks.map { s ->
                                        if (s.id != selectedSubMask.id) s
                                        else s.copy(radial = s.radial.copy(feather = newValue))
                                    }
                                )
                            }
                            onMasksChange(updated)
                        },
                        valueRange = 0f..1f
                    )
                }

                SubMaskType.Linear.id -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onPaintingMaskChange(false)
                                onMaskTapModeChange(MaskTapMode.SetLinearStart)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Set Start") }
                        FilledTonalButton(
                            onClick = {
                                onPaintingMaskChange(false)
                                onMaskTapModeChange(MaskTapMode.SetLinearEnd)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Set End") }
                    }

                    Text("Softness: ${(selectedSubMask.linear.range * 100f).roundToInt()}%", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = selectedSubMask.linear.range.coerceIn(0.01f, 1.5f),
                        onValueChange = { newValue ->
                            val updated = masks.map { m ->
                                if (m.id != selectedMask.id) m
                                else m.copy(
                                    subMasks = m.subMasks.map { s ->
                                        if (s.id != selectedSubMask.id) s
                                        else s.copy(linear = s.linear.copy(range = newValue))
                                    }
                                )
                            }
                            onMasksChange(updated)
                        },
                        valueRange = 0.01f..1.5f
                    )
                }
            }
            }
            }

            val maskInnerTabs = listOf("Adjust", "Color")
            val selectedMaskTab = maskTabsByMaskId.getOrPut(selectedMask.id) { 0 }
            TabRow(selectedTabIndex = selectedMaskTab) {
                Tab(
                    selected = selectedMaskTab == 0,
                    onClick = { maskTabsByMaskId[selectedMask.id] = 0 },
                    text = { Text(maskInnerTabs[0]) }
                )
                Tab(
                    selected = selectedMaskTab == 1,
                    onClick = { maskTabsByMaskId[selectedMask.id] = 1 },
                    text = { Text(maskInnerTabs[1]) }
                )
            }

            fun updateSelectedMaskAdjustments(updatedAdjustments: AdjustmentState) {
                val updated = masks.map { m ->
                    if (m.id != selectedMask.id) m else m.copy(adjustments = updatedAdjustments)
                }
                onMasksChange(updated)
                val newMask = updated.firstOrNull { it.id == selectedMask.id }
                onShowMaskOverlayChange(newMask?.adjustments?.isNeutralForMask() == true)
            }

            when (selectedMaskTab) {
                1 -> {
                    PanelSectionCard(
                        title = "Curves",
                        subtitle = "Tap to add points \u0007 Drag to adjust"
                    ) {
                        CurvesEditor(
                            adjustments = selectedMask.adjustments,
                            histogramData = histogramData,
                            onAdjustmentsChange = ::updateSelectedMaskAdjustments
                        )
                    }

                    PanelSectionCard(
                        title = "Color Grading",
                        subtitle = "Shadows / Midtones / Highlights"
                    ) {
                        ColorGradingEditor(
                            colorGrading = selectedMask.adjustments.colorGrading,
                            onColorGradingChange = { updated ->
                                updateSelectedMaskAdjustments(selectedMask.adjustments.copy(colorGrading = updated))
                            }
                        )
                    }
                }

                else -> {
                    PanelSectionCard(title = "Mask Adjustments", subtitle = "Edits inside this mask") {
                        adjustmentSections.forEach { (sectionTitle, controls) ->
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                controls.forEach { control ->
                                    val currentValue = selectedMask.adjustments.valueFor(control.field)
                                    AdjustmentSlider(
                                        label = control.label,
                                        value = currentValue,
                                        range = control.range,
                                        step = control.step,
                                        defaultValue = control.defaultValue,
                                        formatter = control.formatter,
                                        onValueChange = { snapped ->
                                            updateSelectedMaskAdjustments(selectedMask.adjustments.withValue(control.field, snapped))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneMapperSection(
    toneMapper: String,
    onToneMapperChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Tone Mapper",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = { onToneMapperChange("basic") },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (toneMapper == "basic") 
                            MaterialTheme.colorScheme.secondaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (toneMapper == "basic")
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Basic")
                }
                FilledTonalButton(
                    onClick = { onToneMapperChange("agx") },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (toneMapper == "agx") 
                            MaterialTheme.colorScheme.secondaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (toneMapper == "agx")
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("AgX")
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        thumbColor = MaterialTheme.colorScheme.primary
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(label, defaultValue) {
                    detectTapGestures(onDoubleTap = { onValueChange(defaultValue) })
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                val snapped = snapToStep(newValue, step, range)
                onValueChange(snapped)
            },
            valueRange = range,
            colors = colors
        )
    }
}

private fun snapToStep(
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (step <= 0f) return clamped
    val steps = ((clamped - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}

private data class HistogramData(
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val luma: FloatArray
)

private fun calculateHistogram(bitmap: Bitmap): HistogramData {
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

private fun applyGaussianSmoothing(histogram: FloatArray, sigma: Float) {
    if (sigma <= 0f) return
    val kernelRadius = kotlin.math.ceil(sigma * 3f).toInt()
    if (kernelRadius <= 0 || kernelRadius >= histogram.size) return

    val kernelSize = 2 * kernelRadius + 1
    val kernel = FloatArray(kernelSize)
    var kernelSum = 0f

    val twoSigmaSq = 2f * sigma * sigma
    for (i in 0 until kernelSize) {
        val x = (i - kernelRadius).toFloat()
        val v = kotlin.math.exp((-x * x / twoSigmaSq).toDouble()).toFloat()
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

private fun normalizeHistogramRange(histogram: FloatArray, percentileClip: Float) {
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

private enum class CurveChannel(val label: String) {
    Luma("L"),
    Red("R"),
    Green("G"),
    Blue("B"),
}

@Composable
private fun PanelSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable (() -> Unit))? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

private fun CurvesState.pointsFor(channel: CurveChannel): List<CurvePointState> {
    return when (channel) {
        CurveChannel.Luma -> luma
        CurveChannel.Red -> red
        CurveChannel.Green -> green
        CurveChannel.Blue -> blue
    }
}

private fun CurvesState.withPoints(channel: CurveChannel, points: List<CurvePointState>): CurvesState {
    return when (channel) {
        CurveChannel.Luma -> copy(luma = points)
        CurveChannel.Red -> copy(red = points)
        CurveChannel.Green -> copy(green = points)
        CurveChannel.Blue -> copy(blue = points)
    }
}

@Composable
private fun CurvesEditor(
    adjustments: AdjustmentState,
    histogramData: HistogramData?,
    onAdjustmentsChange: (AdjustmentState) -> Unit
) {
    var activeChannel by remember { mutableStateOf(CurveChannel.Luma) }
    val points = adjustments.curves.pointsFor(activeChannel)

    val latestAdjustments by rememberUpdatedState(adjustments)
    val latestOnAdjustmentsChange by rememberUpdatedState(onAdjustmentsChange)
    val latestCurves by rememberUpdatedState(adjustments.curves)
    val latestPoints by rememberUpdatedState(points)

    val channelColor = when (activeChannel) {
        CurveChannel.Luma -> MaterialTheme.colorScheme.primary
        CurveChannel.Red -> Color(0xFFFF6B6B)
        CurveChannel.Green -> Color(0xFF6BCB77)
        CurveChannel.Blue -> Color(0xFF4D96FF)
    }

    val histogram = when (activeChannel) {
        CurveChannel.Luma -> histogramData?.luma
        CurveChannel.Red -> histogramData?.red
        CurveChannel.Green -> histogramData?.green
        CurveChannel.Blue -> histogramData?.blue
    }

    val pointHitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val resetChannel: () -> Unit = {
        val updatedCurves = latestCurves.withPoints(activeChannel, defaultCurvePoints())
        latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CurveChannel.values().forEach { channel ->
                val selected = channel == activeChannel
                val accent = when (channel) {
                    CurveChannel.Luma -> MaterialTheme.colorScheme.primary
                    CurveChannel.Red -> Color(0xFFFF6B6B)
                    CurveChannel.Green -> Color(0xFF6BCB77)
                    CurveChannel.Blue -> Color(0xFF4D96FF)
                }
                FilledTonalButton(
                    onClick = { activeChannel = channel },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else accent
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (selected) Modifier.border(2.dp, accent, CircleShape) else Modifier
                        ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(channel.label)
                }
            }
            }
            OutlinedButton(onClick = resetChannel, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Reset")
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val graphSize = minOf(maxWidth, 340.dp)
            Box(
                modifier = Modifier
                    .size(graphSize)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(activeChannel, pointHitRadiusPx) {
                    fun toCurvePoint(pos: Offset): CurvePointState {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val x = (pos.x / w * 255f).coerceIn(0f, 255f)
                        val y = (255f - (pos.y / h * 255f)).coerceIn(0f, 255f)
                        return CurvePointState(x = x, y = y)
                    }

                    fun toScreenPoint(p: CurvePointState): Offset {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val x = p.x / 255f * w
                        val y = (255f - p.y) / 255f * h
                        return Offset(x, y)
                    }

                    fun closestPointIndex(pos: Offset, pts: List<CurvePointState>): Int? {
                        var best: Int? = null
                        var bestDist = Float.MAX_VALUE
                        pts.forEachIndexed { index, p ->
                            val sp = toScreenPoint(p)
                            val dx = sp.x - pos.x
                            val dy = sp.y - pos.y
                            val d = dx * dx + dy * dy
                            if (d < bestDist) {
                                bestDist = d
                                best = index
                            }
                        }
                        return if (best != null && kotlin.math.sqrt(bestDist) <= pointHitRadiusPx) best else null
                    }

                    fun movePoint(
                        pts: List<CurvePointState>,
                        index: Int,
                        target: CurvePointState
                    ): List<CurvePointState> {
                        val clampedY = target.y.coerceIn(0f, 255f)
                        val isEndPoint = index == 0 || index == pts.lastIndex
                        val clampedX = if (isEndPoint) {
                            pts[index].x
                        } else {
                            val prevX = pts[index - 1].x
                            val nextX = pts[index + 1].x
                            target.x.coerceIn(prevX + 0.01f, nextX - 0.01f)
                        }
                        val out = pts.toMutableList()
                        out[index] = CurvePointState(x = clampedX, y = clampedY)
                        return out
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var working = latestPoints
                        val downPos = down.position
                        var draggingIndex = closestPointIndex(downPos, working)

                        if (draggingIndex == null) {
                            // Don't add points on scroll: only add on a real tap (no movement beyond slop).
                            val slop = viewConfiguration.touchSlop
                            var movedTooMuch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed) {
                                    if (!movedTooMuch) {
                                        if (working.size >= 16) return@awaitEachGesture
                                        val newPoint = toCurvePoint(change.position)
                                        val newPoints = (working + newPoint).sortedBy { it.x }
                                        working = newPoints
                                        val updatedCurves = latestCurves.withPoints(activeChannel, working)
                                        latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
                                    }
                                    return@awaitEachGesture
                                }
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if ((dx * dx + dy * dy) > slop * slop) {
                                    movedTooMuch = true
                                }
                            }
                        }

                        if (draggingIndex == null) return@awaitEachGesture

                        // User grabbed a point: capture the gesture so scrolling doesn't steal it.
                        down.consume()

                        drag(down.id) { change ->
                            change.consume()
                            val target = toCurvePoint(change.position)
                            working = movePoint(working, draggingIndex!!, target)
                            val updatedCurves = latestCurves.withPoints(activeChannel, working)
                            latestOnAdjustmentsChange(latestAdjustments.copy(curves = updatedCurves))
                        }
                    }
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid (4x4)
                    for (i in 1..3) {
                        val t = i / 4f
                        drawLine(
                            color = gridColor,
                            start = Offset(w * t, 0f),
                            end = Offset(w * t, h),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * t),
                            end = Offset(w, h * t),
                            strokeWidth = 1f
                        )
                    }

                    // Histogram backdrop
                    histogram?.let { data ->
                        val maxVal = data.maxOrNull() ?: 0f
                        if (maxVal > 0f) {
                            val path = Path().apply {
                                moveTo(0f, h)
                                for (i in data.indices) {
                                    val x = (i / 255f) * w
                                    val y = (data[i] / maxVal) * h
                                    lineTo(x, h - y)
                                }
                                lineTo(w, h)
                                close()
                            }
                            drawPath(path, color = channelColor.copy(alpha = 0.18f))
                        }
                    }

                    // Curve
                    if (points.size >= 2) {
                        val curvePath = buildCurvePath(points, size)
                        drawPath(curvePath, color = channelColor, style = Stroke(width = 7f))
                    }

                    // Points
                    points.forEach { p ->
                        val x = p.x / 255f * w
                        val y = (255f - p.y) / 255f * h
                        drawCircle(
                            color = Color.White,
                            radius = 11f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = 11f,
                            center = Offset(x, y),
                            style = Stroke(width = 6f)
                        )
                    }
                }
            }
        }
    }
}

private fun buildCurvePath(points: List<CurvePointState>, size: Size): Path {
    if (points.size < 2) return Path()

    val pts = points.sortedBy { it.x }
    val n = pts.size
    val deltas = FloatArray(n - 1)
    for (i in 0 until n - 1) {
        val dx = pts[i + 1].x - pts[i].x
        val dy = pts[i + 1].y - pts[i].y
        deltas[i] = if (dx == 0f) {
            when {
                dy > 0f -> 1e6f
                dy < 0f -> -1e6f
                else -> 0f
            }
        } else {
            dy / dx
        }
    }

    val ms = FloatArray(n)
    ms[0] = deltas[0]
    for (i in 1 until n - 1) {
        ms[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f else (deltas[i - 1] + deltas[i]) / 2f
    }
    ms[n - 1] = deltas[n - 2]

    for (i in 0 until n - 1) {
        if (deltas[i] == 0f) {
            ms[i] = 0f
            ms[i + 1] = 0f
        } else {
            val alpha = ms[i] / deltas[i]
            val beta = ms[i + 1] / deltas[i]
            val tau = alpha * alpha + beta * beta
            if (tau > 9f) {
                val scale = 3f / kotlin.math.sqrt(tau)
                ms[i] = scale * alpha * deltas[i]
                ms[i + 1] = scale * beta * deltas[i]
            }
        }
    }

    fun map(p: CurvePointState): Offset {
        val x = (p.x / 255f) * size.width
        val y = ((255f - p.y) / 255f) * size.height
        return Offset(x, y)
    }

    fun mapXY(x: Float, y: Float): Offset {
        val px = (x / 255f) * size.width
        val py = ((255f - y) / 255f) * size.height
        return Offset(px, py)
    }

    val path = Path()
    val first = pts.first()
    path.moveTo(map(first).x, map(first).y)
    for (i in 0 until n - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val m0 = ms[i]
        val m1 = ms[i + 1]
        val dx = p1.x - p0.x

        val cp1x = p0.x + dx / 3f
        val cp1y = p0.y + (m0 * dx) / 3f
        val cp2x = p1.x - dx / 3f
        val cp2y = p1.y - (m1 * dx) / 3f

        val cp1 = mapXY(cp1x, cp1y)
        val cp2 = mapXY(cp2x, cp2y)
        val end = map(p1)
        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
    }
    return path
}

@Composable
private fun ColorGradingEditor(
    colorGrading: ColorGradingState,
    onColorGradingChange: (ColorGradingState) -> Unit
) {
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val midWheelSize = if (isWide) 240.dp else minOf(maxWidth, 220.dp)
        val sideWheelSize = minOf((maxWidth / 2) - 10.dp, if (isWide) 220.dp else 170.dp)

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ColorWheelControl(
                label = "Midtones",
                wheelSize = midWheelSize,
                modifier = Modifier.fillMaxWidth(),
                value = colorGrading.midtones,
                defaultValue = HueSatLumState(),
                onValueChange = { onColorGradingChange(colorGrading.copy(midtones = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ColorWheelControl(
                    label = "Shadows",
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.shadows,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(shadows = it)) }
                )
                ColorWheelControl(
                    label = "Highlights",
                    wheelSize = sideWheelSize,
                    modifier = Modifier.weight(1f),
                    value = colorGrading.highlights,
                    defaultValue = HueSatLumState(),
                    onValueChange = { onColorGradingChange(colorGrading.copy(highlights = it)) }
                )
            }

            AdjustmentSlider(
                label = "Blending",
                value = colorGrading.blending,
                range = 0f..100f,
                step = 1f,
                defaultValue = 50f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(blending = it)) }
            )
            AdjustmentSlider(
                label = "Balance",
                value = colorGrading.balance,
                range = -100f..100f,
                step = 1f,
                defaultValue = 0f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(balance = it)) }
            )
        }
    }
}

@Composable
private fun ColorWheelControl(
    label: String,
    wheelSize: Dp,
    modifier: Modifier = Modifier,
    value: HueSatLumState,
    defaultValue: HueSatLumState,
    onValueChange: (HueSatLumState) -> Unit
) {
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val handleHitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "H ${value.hue.roundToInt()}  S ${value.saturation.roundToInt()}  L ${value.luminance.roundToInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onValueChange(defaultValue) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Reset")
                }
            }
        }

        Box(
            modifier = Modifier
                .size(wheelSize)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    fun calcHueSat(pos: Offset): HueSatLumState {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val cx = w / 2f
                        val cy = h / 2f
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val radius = kotlin.math.sqrt(dx * dx + dy * dy)
                        val maxRadius = kotlin.math.min(cx, cy).coerceAtLeast(1f)
                        val sat = ((radius / maxRadius).coerceIn(0f, 1f) * 100f)
                        var hue = (kotlin.math.atan2(dy, dx) * 180.0 / kotlin.math.PI).toFloat()
                        if (hue < 0f) hue += 360f
                        return latestValue.copy(hue = hue, saturation = sat)
                    }

                    fun handleOffsetFor(v: HueSatLumState): Offset {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val cx = w / 2f
                        val cy = h / 2f
                        val radius = kotlin.math.min(cx, cy)
                        val angleRad = (v.hue / 180f) * kotlin.math.PI.toFloat()
                        val satNorm = (v.saturation / 100f).coerceIn(0f, 1f)
                        val x = cx + kotlin.math.cos(angleRad) * radius * satNorm
                        val y = cy + kotlin.math.sin(angleRad) * radius * satNorm
                        return Offset(x, y)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        val slop = viewConfiguration.touchSlop

                        val handlePos = handleOffsetFor(latestValue)
                        val dist = (handlePos - downPos).getDistance()

                        if (dist <= handleHitRadiusPx) {
                            // Dragging the handle: capture (prevents scroll stealing the gesture).
                            down.consume()
                            drag(down.id) { change ->
                                change.consume()
                                latestOnValueChange(calcHueSat(change.position))
                            }
                        } else {
                            // Only update on a real tap (no movement beyond slop); ignore scroll swipes.
                            var movedTooMuch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed) {
                                    if (!movedTooMuch) {
                                        latestOnValueChange(calcHueSat(change.position))
                                    }
                                    break
                                }
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if ((dx * dx + dy * dy) > slop * slop) {
                                    movedTooMuch = true
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)
                val radius = kotlin.math.min(w, h) / 2f

                val sweep = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                )

                drawCircle(brush = Brush.sweepGradient(sweep), radius = radius, center = center)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(color = Color.Black.copy(alpha = 0.15f), radius = radius, center = center, style = Stroke(2f))

                val angleRad = (value.hue / 180f) * kotlin.math.PI.toFloat()
                val satNorm = (value.saturation / 100f).coerceIn(0f, 1f)
                val px = center.x + kotlin.math.cos(angleRad) * radius * satNorm
                val py = center.y + kotlin.math.sin(angleRad) * radius * satNorm
                val pointerColor = if (value.saturation > 5f) Color.hsv(value.hue, satNorm, 1f) else Color.Transparent

                drawCircle(color = pointerColor, radius = 14f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 14f, center = Offset(px, py), style = Stroke(4f))
            }
        }

        AdjustmentSlider(
            label = "Luminance",
            value = value.luminance,
            range = -100f..100f,
            step = 1f,
            defaultValue = 0f,
            formatter = formatterInt,
            onValueChange = { onValueChange(value.copy(luminance = it)) }
        )
    }
}

@Composable
private fun ImagePreview(
    bitmap: Bitmap?,
    isLoading: Boolean,
    maskOverlay: MaskState? = null,
    activeSubMask: SubMaskState? = null,
    isMaskMode: Boolean = false,
    showMaskOverlay: Boolean = true,
    maskOverlayBlinkKey: Long = 0L,
    isPainting: Boolean = false,
    brushSize: Float = 60f,
    maskTapMode: MaskTapMode = MaskTapMode.None,
    onMaskTap: ((MaskPoint) -> Unit)? = null,
    onBrushStrokeFinished: ((List<MaskPoint>, Float) -> Unit)? = null,
    onLassoFinished: ((List<MaskPoint>) -> Unit)? = null,
    onSubMaskHandleDrag: ((MaskHandle, MaskPoint) -> Unit)? = null,
    onSubMaskHandleDragStateChange: ((Boolean) -> Unit)? = null,
    onRequestAiSubjectOverride: (() -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val currentStroke = remember { mutableStateListOf<MaskPoint>() }
    val density = LocalDensity.current
    val activeSubMaskState by rememberUpdatedState(activeSubMask)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .let { base ->
                if (!isMaskMode) base
                else base.pointerInput(bitmap) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.any { it.type == PointerType.Stylus }) continue

                            val touchCount = pressed.count { it.type == PointerType.Touch }
                            if (touchCount < 2) continue

                            val touches = pressed.filter { it.type == PointerType.Touch }
                            val a = touches[0]
                            val b = touches[1]

                            val prevCentroid = Offset(
                                (a.previousPosition.x + b.previousPosition.x) / 2f,
                                (a.previousPosition.y + b.previousPosition.y) / 2f
                            )
                            val currCentroid = Offset(
                                (a.position.x + b.position.x) / 2f,
                                (a.position.y + b.position.y) / 2f
                            )
                            val pan = currCentroid - prevCentroid

                            val prevDx = a.previousPosition.x - b.previousPosition.x
                            val prevDy = a.previousPosition.y - b.previousPosition.y
                            val currDx = a.position.x - b.position.x
                            val currDy = a.position.y - b.position.y
                            val prevDist = kotlin.math.sqrt(prevDx * prevDx + prevDy * prevDy).coerceAtLeast(0.0001f)
                            val currDist = kotlin.math.sqrt(currDx * currDx + currDy * currDy).coerceAtLeast(0.0001f)
                            val zoom = currDist / prevDist

                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y

                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }

        if (bitmap != null) {
            val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
            val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)
            val baseScale = minOf(containerW / bmpW, containerH / bmpH)
            val displayW = bmpW * baseScale
            val displayH = bmpH * baseScale
            val left = (containerW - displayW) / 2f
            val top = (containerH - displayH) / 2f

            val baseDim = minOf(bmpW, bmpH)

            fun toContentOffset(pos: Offset): Offset {
                val pivot = Offset(containerW / 2f, containerH / 2f)
                val x = pivot.x + (pos.x - offsetX - pivot.x) / scale
                val y = pivot.y + (pos.y - offsetY - pivot.y) / scale
                return Offset(x, y)
            }

            fun toImagePoint(pos: Offset): MaskPoint {
                val contentPos = toContentOffset(pos)
                val nx = ((contentPos.x - left) / displayW).coerceIn(0f, 1f)
                val ny = ((contentPos.y - top) / displayH).coerceIn(0f, 1f)
                return MaskPoint(x = nx, y = ny)
            }

            val imageModifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (isMaskMode) base
                    else base.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Processed preview",
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )

            if (isMaskMode) {
                val persistentOverlayVisible = showMaskOverlay && maskOverlay?.adjustments?.isNeutralForMask() == true
                val latestPersistentOverlayVisible by rememberUpdatedState(persistentOverlayVisible)

                var overlayIsFlashing by remember { mutableStateOf(false) }
                val overlayAlpha = remember { Animatable(0f) }

                LaunchedEffect(maskOverlay?.id, persistentOverlayVisible, overlayIsFlashing) {
                    if (!overlayIsFlashing) {
                        overlayAlpha.snapTo(if (persistentOverlayVisible) 1f else 0f)
                    }
                }

                LaunchedEffect(maskOverlay?.id, maskOverlayBlinkKey) {
                    if (maskOverlay == null) return@LaunchedEffect
                    if (maskOverlayBlinkKey == 0L) return@LaunchedEffect

                    overlayIsFlashing = true
                    overlayAlpha.snapTo(0f)
                    repeat(2) {
                        overlayAlpha.animateTo(1f, animationSpec = tween(durationMillis = 160))
                        overlayAlpha.animateTo(0f, animationSpec = tween(durationMillis = 160))
                    }
                    overlayAlpha.snapTo(if (latestPersistentOverlayVisible) 1f else 0f)
                    overlayIsFlashing = false
                }

                val shouldDrawOverlay = maskOverlay != null && (persistentOverlayVisible || overlayIsFlashing)
                val overlayBitmap = remember(maskOverlay, shouldDrawOverlay, bitmap.width, bitmap.height) {
                    if (!shouldDrawOverlay) {
                        null
                    } else {
                        val overlayMask = requireNotNull(maskOverlay)
                        val maxDim = 512
                        val w = bitmap.width
                        val h = bitmap.height
                        val scale = if (w >= h) maxDim.toFloat() / w.coerceAtLeast(1) else maxDim.toFloat() / h.coerceAtLeast(1)
                        val outW = (w * scale).toInt().coerceAtLeast(1)
                        val outH = (h * scale).toInt().coerceAtLeast(1)
                        buildMaskOverlayBitmap(overlayMask, outW, outH)
                    }
                }
                if (overlayBitmap != null) {
                    Image(
                        bitmap = overlayBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                                alpha = overlayAlpha.value.coerceIn(0f, 1f)
                            )
                    )
                }

                if (activeSubMask != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        val maxX = (bmpW - 1f).coerceAtLeast(1f)
                        val maxY = (bmpH - 1f).coerceAtLeast(1f)
                        fun toDisplayOffset(p: MaskPoint): Offset {
                            val px = if (p.x <= 1.5f) p.x * maxX else p.x
                            val py = if (p.y <= 1.5f) p.y * maxY else p.y
                            return Offset(left + px * baseScale, top + py * baseScale)
                        }

                        val strokeColor = Color(0xCCFFFFFF)
                        val handleRadius = 7.dp.toPx()

                        when (activeSubMask.type) {
                            SubMaskType.Linear.id -> {
                                val start = toDisplayOffset(MaskPoint(activeSubMask.linear.startX, activeSubMask.linear.startY))
                                val end = toDisplayOffset(MaskPoint(activeSubMask.linear.endX, activeSubMask.linear.endY))
                                drawLine(color = strokeColor, start = start, end = end, strokeWidth = 2.dp.toPx())
                                drawCircle(color = strokeColor, radius = handleRadius, center = start)
                                drawCircle(color = strokeColor, radius = handleRadius, center = end)

                                val dx = end.x - start.x
                                val dy = end.y - start.y
                                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                                val nx = -dy / len
                                val ny = dx / len
                                val half = run {
                                    val base = minOf(bmpW, bmpH)
                                    val rangePx = if (activeSubMask.linear.range <= 1.5f) activeSubMask.linear.range * base else activeSubMask.linear.range
                                    rangePx * baseScale
                                }
                                val a1 = Offset(start.x + nx * half, start.y + ny * half)
                                val a2 = Offset(end.x + nx * half, end.y + ny * half)
                                val b1 = Offset(start.x - nx * half, start.y - ny * half)
                                val b2 = Offset(end.x - nx * half, end.y - ny * half)
                                drawLine(color = Color(0x88FFFFFF), start = a1, end = a2, strokeWidth = 1.dp.toPx())
                                drawLine(color = Color(0x88FFFFFF), start = b1, end = b2, strokeWidth = 1.dp.toPx())
                            }

                            SubMaskType.Radial.id -> {
                                val center = toDisplayOffset(MaskPoint(activeSubMask.radial.centerX, activeSubMask.radial.centerY))
                                val radiusPx = run {
                                    val base = minOf(bmpW, bmpH)
                                    val r = if (activeSubMask.radial.radiusX <= 1.5f) activeSubMask.radial.radiusX * base else activeSubMask.radial.radiusX
                                    r * baseScale
                                }
                                drawCircle(color = strokeColor, radius = radiusPx, center = center, style = Stroke(width = 2.dp.toPx()))
                                drawCircle(color = strokeColor, radius = handleRadius, center = center)

                                val innerRadius = radiusPx * (1f - activeSubMask.radial.feather.coerceIn(0f, 1f))
                                if (innerRadius > 0.5f) {
                                    drawCircle(color = Color(0x88FFFFFF), radius = innerRadius, center = center, style = Stroke(width = 1.dp.toPx()))
                                }
                            }
                        }
                    }
                }

                if (activeSubMask != null && onSubMaskHandleDrag != null && !isPainting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeSubMask.id, bitmap) {
                                var dragging: MaskHandle? = null
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val sub = activeSubMaskState ?: return@detectDragGestures
                                        fun dist(a: Offset, b: Offset): Float {
                                            val dx = a.x - b.x
                                            val dy = a.y - b.y
                                            return kotlin.math.sqrt(dx * dx + dy * dy)
                                        }

                                        val handlePx = with(density) { 24.dp.toPx() } / scale.coerceAtLeast(0.0001f)
                                        val startPos = toContentOffset(start)

                                        dragging = when (sub.type) {
                                            SubMaskType.Radial.id -> {
                                                val centerPx = run {
                                                    val maxX = (bmpW - 1f).coerceAtLeast(1f)
                                                    val maxY = (bmpH - 1f).coerceAtLeast(1f)
                                                    val cx = sub.radial.centerX
                                                    val cy = sub.radial.centerY
                                                    val px = if (cx <= 1.5f) cx.coerceIn(0f, 1f) * maxX else cx.coerceIn(0f, maxX)
                                                    val py = if (cy <= 1.5f) cy.coerceIn(0f, 1f) * maxY else cy.coerceIn(0f, maxY)
                                                    Offset(left + px * baseScale, top + py * baseScale)
                                                }
                                                if (dist(startPos, centerPx) <= handlePx) MaskHandle.RadialCenter else null
                                            }

                                            SubMaskType.Linear.id -> {
                                                val startPx = run {
                                                    val maxX = (bmpW - 1f).coerceAtLeast(1f)
                                                    val maxY = (bmpH - 1f).coerceAtLeast(1f)
                                                    val sx = sub.linear.startX
                                                    val sy = sub.linear.startY
                                                    val px = if (sx <= 1.5f) sx.coerceIn(0f, 1f) * maxX else sx.coerceIn(0f, maxX)
                                                    val py = if (sy <= 1.5f) sy.coerceIn(0f, 1f) * maxY else sy.coerceIn(0f, maxY)
                                                    Offset(left + px * baseScale, top + py * baseScale)
                                                }
                                                val endPx = run {
                                                    val maxX = (bmpW - 1f).coerceAtLeast(1f)
                                                    val maxY = (bmpH - 1f).coerceAtLeast(1f)
                                                    val ex = sub.linear.endX
                                                    val ey = sub.linear.endY
                                                    val px = if (ex <= 1.5f) ex.coerceIn(0f, 1f) * maxX else ex.coerceIn(0f, maxX)
                                                    val py = if (ey <= 1.5f) ey.coerceIn(0f, 1f) * maxY else ey.coerceIn(0f, maxY)
                                                    Offset(left + px * baseScale, top + py * baseScale)
                                                }
                                                when {
                                                    dist(startPos, startPx) <= handlePx -> MaskHandle.LinearStart
                                                    dist(startPos, endPx) <= handlePx -> MaskHandle.LinearEnd
                                                    else -> null
                                                }
                                            }

                                            else -> null
                                        }

                                        val active = dragging ?: return@detectDragGestures
                                        onSubMaskHandleDragStateChange?.invoke(true)
                                        onSubMaskHandleDrag(active, toImagePoint(start))
                                    },
                                    onDrag = { change, _ ->
                                        val active = dragging ?: return@detectDragGestures
                                        change.consume()
                                        onSubMaskHandleDrag(active, toImagePoint(change.position))
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        onSubMaskHandleDragStateChange?.invoke(false)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        onSubMaskHandleDragStateChange?.invoke(false)
                                    }
                                )
                            }
                    )
                }

                if (onMaskTap != null && maskTapMode != MaskTapMode.None && !isPainting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(maskTapMode, bitmap) {
                                detectTapGestures { pos ->
                                    onMaskTap(toImagePoint(pos))
                                }
                            }
                    )
                }

                if (currentStroke.isNotEmpty()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        val strokeWidth = when (activeSubMask?.type) {
                            SubMaskType.AiSubject.id -> 2.dp.toPx()
                            else -> (brushSize * baseScale).coerceAtLeast(1f)
                        }
                        val maxX = (bmpW - 1f).coerceAtLeast(1f)
                        val maxY = (bmpH - 1f).coerceAtLeast(1f)
                        fun toDisplayOffset(p: MaskPoint): Offset {
                            val px = if (p.x <= 1.5f) p.x * maxX else p.x
                            val py = if (p.y <= 1.5f) p.y * maxY else p.y
                            return Offset(left + px * baseScale, top + py * baseScale)
                        }
                        val path = Path().apply {
                            val first = currentStroke.firstOrNull()?.let(::toDisplayOffset) ?: return@Canvas
                            moveTo(first.x, first.y)
                            currentStroke.drop(1).forEach { p ->
                                val o = toDisplayOffset(p)
                                lineTo(o.x, o.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(0x88FFFFFF),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            if (isMaskMode && isPainting && activeSubMask != null) {
                when (activeSubMask.type) {
                    SubMaskType.Brush.id -> {
                        val callback = onBrushStrokeFinished ?: return@BoxWithConstraints
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isPainting, brushSize, bitmap) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture

                                        val cancelOnMultiTouch = down.type == PointerType.Touch
                                        val brushSizeNorm = (brushSize / baseDim).coerceAtLeast(0.0001f)
                                        val minStep = (brushSizeNorm / 6f).coerceAtLeast(0.003f)

                                        currentStroke.clear()
                                        currentStroke.add(toImagePoint(down.position))
                                        var last = currentStroke.lastOrNull() ?: return@awaitEachGesture
                                        var canceled = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.none { it.id == down.id }) break

                                            if (cancelOnMultiTouch) {
                                                val touchCount = pressed.count { it.type == PointerType.Touch }
                                                if (touchCount >= 2) {
                                                    canceled = true
                                                    break
                                                }
                                            }

                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            if (change.position == change.previousPosition) continue

                                            change.consume()
                                            val newPoint = toImagePoint(change.position)
                                            val dx = newPoint.x - last.x
                                            val dy = newPoint.y - last.y
                                            if (dx * dx + dy * dy >= minStep * minStep) {
                                                currentStroke.add(newPoint)
                                                last = newPoint
                                            }
                                        }

                                        if (!canceled && currentStroke.isNotEmpty()) {
                                            callback(currentStroke.toList(), brushSizeNorm)
                                        }
                                        currentStroke.clear()
                                    }
                                }
                        )
                    }

                    SubMaskType.AiSubject.id -> {
                        val callback = onLassoFinished ?: return@BoxWithConstraints
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isPainting, bitmap) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
                                        val cancelOnMultiTouch = down.type == PointerType.Touch

                                        val sub = activeSubMaskState ?: return@awaitEachGesture
                                        if (sub.aiSubject.maskDataBase64 != null) {
                                            onRequestAiSubjectOverride?.invoke()
                                            currentStroke.clear()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                                if (event.changes.all { !it.pressed }) break
                                            }
                                            return@awaitEachGesture
                                        }

                                        currentStroke.clear()
                                        currentStroke.add(toImagePoint(down.position))
                                        var lastPoint = currentStroke.last()
                                        val minStep = 0.004f
                                        var canceled = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.none { it.id == down.id }) break

                                            if (cancelOnMultiTouch) {
                                                val touchCount = pressed.count { it.type == PointerType.Touch }
                                                if (touchCount >= 2) {
                                                    canceled = true
                                                    break
                                                }
                                            }

                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            if (change.position == change.previousPosition) continue

                                            change.consume()
                                            val newPoint = toImagePoint(change.position)
                                            val dx = newPoint.x - lastPoint.x
                                            val dy = newPoint.y - lastPoint.y
                                            if (dx * dx + dy * dy >= minStep * minStep) {
                                                currentStroke.add(newPoint)
                                                lastPoint = newPoint
                                            }
                                        }

                                        if (!canceled && currentStroke.size >= 3) callback(currentStroke.toList())
                                        currentStroke.clear()
                                    }
                                }
                        )
                    }
                }
            }
        } else {
            Text(
                text = "No preview yet",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isLoading) {
            ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "Imported RAW"
}

private fun saveJpegToPictures(context: Context, jpegBytes: ByteArray): Uri? {
    val filename = "IRIDIS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/IRIDIS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { it.write(jpegBytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}

private fun ByteArray.decodeToBitmap(): Bitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)
