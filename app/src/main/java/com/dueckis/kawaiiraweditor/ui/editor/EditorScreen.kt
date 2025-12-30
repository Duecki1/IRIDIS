package com.dueckis.kawaiiraweditor.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushLineState
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.CropState
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import com.dueckis.kawaiiraweditor.data.model.CurvesState
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.MaskHandle
import com.dueckis.kawaiiraweditor.data.model.MaskPoint
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.RadialMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.RenderRequest
import com.dueckis.kawaiiraweditor.data.model.RenderTarget
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.domain.HistogramUtils
import com.dueckis.kawaiiraweditor.domain.ai.AiEnvironmentMaskGenerator
import com.dueckis.kawaiiraweditor.domain.ai.AiSubjectMaskGenerator
import com.dueckis.kawaiiraweditor.domain.ai.NormalizedPoint
import com.dueckis.kawaiiraweditor.domain.editor.EditorHistoryEntry
import com.dueckis.kawaiiraweditor.ui.editor.components.ExportButton
import com.dueckis.kawaiiraweditor.ui.editor.controls.AutoCropParams
import com.dueckis.kawaiiraweditor.ui.editor.controls.EditorControlsContent
import com.dueckis.kawaiiraweditor.ui.editor.controls.computeMaxCropNormalized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
internal fun EditorScreen(
    galleryItem: GalleryItem?,
    lowQualityPreviewEnabled: Boolean,
    environmentMaskingEnabled: Boolean,
    onBackClick: () -> Unit,
    onPredictiveBackProgress: (Float) -> Unit,
    onPredictiveBackCancelled: () -> Unit,
    onPredictiveBackCommitted: () -> Unit
) {
    val logTag = "EditorScreen"
    if (galleryItem == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }

    var detectedAiEnvironmentCategories by remember { mutableStateOf<List<AiEnvironmentCategory>?>(null) }
    var isDetectingAiEnvironmentCategories by remember { mutableStateOf(false) }
    LaunchedEffect(galleryItem?.projectId) {
        detectedAiEnvironmentCategories = null
        isDetectingAiEnvironmentCategories = false
    }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var sessionHandle by remember { mutableStateOf(0L) }
    var adjustments by remember { mutableStateOf(AdjustmentState()) }
    var masks by remember { mutableStateOf<List<MaskState>>(emptyList()) }
    val maskNumbers = remember { mutableStateMapOf<String, Int>() }

    fun masksForRender(source: List<MaskState>): List<MaskState> {
        if (environmentMaskingEnabled) return source
        return source.mapNotNull { mask ->
            val remaining = mask.subMasks.filterNot { it.type == SubMaskType.AiEnvironment.id }
            if (remaining.isEmpty()) null else mask.copy(subMasks = remaining)
        }
    }

    fun assignNumber(maskId: String) {
        if (maskId !in maskNumbers) {
            val next = (maskNumbers.values.maxOrNull() ?: 0) + 1
            maskNumbers[maskId] = next
        }
    }

    var editedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedViewportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedViewportRoi by remember { mutableStateOf<CropState?>(null) }
    val currentViewportRoi = remember { AtomicReference<CropState?>(null) }
    var viewportScale by remember { mutableFloatStateOf(1f) }
    var viewportRoiForDebounce by remember { mutableStateOf<CropState?>(null) }
    var fullPreviewDirtyByViewport by remember { mutableStateOf(false) }

    fun zoomMaxDimensionForScale(scale: Float): Int {
        val minDim = 1280
        val maxDim = 2304
        val minScale = 1.1f
        val maxScale = 5f
        val t = ((scale - minScale) / (maxScale - minScale)).coerceIn(0f, 1f)
        val desired = (minDim + (maxDim - minDim) * t).roundToInt()
        val clamped = desired.coerceIn(minDim, maxDim)
        val step = 128
        return (clamped / step) * step
    }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uncroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedPreviewRotationDegrees by remember(galleryItem.projectId) { mutableFloatStateOf(0f) }
    var uncroppedPreviewRotationDegrees by remember(galleryItem.projectId) { mutableFloatStateOf(0f) }
    var isComparingOriginal by remember { mutableStateOf(false) }
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
    val editHistory = remember(galleryItem.projectId) { mutableStateListOf<EditorHistoryEntry>() }
    var editHistoryIndex by remember(galleryItem.projectId) { mutableIntStateOf(-1) }
    var isRestoringEditHistory by remember(galleryItem.projectId) { mutableStateOf(false) }
    var isHistoryInteractionActive by remember(galleryItem.projectId) { mutableStateOf(false) }

    PredictiveBackHandler {
        try {
            val shouldAnimate = !showAiSubjectOverrideDialog && !showMetadataDialog
            it.collect { event ->
                if (shouldAnimate) {
                    onPredictiveBackProgress(event.progress)
                }
            }
            when {
                showAiSubjectOverrideDialog -> {
                    showAiSubjectOverrideDialog = false
                    aiSubjectOverrideTarget = null
                }

                showMetadataDialog -> {
                    showMetadataDialog = false
                }

                else -> onPredictiveBackCommitted()
            }
        } catch (_: CancellationException) {
            onPredictiveBackCancelled()
        }
    }

    var panelTab by remember { mutableStateOf(EditorPanelTab.Adjustments) }
    val isCropMode = panelTab == EditorPanelTab.CropTransform
    val isCropPreviewActive = isCropMode && !isComparingOriginal
    var selectedMaskId by remember { mutableStateOf<String?>(null) }
    var selectedSubMaskId by remember { mutableStateOf<String?>(null) }
    var isPaintingMask by remember { mutableStateOf(false) }
    var maskTapMode by remember { mutableStateOf(MaskTapMode.None) }
    var brushSize by remember { mutableStateOf(60f) }
    var brushTool by remember { mutableStateOf(BrushTool.Brush) }
    var brushSoftness by remember { mutableStateOf(0.5f) }
    var eraserSoftness by remember { mutableStateOf(0.5f) }
    var showMaskOverlay by remember { mutableStateOf(false) }
    var maskOverlayBlinkKey by remember { mutableStateOf(0L) }
    var maskOverlayBlinkSubMaskId by remember { mutableStateOf<String?>(null) }
    fun requestMaskOverlayBlink(highlightSubMaskId: String?) {
        maskOverlayBlinkSubMaskId = highlightSubMaskId
        maskOverlayBlinkKey++
    }
    val strokeOrder = remember { AtomicLong(0L) }

    var cropDraft by remember(galleryItem.projectId) { mutableStateOf<CropState?>(null) }
    var rotationDraft by remember(galleryItem.projectId) { mutableStateOf<Float?>(null) }
    var isStraightenActive by remember(galleryItem.projectId) { mutableStateOf(false) }
    var isCropGestureActive by remember(galleryItem.projectId) { mutableStateOf(false) }

    val cropBaseBitmap = if (isCropMode) (uncroppedBitmap ?: editedBitmap) else null
    val cropBaseWidthPx = cropBaseBitmap?.width
    val cropBaseHeightPx = cropBaseBitmap?.height
    val cropPreviewBaseRotation =
        if (uncroppedBitmap != null) uncroppedPreviewRotationDegrees else editedPreviewRotationDegrees
    val previewRotationDelta =
        if (!isCropPreviewActive) 0f else ((rotationDraft ?: adjustments.rotation) - cropPreviewBaseRotation)

    val editedBitmapForUi = if (isCropMode) (uncroppedBitmap ?: editedBitmap) else editedBitmap
    val displayBitmap = if (isComparingOriginal) (originalBitmap ?: editedBitmapForUi) else editedBitmapForUi

    fun normalizedCropOrFull(crop: CropState?): CropState {
        val c = crop?.normalized()
        val w = c?.width ?: 1f
        val h = c?.height ?: 1f
        if (c == null || w <= 0.0001f || h <= 0.0001f) return CropState(0f, 0f, 1f, 1f)
        return c
    }

    fun applyOrientationSteps(point: MaskPoint, steps: Int): MaskPoint {
        return when (((steps % 4) + 4) % 4) {
            1 -> MaskPoint(x = 1f - point.y, y = point.x, pressure = point.pressure)
            2 -> MaskPoint(x = 1f - point.x, y = 1f - point.y, pressure = point.pressure)
            3 -> MaskPoint(x = point.y, y = 1f - point.x, pressure = point.pressure)
            else -> point
        }
    }

    fun applyFlips(point: MaskPoint, flipH: Boolean, flipV: Boolean): MaskPoint {
        val x = if (flipH) 1f - point.x else point.x
        val y = if (flipV) 1f - point.y else point.y
        return MaskPoint(x = x, y = y, pressure = point.pressure)
    }

    fun rotatePointNormalized(point: MaskPoint, angleDegrees: Float, widthPx: Float, heightPx: Float): MaskPoint {
        val w = widthPx.coerceAtLeast(1f)
        val h = heightPx.coerceAtLeast(1f)
        val rad = (angleDegrees.toDouble() * PI) / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val ux = (point.x - 0.5f) * w
        val uy = (point.y - 0.5f) * h
        val rx = ux * cosA - uy * sinA
        val ry = ux * sinA + uy * cosA
        return MaskPoint(x = (rx / w) + 0.5f, y = (ry / h) + 0.5f, pressure = point.pressure)
    }

    fun remapPointForTransform(
        point: MaskPoint,
        oldCrop: CropState,
        oldRotation: Float,
        oldOrientationSteps: Int,
        oldFlipH: Boolean,
        oldFlipV: Boolean,
        newCrop: CropState,
        newRotation: Float,
        newOrientationSteps: Int,
        newFlipH: Boolean,
        newFlipV: Boolean,
        baseWidthPx: Float,
        baseHeightPx: Float
    ): MaskPoint {
        val baseW = baseWidthPx.coerceAtLeast(1f)
        val baseH = baseHeightPx.coerceAtLeast(1f)
        val oldSteps = ((oldOrientationSteps % 4) + 4) % 4
        val newSteps = ((newOrientationSteps % 4) + 4) % 4
        val oldW = if (oldSteps % 2 == 1) baseH else baseW
        val oldH = if (oldSteps % 2 == 1) baseW else baseH
        val newW = if (newSteps % 2 == 1) baseH else baseW
        val newH = if (newSteps % 2 == 1) baseW else baseH

        val preCrop =
            MaskPoint(
                x = oldCrop.x + point.x * oldCrop.width,
                y = oldCrop.y + point.y * oldCrop.height,
                pressure = point.pressure
            )
        val unrotated = rotatePointNormalized(preCrop, -oldRotation, oldW, oldH)
        val unflipped = applyFlips(unrotated, flipH = oldFlipH, flipV = oldFlipV)
        val canonical = applyOrientationSteps(unflipped, steps = -oldSteps)

        val oriented = applyOrientationSteps(canonical, steps = newSteps)
        val flipped = applyFlips(oriented, flipH = newFlipH, flipV = newFlipV)
        val rotatedNew = rotatePointNormalized(flipped, newRotation, newW, newH)
        val outX = if (newCrop.width <= 0.0001f) rotatedNew.x else (rotatedNew.x - newCrop.x) / newCrop.width
        val outY = if (newCrop.height <= 0.0001f) rotatedNew.y else (rotatedNew.y - newCrop.y) / newCrop.height
        return MaskPoint(x = outX, y = outY, pressure = point.pressure)
    }

    fun decodeDataUrlBitmap(dataUrl: String): Bitmap? {
        val idx = dataUrl.indexOf("base64,")
        if (idx < 0) return null
        val b64 = dataUrl.substring(idx + "base64,".length)
        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun encodeBitmapToPngDataUrl(bitmap: Bitmap): String? {
        return runCatching {
            val outputStream = java.io.ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) return@runCatching null
            val b64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            "data:image/png;base64,$b64"
        }.getOrNull()
    }

    fun rotateBitmapInPlaceSameSize(src: Bitmap, angleDegrees: Float): Bitmap {
        if (angleDegrees == 0f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val matrix = Matrix().apply { postRotate(angleDegrees, w / 2f, h / 2f) }
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    fun rotateBitmapSteps(src: Bitmap, steps: Int): Bitmap {
        val s = ((steps % 4) + 4) % 4
        if (s == 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val matrix = Matrix().apply { postRotate((s * 90).toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out.config != Bitmap.Config.ARGB_8888) {
            val converted = out.copy(Bitmap.Config.ARGB_8888, true)
            out.recycle()
            return converted
        }
        return out
    }

    fun flipBitmap(src: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        if (!horizontal && !vertical) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val matrix = Matrix().apply { postScale(sx, sy, src.width / 2f, src.height / 2f) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out.config != Bitmap.Config.ARGB_8888) {
            val converted = out.copy(Bitmap.Config.ARGB_8888, true)
            out.recycle()
            return converted
        }
        return out
    }

    fun remapMaskDataUrlForTransform(
        dataUrl: String,
        baseWidthPx: Int,
        baseHeightPx: Int,
        oldAdjustments: AdjustmentState,
        newAdjustments: AdjustmentState
    ): String? {
        val decoded = decodeDataUrlBitmap(dataUrl) ?: return null
        val baseW = baseWidthPx.coerceAtLeast(1)
        val baseH = baseHeightPx.coerceAtLeast(1)

        val oldCrop = normalizedCropOrFull(oldAdjustments.crop)
        val newCrop = normalizedCropOrFull(newAdjustments.crop)

        val oldSteps = ((oldAdjustments.orientationSteps % 4) + 4) % 4
        val newSteps = ((newAdjustments.orientationSteps % 4) + 4) % 4
        val oldW = if (oldSteps % 2 == 1) baseH else baseW
        val oldH = if (oldSteps % 2 == 1) baseW else baseH
        val newW = if (newSteps % 2 == 1) baseH else baseW
        val newH = if (newSteps % 2 == 1) baseW else baseH

        val oldFull = Bitmap.createBitmap(oldW, oldH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(oldFull)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        val left = (oldCrop.x * oldW).coerceIn(0f, oldW.toFloat())
        val top = (oldCrop.y * oldH).coerceIn(0f, oldH.toFloat())
        val right = ((oldCrop.x + oldCrop.width) * oldW).coerceIn(0f, oldW.toFloat())
        val bottom = ((oldCrop.y + oldCrop.height) * oldH).coerceIn(0f, oldH.toFloat())
        if (right - left < 1f || bottom - top < 1f) {
            decoded.recycle()
            oldFull.recycle()
            return null
        }
        canvas.drawBitmap(decoded, null, android.graphics.RectF(left, top, right, bottom), paint)
        decoded.recycle()

        val unrotated =
            if (oldAdjustments.rotation == 0f) oldFull
            else rotateBitmapInPlaceSameSize(oldFull, -oldAdjustments.rotation).also { oldFull.recycle() }
        val unflipped =
            if (!oldAdjustments.flipHorizontal && !oldAdjustments.flipVertical) unrotated
            else flipBitmap(unrotated, oldAdjustments.flipHorizontal, oldAdjustments.flipVertical).also { unrotated.recycle() }
        val canonical =
            if (oldSteps == 0) unflipped
            else rotateBitmapSteps(unflipped, steps = -oldSteps).also { unflipped.recycle() }

        val oriented =
            if (newSteps == 0) canonical
            else rotateBitmapSteps(canonical, steps = newSteps).also { canonical.recycle() }
        val flipped =
            if (!newAdjustments.flipHorizontal && !newAdjustments.flipVertical) oriented
            else flipBitmap(oriented, newAdjustments.flipHorizontal, newAdjustments.flipVertical).also { oriented.recycle() }
        val rotated =
            if (newAdjustments.rotation == 0f) flipped
            else rotateBitmapInPlaceSameSize(flipped, newAdjustments.rotation).also { flipped.recycle() }

        val cropLeft = (newCrop.x * newW).roundToInt().coerceIn(0, newW - 1)
        val cropTop = (newCrop.y * newH).roundToInt().coerceIn(0, newH - 1)
        val cropW = (newCrop.width * newW).roundToInt().coerceAtLeast(1).coerceAtMost(newW - cropLeft)
        val cropH = (newCrop.height * newH).roundToInt().coerceAtLeast(1).coerceAtMost(newH - cropTop)
        val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropW, cropH)
        if (rotated != cropped) rotated.recycle()

        val encoded = encodeBitmapToPngDataUrl(cropped)
        cropped.recycle()
        return encoded
    }

    fun remapMasksForTransform(old: AdjustmentState, next: AdjustmentState, currentMasks: List<MaskState>): List<MaskState> {
        if (currentMasks.isEmpty()) return currentMasks
        val oldCrop = normalizedCropOrFull(old.crop)
        val newCrop = normalizedCropOrFull(next.crop)
        val oldRotation = old.rotation
        val newRotation = next.rotation
        val oldSteps = old.orientationSteps
        val newSteps = next.orientationSteps
        val oldFlipH = old.flipHorizontal
        val newFlipH = next.flipHorizontal
        val oldFlipV = old.flipVertical
        val newFlipV = next.flipVertical
        if (oldCrop == newCrop && oldRotation == newRotation && oldSteps == newSteps && oldFlipH == newFlipH && oldFlipV == newFlipV) {
            return currentMasks
        }

        val orientedBitmap = (uncroppedBitmap ?: editedBitmap) ?: return currentMasks
        val orientedW = orientedBitmap.width.coerceAtLeast(1)
        val orientedH = orientedBitmap.height.coerceAtLeast(1)
        val oldStepsMod = ((oldSteps % 4) + 4) % 4
        val baseW = if (oldStepsMod % 2 == 1) orientedH else orientedW
        val baseH = if (oldStepsMod % 2 == 1) orientedW else orientedH
        val baseWf = baseW.toFloat()
        val baseHf = baseH.toFloat()

        fun remapPoint(p: MaskPoint): MaskPoint {
            val mapped =
                remapPointForTransform(
                    point = p,
                    oldCrop = oldCrop,
                    oldRotation = oldRotation,
                    oldOrientationSteps = oldSteps,
                    oldFlipH = oldFlipH,
                    oldFlipV = oldFlipV,
                    newCrop = newCrop,
                    newRotation = newRotation,
                    newOrientationSteps = newSteps,
                    newFlipH = newFlipH,
                    newFlipV = newFlipV,
                    baseWidthPx = baseWf,
                    baseHeightPx = baseHf
                )
            return MaskPoint(x = mapped.x.coerceIn(-1f, 2f), y = mapped.y.coerceIn(-1f, 2f), pressure = p.pressure)
        }

        return currentMasks.map { mask ->
            mask.copy(
                subMasks =
                    mask.subMasks.map { sub ->
                        when (sub.type) {
                            SubMaskType.Brush.id ->
                                sub.copy(
                                    lines =
                                        sub.lines.map { line ->
                                            line.copy(points = line.points.map(::remapPoint))
                                        }
                                )

                            SubMaskType.Linear.id -> {
                                val start = remapPoint(MaskPoint(sub.linear.startX, sub.linear.startY))
                                val end = remapPoint(MaskPoint(sub.linear.endX, sub.linear.endY))
                                sub.copy(
                                    linear =
                                        sub.linear.copy(
                                            startX = start.x,
                                            startY = start.y,
                                            endX = end.x,
                                            endY = end.y,
                                        )
                                )
                            }

                            SubMaskType.Radial.id -> {
                                val center = remapPoint(MaskPoint(sub.radial.centerX, sub.radial.centerY))
                                sub.copy(radial = sub.radial.copy(centerX = center.x, centerY = center.y))
                            }

                            SubMaskType.AiSubject.id -> {
                                val dataUrl = sub.aiSubject.maskDataBase64
                                if (dataUrl.isNullOrBlank()) sub
                                else {
                                    val remapped =
                                        remapMaskDataUrlForTransform(
                                            dataUrl = dataUrl,
                                            baseWidthPx = baseW,
                                            baseHeightPx = baseH,
                                            oldAdjustments = old,
                                            newAdjustments = next
                                        )
                                    if (remapped == null) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = remapped))
                                }
                            }

                            SubMaskType.AiEnvironment.id -> {
                                val dataUrl = sub.aiEnvironment.maskDataBase64
                                if (dataUrl.isNullOrBlank()) sub
                                else {
                                    val remapped =
                                        remapMaskDataUrlForTransform(
                                            dataUrl = dataUrl,
                                            baseWidthPx = baseW,
                                            baseHeightPx = baseH,
                                            oldAdjustments = old,
                                            newAdjustments = next
                                        )
                                    if (remapped == null) sub else sub.copy(aiEnvironment = sub.aiEnvironment.copy(maskDataBase64 = remapped))
                                }
                            }

                            else -> sub
                        }
                    }
            )
        }
    }

    fun applyAdjustmentsPreservingMasks(next: AdjustmentState) {
        val old = adjustments
        if (masks.isNotEmpty()) {
            masks = remapMasksForTransform(old = old, next = next, currentMasks = masks)
        }
        adjustments = next
    }

    LaunchedEffect(isCropMode) {
        if (!isCropMode) {
            cropDraft = null
            rotationDraft = null
            isStraightenActive = false
            isCropGestureActive = false
        } else {
            if (cropDraft == null && adjustments.crop == null) {
                cropDraft = CropState(0f, 0f, 1f, 1f)
            }
        }
    }

    LaunchedEffect(isCropMode, cropBaseWidthPx, cropBaseHeightPx, adjustments.rotation, adjustments.aspectRatio, adjustments.crop, isHistoryInteractionActive) {
        if (!isCropMode) return@LaunchedEffect
        if (isHistoryInteractionActive) return@LaunchedEffect

        val w = cropBaseWidthPx ?: return@LaunchedEffect
        val h = cropBaseHeightPx ?: return@LaunchedEffect
        val baseRatio = w.toFloat().coerceAtLeast(1f) / h.toFloat().coerceAtLeast(1f)

        if (adjustments.crop == null) {
            val auto =
                computeMaxCropNormalized(
                    AutoCropParams(
                        baseAspectRatio = baseRatio,
                        rotationDegrees = adjustments.rotation,
                        aspectRatio = adjustments.aspectRatio
                    )
                )
            cropDraft = auto
            if (adjustments.rotation != 0f || adjustments.aspectRatio != null) {
                applyAdjustmentsPreservingMasks(adjustments.copy(crop = auto))
            }
        } else if (cropDraft == null) {
            cropDraft = adjustments.crop
        }
    }

    LaunchedEffect(isCropMode, adjustments.crop, isCropGestureActive, isHistoryInteractionActive) {
        if (!isCropMode) return@LaunchedEffect
        if (isHistoryInteractionActive) return@LaunchedEffect
        if (isCropGestureActive) return@LaunchedEffect
        if (cropDraft != adjustments.crop) {
            cropDraft = adjustments.crop
        }
    }

    fun normalizeMaskSelectionForCurrentState() {
        val selectedMask = selectedMaskId?.takeIf { id -> masks.any { it.id == id } }
        val normalizedMaskId = selectedMask ?: masks.firstOrNull()?.id
        if (normalizedMaskId != selectedMaskId) {
            selectedMaskId = normalizedMaskId
        }
        val activeMask = masks.firstOrNull { it.id == normalizedMaskId }
        val selectedSub = selectedSubMaskId?.takeIf { id -> activeMask?.subMasks?.any { it.id == id } == true }
        val normalizedSubId = selectedSub ?: activeMask?.subMasks?.firstOrNull()?.id
        if (normalizedSubId != selectedSubMaskId) {
            selectedSubMaskId = normalizedSubId
        }
    }

    fun applyEditHistoryEntry(entry: EditorHistoryEntry) {
        isRestoringEditHistory = true
        adjustments = entry.adjustments
        masks = entry.masks
        normalizeMaskSelectionForCurrentState()
        isRestoringEditHistory = false
    }

    fun pushEditHistoryEntry(entry: EditorHistoryEntry) {
        if (isRestoringEditHistory) return
        if (editHistoryIndex < 0) return
        if (editHistory.getOrNull(editHistoryIndex) == entry) return

        while (editHistory.lastIndex > editHistoryIndex) {
            editHistory.removeAt(editHistory.lastIndex)
        }

        editHistory.add(entry)
        editHistoryIndex = editHistory.lastIndex

        val maxEntries = 250
        val overflow = editHistory.size - maxEntries
        if (overflow > 0) {
            repeat(overflow) { editHistory.removeAt(0) }
            editHistoryIndex = (editHistoryIndex - overflow).coerceAtLeast(0)
        }
    }

    fun beginEditInteraction() {
        if (!isHistoryInteractionActive) isHistoryInteractionActive = true
    }

    fun endEditInteraction() {
        if (!isHistoryInteractionActive) return
        isHistoryInteractionActive = false
        pushEditHistoryEntry(EditorHistoryEntry(adjustments = adjustments, masks = masks))
    }

    val renderVersion = remember { AtomicLong(0L) }
    val lastEditedPreviewStamp = remember { AtomicLong(-1L) }
    val lastEditedViewportStamp = remember { AtomicLong(-1L) }
    val lastOriginalPreviewStamp = remember { AtomicLong(-1L) }
    val lastUncroppedPreviewStamp = remember { AtomicLong(-1L) }
    val renderRequests = remember { Channel<RenderRequest>(capacity = Channel.CONFLATED) }
    val renderDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(renderDispatcher) { onDispose { renderDispatcher.close() } }

    // Helper to request a one-off "original / no-adjustments" preview without changing global compare state
    suspend fun requestIdentityPreview(): Bitmap? {
        val handle = sessionHandle
        if (handle == 0L) return null
        val json = withContext(Dispatchers.Default) {
            AdjustmentState(
                rotation = adjustments.rotation,
                flipHorizontal = adjustments.flipHorizontal,
                flipVertical = adjustments.flipVertical,
                orientationSteps = adjustments.orientationSteps,
                crop = adjustments.crop,
                toneMapper = adjustments.toneMapper
            ).toJson(emptyList())
        }
        // Try to get a quick low-quality preview directly to avoid depending on the shared
        // render loop timing. If that fails, fall back to queuing a request and waiting briefly.
        val bmp = withContext(renderDispatcher) {
            runCatching { LibRawDecoder.lowdecodeFromSession(handle, json) }.getOrNull()
                ?.decodeToBitmap()
                ?: runCatching { LibRawDecoder.decodeFromSession(handle, json) }.getOrNull()?.decodeToBitmap()
        }
        if (bmp != null) return bmp

        // Fallback: enqueue into the render loop and wait a bit for originalBitmap to update.
        val version = renderVersion.incrementAndGet()
        val stampBefore = lastOriginalPreviewStamp.get()
        renderRequests.trySend(
            RenderRequest(
                version = version,
                adjustmentsJson = json,
                target = RenderTarget.Original,
                rotationDegrees = adjustments.rotation
            )
        )

        var waited = 0
        while (waited < 2500) {
            if (lastOriginalPreviewStamp.get() > stampBefore) break
            delay(20)
            waited += 20
        }
        return originalBitmap
    }
    DisposableEffect(sessionHandle) {
        val handleToRelease = sessionHandle
        onDispose {
            if (handleToRelease != 0L) {
                LibRawDecoder.releaseSession(handleToRelease)
            }
        }
    }

    LaunchedEffect(galleryItem.projectId) {
        isRestoringEditHistory = true
        adjustments = AdjustmentState()
        masks = emptyList()
        selectedMaskId = null
        selectedSubMaskId = null
        maskOverlayBlinkSubMaskId = null
        maskOverlayBlinkKey = 0L
        editHistory.clear()
        editHistoryIndex = -1

        isComparingOriginal = false
        originalBitmap = null
        editedBitmap = null
        editedViewportBitmap = null
        editedViewportRoi = null
        currentViewportRoi.set(null)
        viewportScale = 1f
        viewportRoiForDebounce = null
        fullPreviewDirtyByViewport = false
        uncroppedBitmap = null
        editedPreviewRotationDegrees = 0f
        uncroppedPreviewRotationDegrees = 0f
        lastEditedPreviewStamp.set(-1L)
        lastEditedViewportStamp.set(-1L)
        lastOriginalPreviewStamp.set(-1L)
        lastUncroppedPreviewStamp.set(-1L)

        val raw = withContext(Dispatchers.IO) { storage.loadRawBytes(galleryItem.projectId) }
        if (raw == null) {
            sessionHandle = 0L
            errorMessage = "Failed to load RAW file."
            return@LaunchedEffect
        }
        val createResult = withContext(renderDispatcher) { runCatching { LibRawDecoder.createSession(raw) } }
        sessionHandle = createResult.getOrDefault(0L)
        if (sessionHandle == 0L) {
            val err = createResult.exceptionOrNull() ?: LibRawDecoder.loadErrorOrNull()
            errorMessage =
                when {
                    !LibRawDecoder.isAvailable() ->
                        "Native decoder failed to load (${err?.javaClass?.simpleName})."
                    err is UnsatisfiedLinkError ->
                        "Native decoder is out of date (JNI link error)."
                    else -> "Failed to initialize native decoder."
                }
            Log.e(logTag, "Failed to initialize native decoder", err)
            return@LaunchedEffect
        }

        val savedAdjustmentsJson = withContext(Dispatchers.IO) { storage.loadAdjustments(galleryItem.projectId) }
        if (savedAdjustmentsJson != "{}") {
            try {
                val json = JSONObject(savedAdjustmentsJson)

                val savedDetected = json.optJSONArray("aiEnvironmentDetectedCategories")
                if (savedDetected != null) {
                    val parsed =
                        (0 until savedDetected.length()).mapNotNull { i ->
                            val rawId = savedDetected.optString(i).orEmpty().trim()
                            if (rawId.isBlank()) null else AiEnvironmentCategory.fromId(rawId)
                        }.distinct()
                    detectedAiEnvironmentCategories = parsed.takeIf { it.isNotEmpty() }
                }

                fun parseCurvePoints(curvesObj: JSONObject?, key: String): List<CurvePointState> {
                    val arr = curvesObj?.optJSONArray(key) ?: return com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints()
                    val points =
                        (0 until arr.length()).mapNotNull { idx ->
                            val pObj = arr.optJSONObject(idx) ?: return@mapNotNull null
                            CurvePointState(x = pObj.optDouble("x", 0.0).toFloat(), y = pObj.optDouble("y", 0.0).toFloat())
                        }
                    return if (points.size >= 2) points else com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints()
                }

                fun parseCurves(curvesObj: JSONObject?): CurvesState {
                    return CurvesState(
                        luma = parseCurvePoints(curvesObj, "luma"),
                        red = parseCurvePoints(curvesObj, "red"),
                        green = parseCurvePoints(curvesObj, "green"),
                        blue = parseCurvePoints(curvesObj, "blue")
                    )
                }

                fun parseHsl(obj: JSONObject?): HslState {
                    if (obj == null) return HslState()
                    fun parseHueSatLum(parent: JSONObject?, key: String): com.dueckis.kawaiiraweditor.data.model.HueSatLumState {
                        val o = parent?.optJSONObject(key) ?: return com.dueckis.kawaiiraweditor.data.model.HueSatLumState()
                        return com.dueckis.kawaiiraweditor.data.model.HueSatLumState(
                            hue = o.optDouble("hue", 0.0).toFloat(),
                            saturation = o.optDouble("saturation", 0.0).toFloat(),
                            luminance = o.optDouble("luminance", 0.0).toFloat()
                        )
                    }
                    return HslState(
                        reds = parseHueSatLum(obj, "reds"),
                        oranges = parseHueSatLum(obj, "oranges"),
                        yellows = parseHueSatLum(obj, "yellows"),
                        greens = parseHueSatLum(obj, "greens"),
                        aquas = parseHueSatLum(obj, "aquas"),
                        blues = parseHueSatLum(obj, "blues"),
                        purples = parseHueSatLum(obj, "purples"),
                        magentas = parseHueSatLum(obj, "magentas")
                    )
                }

                fun parseColorGrading(obj: JSONObject?): com.dueckis.kawaiiraweditor.data.model.ColorGradingState {
                    if (obj == null) return com.dueckis.kawaiiraweditor.data.model.ColorGradingState()
                    fun parseHueSatLum(parent: JSONObject?, key: String): com.dueckis.kawaiiraweditor.data.model.HueSatLumState {
                        val o = parent?.optJSONObject(key) ?: return com.dueckis.kawaiiraweditor.data.model.HueSatLumState()
                        return com.dueckis.kawaiiraweditor.data.model.HueSatLumState(
                            hue = o.optDouble("hue", 0.0).toFloat(),
                            saturation = o.optDouble("saturation", 0.0).toFloat(),
                            luminance = o.optDouble("luminance", 0.0).toFloat()
                        )
                    }
                    return com.dueckis.kawaiiraweditor.data.model.ColorGradingState(
                        shadows = parseHueSatLum(obj, "shadows"),
                        midtones = parseHueSatLum(obj, "midtones"),
                        highlights = parseHueSatLum(obj, "highlights"),
                        blending = obj.optDouble("blending", 50.0).toFloat(),
                        balance = obj.optDouble("balance", 0.0).toFloat()
                    )
                }

                val parsedCurves = parseCurves(json.optJSONObject("curves"))
                val parsedColorGrading = parseColorGrading(json.optJSONObject("colorGrading"))
                val parsedHsl = parseHsl(json.optJSONObject("hsl"))

                val parsedAspectRatio =
                    if (json.has("aspectRatio") && !json.isNull("aspectRatio")) json.optDouble("aspectRatio", 0.0).toFloat() else null
                val parsedCrop =
                    json.optJSONObject("crop")?.let { cropObj ->
                        CropState(
                            x = cropObj.optDouble("x", 0.0).toFloat(),
                            y = cropObj.optDouble("y", 0.0).toFloat(),
                            width = cropObj.optDouble("width", 1.0).toFloat(),
                            height = cropObj.optDouble("height", 1.0).toFloat()
                        ).normalized()
                    }

                adjustments =
                    AdjustmentState(
                        rotation = json.optDouble("rotation", 0.0).toFloat(),
                        flipHorizontal = json.optBoolean("flipHorizontal", false),
                        flipVertical = json.optBoolean("flipVertical", false),
                        orientationSteps = json.optInt("orientationSteps", 0),
                        aspectRatio = parsedAspectRatio,
                        crop = parsedCrop,
                        exposure = json.optDouble("exposure", 0.0).toFloat(),
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
                        colorGrading = parsedColorGrading,
                        hsl = parsedHsl
                    )

                val masksArr = json.optJSONArray("masks") ?: JSONArray()
                val parsedMasks =
                    (0 until masksArr.length()).mapNotNull { idx ->
                        val maskObj = masksArr.optJSONObject(idx) ?: return@mapNotNull null
                        val maskId = maskObj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                        val maskAdjustmentsObj = maskObj.optJSONObject("adjustments") ?: JSONObject()
                        val maskCurves = parseCurves(maskAdjustmentsObj.optJSONObject("curves"))
                        val maskColorGrading = parseColorGrading(maskAdjustmentsObj.optJSONObject("colorGrading"))
                        val maskHsl = parseHsl(maskAdjustmentsObj.optJSONObject("hsl"))
                        val maskAdjustments =
                            AdjustmentState(
                                exposure = maskAdjustmentsObj.optDouble("exposure", 0.0).toFloat(),
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
                                colorGrading = maskColorGrading,
                                hsl = maskHsl
                            )

                        val subMasksArr = maskObj.optJSONArray("subMasks") ?: JSONArray()
                        val subMasks =
                            (0 until subMasksArr.length()).mapNotNull { sIdx ->
                                val subObj = subMasksArr.optJSONObject(sIdx) ?: return@mapNotNull null
                                val subId = subObj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                                val subType = subObj.optString("type", SubMaskType.Brush.id).lowercase(Locale.US)
                                val modeStr = subObj.optString("mode", "additive").lowercase(Locale.US)
                                val mode = if (modeStr == "subtractive") SubMaskMode.Subtractive else SubMaskMode.Additive
                                val paramsObj = subObj.optJSONObject("parameters") ?: JSONObject()
                                val visible = subObj.optBoolean("visible", true)

                                when (subType) {
                                    SubMaskType.Radial.id ->
                                        SubMaskState(
                                            id = subId,
                                            type = SubMaskType.Radial.id,
                                            visible = visible,
                                            mode = mode,
                                            radial =
                                                RadialMaskParametersState(
                                                    centerX = paramsObj.optDouble("centerX", 0.5).toFloat(),
                                                    centerY = paramsObj.optDouble("centerY", 0.5).toFloat(),
                                                    radiusX = paramsObj.optDouble("radiusX", 0.35).toFloat(),
                                                    radiusY = paramsObj.optDouble("radiusY", 0.35).toFloat(),
                                                    rotation = paramsObj.optDouble("rotation", 0.0).toFloat(),
                                                    feather = paramsObj.optDouble("feather", 0.5).toFloat()
                                                )
                                        )

                                    SubMaskType.AiSubject.id ->
                                        SubMaskState(
                                            id = subId,
                                            type = SubMaskType.AiSubject.id,
                                            visible = visible,
                                            mode = mode,
                                            aiSubject =
                                                com.dueckis.kawaiiraweditor.data.model.AiSubjectMaskParametersState(
                                                    maskDataBase64 = paramsObj.optString("maskDataBase64").takeIf { it.isNotBlank() },
                                                    softness = paramsObj.optDouble("softness", 0.25).toFloat().coerceIn(0f, 1f)
                                                )
                                        )

                                    SubMaskType.AiEnvironment.id ->
                                        SubMaskState(
                                            id = subId,
                                            type = SubMaskType.AiEnvironment.id,
                                            visible = visible,
                                            mode = mode,
                                            aiEnvironment =
                                                com.dueckis.kawaiiraweditor.data.model.AiEnvironmentMaskParametersState(
                                                    category = paramsObj.optString("category", "sky"),
                                                    maskDataBase64 = paramsObj.optString("maskDataBase64").takeIf { it.isNotBlank() },
                                                    softness = paramsObj.optDouble("softness", 0.25).toFloat().coerceIn(0f, 1f)
                                                )
                                        )

                                    SubMaskType.Linear.id ->
                                        SubMaskState(
                                            id = subId,
                                            type = SubMaskType.Linear.id,
                                            visible = visible,
                                            mode = mode,
                                            linear =
                                                com.dueckis.kawaiiraweditor.data.model.LinearMaskParametersState(
                                                    startX = paramsObj.optDouble("startX", 0.5).toFloat(),
                                                    startY = paramsObj.optDouble("startY", 0.2).toFloat(),
                                                    endX = paramsObj.optDouble("endX", 0.5).toFloat(),
                                                    endY = paramsObj.optDouble("endY", 0.8).toFloat(),
                                                    range = paramsObj.optDouble("range", 0.25).toFloat()
                                                )
                                        )

                                    else -> {
                                        val linesArr = paramsObj.optJSONArray("lines") ?: JSONArray()
                                        val lines =
                                            (0 until linesArr.length()).mapNotNull { lIdx ->
                                                val lineObj = linesArr.optJSONObject(lIdx) ?: return@mapNotNull null
                                                val pointsArr = lineObj.optJSONArray("points") ?: JSONArray()
                                                val points =
                                                    (0 until pointsArr.length()).mapNotNull { pIdx ->
                                                        val pObj = pointsArr.optJSONObject(pIdx) ?: return@mapNotNull null
                                                        MaskPoint(
                                                            x = pObj.optDouble("x", 0.0).toFloat(),
                                                            y = pObj.optDouble("y", 0.0).toFloat(),
                                                            pressure = pObj.optDouble("pressure", 1.0).toFloat().coerceIn(0f, 1f)
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
                                        SubMaskState(id = subId, type = SubMaskType.Brush.id, visible = visible, mode = mode, lines = lines)
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

                val maxOrder = parsedMasks.flatMap { it.subMasks }.flatMap { it.lines }.maxOfOrNull { it.order } ?: 0L
                strokeOrder.set(maxOf(strokeOrder.get(), maxOrder))
                masks = parsedMasks
                parsedMasks.forEach { m -> assignNumber(m.id) }
                if (selectedMaskId == null && parsedMasks.isNotEmpty()) {
                    selectedMaskId = parsedMasks.first().id
                    selectedSubMaskId = parsedMasks.first().subMasks.firstOrNull()?.id
                }
            } catch (_: Exception) {
                // Keep defaults.
            }
        }

        isRestoringEditHistory = true
        editHistory.clear()
        editHistory.add(EditorHistoryEntry(adjustments = adjustments, masks = masks))
        editHistoryIndex = 0
        isRestoringEditHistory = false
    }

    LaunchedEffect(sessionHandle) { metadataJson = null }

    LaunchedEffect(displayBitmap) {
        val bmp = displayBitmap ?: run {
            histogramData = null
            return@LaunchedEffect
        }
        if (bmp.width < 512 && bmp.height < 512) return@LaunchedEffect
        delay(80)
        if (displayBitmap !== bmp) return@LaunchedEffect
        histogramData = withContext(Dispatchers.Default) { HistogramUtils.calculateHistogram(bmp) }
    }

    LaunchedEffect(adjustments, masks, isDraggingMaskHandle, isComparingOriginal) {
        if (isDraggingMaskHandle) return@LaunchedEffect
        if (isCropMode && !isComparingOriginal) return@LaunchedEffect
        val viewportRoi = currentViewportRoi.get()
        val useViewportRender = viewportRoi != null && !isComparingOriginal && !isCropMode
        val viewportMaxDim = if (useViewportRender) zoomMaxDimensionForScale(viewportScale) else 0
        val json =
            withContext(Dispatchers.Default) {
                if (useViewportRender) {
                    adjustments.toJsonObject(includeToneMapper = true).apply {
                        put("masks", JSONArray().apply { masks.forEach { put(it.toJsonObject()) } })
                        put(
                            "preview",
                            JSONObject().apply {
                                put("useZoom", true)
                                put("roi", viewportRoi?.toJsonObject() ?: JSONObject.NULL)
                                put("maxDimension", viewportMaxDim)
                            }
                        )
                    }.toString()
                } else if (isComparingOriginal) {
                    AdjustmentState(
                        rotation = adjustments.rotation,
                        flipHorizontal = adjustments.flipHorizontal,
                        flipVertical = adjustments.flipVertical,
                        orientationSteps = adjustments.orientationSteps,
                        crop = adjustments.crop,
                        toneMapper = adjustments.toneMapper
                    ).toJson(emptyList())
                } else {
                    adjustments.toJson(masksForRender(masks))
                }
            }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(
                version = version,
                adjustmentsJson = json,
                target = if (isComparingOriginal) RenderTarget.Original else RenderTarget.Edited,
                rotationDegrees = adjustments.rotation,
                previewRoi = if (useViewportRender) viewportRoi else null
            )
        )
        if (useViewportRender) {
            fullPreviewDirtyByViewport = true
        } else if (!isComparingOriginal) {
            fullPreviewDirtyByViewport = false
        }
    }

    val uncroppedRenderKey = remember(adjustments) { adjustments.copy(crop = null, aspectRatio = null) }
    LaunchedEffect(isCropMode, isComparingOriginal, uncroppedRenderKey, isDraggingMaskHandle, sessionHandle) {
        if (!isCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect
        if (sessionHandle == 0L) return@LaunchedEffect
        if (isDraggingMaskHandle) return@LaunchedEffect

        val json = withContext(Dispatchers.Default) { uncroppedRenderKey.toJson(emptyList()) }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.UncroppedEdited, rotationDegrees = uncroppedRenderKey.rotation)
        )
    }

    var wasCropMode by remember(galleryItem.projectId) { mutableStateOf(false) }
    LaunchedEffect(isCropMode, isComparingOriginal, sessionHandle) {
        if (sessionHandle == 0L) {
            wasCropMode = isCropMode
            return@LaunchedEffect
        }

        val leavingCropMode = wasCropMode && !isCropMode
        wasCropMode = isCropMode
        if (!leavingCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect

        val json = withContext(Dispatchers.Default) { adjustments.toJson(masksForRender(masks)) }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.Edited, rotationDegrees = adjustments.rotation)
        )
    }

    LaunchedEffect(adjustments, masks, isDraggingMaskHandle, detectedAiEnvironmentCategories) {
        if (isDraggingMaskHandle) return@LaunchedEffect
        if (isRestoringEditHistory || editHistoryIndex < 0) return@LaunchedEffect
        val json =
            withContext(Dispatchers.Default) {
                val base = adjustments.toJson(masks)
                val obj = JSONObject(base)
                val detected = detectedAiEnvironmentCategories.orEmpty()
                if (detected.isEmpty()) {
                    obj.remove("aiEnvironmentDetectedCategories")
                } else {
                    val arr = JSONArray()
                    detected.forEach { arr.put(it.id) }
                    obj.put("aiEnvironmentDetectedCategories", arr)
                }
                obj.toString()
            }
        delay(350)
        withContext(Dispatchers.IO) { storage.saveAdjustments(galleryItem.projectId, json) }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { EditorHistoryEntry(adjustments = adjustments, masks = masks) }
            .distinctUntilChanged()
            .collect { entry ->
                if (isRestoringEditHistory || isHistoryInteractionActive || isDraggingMaskHandle) return@collect
                pushEditHistoryEntry(entry)
            }
    }

    LaunchedEffect(isDraggingMaskHandle) { if (isDraggingMaskHandle) beginEditInteraction() else endEditInteraction() }

    LaunchedEffect(sessionHandle) {
        val handle = sessionHandle
        if (handle == 0L) return@LaunchedEffect

        renderRequests.trySend(
            RenderRequest(
                version = renderVersion.incrementAndGet(),
                adjustmentsJson = adjustments.toJson(masksForRender(masks)),
                target = RenderTarget.Edited,
                rotationDegrees = adjustments.rotation
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
            val requestTarget = currentRequest.target
            val requestRotation = currentRequest.rotationDegrees
            val requestPreviewRoi = currentRequest.previewRoi
            val isViewportRequest = requestTarget == RenderTarget.Edited && requestPreviewRoi != null

            fun updateBitmapForRequest(version: Long, quality: Int, bitmap: Bitmap) {
                val stamp = version * 10L + quality.coerceIn(0, 9).toLong()
                when (requestTarget) {
                    RenderTarget.Original -> {
                        if (stamp > lastOriginalPreviewStamp.get()) {
                            originalBitmap = bitmap
                            lastOriginalPreviewStamp.set(stamp)
                        }
                    }

                    RenderTarget.UncroppedEdited -> {
                        if (stamp > lastUncroppedPreviewStamp.get()) {
                            uncroppedBitmap = bitmap
                            lastUncroppedPreviewStamp.set(stamp)
                            uncroppedPreviewRotationDegrees = requestRotation
                        }
                    }

                    RenderTarget.Edited -> {
                        if (requestPreviewRoi != null) {
                            if (stamp > lastEditedViewportStamp.get()) {
                                editedViewportBitmap = bitmap
                                editedViewportRoi = requestPreviewRoi
                                lastEditedViewportStamp.set(stamp)
                            }
                        } else if (stamp > lastEditedPreviewStamp.get()) {
                            editedBitmap = bitmap
                            lastEditedPreviewStamp.set(stamp)
                            editedPreviewRotationDegrees = requestRotation
                        }
                    }
                }
            }

            if (lowQualityPreviewEnabled) {
                if (!isViewportRequest) {
                    val superLowBitmap =
                        withContext(renderDispatcher) {
                            val bytes = runCatching { LibRawDecoder.lowlowdecodeFromSession(handle, requestJson) }.getOrNull()
                            bytes?.decodeToBitmap()
                        }
                    if (superLowBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 0, bitmap = superLowBitmap)

                    val maybeUpdatedAfterSuperLow = withTimeoutOrNull(60) { renderRequests.receive() }
                    if (maybeUpdatedAfterSuperLow != null) {
                        currentRequest = maybeUpdatedAfterSuperLow
                        continue
                    }
                }

                val lowBitmap =
                    withContext(renderDispatcher) {
                        val bytes = runCatching { LibRawDecoder.lowdecodeFromSession(handle, requestJson) }.getOrNull()
                        bytes?.decodeToBitmap()
                    }
                if (lowBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 1, bitmap = lowBitmap)

                val maybeUpdatedAfterLow = withTimeoutOrNull(180) { renderRequests.receive() }
                if (maybeUpdatedAfterLow != null) {
                    currentRequest = maybeUpdatedAfterLow
                    continue
                }
            }

            isLoading = true
            val fullBitmap =
                withContext(renderDispatcher) {
                    val bytes = runCatching { LibRawDecoder.decodeFromSession(handle, requestJson) }.getOrNull()
                    bytes?.decodeToBitmap()
                }
            isLoading = false

            val isLatest = requestVersion == renderVersion.get()
            if (requestTarget == RenderTarget.Edited && requestPreviewRoi == null && isLatest) {
                errorMessage = if (fullBitmap == null) "Failed to render preview." else null
            }
            if (fullBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 2, bitmap = fullBitmap)

            if (requestTarget == RenderTarget.Edited && requestPreviewRoi == null && isLatest) fullBitmap?.let { bmp ->
                withContext(Dispatchers.IO) {
                    val maxSize = 1024
                    val scale = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height)
                    val scaledWidth = (bmp.width * scale).toInt()
                    val scaledHeight = (bmp.height * scale).toInt()
                    val thumbnail = Bitmap.createScaledBitmap(bmp, scaledWidth, scaledHeight, true)

                    val outputStream = java.io.ByteArrayOutputStream()
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    val thumbnailBytes = outputStream.toByteArray()
                    storage.saveThumbnail(galleryItem.projectId, thumbnailBytes)
                    if (thumbnail != bmp) thumbnail.recycle()
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
            masks =
                masks.map { mask ->
                    if (mask.id != maskId) return@map mask
                    mask.copy(
                        subMasks =
                            mask.subMasks.map { sub ->
                                if (sub.id != subId) return@map sub
                                when (maskTapMode) {
                                    MaskTapMode.SetRadialCenter ->
                                        if (sub.type != SubMaskType.Radial.id) sub else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))

                                    MaskTapMode.SetLinearStart ->
                                        if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))

                                    MaskTapMode.SetLinearEnd ->
                                        if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))

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
        val newLine =
            BrushLineState(
                tool = if (brushTool == BrushTool.Eraser) "eraser" else "brush",
                brushSize = brushSizeNorm,
                feather = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness,
                order = strokeOrder.incrementAndGet(),
                points = points
            )
        masks =
            masks.map { mask ->
                if (mask.id != maskId) return@map mask
                mask.copy(subMasks = mask.subMasks.map { sub -> if (sub.id != subId) sub else sub.copy(lines = sub.lines + newLine) })
            }
        showMaskOverlay = true
    }

    val onSubMaskHandleDrag: (MaskHandle, MaskPoint) -> Unit = onDrag@{ handle, point ->
        val maskId = selectedMaskId ?: return@onDrag
        val subId = selectedSubMaskId ?: return@onDrag
        masks =
            masks.map { mask ->
                if (mask.id != maskId) return@map mask
                mask.copy(
                    subMasks =
                        mask.subMasks.map { sub ->
                            if (sub.id != subId) return@map sub
                            when (handle) {
                                MaskHandle.RadialCenter ->
                                    if (sub.type != SubMaskType.Radial.id) sub else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))
                                MaskHandle.LinearStart ->
                                    if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))
                                MaskHandle.LinearEnd ->
                                    if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))
                            }
                        }
                )
            }
        showMaskOverlay = true
    }

    val aiSubjectMaskGenerator = remember { AiSubjectMaskGenerator(context) }
    val aiEnvironmentMaskGenerator = remember(environmentMaskingEnabled) { if (environmentMaskingEnabled) AiEnvironmentMaskGenerator(context) else null }

    var aiEnvironmentSourceBitmap by remember(galleryItem.projectId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sessionHandle) {
        aiEnvironmentSourceBitmap?.recycle()
        aiEnvironmentSourceBitmap = null
    }

    suspend fun getAiEnvironmentSourceBitmap(handle: Long): Bitmap? {
        aiEnvironmentSourceBitmap?.let { return it }
        val baseJson = AdjustmentState().toJson(emptyList())
        val bytes =
            withContext(renderDispatcher) {
                runCatching { LibRawDecoder.decodeFromSession(handle, baseJson) }.getOrNull()
            } ?: return null
        val decoded = bytes.decodeToBitmap() ?: return null
        aiEnvironmentSourceBitmap = decoded
        return decoded
    }
    val onLassoFinished: (List<MaskPoint>) -> Unit = onLasso@{ points ->
        val maskId = selectedMaskId ?: return@onLasso
        val subId = selectedSubMaskId ?: return@onLasso
        val bmp = editedBitmap ?: return@onLasso
        if (points.size < 3) return@onLasso

        coroutineScope.launch {
            isGeneratingAiMask = true
            statusMessage = "Generating subject mask…"
            val dataUrl =
                runCatching {
                    aiSubjectMaskGenerator.generateSubjectMaskDataUrl(
                        previewBitmap = bmp,
                        lassoPoints = points.map { NormalizedPoint(it.x, it.y) }
                    )
                }.getOrNull()

            if (dataUrl == null) {
                statusMessage = "Failed to generate subject mask."
            } else {
                masks =
                    masks.map { mask ->
                        if (mask.id != maskId) return@map mask
                        mask.copy(
                            subMasks =
                                mask.subMasks.map { sub ->
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

    val onGenerateAiEnvironmentMask: (() -> Unit)? = if (!environmentMaskingEnabled) null else fun() {
        val maskId = selectedMaskId ?: return
        val subId = selectedSubMaskId ?: return
        val handle = sessionHandle
        if (handle == 0L) return
        val sub =
            masks.firstOrNull { it.id == maskId }?.subMasks?.firstOrNull { it.id == subId }
                ?: return
        if (sub.type != SubMaskType.AiEnvironment.id) return

        val category = AiEnvironmentCategory.fromId(sub.aiEnvironment.category)
        coroutineScope.launch {
            isGeneratingAiMask = true
            statusMessage = "Generating ${category.label.lowercase()} mask…"
            val result =
                runCatching {
                    val generator = aiEnvironmentMaskGenerator ?: error("Environment masking disabled.")
                    val srcBmp = getAiEnvironmentSourceBitmap(handle) ?: error("Failed to render environment mask source.")
                    generator.generateEnvironmentMaskDataUrl(
                        previewBitmap = srcBmp,
                        category = category
                    )
                }

            val dataUrl = result.getOrNull()
            if (dataUrl == null) {
                val err = result.exceptionOrNull()
                val detail = err?.message?.takeIf { it.isNotBlank() } ?: err?.javaClass?.simpleName
                statusMessage = if (detail.isNullOrBlank()) "Failed to generate environment mask." else "Failed to generate environment mask: $detail"
            } else {
                val srcBmp = aiEnvironmentSourceBitmap
                val remapped =
                    if (srcBmp == null) dataUrl
                    else {
                        remapMaskDataUrlForTransform(
                            dataUrl = dataUrl,
                            baseWidthPx = srcBmp.width,
                            baseHeightPx = srcBmp.height,
                            oldAdjustments = AdjustmentState(),
                            newAdjustments = adjustments
                        ) ?: dataUrl
                    }
                masks =
                    masks.map { mask ->
                        if (mask.id != maskId) return@map mask
                        mask.copy(
                            subMasks =
                                mask.subMasks.map { s ->
                                    if (s.id != subId) s else s.copy(aiEnvironment = s.aiEnvironment.copy(maskDataBase64 = remapped))
                                }
                        )
                    }
                showMaskOverlay = true
                statusMessage = "${category.label} mask added."
            }
            isGeneratingAiMask = false
            delay(1500)
            if (statusMessage == "${category.label} mask added.") statusMessage = null
        }
    }

    val onDetectAiEnvironmentCategories: (() -> Unit)? = if (!environmentMaskingEnabled) null else fun() {
        if (isDetectingAiEnvironmentCategories) return
        if (detectedAiEnvironmentCategories != null) return
        val handle = sessionHandle
        if (handle == 0L) return

        coroutineScope.launch {
            isDetectingAiEnvironmentCategories = true
            val detected =
                runCatching {
                    val generator = aiEnvironmentMaskGenerator ?: error("Environment masking disabled.")
                    val srcBmp = getAiEnvironmentSourceBitmap(handle) ?: error("Failed to render environment mask source.")
                    generator.detectAvailableCategories(srcBmp)
                }.getOrNull()
            if (detected != null) detectedAiEnvironmentCategories = detected
            isDetectingAiEnvironmentCategories = false
        }
    }

    val canUndo = editHistoryIndex > 0
    val canRedo = editHistoryIndex in 0 until editHistory.lastIndex

    fun undo() {
        val nextIndex = editHistoryIndex - 1
        val entry = editHistory.getOrNull(nextIndex) ?: return
        editHistoryIndex = nextIndex
        applyEditHistoryEntry(entry)
    }

    fun redo() {
        val nextIndex = editHistoryIndex + 1
        val entry = editHistory.getOrNull(nextIndex) ?: return
        editHistoryIndex = nextIndex
        applyEditHistoryEntry(entry)
    }

    val onPreviewViewportRoiChange: (CropState?, Float) -> Unit = { roi, scale ->
        viewportScale = scale
        viewportRoiForDebounce = roi
        currentViewportRoi.set(roi)
        if (roi == null) {
            editedViewportBitmap = null
            editedViewportRoi = null
        }
    }

    LaunchedEffect(sessionHandle, isCropMode, isComparingOriginal) {
        val handle = sessionHandle
        if (handle == 0L) return@LaunchedEffect
        if (isCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect

        snapshotFlow { viewportRoiForDebounce?.normalized() to viewportScale }
            .debounce(1000)
            .collect { (roi, scale) ->
                if (isDraggingMaskHandle) return@collect
                if (sessionHandle != handle) return@collect
                if (isCropMode) return@collect
                if (isComparingOriginal) return@collect

                if (roi != null) {
                    val maxDim = zoomMaxDimensionForScale(scale)
                    val json =
                        withContext(Dispatchers.Default) {
                            adjustments.toJsonObject(includeToneMapper = true).apply {
                                put("masks", JSONArray().apply { masksForRender(masks).forEach { put(it.toJsonObject()) } })
                                put(
                                    "preview",
                                    JSONObject().apply {
                                        put("useZoom", true)
                                        put("roi", roi.toJsonObject())
                                        put("maxDimension", maxDim)
                                    }
                                )
                            }.toString()
                        }
                    val version = renderVersion.incrementAndGet()
                    renderRequests.trySend(
                        RenderRequest(
                            version = version,
                            adjustmentsJson = json,
                            target = RenderTarget.Edited,
                            rotationDegrees = adjustments.rotation,
                            previewRoi = roi
                        )
                    )
                } else if (fullPreviewDirtyByViewport) {
                    fullPreviewDirtyByViewport = false
                    val json = withContext(Dispatchers.Default) { adjustments.toJson(masksForRender(masks)) }
                    val version = renderVersion.incrementAndGet()
                    renderRequests.trySend(
                        RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.Edited, rotationDegrees = adjustments.rotation)
                    )
                }
            }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
            val onSelectPanelTab: (EditorPanelTab) -> Unit = { tab ->
                when (tab) {
                    EditorPanelTab.Masks -> {
                        maskTapMode = MaskTapMode.None
                    }
                    else -> {
                        isPaintingMask = false
                        maskTapMode = MaskTapMode.None
                    }
                }
                panelTab = tab
            }

            val cropAspectRatio = if (isCropMode) adjustments.aspectRatio else null

            Box(modifier = Modifier.fillMaxSize()) {
                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(3f).fillMaxHeight().background(Color.Black)) {
                            ImagePreview(
                                bitmap = displayBitmap,
                                isLoading = isLoading || isGeneratingAiMask,
                                viewportBitmap = if (isComparingOriginal || isCropMode) null else editedViewportBitmap,
                                viewportRoi = if (isComparingOriginal || isCropMode) null else editedViewportRoi,
                                onViewportRoiChange = onPreviewViewportRoiChange,
                                maskOverlay = selectedMaskForOverlay,
                                activeSubMask = selectedSubMaskForEdit,
                                isMaskMode = isMaskMode && !isComparingOriginal,
                                showMaskOverlay = showMaskOverlay && !isComparingOriginal,
                                maskOverlayBlinkKey = maskOverlayBlinkKey,
                                maskOverlayBlinkSubMaskId = maskOverlayBlinkSubMaskId,
                                isPainting = isInteractiveMaskingEnabled && !isComparingOriginal,
                                brushSize = brushSize,
                                maskTapMode = if (isComparingOriginal) MaskTapMode.None else maskTapMode,
                                onMaskTap = if (isComparingOriginal) null else onMaskTap,
                                requestBeforePreview = { requestIdentityPreview() },
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
                                },
                                isCropMode = isCropMode && !isComparingOriginal,
                                cropState = if (isCropMode && !isComparingOriginal) (cropDraft ?: adjustments.crop) else null,
                                cropAspectRatio = cropAspectRatio,
                                extraRotationDegrees = previewRotationDelta,
                                isStraightenActive = isCropMode && isStraightenActive && !isComparingOriginal,
                                onStraightenResult = { rotation ->
                                    beginEditInteraction()
                                    val baseRatio =
                                        (cropBaseWidthPx?.toFloat()?.coerceAtLeast(1f) ?: 1f) /
                                                (cropBaseHeightPx?.toFloat()?.coerceAtLeast(1f) ?: 1f)
                                    val autoCrop =
                                        computeMaxCropNormalized(AutoCropParams(baseAspectRatio = baseRatio, rotationDegrees = rotation, aspectRatio = adjustments.aspectRatio))
                                    cropDraft = autoCrop
                                    rotationDraft = null
                                    isStraightenActive = false
                                    applyAdjustmentsPreservingMasks(adjustments.copy(rotation = rotation, crop = autoCrop))
                                    endEditInteraction()
                                },
                                onCropDraftChange = { cropDraft = it },
                                onCropInteractionStart = {
                                    isCropGestureActive = true
                                    beginEditInteraction()
                                },
                                onCropInteractionEnd = { crop ->
                                    isCropGestureActive = false
                                    cropDraft = crop
                                    applyAdjustmentsPreservingMasks(adjustments.copy(crop = crop))
                                    endEditInteraction()
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                            Spacer(modifier = Modifier.height(56.dp))

                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EditorControlsContent(
                                    panelTab = panelTab,
                                    adjustments = adjustments,
                                    onAdjustmentsChange = { applyAdjustmentsPreservingMasks(it) },
                                    onBeginEditInteraction = ::beginEditInteraction,
                                    onEndEditInteraction = ::endEditInteraction,
                                    histogramData = histogramData,
                                    masks = masks,
                                    onMasksChange = { updated ->
                                        if (panelTab == EditorPanelTab.Masks && showMaskOverlay) showMaskOverlay = false
                                        masks = updated
                                    },
                                    maskNumbers = maskNumbers,
                                    selectedMaskId = selectedMaskId,
                                    onSelectedMaskIdChange = { selectedMaskId = it },
                                    selectedSubMaskId = selectedSubMaskId,
                                    onSelectedSubMaskIdChange = { selectedSubMaskId = it },
                                    isPaintingMask = isPaintingMask,
                                    onPaintingMaskChange = { isPaintingMask = it },
                                    showMaskOverlay = showMaskOverlay,
                                    onShowMaskOverlayChange = { showMaskOverlay = it },
                                    onRequestMaskOverlayBlink = ::requestMaskOverlayBlink,
                                    brushSize = brushSize,
                                    onBrushSizeChange = { brushSize = it },
                                    brushTool = brushTool,
                                    onBrushToolChange = { brushTool = it },
                                    brushSoftness = brushSoftness,
                                    onBrushSoftnessChange = { brushSoftness = it },
                                    eraserSoftness = eraserSoftness,
                                    onEraserSoftnessChange = { eraserSoftness = it },
                                    maskTapMode = maskTapMode,
                                    onMaskTapModeChange = { maskTapMode = it },
                                    cropBaseWidthPx = cropBaseWidthPx,
                                    cropBaseHeightPx = cropBaseHeightPx,
                                    rotationDraft = rotationDraft,
                                    onRotationDraftChange = { rotationDraft = it },
                                    isStraightenActive = isStraightenActive,
                                    onStraightenActiveChange = { isStraightenActive = it },
                                    environmentMaskingEnabled = environmentMaskingEnabled,
                                    isGeneratingAiMask = isGeneratingAiMask,
                                    onGenerateAiEnvironmentMask = onGenerateAiEnvironmentMask,
                                    detectedAiEnvironmentCategories = detectedAiEnvironmentCategories,
                                    isDetectingAiEnvironmentCategories = isDetectingAiEnvironmentCategories,
                                    onDetectAiEnvironmentCategories = onDetectAiEnvironmentCategories
                                )

                                errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                statusMessage?.let { Text(text = it, color = Color(0xFF1B5E20), style = MaterialTheme.typography.bodySmall) }
                            }

                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                                EditorPanelTab.entries.forEach { tab ->
                                    val selected = panelTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { onSelectPanelTab(tab) },
                                        icon = { Icon(imageVector = if (selected) tab.iconSelected else tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                        colors =
                                            NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).windowInsetsPadding(WindowInsets.statusBars),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Surface(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape) {
                                Text(
                                    text = galleryItem.fileName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                enabled = canUndo,
                                onClick = ::undo,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                enabled = canRedo,
                                onClick = ::redo,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                enabled = sessionHandle != 0L,
                                onClick = { showMetadataDialog = true },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Using the box icon pattern for Tablet too for consistency,
                            // though layout is different (in top bar vs floating bottom)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFBFA2F8),
                                contentColor = Color.Black
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Download, contentDescription = "Export", modifier = Modifier.padding(8.dp))
                                    ExportButton(
                                        label = "",
                                        sessionHandle = sessionHandle,
                                        adjustments = adjustments,
                                        masks = masksForRender(masks),
                                        isExporting = isExporting,
                                        nativeDispatcher = renderDispatcher,
                                        context = context,
                                        onExportStart = { isExporting = true },
                                        onExportComplete = { success, message ->
                                            isExporting = false
                                            if (success) {
                                                if (message.startsWith("Saved to ")) {
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                    statusMessage = null
                                                } else {
                                                    statusMessage = message
                                                }
                                                errorMessage = null
                                            } else {
                                                errorMessage = message
                                                statusMessage = null
                                            }
                                        },
                                        modifier = Modifier
                                            .matchParentSize()
                                            .alpha(0f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // PHONE LAYOUT
                    Column(modifier = Modifier.fillMaxSize()) {
                        // TOP BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Surface(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape) {
                                    Text(
                                        text = galleryItem.fileName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = sessionHandle != 0L,
                                    onClick = { showMetadataDialog = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                                }
                            }
                        }

                        // MAIN CONTENT AREA (Preview + Controls + Overlay FAB)
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // 1. Preview Area
                                Box(modifier = Modifier
                                    .weight(1f)
                                    .background(Color.Black)) {
                                    Box {
                                        ImagePreview(
                                            bitmap = if (isComparingOriginal) originalBitmap ?: displayBitmap else displayBitmap,
                                            isLoading = isLoading || isGeneratingAiMask,
                                            viewportBitmap = if (isComparingOriginal || isCropMode) null else editedViewportBitmap,
                                            viewportRoi = if (isComparingOriginal || isCropMode) null else editedViewportRoi,
                                            onViewportRoiChange = onPreviewViewportRoiChange,
                                            maskOverlay = selectedMaskForOverlay,
                                            activeSubMask = selectedSubMaskForEdit,
                                            isMaskMode = isMaskMode && !isComparingOriginal,
                                            showMaskOverlay = showMaskOverlay && !isComparingOriginal,
                                            maskOverlayBlinkKey = maskOverlayBlinkKey,
                                            maskOverlayBlinkSubMaskId = maskOverlayBlinkSubMaskId,
                                            isPainting = isInteractiveMaskingEnabled && !isComparingOriginal,
                                            brushSize = brushSize,
                                            maskTapMode = if (isComparingOriginal) MaskTapMode.None else maskTapMode,
                                            onMaskTap = if (isComparingOriginal) null else onMaskTap,
                                            requestBeforePreview = { requestIdentityPreview() },
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
                                            },
                                            isCropMode = isCropMode && !isComparingOriginal,
                                            cropState = if (isCropMode && !isComparingOriginal) (cropDraft ?: adjustments.crop) else null,
                                            cropAspectRatio = cropAspectRatio,
                                            extraRotationDegrees = previewRotationDelta,
                                            isStraightenActive = isCropMode && isStraightenActive && !isComparingOriginal,
                                            onStraightenResult = { rotation ->
                                                beginEditInteraction()
                                                val baseRatio =
                                                    (cropBaseWidthPx?.toFloat()?.coerceAtLeast(1f) ?: 1f) /
                                                            (cropBaseHeightPx?.toFloat()?.coerceAtLeast(1f) ?: 1f)
                                                val autoCrop =
                                                    computeMaxCropNormalized(AutoCropParams(baseAspectRatio = baseRatio, rotationDegrees = rotation, aspectRatio = adjustments.aspectRatio))
                                                cropDraft = autoCrop
                                                rotationDraft = null
                                                isStraightenActive = false
                                                applyAdjustmentsPreservingMasks(adjustments.copy(rotation = rotation, crop = autoCrop))
                                                endEditInteraction()
                                            },
                                            onCropDraftChange = { cropDraft = it },
                                            onCropInteractionStart = {
                                                isCropGestureActive = true
                                                beginEditInteraction()
                                            },
                                            onCropInteractionEnd = { crop ->
                                                isCropGestureActive = false
                                                cropDraft = crop
                                                applyAdjustmentsPreservingMasks(adjustments.copy(crop = crop))
                                                endEditInteraction()
                                            }
                                        )

                                        // Overlay Controls - Bottom Left (Undo/Redo)
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            // Undo Button (Left half of pill)
                                            IconButton(
                                                onClick = ::undo,
                                                enabled = canUndo,
                                                modifier = Modifier
                                                    .background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                                                    ),
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f))
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                            }
                                            // Redo Button (Right half of pill)
                                            IconButton(
                                                onClick = ::redo,
                                                enabled = canRedo,
                                                modifier = Modifier
                                                    .background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                                                    ),
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f))
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                                            }
                                        }

                                        // Overlay Controls - Bottom Right (Compare)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(16.dp)
                                        ) {
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()

                                            LaunchedEffect(isPressed) {
                                                isComparingOriginal = isPressed
                                                if (isPressed && originalBitmap == null) {
                                                    originalBitmap = requestIdentityPreview()
                                                }
                                            }

                                            IconButton(
                                                onClick = { /* handled by interactionSource */ },
                                                interactionSource = interactionSource,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                            ) {
                                                Icon(Icons.Filled.CompareArrows, contentDescription = "Compare")
                                            }
                                        }
                                    }
                                }

                                // 2. Controls Sheet (Sits at bottom of Column)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(360.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 3.dp,
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                                ) {
                                    AnimatedContent(
                                        targetState = panelTab,
                                        transitionSpec = {
                                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                            (slideInHorizontally(
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                initialOffsetX = { it * direction }
                                            ) + fadeIn(animationSpec = tween(200))).togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                    targetOffsetX = { -it * direction }
                                                ) + fadeOut(animationSpec = tween(200))
                                            )
                                        },
                                        label = "TabAnimation"
                                    ) { currentTab ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            statusMessage?.let { Text(text = it, color = Color(0xFF1B5E20), style = MaterialTheme.typography.bodySmall) }

                                            EditorControlsContent(
                                                panelTab = currentTab,
                                                adjustments = adjustments,
                                                onAdjustmentsChange = { applyAdjustmentsPreservingMasks(it) },
                                                onBeginEditInteraction = ::beginEditInteraction,
                                                onEndEditInteraction = ::endEditInteraction,
                                                histogramData = histogramData,
                                                masks = masks,
                                                onMasksChange = { updated ->
                                                    if (panelTab == EditorPanelTab.Masks && showMaskOverlay) showMaskOverlay = false
                                                    masks = updated
                                                },
                                                maskNumbers = maskNumbers,
                                                selectedMaskId = selectedMaskId,
                                                onSelectedMaskIdChange = { selectedMaskId = it },
                                                selectedSubMaskId = selectedSubMaskId,
                                                onSelectedSubMaskIdChange = { selectedSubMaskId = it },
                                                isPaintingMask = isPaintingMask,
                                                onPaintingMaskChange = { isPaintingMask = it },
                                                showMaskOverlay = showMaskOverlay,
                                                onShowMaskOverlayChange = { showMaskOverlay = it },
                                                onRequestMaskOverlayBlink = ::requestMaskOverlayBlink,
                                                brushSize = brushSize,
                                                onBrushSizeChange = { brushSize = it },
                                                brushTool = brushTool,
                                                onBrushToolChange = { brushTool = it },
                                                brushSoftness = brushSoftness,
                                                onBrushSoftnessChange = { brushSoftness = it },
                                                eraserSoftness = eraserSoftness,
                                                onEraserSoftnessChange = { eraserSoftness = it },
                                                maskTapMode = maskTapMode,
                                                onMaskTapModeChange = { maskTapMode = it },
                                                cropBaseWidthPx = cropBaseWidthPx,
                                                cropBaseHeightPx = cropBaseHeightPx,
                                                rotationDraft = rotationDraft,
                                                onRotationDraftChange = { rotationDraft = it },
                                                isStraightenActive = isStraightenActive,
                                                onStraightenActiveChange = { isStraightenActive = it },
                                                environmentMaskingEnabled = environmentMaskingEnabled,
                                                isGeneratingAiMask = isGeneratingAiMask,
                                                onGenerateAiEnvironmentMask = onGenerateAiEnvironmentMask,
                                                detectedAiEnvironmentCategories = detectedAiEnvironmentCategories,
                                                isDetectingAiEnvironmentCategories = isDetectingAiEnvironmentCategories,
                                                onDetectAiEnvironmentCategories = onDetectAiEnvironmentCategories
                                            )
                                            // Extra spacing at bottom so content isn't hidden by FAB
                                            Spacer(modifier = Modifier.height(100.dp))
                                        }
                                    }
                                }
                            }

                            // 3. Floating App Bar (Overlay)
                            val customColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                                toolbarContainerColor = Color(0xFF1E1E1E), // Dark grey pill color
                                toolbarContentColor = Color.White
                            )

                            // Positioned directly over content via Box alignment
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .zIndex(1f)
                            ) {
                                HorizontalFloatingToolbar(
                                    expanded = true,
                                    floatingActionButton = {
                                        // The Container for the FAB
                                        Box {
                                            // The Visual FAB - using StandardFloatingActionButton for shape/elevation
                                            FloatingToolbarDefaults.StandardFloatingActionButton(
                                                onClick = { /* No-op, intercepted by ExportButton below */ },
                                                containerColor = Color(0xFFBFA2F8), // Accent color
                                                contentColor = Color.Black
                                            ) {
                                                Icon(Icons.Filled.Download, "Export")
                                            }

                                            // The Logic (Invisible Overlay)
                                            ExportButton(
                                                label = "",
                                                sessionHandle = sessionHandle,
                                                adjustments = adjustments,
                                                masks = masksForRender(masks),
                                                isExporting = isExporting,
                                                nativeDispatcher = renderDispatcher,
                                                context = context,
                                                onExportStart = { isExporting = true },
                                                onExportComplete = { success, message ->
                                                    isExporting = false
                                                    if (success) {
                                                        if (message.startsWith("Saved to ")) {
                                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                            statusMessage = null
                                                        } else {
                                                            statusMessage = message
                                                        }
                                                        errorMessage = null
                                                    } else {
                                                        errorMessage = message
                                                        statusMessage = null
                                                    }
                                                },
                                                modifier = Modifier.matchParentSize().alpha(0f)
                                            )
                                        }
                                    },
                                    // Use customColors directly to keep the Dark Grey Pill background
                                    colors = customColors,
                                    content = {
                                        // Navigation Icons
                                        EditorPanelTab.entries.forEach { tab ->
                                            val selected = panelTab == tab
                                            IconButton(onClick = { onSelectPanelTab(tab) }) {
                                                Icon(
                                                    imageVector = if (selected) tab.iconSelected else tab.icon,
                                                    contentDescription = tab.label,
                                                    tint = if (selected) Color.White else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.surface
                ) {}
            }
        }
    }

    if (showMetadataDialog) {
        val handle = sessionHandle
        LaunchedEffect(handle) {
            if (handle != 0L && metadataJson == null) {
                metadataJson = withContext(renderDispatcher) { runCatching { LibRawDecoder.getMetadataJsonFromSession(handle) }.getOrNull() }
            }
        }

        val pairs =
            remember(metadataJson) {
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
                        "Date" to obj.optString("dateTimeOriginal")
                    ).filter { it.second.isNotBlank() && it.second != "null" }
                }.getOrDefault(emptyList())
            }

        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            confirmButton = { TextButton(onClick = { showMetadataDialog = false }) { Text("Close") } },
            title = { Text("RAW Metadata") },
            text = {
                if (metadataJson == null) {
                    Text("Reading metadata…")
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
                            masks =
                                masks.map { mask ->
                                    if (mask.id != maskId) return@map mask
                                    mask.copy(
                                        subMasks =
                                            mask.subMasks.map { sub ->
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
            dismissButton = { TextButton(onClick = { showAiSubjectOverrideDialog = false; aiSubjectOverrideTarget = null }) { Text("Cancel") } },
            title = { Text("Replace subject mask?") },
            text = { Text("This will delete the current subject mask so you can draw a new one.") }
        )
    }
}