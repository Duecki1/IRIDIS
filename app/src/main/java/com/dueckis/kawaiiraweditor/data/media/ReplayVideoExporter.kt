package com.dueckis.kawaiiraweditor.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.provider.MediaStore
import android.view.animation.PathInterpolator
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
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_REPLAY_FPS = 30
private const val DEFAULT_REPLAY_MAX_DIM = 1080
private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

private const val OPENING_HOLD_SPLIT_SECONDS = 0.3
private const val OPENING_WIPE_TO_EDITED_SECONDS = 0.7
private const val OPENING_HOLD_EDITED_SECONDS = 0.2
private const val OPENING_WIPE_TO_UNEDITED_SECONDS = 0.9
private const val OPENING_HOLD_UNEDITED_SECONDS = 0.2
private const val STAGE_HOLD_SECONDS = 0.25
private const val STAGE_CROSSFADE_SECONDS = 0.65
private const val FINAL_HOLD_SECONDS = 0.6
private const val FINAL_FADE_TO_BLACK_SECONDS = 0.4
private const val BLACK_HOLD_SECONDS = 0.1

private const val LOGO_TOTAL_MS = 1_200f
private const val LOGO_BLADE_MS = 360f
private const val LOGO_BLADE_STAGGER_MS = 25f
private const val LOGO_CENTER_MS = 330f
private const val LOGO_HOLD_MS = 180f
private const val LOGO_FADE_MS = 180f

internal data class ReplayExportResult(
    val success: Boolean,
    val uri: Uri? = null,
    val errorMessage: String? = null
)

private data class ReplayRenderSpec(
    val adjustments: AdjustmentState,
    val masks: List<MaskState>
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

        val frameGenerator = ReplayFrameGenerator(scaledBitmaps, fps)
        val frameSequence = frameGenerator.frames()
        val frameDurationUs = 1_000_000L / fps

        val displayName = "IRIDIS_REPLAY_${DATE_FORMAT.format(Date())}.mp4"
        val uri = saveMp4ToMovies(
            context = context,
            displayName = displayName,
            relativePath = "Movies/IRIDIS",
            durationMs = 0
        ) { fd ->
            val encoder = Mp4FrameEncoder(
                width = targetSize.first,
                height = targetSize.second,
                fps = fps,
                outFd = fd
            )
            encoder.encode(frameSequence, frameDurationUs)
        }

        if (uri != null) {
            val framesGenerated = frameGenerator.frameCount
            val durationUs = framesGenerated.toLong() * frameDurationUs
            if (durationUs > 0) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.VideoColumns.DURATION, durationUs / 1000L)
                }
                context.contentResolver.update(uri, values, null, null)
            }
            return ReplayExportResult(success = true, uri = uri)
        }

        return ReplayExportResult(success = false, errorMessage = "Failed to save replay video.")
    } catch (t: Throwable) {
        return ReplayExportResult(success = false, errorMessage = t.message ?: "Replay export failed.")
    } finally {
        scaledBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        renderedBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
    }
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

private class ReplayFrameGenerator(
    private val bitmaps: Map<ReplayStage, Bitmap>,
    private val fps: Int
) {
    private val frameDurationUs = 1_000_000L / fps
    private val fastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    var frameCount: Int = 0
        private set

    fun frames(): Sequence<PreparedFrame> = sequence {
        val edited = bitmaps[ReplayStage.FinalEdited] ?: return@sequence
        val unedited = bitmaps[ReplayStage.Unedited] ?: return@sequence
        val width = edited.width
        val height = edited.height
        val working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = max(2f, width * 0.0025f)
        }
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val logoRenderer = LogoOutroRenderer(width, height)
        val holdStageFrames = secondsToFrames(STAGE_HOLD_SECONDS, fps)
        val crossfadeStageFrames = secondsToFrames(STAGE_CROSSFADE_SECONDS, fps)
        val blackHoldFrames = secondsToFrames(BLACK_HOLD_SECONDS, fps)
        val blackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

        var localFrameCount = 0

        suspend fun SequenceScope<PreparedFrame>.emitFrame(draw: () -> Unit) {
            draw()
            val data = working.toI420()
            yield(PreparedFrame(data, localFrameCount.toLong() * frameDurationUs))
            localFrameCount += 1
        }

        try {
            val holdSplitFrames = secondsToFrames(OPENING_HOLD_SPLIT_SECONDS, fps)
            for (i in 0 until holdSplitFrames) {
                emitFrame { drawWipeFrame(working, edited, unedited, width / 2f, dividerPaint) }
            }

            val toEditedFrames = secondsToFrames(OPENING_WIPE_TO_EDITED_SECONDS, fps)
            for (i in 0 until toEditedFrames.coerceAtLeast(1)) {
                val ratio = (i + 1).toFloat() / toEditedFrames.coerceAtLeast(1).toFloat()
                val eased = fastOutSlowIn.getInterpolation(ratio)
                val dividerX = (width / 2f) + (width / 2f) * eased
                emitFrame { drawWipeFrame(working, edited, unedited, dividerX, dividerPaint) }
            }

            val holdEditedFrames = secondsToFrames(OPENING_HOLD_EDITED_SECONDS, fps)
            for (i in 0 until holdEditedFrames) {
                emitFrame { drawBitmapFrame(working, edited) }
            }

            val toUneditedFrames = secondsToFrames(OPENING_WIPE_TO_UNEDITED_SECONDS, fps)
            for (i in 0 until toUneditedFrames.coerceAtLeast(1)) {
                val ratio = (i + 1).toFloat() / toUneditedFrames.coerceAtLeast(1).toFloat()
                val eased = fastOutSlowIn.getInterpolation(ratio)
                val dividerX = width.toFloat() * (1f - eased)
                emitFrame { drawWipeFrame(working, edited, unedited, dividerX, dividerPaint) }
            }

            val holdUneditedFrames = secondsToFrames(OPENING_HOLD_UNEDITED_SECONDS, fps)
            for (i in 0 until holdUneditedFrames) {
                emitFrame { drawBitmapFrame(working, unedited) }
            }

            val stageSequence = buildCrossfadeStages(bitmaps)
            for (index in 0 until stageSequence.size - 1) {
                val current = stageSequence[index]
                val next = stageSequence[index + 1]

                for (i in 0 until holdStageFrames) {
                    emitFrame { drawBitmapFrame(working, current) }
                }

                if (current !== next) {
                    val frames = crossfadeStageFrames.coerceAtLeast(1)
                    for (i in 0 until frames) {
                        val ratio = if (frames == 1) 1f else fastOutSlowIn.getInterpolation(i.toFloat() / (frames - 1).coerceAtLeast(1))
                        emitFrame { drawCrossfadeFrame(working, current, next, ratio) }
                    }
                }
            }

            val finalStage = stageSequence.lastOrNull() ?: edited
            val finalHoldFrames = secondsToFrames(FINAL_HOLD_SECONDS, fps)
            for (i in 0 until finalHoldFrames) {
                emitFrame { drawBitmapFrame(working, finalStage) }
            }

            val fadeFrames = secondsToFrames(FINAL_FADE_TO_BLACK_SECONDS, fps)
            for (i in 0 until fadeFrames.coerceAtLeast(1)) {
                val ratio = (i + 1).toFloat() / fadeFrames.coerceAtLeast(1).toFloat()
                val eased = fastOutSlowIn.getInterpolation(ratio)
                emitFrame { drawFadeToBlack(working, finalStage, eased, overlayPaint) }
            }

            for (i in 0 until blackHoldFrames) {
                emitFrame { drawBitmapFrame(working, blackBitmap) }
            }

            val totalLogoFrames = secondsToFrames(LOGO_TOTAL_MS / 1000.0, fps).coerceAtLeast(1)
            val denominator = max(1, totalLogoFrames - 1)
            for (i in 0 until totalLogoFrames) {
                val timeMs = LOGO_TOTAL_MS * (i.toFloat() / denominator.toFloat())
                emitFrame { logoRenderer.render(working, timeMs.coerceIn(0f, LOGO_TOTAL_MS)) }
            }
        } finally {
            frameCount = localFrameCount
            working.recycle()
            blackBitmap.recycle()
        }
    }
}

private fun buildCrossfadeStages(bitmaps: Map<ReplayStage, Bitmap>): List<Bitmap> {
    val orderedStages = listOf(
        ReplayStage.Unedited,
        ReplayStage.ColorOnly,
        ReplayStage.ToneOnly,
        ReplayStage.ColorGrading,
        ReplayStage.Masks,
        ReplayStage.Effects,
        ReplayStage.FinalEdited
    )
    val sequence = ArrayList<Bitmap>()
    orderedStages.forEach { stage ->
        val bitmap = bitmaps[stage]
        if (bitmap != null && (sequence.isEmpty() || sequence.last() !== bitmap)) {
            sequence += bitmap
        }
    }
    return if (sequence.isEmpty()) listOfNotNull(bitmaps[ReplayStage.Unedited], bitmaps[ReplayStage.FinalEdited]) else sequence
}

private fun drawBitmapFrame(target: Bitmap, source: Bitmap) {
    val canvas = Canvas(target)
    canvas.drawBitmap(source, 0f, 0f, null)
}

private fun drawWipeFrame(target: Bitmap, edited: Bitmap, unedited: Bitmap, dividerX: Float, dividerPaint: Paint) {
    val width = target.width
    val height = target.height
    val canvas = Canvas(target)
    canvas.drawBitmap(edited, 0f, 0f, null)

    canvas.save()
    canvas.clipRect(dividerX.coerceAtLeast(0f), 0f, width.toFloat(), height.toFloat())
    canvas.drawBitmap(unedited, 0f, 0f, null)
    canvas.restore()

    val divider = dividerX.coerceIn(0f, width.toFloat())
    canvas.drawLine(divider, 0f, divider, height.toFloat(), dividerPaint)
}

private fun drawCrossfadeFrame(target: Bitmap, from: Bitmap, to: Bitmap, alpha: Float) {
    val canvas = Canvas(target)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.alpha = ((1f - alpha.coerceIn(0f, 1f)) * 255f).roundToInt()
    canvas.drawBitmap(from, 0f, 0f, paint)
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    canvas.drawBitmap(to, 0f, 0f, paint)
}

private fun drawFadeToBlack(target: Bitmap, source: Bitmap, alpha: Float, overlayPaint: Paint) {
    val canvas = Canvas(target)
    canvas.drawBitmap(source, 0f, 0f, null)
    overlayPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    canvas.drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), overlayPaint)
}

private fun secondsToFrames(seconds: Double, fps: Int): Int {
    return max(1, (seconds * fps).roundToInt())
}

private class LogoOutroRenderer(
    private val width: Int,
    private val height: Int
) {
    private val bladePath = Path().apply {
        moveTo(-10f, -150f)
        lineTo(80f, -150f)
        lineTo(140f, -40f)
        lineTo(0f, -40f)
        close()
    }
    private val centerHexPath = Path().apply {
        moveTo(0f, -40f)
        lineTo(35f, -20f)
        lineTo(35f, 20f)
        lineTo(0f, 40f)
        lineTo(-35f, 20f)
        lineTo(-35f, -20f)
        close()
    }
    private val blades = listOf(
        0f to 0xFFFF5252.toInt(),
        60f to 0xFFFFD740.toInt(),
        120f to 0xFF69F0AE.toInt(),
        180f to 0xFF40C4FF.toInt(),
        240f to 0xFF536DFE.toInt(),
        300f to 0xFFE040FB.toInt()
    )
    private val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val overshoot = PathInterpolator(0.3f, 0f, 0f, 1.3f)

    private val bladeCount = blades.size
    private val bladeHalfDuration = LOGO_BLADE_MS / 2f
    private val bladeSequenceEnd = LOGO_BLADE_MS + (bladeCount - 1) * LOGO_BLADE_STAGGER_MS
    private val centerStart = bladeSequenceEnd
    private val centerEnd = centerStart + LOGO_CENTER_MS
    private val fadeStart = centerEnd + LOGO_HOLD_MS
    private val fadeEnd = fadeStart + LOGO_FADE_MS

    fun render(target: Bitmap, timeMs: Float) {
        val canvas = Canvas(target)
        canvas.drawColor(Color.BLACK)
        canvas.save()
        val scale = min(width, height) / 420f
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)

        val alpha = computeOverlayAlpha(timeMs)
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()

        blades.forEachIndexed { index, (initialRotation, color) ->
            val travel = computeBladeTravel(timeMs, index)
            val tilt = computeBladeTilt(timeMs, index)
            bladePaint.color = applyAlpha(color, alphaInt)
            canvas.save()
            canvas.rotate(initialRotation)
            canvas.translate(0f, -travel)
            canvas.rotate(tilt, 0f, -40f)
            canvas.drawPath(bladePath, bladePaint)
            canvas.restore()
        }

        val centerScale = computeCenterScale(timeMs)
        if (centerScale > 0.0001f) {
            canvas.save()
            canvas.rotate(computeCenterRotation(timeMs))
            canvas.scale(centerScale, centerScale)
            centerPaint.alpha = alphaInt
            canvas.drawPath(centerHexPath, centerPaint)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun computeBladeTravel(timeMs: Float, index: Int): Float {
        val start = index * LOGO_BLADE_STAGGER_MS
        val elapsed = (timeMs - start).coerceAtLeast(0f)
        if (elapsed <= 0f) return 200f
        if (elapsed >= LOGO_BLADE_MS) return 0f
        val progress = (elapsed / LOGO_BLADE_MS).coerceIn(0f, 1f)
        val eased = overshoot.getInterpolation(progress)
        return (1f - eased.coerceIn(0f, 1f)) * 200f
    }

    private fun computeBladeTilt(timeMs: Float, index: Int): Float {
        val start = index * LOGO_BLADE_STAGGER_MS
        val elapsed = timeMs - start
        if (elapsed <= 0f || elapsed >= LOGO_BLADE_MS) return 0f
        return if (elapsed <= bladeHalfDuration) {
            val progress = (elapsed / bladeHalfDuration).coerceIn(0f, 1f)
            -15f * fastOutSlowIn.getInterpolation(progress)
        } else {
            val progress = ((elapsed - bladeHalfDuration) / bladeHalfDuration).coerceIn(0f, 1f)
            -15f * (1f - fastOutSlowIn.getInterpolation(progress))
        }
    }

    private fun computeCenterScale(timeMs: Float): Float {
        if (timeMs <= centerStart) return 0f
        if (timeMs >= centerEnd) return 1f
        val progress = ((timeMs - centerStart) / LOGO_CENTER_MS).coerceIn(0f, 1f)
        return overshoot.getInterpolation(progress).coerceIn(0f, 1.2f)
    }

    private fun computeCenterRotation(timeMs: Float): Float {
        if (timeMs <= centerStart) return 0f
        if (timeMs >= centerEnd) return 360f
        val progress = ((timeMs - centerStart) / LOGO_CENTER_MS).coerceIn(0f, 1f)
        return 360f * fastOutSlowIn.getInterpolation(progress)
    }

    private fun computeOverlayAlpha(timeMs: Float): Float {
        if (timeMs <= fadeStart) return 1f
        if (timeMs >= fadeEnd) return 0f
        val progress = ((timeMs - fadeStart) / LOGO_FADE_MS).coerceIn(0f, 1f)
        return 1f - fastOutSlowIn.getInterpolation(progress)
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        val combinedAlpha = (Color.alpha(color) * alpha) / 255
        return Color.argb(
            combinedAlpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
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
