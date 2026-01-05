package com.dueckis.kawaiiraweditor.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_REPLAY_FPS = 12
private const val DEFAULT_REPLAY_MAX_DIM = 1080
private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

internal data class ReplayExportResult(
    val success: Boolean,
    val uri: Uri? = null,
    val errorMessage: String? = null
)

private data class ReplayRenderSpec(
    val adjustments: AdjustmentState,
    val masks: List<MaskState>
)

private data class ReplayVideoFrame(
    val bitmap: Bitmap,
    val holdFrames: Int
)

internal data class PreparedFrame(
    val data: ByteArray,
    val presentationTimeUs: Long
)

private enum class ReplayStage {
    FinalEdited,
    Unedited,
    ColorOnly,
    ToneOnly,
    ColorGrading,
    Masks,
    Effects
}

internal suspend fun exportReplayVideo(
    context: Context,
    sessionHandle: Long,
    adjustments: AdjustmentState,
    masks: List<MaskState>,
    nativeDispatcher: CoroutineDispatcher,
    maxDimension: Int = DEFAULT_REPLAY_MAX_DIM,
    fps: Int = DEFAULT_REPLAY_FPS,
    lowRamMode: Boolean = false
): ReplayExportResult {
    if (sessionHandle == 0L) {
        return ReplayExportResult(success = false, errorMessage = "Session is unavailable.")
    }

    val renderSpecs = buildRenderSpecs(adjustments, masks)
    if (!renderSpecs.containsKey(ReplayStage.FinalEdited) || !renderSpecs.containsKey(ReplayStage.Unedited)) {
        return ReplayExportResult(success = false, errorMessage = "Replay stages incomplete.")
    }

    val renderedBitmaps = LinkedHashMap<ReplayStage, Bitmap>()
    val scaledBitmaps = LinkedHashMap<ReplayStage, Bitmap>()
    var frameSequence: List<ReplayVideoFrame>? = null
    var result: ReplayExportResult

    try {
        for ((stage, spec) in renderSpecs) {
            val bitmap = renderStageBitmap(
                sessionHandle = sessionHandle,
                spec = spec,
                nativeDispatcher = nativeDispatcher,
                maxDimension = maxDimension,
                lowRamMode = lowRamMode
            ) ?: return ReplayExportResult(success = false, errorMessage = "Failed to render ${stage.name.lowercase(Locale.US)} stage.")
            renderedBitmaps[stage] = bitmap
        }

        val finalBitmap = renderedBitmaps[ReplayStage.FinalEdited]
            ?: return ReplayExportResult(success = false, errorMessage = "Missing final frame.")

        val targetSize = determineTargetSize(finalBitmap, maxDimension)
        for ((stage, bitmap) in renderedBitmaps) {
            val scaled = scaleForVideo(bitmap, targetSize.first, targetSize.second)
            scaledBitmaps[stage] = scaled
            if (scaled !== bitmap) {
                bitmap.recycle()
            }
        }

        val frames = buildFrameSequence(scaledBitmaps, fps)
        frameSequence = frames
        if (frames.isEmpty()) {
            return ReplayExportResult(success = false, errorMessage = "No replay frames generated.")
        }

        val videoWidth = frames.first().bitmap.width
        val videoHeight = frames.first().bitmap.height
        val frameDurationUs = 1_000_000L / fps
        val preparedFrames = prepareFrames(frames, fps)
        val durationUs = if (preparedFrames.isEmpty()) 0L else preparedFrames.last().presentationTimeUs + frameDurationUs

        val displayName = "IRIDIS_REPLAY_${DATE_FORMAT.format(Date())}.mp4"
        val uri = saveMp4ToMovies(
            context = context,
            displayName = displayName,
            relativePath = "Movies/IRIDIS",
            durationMs = durationUs / 1000L
        ) { fd ->
            val encoder = Mp4FrameEncoder(
                width = videoWidth,
                height = videoHeight,
                fps = fps,
                outFd = fd
            )
            encoder.encode(preparedFrames, frameDurationUs)
        }

        result = if (uri != null) {
            ReplayExportResult(success = true, uri = uri)
        } else {
            ReplayExportResult(success = false, errorMessage = "Failed to save replay video.")
        }
    } catch (t: Throwable) {
        result = ReplayExportResult(success = false, errorMessage = t.message ?: "Replay export failed.")
    } finally {
        scaledBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        renderedBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        frameSequence?.forEach { frame -> if (!frame.bitmap.isRecycled) frame.bitmap.recycle() }
    }
    return result
}

private fun buildRenderSpecs(
    adjustments: AdjustmentState,
    masks: List<MaskState>
): LinkedHashMap<ReplayStage, ReplayRenderSpec> {
    val specs = LinkedHashMap<ReplayStage, ReplayRenderSpec>()
    val base = geometryBase(adjustments)

    val finalSpec = ReplayRenderSpec(adjustments = adjustments, masks = masks)
    specs[ReplayStage.FinalEdited] = finalSpec
    specs[ReplayStage.Unedited] = ReplayRenderSpec(adjustments = base, masks = emptyList())

    val colorOnly = withColorOnly(base, adjustments)
    if (colorOnly != base) {
        specs[ReplayStage.ColorOnly] = ReplayRenderSpec(colorOnly, emptyList())
    }

    val toneOnly = withTone(base, adjustments)
    if (toneOnly != base) {
        specs[ReplayStage.ToneOnly] = ReplayRenderSpec(toneOnly, emptyList())
    }

    if (!adjustments.colorGrading.isDefault()) {
        specs[ReplayStage.ColorGrading] = ReplayRenderSpec(withColorGrading(base, adjustments), emptyList())
    }

    if (masks.isNotEmpty()) {
        specs[ReplayStage.Masks] = finalSpec
    }

    val effects = withEffects(base, adjustments)
    if (effects != base) {
        specs[ReplayStage.Effects] = ReplayRenderSpec(effects, emptyList())
    }

    return specs
}

private suspend fun renderStageBitmap(
    sessionHandle: Long,
    spec: ReplayRenderSpec,
    nativeDispatcher: CoroutineDispatcher,
    maxDimension: Int,
    lowRamMode: Boolean
): Bitmap? {
    val json = withContext(Dispatchers.Default) { spec.adjustments.toJson(spec.masks) }
    val jpegBytes = withContext(nativeDispatcher) {
        LibRawDecoder.exportFromSession(sessionHandle, json, maxDimension, lowRamMode)
    } ?: return null

    return withContext(Dispatchers.Default) { jpegBytes.decodeToBitmap() }
}

private fun determineTargetSize(bitmap: Bitmap, maxLongEdge: Int): Pair<Int, Int> {
    val maxEdge = max(bitmap.width, bitmap.height).coerceAtLeast(1)
    val limit = max(maxLongEdge, 256)
    val scale = if (maxEdge <= limit) 1f else limit.toFloat() / maxEdge.toFloat()
    var width = (bitmap.width * scale).roundToInt().coerceAtLeast(2)
    var height = (bitmap.height * scale).roundToInt().coerceAtLeast(2)
    if (width % 2 != 0) width = (width - 1).coerceAtLeast(2)
    if (height % 2 != 0) height = (height - 1).coerceAtLeast(2)
    return width to height
}

private fun scaleForVideo(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    if (bitmap.width == targetWidth && bitmap.height == targetHeight) return bitmap
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun buildFrameSequence(
    bitmaps: Map<ReplayStage, Bitmap>,
    fps: Int
): List<ReplayVideoFrame> {
    val final = bitmaps[ReplayStage.FinalEdited] ?: return emptyList()
    val unedited = bitmaps[ReplayStage.Unedited] ?: return emptyList()
    val frames = ArrayList<ReplayVideoFrame>()

    frames += ReplayVideoFrame(makeSplitFrame(final, unedited), fps)
    frames += ReplayVideoFrame(final.copy(Bitmap.Config.ARGB_8888, false), fps)
    frames += ReplayVideoFrame(unedited.copy(Bitmap.Config.ARGB_8888, false), fps)

    bitmaps[ReplayStage.ColorOnly]?.let { frames += ReplayVideoFrame(it.copy(Bitmap.Config.ARGB_8888, false), fps) }
    bitmaps[ReplayStage.ToneOnly]?.let { frames += ReplayVideoFrame(it.copy(Bitmap.Config.ARGB_8888, false), fps) }
    bitmaps[ReplayStage.ColorGrading]?.let { frames += ReplayVideoFrame(it.copy(Bitmap.Config.ARGB_8888, false), fps) }
    bitmaps[ReplayStage.Masks]?.let { frames += ReplayVideoFrame(it.copy(Bitmap.Config.ARGB_8888, false), fps) }
    bitmaps[ReplayStage.Effects]?.let { frames += ReplayVideoFrame(it.copy(Bitmap.Config.ARGB_8888, false), fps) }

    frames += ReplayVideoFrame(makeWatermarkFrame(final), fps * 2)
    return frames
}

private fun makeSplitFrame(edited: Bitmap, unedited: Bitmap): Bitmap {
    val width = edited.width
    val height = edited.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val mid = width / 2

    canvas.save()
    canvas.clipRect(0, 0, mid, height)
    canvas.drawBitmap(edited, 0f, 0f, null)
    canvas.restore()

    canvas.save()
    canvas.clipRect(mid, 0, width, height)
    canvas.drawBitmap(unedited, 0f, 0f, null)
    canvas.restore()

    val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = max(2f, width * 0.002f)
    }
    canvas.drawLine(mid.toFloat(), 0f, mid.toFloat(), height.toFloat(), divider)
    return output
}

private fun makeWatermarkFrame(source: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    val padding = max(source.width, source.height) * 0.04f
    val text = "Made with IRIDIS"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = source.width * 0.05f
        setShadowLayer(8f, 0f, 4f, Color.argb(170, 0, 0, 0))
    }

    val textWidth = textPaint.measureText(text)
    val metrics = textPaint.fontMetrics
    val textHeight = metrics.bottom - metrics.top
    val rect = RectF(
        output.width - textWidth - padding * 0.75f - padding,
        output.height - textHeight - padding,
        output.width - padding * 0.5f,
        output.height - padding * 0.5f
    )

    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    canvas.drawRoundRect(rect, padding * 0.3f, padding * 0.3f, background)

    val x = rect.left + padding * 0.3f
    val y = rect.bottom - padding * 0.35f
    canvas.drawText(text, x, y, textPaint)
    return output
}

private fun prepareFrames(
    frames: List<ReplayVideoFrame>,
    fps: Int
): List<PreparedFrame> {
    val frameDurationUs = 1_000_000L / fps
    var presentationTimeUs = 0L
    val prepared = ArrayList<PreparedFrame>(frames.sumOf { it.holdFrames.coerceAtLeast(1) })

    frames.forEach { frame ->
        val yuv = frame.bitmap.toI420()
        val repeats = frame.holdFrames.coerceAtLeast(1)
        repeat(repeats) {
            prepared += PreparedFrame(yuv, presentationTimeUs)
            presentationTimeUs += frameDurationUs
        }
        frame.bitmap.recycle()
    }

    return prepared
}

private fun Bitmap.toI420(): ByteArray {
    val width = this.width
    val height = this.height
    val ySize = width * height
    val uvWidth = width / 2
    val uvHeight = height / 2
    val uvSize = uvWidth * uvHeight
    val out = ByteArray(ySize + uvSize * 2)

    val row0 = IntArray(width)
    val row1 = IntArray(width)

    for (y in 0 until height step 2) {
        getPixels(row0, 0, width, 0, y, width, 1)
        getPixels(row1, 0, width, 0, y + 1, width, 1)

        for (x in 0 until width step 2) {
            val c00 = row0[x]
            val c01 = row0[x + 1]
            val c10 = row1[x]
            val c11 = row1[x + 1]

            val top = y * width + x
            val bottom = (y + 1) * width + x

            out[top] = rgbToYByte(c00)
            out[top + 1] = rgbToYByte(c01)
            out[bottom] = rgbToYByte(c10)
            out[bottom + 1] = rgbToYByte(c11)

            val u = (rgbToUInt(c00) + rgbToUInt(c01) + rgbToUInt(c10) + rgbToUInt(c11)) / 4
            val v = (rgbToVInt(c00) + rgbToVInt(c01) + rgbToVInt(c10) + rgbToVInt(c11)) / 4

            val chromaIndex = (y / 2) * uvWidth + (x / 2)
            out[ySize + chromaIndex] = u.toByte()
            out[ySize + uvSize + chromaIndex] = v.toByte()
        }
    }

    return out
}

private fun rgbToYByte(color: Int): Byte {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
    return y.coerceIn(0, 255).toByte()
}

private fun rgbToUInt(color: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
    return u.coerceIn(0, 255)
}

private fun rgbToVInt(color: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
    return v.coerceIn(0, 255)
}

private fun geometryBase(from: AdjustmentState): AdjustmentState {
    return AdjustmentState(
        rotation = from.rotation,
        flipHorizontal = from.flipHorizontal,
        flipVertical = from.flipVertical,
        orientationSteps = from.orientationSteps,
        aspectRatio = from.aspectRatio,
        crop = from.crop,
        toneMapper = from.toneMapper
    )
}

private fun withColorOnly(base: AdjustmentState, final: AdjustmentState): AdjustmentState {
    return base.copy(
        saturation = final.saturation,
        temperature = final.temperature,
        tint = final.tint,
        vibrance = final.vibrance,
        hsl = final.hsl
    )
}

private fun withTone(base: AdjustmentState, final: AdjustmentState): AdjustmentState {
    return base.copy(
        exposure = final.exposure,
        brightness = final.brightness,
        contrast = final.contrast,
        highlights = final.highlights,
        shadows = final.shadows,
        whites = final.whites,
        blacks = final.blacks,
        curves = final.curves
    )
}

private fun withColorGrading(base: AdjustmentState, final: AdjustmentState): AdjustmentState {
    return base.copy(colorGrading = final.colorGrading)
}

private fun withEffects(base: AdjustmentState, final: AdjustmentState): AdjustmentState {
    return base.copy(
        clarity = final.clarity,
        dehaze = final.dehaze,
        structure = final.structure,
        centre = final.centre,
        vignetteAmount = final.vignetteAmount,
        vignetteMidpoint = final.vignetteMidpoint,
        vignetteRoundness = final.vignetteRoundness,
        vignetteFeather = final.vignetteFeather,
        sharpness = final.sharpness,
        lumaNoiseReduction = final.lumaNoiseReduction,
        colorNoiseReduction = final.colorNoiseReduction,
        chromaticAberrationRedCyan = final.chromaticAberrationRedCyan,
        chromaticAberrationBlueYellow = final.chromaticAberrationBlueYellow
    )
}
