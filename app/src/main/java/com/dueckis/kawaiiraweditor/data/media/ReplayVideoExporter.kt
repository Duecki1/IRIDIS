package com.dueckis.kawaiiraweditor.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.view.animation.PathInterpolator
import com.dueckis.kawaiiraweditor.R
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.sequences.SequenceScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_REPLAY_FPS = 30
private const val DEFAULT_REPLAY_MAX_DIM = 1440
private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

private const val LOGO_TOTAL_MS_BASE = 1_200f
private const val LOGO_BLADE_MS_BASE = 360f
private const val LOGO_BLADE_STAGGER_MS_BASE = 25f
private const val LOGO_CENTER_MS_BASE = 330f
private const val LOGO_HOLD_MS_BASE = 180f
private const val LOGO_FADE_MS_BASE = 180f

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

data class ReplayTiming(
    val fps: Int = DEFAULT_REPLAY_FPS,
    val wipeHoldSec: Double = 1.0,
    val wipeToEditedSec: Double = 1.6,
    val editedHoldSec: Double = 0.8,
    val wipeToOriginalSec: Double = 1.8,
    val originalHoldSec: Double = 0.8,
    val stageHoldSec: Double = 1.2,
    val stageFadeSec: Double = 1.4,
    val fadeToBlackSec: Double = 1.0,
    val blackHoldSec: Double = 0.6,
    val logoOutroSec: Double = 2.2,
    val watermarkFadeSec: Double = 0.5
)

data class OverlayStyle(
    val textScale: Float = 0.045f,
    val marginScale: Float = 0.030f,
    val padScale: Float = 0.018f,
    val cornerScale: Float = 0.014f,
    val bgAlpha: Int = 150
)

private data class LabelState(
    val text: String,
    val alpha: Float,
    val topRight: Boolean = true
)

private data class StageFrame(
    val stage: ReplayStage,
    val bitmap: Bitmap
)

internal suspend fun exportReplayVideo(
    context: Context,
    sessionHandle: Long,
    adjustments: AdjustmentState,
    masks: List<MaskState>,
    nativeDispatcher: CoroutineDispatcher,
    maxDimension: Int = DEFAULT_REPLAY_MAX_DIM,
    timing: ReplayTiming = ReplayTiming(),
    overlayStyle: OverlayStyle = OverlayStyle(),
    lowRamMode: Boolean = false,
    onProgress: ((current: Int, total: Int) -> Unit)? = null
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
    val normalizedTiming = if (timing.fps <= 0) timing.copy(fps = DEFAULT_REPLAY_FPS) else timing
    val fps = normalizedTiming.fps

    var watermarkIcon: Bitmap? = null

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

        watermarkIcon = loadWatermarkIcon(context, min(targetSize.first, targetSize.second))
        val frameGenerator = ReplayFrameGenerator(scaledBitmaps, normalizedTiming, overlayStyle, watermarkIcon)
        val frameSequence = frameGenerator.frames()
        val totalFramesEstimate = frameGenerator.estimatedFrameCount
        val trackedSequence = if (onProgress != null && totalFramesEstimate > 0) {
            sequence {
                var produced = 0
                onProgress.invoke(0, totalFramesEstimate)
                for (frame in frameSequence) {
                    produced += 1
                    onProgress.invoke(produced, totalFramesEstimate)
                    yield(frame)
                }
            }
        } else {
            frameSequence
        }
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
            encoder.encode(trackedSequence, frameDurationUs)
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
        watermarkIcon?.let {
            if (!it.isRecycled) it.recycle()
        }
        watermarkIcon = null
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

    var currentState = base

    val toneState = withTone(base, adjustments)
    if (toneState != currentState) {
        specs[ReplayStage.ToneOnly] = ReplayRenderSpec(toneState, emptyList())
        currentState = toneState
    }

    val colorState = withColorOnly(currentState, adjustments)
    if (colorState != currentState) {
        specs[ReplayStage.ColorOnly] = ReplayRenderSpec(colorState, emptyList())
        currentState = colorState
    }

    val gradingState = if (!adjustments.colorGrading.isDefault()) {
        val graded = withColorGrading(currentState, adjustments)
        if (graded != currentState) {
            specs[ReplayStage.ColorGrading] = ReplayRenderSpec(graded, emptyList())
        }
        graded
    } else {
        currentState
    }

    currentState = gradingState

    val maskList = if (masks.isNotEmpty()) masks else emptyList()
    if (masks.isNotEmpty()) {
        specs[ReplayStage.Masks] = ReplayRenderSpec(currentState, maskList)
    }

    val effectsState = withEffects(currentState, adjustments)
    if (effectsState != currentState) {
        specs[ReplayStage.Effects] = ReplayRenderSpec(effectsState, maskList)
        currentState = effectsState
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

private fun secondsToFrames(seconds: Double, fps: Int): Int {
    if (seconds <= 0.0) return 0
    return max(1, (seconds * fps).roundToInt())
}

private fun drawStageLabel(
    canvas: Canvas,
    label: String,
    frameW: Int,
    frameH: Int,
    alpha01: Float = 1f,
    style: OverlayStyle = OverlayStyle(),
    topRight: Boolean = true
) {
    if (label.isBlank()) return
    val labelAlpha = alpha01.coerceIn(0f, 1f)
    if (labelAlpha <= 0.001f) return

    val minDim = min(frameW, frameH).toFloat()
    val textSize = (minDim * style.textScale).coerceAtLeast(18f)
    val margin = minDim * style.marginScale
    val pad = minDim * style.padScale
    val radius = minDim * style.cornerScale

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = textSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        this.alpha = (labelAlpha * 255f).roundToInt().coerceIn(0, 255)
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        this.alpha = (style.bgAlpha * labelAlpha).roundToInt().coerceIn(0, 255)
    }

    val textBounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, textBounds)
    val textWidth = textPaint.measureText(label)
    val textHeight = textBounds.height().toFloat()

    val boxWidth = textWidth + pad * 2f
    val boxHeight = textHeight + pad * 2f
    val left = if (topRight) frameW - margin - boxWidth else margin
    val top = margin
    val rect = RectF(left, top, left + boxWidth, top + boxHeight)

    canvas.drawRoundRect(rect, radius, radius, bgPaint)
    val baseline = rect.top + pad - textBounds.top
    canvas.drawText(label, rect.left + pad, baseline, textPaint)
}

private fun loadWatermarkIcon(context: Context, frameMinDimension: Int): Bitmap? {
    val baseSize = (frameMinDimension * 0.12f).roundToInt().coerceIn(48, 256)
    val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: baseSize
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: baseSize
    val scale = baseSize / max(width, height).toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(24)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, targetWidth, targetHeight)
    drawable.draw(canvas)
    return bitmap
}

private fun drawWatermark(bitmap: Bitmap, overlayStyle: OverlayStyle, iconBitmap: Bitmap?, alpha: Float) {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return

    val minDim = min(width, height).toFloat()
    val margin = minDim * 0.02f
    val innerPad = margin * 0.6f
    val iconSize = (minDim * 0.085f).coerceAtLeast(40f)
    val titleSize = (minDim * 0.028f).coerceAtLeast(16f)
    val subtitleSize = (titleSize * 0.58f).coerceAtLeast(11f)
    val subtitleSpacing = subtitleSize * 0.45f
    val textBlockHeight = titleSize + subtitleSpacing + subtitleSize
    val contentHeight = max(iconSize, textBlockHeight)
    val bgHeight = contentHeight + innerPad * 2f
    val bgTop = height - margin - bgHeight
    if (bgTop < 0f) return
    val bgLeft = margin
    val brandText = "IRIDIS"
    val taglineText = "Open-source raw editor for android"

    val opacity = alpha.coerceIn(0f, 1f)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = titleSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        this.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.alpha = ((overlayStyle.bgAlpha * 0.85f) * opacity).roundToInt().coerceIn(0, 255)
        textSize = subtitleSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val titleWidth = textPaint.measureText(brandText)
    val subtitleWidth = subtitlePaint.measureText(taglineText)
    val maxTextWidth = max(titleWidth, subtitleWidth)
    val contentWidth = iconSize + innerPad + maxTextWidth
    val bgWidth = contentWidth + innerPad * 2f
    val bgRight = (bgLeft + bgWidth).coerceAtMost(width - margin)
    val adjustedContentWidth = bgRight - bgLeft - innerPad * 2f
    val textScale = if (adjustedContentWidth < contentWidth) adjustedContentWidth / contentWidth else 1f
    if (textScale < 1f) {
        textPaint.textSize *= textScale
        subtitlePaint.textSize *= textScale
    }

    val canvas = Canvas(bitmap)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        this.alpha = ((overlayStyle.bgAlpha * 0.85f) * opacity).roundToInt().coerceIn(0, 255)
    }
    val radius = bgHeight * 0.22f
    val bgRect = RectF(bgLeft, bgTop, bgRight, bgTop + bgHeight)
    canvas.drawRoundRect(bgRect, radius, radius, bgPaint)

    val iconLeft = bgRect.left + innerPad
    val iconTop = bgRect.top + innerPad + (contentHeight - iconSize) / 2f
    val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)

    val icon = iconBitmap
    if (icon != null && !icon.isRecycled) {
        val srcRect = Rect(0, 0, icon.width, icon.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255) }
        canvas.drawBitmap(icon, srcRect, iconRect, paint)
    } else {
        val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7AC4")
            this.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(iconRect, iconSize * 0.2f, iconSize * 0.2f, iconBgPaint)

        val iconCenterX = iconRect.centerX()
        val iconCenterY = iconRect.centerY()
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = iconSize * 0.12f
            this.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        }
        canvas.drawCircle(iconCenterX, iconCenterY, iconSize * 0.28f, ringPaint)
        ringPaint.style = Paint.Style.FILL
        ringPaint.alpha = (180 * opacity).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(iconCenterX, iconCenterY, iconSize * 0.14f, ringPaint)
    }

    val textStartX = iconRect.right + innerPad
    val textBlockTop = bgRect.top + innerPad + (contentHeight - textBlockHeight) / 2f
    val titleBaseline = textBlockTop + textPaint.textSize
    val subtitleBaseline = titleBaseline + subtitleSpacing + subtitlePaint.textSize
    canvas.drawText(brandText, textStartX, titleBaseline, textPaint)
    canvas.drawText(taglineText, textStartX, subtitleBaseline, subtitlePaint)
}

private class ReplayFrameGenerator(
    private val bitmaps: Map<ReplayStage, Bitmap>,
    private val timing: ReplayTiming,
    private val overlayStyle: OverlayStyle,
    private val watermarkIcon: Bitmap?
) {
    private val fps = timing.fps.coerceAtLeast(1)
    private val frameDurationUs = 1_000_000L / fps
    private val fastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val watermarkFadeFrames = secondsToFrames(timing.watermarkFadeSec, fps)

    var frameCount: Int = 0
        private set
    var estimatedFrameCount: Int = 0
        private set

    fun frames(): Sequence<PreparedFrame> {
        val edited = bitmaps[ReplayStage.FinalEdited] ?: return emptySequence()
        val unedited = bitmaps[ReplayStage.Unedited] ?: return emptySequence()
        val stageSequence = buildCrossfadeStages(bitmaps)
        if (stageSequence.isEmpty()) return emptySequence()
        estimatedFrameCount = estimateFrameTotal(stageSequence)
        return sequence {
            val logoStartFrame = computeLogoStartFrame(stageSequence)

            val width = edited.width
            val height = edited.height
            val working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = max(2f, width * 0.0025f)
            }
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            val blackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            val logoDurationMs = (timing.logoOutroSec * 1_000.0).toFloat().coerceAtLeast(1f)
            val logoRenderer = LogoOutroRenderer(width, height, logoDurationMs)

            var localFrame = 0

            suspend fun SequenceScope<PreparedFrame>.emitFrame(labels: List<LabelState>, draw: () -> Unit) {
                draw()
                if (labels.isNotEmpty()) {
                    val labelCanvas = Canvas(working)
                    labels.forEach { state ->
                        if (state.alpha > 0.001f) {
                            drawStageLabel(labelCanvas, state.text, width, height, state.alpha, overlayStyle, state.topRight)
                        }
                    }
                }
                val watermarkOpacity = watermarkAlpha(localFrame, logoStartFrame)
                if (watermarkOpacity > 0f) {
                    drawWatermark(working, overlayStyle, watermarkIcon, watermarkOpacity)
                }
                val data = working.toI420()
                yield(PreparedFrame(data, localFrame.toLong() * frameDurationUs))
                localFrame += 1
            }

            try {
                val finalLabel = labelForStage(ReplayStage.FinalEdited)
                val originalLabel = labelForStage(ReplayStage.Unedited)

            val holdSplitFrames = secondsToFrames(timing.wipeHoldSec, fps).coerceAtLeast(1)
            repeat(holdSplitFrames) {
                emitFrame(
                    listOf(
                        LabelState(finalLabel, 1f, topRight = true),
                        LabelState(originalLabel, 1f, topRight = false)
                    )
                ) { drawWipeFrame(working, edited, unedited, width / 2f, dividerPaint) }
            }

            val toEditedFrames = secondsToFrames(timing.wipeToEditedSec, fps)
            for (i in 0 until toEditedFrames.coerceAtLeast(1)) {
                val progress = if (toEditedFrames <= 1) 1f else (i + 1).toFloat() / toEditedFrames
                val eased = fastOutSlowIn.getInterpolation(progress.coerceIn(0f, 1f))
                val dividerX = (width / 2f) + (width / 2f) * eased
                emitFrame(
                    listOf(
                        LabelState(finalLabel, 1f, topRight = true),
                        LabelState(originalLabel, (1f - eased).coerceAtLeast(0f), topRight = false)
                    )
                ) { drawWipeFrame(working, edited, unedited, dividerX, dividerPaint) }
            }

            val holdEditedFrames = secondsToFrames(timing.editedHoldSec, fps)
            repeat(holdEditedFrames.coerceAtLeast(1)) {
                emitFrame(listOf(LabelState(finalLabel, 1f, topRight = true))) { drawBitmapFrame(working, edited) }
            }

            val toOriginalFrames = secondsToFrames(timing.wipeToOriginalSec, fps)
            for (i in 0 until toOriginalFrames.coerceAtLeast(1)) {
                val progress = if (toOriginalFrames <= 1) 1f else (i + 1).toFloat() / toOriginalFrames
                val eased = fastOutSlowIn.getInterpolation(progress.coerceIn(0f, 1f))
                val dividerX = width.toFloat() * (1f - eased)
                emitFrame(
                    listOf(
                        LabelState(finalLabel, (1f - eased).coerceAtLeast(0f), topRight = true),
                        LabelState(originalLabel, 1f, topRight = false)
                    )
                ) { drawWipeFrame(working, edited, unedited, dividerX, dividerPaint) }
            }

            val holdOriginalFrames = secondsToFrames(timing.originalHoldSec, fps)
            repeat(holdOriginalFrames.coerceAtLeast(1)) {
                emitFrame(listOf(LabelState(originalLabel, 1f, topRight = false))) {
                    drawBitmapFrame(working, unedited)
                }
            }

            stageSequence.windowed(size = 2, step = 1, partialWindows = false).forEach { pair ->
                val current = pair[0]
                val next = pair[1]
                val currentLabel = labelForStage(current.stage)
                val nextLabel = labelForStage(next.stage)

                val holdFrames = secondsToFrames(timing.stageHoldSec, fps)
                repeat(holdFrames.coerceAtLeast(1)) {
                    emitFrame(
                        listOf(LabelState(currentLabel, 1f, isLabelTopRight(current.stage)))
                    ) { drawBitmapFrame(working, current.bitmap) }
                }

                val fadeFrames = secondsToFrames(timing.stageFadeSec, fps)
                val framesForFade = fadeFrames.coerceAtLeast(1)
                for (i in 0 until framesForFade) {
                    val progress = if (framesForFade <= 1) 1f else i.toFloat() / (framesForFade - 1)
                    val eased = fastOutSlowIn.getInterpolation(progress.coerceIn(0f, 1f))
                    val labels = listOfNotNull(
                        LabelState(currentLabel, (1f - eased).coerceAtLeast(0f), isLabelTopRight(current.stage))
                            .takeIf { it.alpha > 0.01f },
                        LabelState(nextLabel, eased.coerceAtLeast(0f), isLabelTopRight(next.stage))
                            .takeIf { it.alpha > 0.01f }
                    )
                    emitFrame(labels) { drawCrossfadeFrame(working, current.bitmap, next.bitmap, eased) }
                }
            }

            val finalStageEntry = stageSequence.last()
            val finalStageLabel = labelForStage(finalStageEntry.stage)
            val finalHoldFrames = secondsToFrames(timing.stageHoldSec, fps)
            repeat(finalHoldFrames.coerceAtLeast(1)) {
                emitFrame(
                    listOf(LabelState(finalStageLabel, 1f, isLabelTopRight(finalStageEntry.stage)))
                ) { drawBitmapFrame(working, finalStageEntry.bitmap) }
            }

            val fadeFrames = secondsToFrames(timing.fadeToBlackSec, fps)
            val fadeFrameCount = fadeFrames.coerceAtLeast(1)
            for (i in 0 until fadeFrameCount) {
                val progress = if (fadeFrameCount <= 1) 1f else (i + 1).toFloat() / fadeFrameCount
                val eased = fastOutSlowIn.getInterpolation(progress.coerceIn(0f, 1f))
                emitFrame(
                    listOf(LabelState(finalStageLabel, (1f - eased).coerceAtLeast(0f), isLabelTopRight(finalStageEntry.stage)))
                ) { drawFadeToBlack(working, finalStageEntry.bitmap, eased, overlayPaint) }
            }

            val blackHoldFrames = secondsToFrames(timing.blackHoldSec, fps)
            repeat(blackHoldFrames) {
                emitFrame(emptyList()) { drawBitmapFrame(working, blackBitmap) }
            }

            val totalLogoFrames = secondsToFrames(timing.logoOutroSec, fps).coerceAtLeast(1)
            val denominator = max(1, totalLogoFrames - 1)
            for (i in 0 until totalLogoFrames) {
                val timeMs = logoDurationMs * (i.toFloat() / denominator.toFloat())
                emitFrame(emptyList()) {
                    logoRenderer.render(working, timeMs.coerceIn(0f, logoRenderer.durationMs))
                }
            }
            } finally {
                frameCount = localFrame
                working.recycle()
                blackBitmap.recycle()
            }
        }
    }

    private fun watermarkAlpha(frameIndex: Int, logoStartFrame: Int): Float {
        val fadeFrames = watermarkFadeFrames.coerceAtLeast(1)
        val fadeStart = (logoStartFrame - fadeFrames).coerceAtLeast(0)
        return when {
            frameIndex >= logoStartFrame -> 0f
            frameIndex <= fadeStart -> 1f
            else -> 1f - ((frameIndex - fadeStart).toFloat() / fadeFrames.toFloat()).coerceIn(0f, 1f)
        }
    }

    private fun computeLogoStartFrame(stageSequence: List<StageFrame>): Int {
        fun positiveFrames(raw: Int): Int = raw.coerceAtLeast(1)

        val holdSplit = positiveFrames(secondsToFrames(timing.wipeHoldSec, fps))
        val toEdited = positiveFrames(secondsToFrames(timing.wipeToEditedSec, fps))
        val holdEdited = positiveFrames(secondsToFrames(timing.editedHoldSec, fps))
        val toOriginal = positiveFrames(secondsToFrames(timing.wipeToOriginalSec, fps))
        val holdOriginal = positiveFrames(secondsToFrames(timing.originalHoldSec, fps))

        val pairCount = (stageSequence.size - 1).coerceAtLeast(0)
        val stageHold = positiveFrames(secondsToFrames(timing.stageHoldSec, fps))
        val stageFade = positiveFrames(secondsToFrames(timing.stageFadeSec, fps))
        val stageFrames = pairCount * (stageHold + stageFade) + stageHold

        val fadeToBlack = positiveFrames(secondsToFrames(timing.fadeToBlackSec, fps))
        val blackHold = secondsToFrames(timing.blackHoldSec, fps).coerceAtLeast(0)

        return holdSplit + toEdited + holdEdited + toOriginal + holdOriginal + stageFrames + fadeToBlack + blackHold
    }

    private fun estimateFrameTotal(stageSequence: List<StageFrame>): Int {
        fun holdFrames(seconds: Double): Int = secondsToFrames(seconds, fps).coerceAtLeast(1)

        var total = 0
        total += holdFrames(timing.wipeHoldSec)
        total += holdFrames(timing.wipeToEditedSec)
        total += holdFrames(timing.editedHoldSec)
        total += holdFrames(timing.wipeToOriginalSec)
        total += holdFrames(timing.originalHoldSec)

        val stageCount = stageSequence.size
        if (stageCount > 0) {
            val stageHold = holdFrames(timing.stageHoldSec)
            val stageFade = holdFrames(timing.stageFadeSec)
            total += stageCount * stageHold
            total += (stageCount - 1).coerceAtLeast(0) * stageFade
        }

        total += holdFrames(timing.fadeToBlackSec)
        total += secondsToFrames(timing.blackHoldSec, fps)
        total += holdFrames(timing.logoOutroSec)

        return total
    }
}

private fun buildCrossfadeStages(bitmaps: Map<ReplayStage, Bitmap>): List<StageFrame> {
    val orderedStages = listOf(
        ReplayStage.ToneOnly,
        ReplayStage.ColorOnly,
        ReplayStage.ColorGrading,
        ReplayStage.Masks,
        ReplayStage.Effects,
        ReplayStage.FinalEdited
    )
    val sequence = ArrayList<StageFrame>()
    orderedStages.forEach { stage ->
        val bitmap = bitmaps[stage]
        if (bitmap != null && (sequence.isEmpty() || sequence.last().bitmap !== bitmap)) {
            sequence += StageFrame(stage, bitmap)
        }
    }
    val unedited = bitmaps[ReplayStage.Unedited]
    if (unedited != null && (sequence.isEmpty() || sequence.first().bitmap !== unedited)) {
        sequence.add(0, StageFrame(ReplayStage.Unedited, unedited))
    }
    if (sequence.isEmpty()) {
        val fallbackUnedited = bitmaps[ReplayStage.Unedited]
        val edited = bitmaps[ReplayStage.FinalEdited]
        return listOfNotNull(
            fallbackUnedited?.let { StageFrame(ReplayStage.Unedited, it) },
            edited?.let { StageFrame(ReplayStage.FinalEdited, it) }
        )
    }
    return sequence
}

private fun labelForStage(stage: ReplayStage): String = when (stage) {
    ReplayStage.Unedited -> "Original"
    ReplayStage.ToneOnly -> "Tone"
    ReplayStage.ColorOnly -> "Color"
    ReplayStage.ColorGrading -> "Color Grading"
    ReplayStage.Masks -> "Masks"
    ReplayStage.Effects -> "Effects"
    ReplayStage.FinalEdited -> "Final"
}

private fun isLabelTopRight(stage: ReplayStage): Boolean = when (stage) {
    ReplayStage.Unedited -> false
    ReplayStage.ColorOnly -> true
    ReplayStage.ToneOnly -> true
    ReplayStage.ColorGrading -> true
    ReplayStage.Masks -> true
    ReplayStage.Effects -> true
    ReplayStage.FinalEdited -> true
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

private class LogoOutroRenderer(
    private val width: Int,
    private val height: Int,
    totalDurationMs: Float
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
    private val scaleFactor = if (LOGO_TOTAL_MS_BASE <= 0f) 1f else (totalDurationMs / LOGO_TOTAL_MS_BASE).coerceAtLeast(0.1f)
    private val bladeDurationMs = LOGO_BLADE_MS_BASE * scaleFactor
    private val bladeStaggerMs = LOGO_BLADE_STAGGER_MS_BASE * scaleFactor
    private val centerDurationMs = LOGO_CENTER_MS_BASE * scaleFactor
    private val holdDurationMs = LOGO_HOLD_MS_BASE * scaleFactor
    private val fadeDurationMs = LOGO_FADE_MS_BASE * scaleFactor
    private val bladeHalfDuration = bladeDurationMs / 2f
    private val bladeSequenceEnd = bladeDurationMs + (bladeCount - 1) * bladeStaggerMs
    private val centerStart = bladeSequenceEnd
    private val centerEnd = centerStart + centerDurationMs
    private val fadeStart = centerEnd + holdDurationMs
    private val fadeEnd = fadeStart + fadeDurationMs

    val durationMs: Float = totalDurationMs

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
        val start = index * bladeStaggerMs
        val elapsed = (timeMs - start).coerceAtLeast(0f)
        if (elapsed <= 0f) return 200f
        if (elapsed >= bladeDurationMs) return 0f
        val progress = (elapsed / bladeDurationMs).coerceIn(0f, 1f)
        val eased = overshoot.getInterpolation(progress)
        return (1f - eased.coerceIn(0f, 1f)) * 200f
    }

    private fun computeBladeTilt(timeMs: Float, index: Int): Float {
        val start = index * bladeStaggerMs
        val elapsed = timeMs - start
        if (elapsed <= 0f || elapsed >= bladeDurationMs) return 0f
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
        val progress = ((timeMs - centerStart) / centerDurationMs).coerceIn(0f, 1f)
        return overshoot.getInterpolation(progress).coerceIn(0f, 1.2f)
    }

    private fun computeCenterRotation(timeMs: Float): Float {
        if (timeMs <= centerStart) return 0f
        if (timeMs >= centerEnd) return 360f
        val progress = ((timeMs - centerStart) / centerDurationMs).coerceIn(0f, 1f)
        return 360f * fastOutSlowIn.getInterpolation(progress)
    }

    private fun computeOverlayAlpha(timeMs: Float): Float {
        if (timeMs <= fadeStart) return 1f
        if (timeMs >= fadeEnd) return 0f
        val progress = ((timeMs - fadeStart) / fadeDurationMs).coerceIn(0f, 1f)
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
