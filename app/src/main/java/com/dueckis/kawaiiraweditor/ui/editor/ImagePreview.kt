@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.dueckis.kawaiiraweditor.ui.editor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
 
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.CropState
import com.dueckis.kawaiiraweditor.data.model.MaskHandle
import com.dueckis.kawaiiraweditor.data.model.MaskPoint
import com.dueckis.kawaiiraweditor.data.model.MaskOverlayMode
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.buildMaskOverlayBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.IconButtonDefaults
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class CropHandle {
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun ImagePreview(
    bitmap: Bitmap?,
    viewportBitmap: Bitmap? = null,
    viewportRoi: CropState? = null,
    onViewportRoiChange: ((CropState?, Float) -> Unit)? = null,
    maskOverlay: MaskState? = null,
    requestBeforePreview: (suspend () -> Bitmap?)? = null,
    activeSubMask: SubMaskState? = null,
    isMaskMode: Boolean = false,
    showMaskOverlay: Boolean = true,
    maskOverlayBlinkKey: Long = 0L,
    maskOverlayBlinkSubMaskId: String? = null,
    isPainting: Boolean = false,
    brushSize: Float = 60f,
    maskTapMode: MaskTapMode = MaskTapMode.None,
    onMaskTap: ((MaskPoint) -> Unit)? = null,
    onBrushStrokeFinished: ((List<MaskPoint>, Float) -> Unit)? = null,
    onLassoFinished: ((List<MaskPoint>) -> Unit)? = null,
    onSubMaskHandleDrag: ((MaskHandle, MaskPoint) -> Unit)? = null,
    onSubMaskHandleDragStateChange: ((Boolean) -> Unit)? = null,
    onRequestAiSubjectOverride: (() -> Unit)? = null,
    isCropMode: Boolean = false,
    cropState: CropState? = null,
    cropAspectRatio: Float? = null,
    extraRotationDegrees: Float = 0f,
    isStraightenActive: Boolean = false,
    onStraightenResult: ((Float) -> Unit)? = null,
    onCropDraftChange: ((CropState) -> Unit)? = null,
    onCropInteractionStart: (() -> Unit)? = null,
    onCropInteractionEnd: ((CropState) -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var beforeDirty by remember { mutableStateOf(true) }
    var showingBeforeLocal by remember { mutableStateOf(false) }
    val beforeAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableStateOf(0f) }

    val currentStroke = remember { mutableStateListOf<MaskPoint>() }
    val density = LocalDensity.current
    val activeSubMaskState by rememberUpdatedState(activeSubMask)
    val onViewportRoiChangeState by rememberUpdatedState(onViewportRoiChange)
    val cropStateState by rememberUpdatedState(cropState)
    val cropAspectRatioState by rememberUpdatedState(cropAspectRatio)
    val onCropDraftChangeState by rememberUpdatedState(onCropDraftChange)
    val onCropInteractionStartState by rememberUpdatedState(onCropInteractionStart)
    val onCropInteractionEndState by rememberUpdatedState(onCropInteractionEnd)
    val onStraightenResultState by rememberUpdatedState(onStraightenResult)
    val isStraightenActiveState by rememberUpdatedState(isStraightenActive)

    BoxWithConstraints(
        modifier =
            Modifier
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
                .fillMaxSize()
                .clipToBounds()
                .let { base ->
                    if (!isMaskMode && !isCropMode) base
                    else
                        base.pointerInput(bitmap) {
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

                                    val prevCentroid =
                                        Offset(
                                            (a.previousPosition.x + b.previousPosition.x) / 2f,
                                            (a.previousPosition.y + b.previousPosition.y) / 2f
                                        )
                                    val currCentroid =
                                        Offset(
                                            (a.position.x + b.position.x) / 2f,
                                            (a.position.y + b.position.y) / 2f
                                        )
                                    val pan = currCentroid - prevCentroid

                                    val prevDx = a.previousPosition.x - b.previousPosition.x
                                    val prevDy = a.previousPosition.y - b.previousPosition.y
                                    val currDx = a.position.x - b.position.x
                                    val currDy = a.position.y - b.position.y
                                    val prevDist =
                                        kotlin.math.sqrt(prevDx * prevDx + prevDy * prevDy).coerceAtLeast(0.0001f)
                                    val currDist =
                                        kotlin.math.sqrt(currDx * currDx + currDy * currDy).coerceAtLeast(0.0001f)
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

            fun computeVisibleViewportRoi(s: Float, ox: Float, oy: Float): CropState? {
                if (s <= 1.1f) return null
                if (displayW <= 0.0001f || displayH <= 0.0001f) return null

                fun toImageNormUnclamped(pos: Offset): Offset {
                    val pivot = Offset(containerW / 2f, containerH / 2f)
                    val x = pivot.x + (pos.x - ox - pivot.x) / s
                    val y = pivot.y + (pos.y - oy - pivot.y) / s
                    return Offset((x - left) / displayW, (y - top) / displayH)
                }

                val corners = listOf(Offset(0f, 0f), Offset(containerW, 0f), Offset(0f, containerH), Offset(containerW, containerH))
                val points = corners.map { toImageNormUnclamped(it) }
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }

                val x0 = minX.coerceIn(0f, 1f)
                val x1 = maxX.coerceIn(0f, 1f)
                val y0 = minY.coerceIn(0f, 1f)
                val y1 = maxY.coerceIn(0f, 1f)
                val w = (x1 - x0).coerceAtLeast(0f)
                val h = (y1 - y0).coerceAtLeast(0f)
                if (w >= 0.999f && h >= 0.999f) return null
                if (w <= 0.0005f || h <= 0.0005f) return null
                return CropState(x = x0, y = y0, width = w, height = h)
            }

            LaunchedEffect(onViewportRoiChangeState, bitmap, isCropMode, containerW, containerH) {
                val callback = onViewportRoiChangeState ?: return@LaunchedEffect
                if (isCropMode) {
                    callback(null, 1f)
                    return@LaunchedEffect
                }
                snapshotFlow { Triple(scale, offsetX, offsetY) }
                    .map { (s, ox, oy) -> computeVisibleViewportRoi(s = s, ox = ox, oy = oy) to s }
                    .distinctUntilChanged()
                    .collect { (roi, s) -> callback(roi, s) }
            }

            val imageModifier =
                Modifier
                    .fillMaxSize()
                    .let { base ->
                        if (isMaskMode || isCropMode)
                            base
                        else
                            base.pointerInput(Unit) {
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
                        translationY = offsetY,
                        rotationZ = if (isCropMode) extraRotationDegrees else 0f,
                        clip = isCropMode && kotlin.math.abs(extraRotationDegrees) > 0.0001f
                    )

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Processed preview",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = imageModifier
            )

            val rb = beforeBitmap
            if (rb != null) {
                Image(
                    bitmap = rb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                            rotationZ = if (isCropMode) extraRotationDegrees else 0f,
                            alpha = beforeAlpha.value
                        )
                )
            }

            val viewportBmp = viewportBitmap
            val viewportRoiSnapshot = viewportRoi?.normalized()
            if (!isCropMode && viewportBmp != null && viewportRoiSnapshot != null) {
                val viewportPaint = remember { android.graphics.Paint().apply { isFilterBitmap = true } }
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                ) {
                    val roiWNorm = viewportRoiSnapshot.width.coerceAtLeast(0.0001f)
                    val roiHNorm = viewportRoiSnapshot.height.coerceAtLeast(0.0001f)
                    val zoomW = (viewportBmp.width.toFloat() / roiWNorm).coerceAtLeast(1f)
                    val zoomH = (viewportBmp.height.toFloat() / roiHNorm).coerceAtLeast(1f)
                    val roiX = (viewportRoiSnapshot.x * zoomW).roundToInt().coerceIn(0, (zoomW - 1f).toInt())
                    val roiY = (viewportRoiSnapshot.y * zoomH).roundToInt().coerceIn(0, (zoomH - 1f).toInt())
                    val alignedRoiX = roiX.toFloat() / zoomW
                    val alignedRoiY = roiY.toFloat() / zoomH
                    val alignedRoiW = viewportBmp.width.toFloat() / zoomW
                    val alignedRoiH = viewportBmp.height.toFloat() / zoomH
                    val roiLeftPx = left + alignedRoiX * displayW
                    val roiTopPx = top + alignedRoiY * displayH
                    val roiWPx = (alignedRoiW * displayW).coerceAtLeast(1f)
                    val roiHPx = (alignedRoiH * displayH).coerceAtLeast(1f)
                    val dst = android.graphics.RectF(roiLeftPx, roiTopPx, roiLeftPx + roiWPx, roiTopPx + roiHPx)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(viewportBmp, null, dst, viewportPaint)
                    }
                }
            }

            val cropSnapshot = cropState?.normalized()
            if (isCropMode && cropSnapshot != null) {
                val cropLeftPx = left + cropSnapshot.x * displayW
                val cropTopPx = top + cropSnapshot.y * displayH
                val cropRightPx = left + (cropSnapshot.x + cropSnapshot.width) * displayW
                val cropBottomPx = top + (cropSnapshot.y + cropSnapshot.height) * displayH

                var straightenStart by remember { mutableStateOf<MaskPoint?>(null) }
                var straightenEnd by remember { mutableStateOf<MaskPoint?>(null) }
                LaunchedEffect(isStraightenActiveState) {
                    if (!isStraightenActiveState) {
                        straightenStart = null
                        straightenEnd = null
                    }
                }

                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                ) {
                    val dimColor = Color.Black.copy(alpha = 0.55f)
                    val imageRight = left + displayW
                    val imageBottom = top + displayH

                    drawRect(color = dimColor, topLeft = Offset(left, top), size = Size(displayW, (cropTopPx - top).coerceAtLeast(0f)))
                    drawRect(
                        color = dimColor,
                        topLeft = Offset(left, cropBottomPx),
                        size = Size(displayW, (imageBottom - cropBottomPx).coerceAtLeast(0f))
                    )
                    drawRect(
                        color = dimColor,
                        topLeft = Offset(left, cropTopPx),
                        size = Size((cropLeftPx - left).coerceAtLeast(0f), (cropBottomPx - cropTopPx).coerceAtLeast(0f))
                    )
                    drawRect(
                        color = dimColor,
                        topLeft = Offset(cropRightPx, cropTopPx),
                        size = Size((imageRight - cropRightPx).coerceAtLeast(0f), (cropBottomPx - cropTopPx).coerceAtLeast(0f))
                    )

                    val strokeWidth = with(density) { 2.dp.toPx() } / scale.coerceAtLeast(0.0001f)
                    val handleRadius = with(density) { 10.dp.toPx() } / scale.coerceAtLeast(0.0001f)

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(cropLeftPx, cropTopPx),
                        size = Size((cropRightPx - cropLeftPx).coerceAtLeast(0f), (cropBottomPx - cropTopPx).coerceAtLeast(0f)),
                        style = Stroke(width = strokeWidth)
                    )

                    listOf(
                        Offset(cropLeftPx, cropTopPx),
                        Offset(cropRightPx, cropTopPx),
                        Offset(cropLeftPx, cropBottomPx),
                        Offset(cropRightPx, cropBottomPx)
                    ).forEach { p ->
                        drawCircle(color = Color.White, radius = handleRadius, center = p)
                    }

                    val s = straightenStart
                    val e = straightenEnd
                    if (isStraightenActiveState && s != null && e != null) {
                        val startPx = Offset(left + s.x * displayW, top + s.y * displayH)
                        val endPx = Offset(left + e.x * displayW, top + e.y * displayH)
                        drawLine(
                            color = Color(0xFFFFC107),
                            start = startPx,
                            end = endPx,
                            strokeWidth = strokeWidth * 1.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (isStraightenActiveState) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(bitmap) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val p = toImagePoint(start)
                                            straightenStart = p
                                            straightenEnd = p
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            straightenEnd = toImagePoint(change.position)
                                        },
                                        onDragCancel = {
                                            straightenStart = null
                                            straightenEnd = null
                                        },
                                        onDragEnd = {
                                            val s = straightenStart
                                            val e = straightenEnd
                                            straightenStart = null
                                            straightenEnd = null

                                            if (s == null || e == null) return@detectDragGestures
                                            val dx = e.x - s.x
                                            val dy = e.y - s.y
                                            if (kotlin.math.abs(dx) <= 0.0001f && kotlin.math.abs(dy) <= 0.0001f) return@detectDragGestures

                                            val angleDeg = (kotlin.math.atan2(dy, dx) * 180.0 / kotlin.math.PI).toFloat()
                                            val rotation = (-angleDeg).coerceIn(-45f, 45f)
                                            onStraightenResultState?.invoke(rotation)
                                        }
                                    )
                                }
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(bitmap) {
                                    var activeHandle: CropHandle? = null
                                    var startCrop: CropState? = null
                                    var startPointer: MaskPoint? = null
                                    val minSize = 0.05f

                                    fun clampCrop(l: Float, t: Float, r: Float, b: Float): CropState {
                                        var leftN = l.coerceIn(0f, 1f)
                                        var topN = t.coerceIn(0f, 1f)
                                        var rightN = r.coerceIn(0f, 1f)
                                        var bottomN = b.coerceIn(0f, 1f)

                                        if (rightN - leftN < minSize) {
                                            val mid = (leftN + rightN) / 2f
                                            leftN = (mid - minSize / 2f).coerceIn(0f, 1f - minSize)
                                            rightN = (leftN + minSize).coerceIn(minSize, 1f)
                                        }
                                        if (bottomN - topN < minSize) {
                                            val mid = (topN + bottomN) / 2f
                                            topN = (mid - minSize / 2f).coerceIn(0f, 1f - minSize)
                                            bottomN = (topN + minSize).coerceIn(minSize, 1f)
                                        }

                                        return CropState(
                                            x = leftN.coerceIn(0f, 1f - minSize),
                                            y = topN.coerceIn(0f, 1f - minSize),
                                            width = (rightN - leftN).coerceIn(minSize, 1f),
                                            height = (bottomN - topN).coerceIn(minSize, 1f)
                                        ).normalized()
                                    }

                                    fun applyAspectRatio(handle: CropHandle, crop: CropState, ratio: Float): CropState {
                                        val l = crop.x
                                        val t = crop.y
                                        val r = crop.x + crop.width
                                        val b = crop.y + crop.height
                                        val width = (r - l).coerceAtLeast(minSize)
                                        val height = (b - t).coerceAtLeast(minSize)
                                        val normalizedRatio = ratio * (displayH / displayW).coerceAtLeast(0.0001f)

                                        return when (handle) {
                                            CropHandle.TopLeft -> {
                                                if (width / height > normalizedRatio) clampCrop(r - height * normalizedRatio, t, r, b)
                                                else clampCrop(l, b - width / normalizedRatio, r, b)
                                            }

                                            CropHandle.TopRight -> {
                                                if (width / height > normalizedRatio) clampCrop(l, t, l + height * normalizedRatio, b)
                                                else clampCrop(l, b - width / normalizedRatio, r, b)
                                            }

                                            CropHandle.BottomLeft -> {
                                                if (width / height > normalizedRatio) clampCrop(r - height * normalizedRatio, t, r, b)
                                                else clampCrop(l, t, r, t + width / normalizedRatio)
                                            }

                                            CropHandle.BottomRight -> {
                                                if (width / height > normalizedRatio) clampCrop(l, t, l + height * normalizedRatio, b)
                                                else clampCrop(l, t, r, t + width / normalizedRatio)
                                            }

                                            CropHandle.Move -> crop
                                        }
                                    }

                                    fun dist(a: Offset, b: Offset): Float {
                                        val dx = a.x - b.x
                                        val dy = a.y - b.y
                                        return kotlin.math.sqrt(dx * dx + dy * dy)
                                    }

                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val current = cropStateState?.normalized() ?: return@detectDragGestures
                                            val startContent = toContentOffset(start)

                                            val curLeft = left + current.x * displayW
                                            val curTop = top + current.y * displayH
                                            val curRight = left + (current.x + current.width) * displayW
                                            val curBottom = top + (current.y + current.height) * displayH

                                            val handlePx = with(density) { 24.dp.toPx() } / scale.coerceAtLeast(0.0001f)
                                            val tl = Offset(curLeft, curTop)
                                            val tr = Offset(curRight, curTop)
                                            val bl = Offset(curLeft, curBottom)
                                            val br = Offset(curRight, curBottom)

                                            activeHandle =
                                                when {
                                                    dist(startContent, tl) <= handlePx -> CropHandle.TopLeft
                                                    dist(startContent, tr) <= handlePx -> CropHandle.TopRight
                                                    dist(startContent, bl) <= handlePx -> CropHandle.BottomLeft
                                                    dist(startContent, br) <= handlePx -> CropHandle.BottomRight
                                                    startContent.x in curLeft..curRight && startContent.y in curTop..curBottom -> CropHandle.Move
                                                    else -> null
                                                }

                                            if (activeHandle != null) {
                                                onCropInteractionStartState?.invoke()
                                                startCrop = current
                                                startPointer = toImagePoint(start)
                                            }
                                        },
                                        onDragCancel = {
                                            val wasActive = activeHandle != null
                                            activeHandle = null
                                            startCrop = null
                                            startPointer = null
                                            if (wasActive) {
                                                val final = cropStateState?.normalized() ?: cropSnapshot
                                                if (final != null) onCropInteractionEndState?.invoke(final)
                                            }
                                        },
                                        onDragEnd = {
                                            activeHandle ?: return@detectDragGestures
                                            activeHandle = null
                                            startCrop = null
                                            startPointer = null
                                            val final = cropStateState?.normalized() ?: return@detectDragGestures
                                            onCropInteractionEndState?.invoke(final)
                                        },
                                        onDrag = { change, _ ->
                                            val handle = activeHandle ?: return@detectDragGestures
                                            val baseCrop = startCrop ?: return@detectDragGestures
                                            change.consume()

                                            val curr = toImagePoint(change.position)
                                            val ratio = cropAspectRatioState?.takeIf { it.isFinite() && it > 0f }

                                            val next =
                                                when (handle) {
                                                    CropHandle.Move -> {
                                                        val sp = startPointer ?: curr
                                                        val dx = curr.x - sp.x
                                                        val dy = curr.y - sp.y
                                                        CropState(
                                                            x = (baseCrop.x + dx).coerceIn(0f, 1f - baseCrop.width),
                                                            y = (baseCrop.y + dy).coerceIn(0f, 1f - baseCrop.height),
                                                            width = baseCrop.width,
                                                            height = baseCrop.height
                                                        ).normalized()
                                                    }

                                                    CropHandle.TopLeft -> {
                                                        val proposed =
                                                            clampCrop(curr.x, curr.y, baseCrop.x + baseCrop.width, baseCrop.y + baseCrop.height)
                                                        if (ratio == null) proposed else applyAspectRatio(CropHandle.TopLeft, proposed, ratio)
                                                    }

                                                    CropHandle.TopRight -> {
                                                        val proposed = clampCrop(baseCrop.x, curr.y, curr.x, baseCrop.y + baseCrop.height)
                                                        if (ratio == null) proposed else applyAspectRatio(CropHandle.TopRight, proposed, ratio)
                                                    }

                                                    CropHandle.BottomLeft -> {
                                                        val proposed = clampCrop(curr.x, baseCrop.y, baseCrop.x + baseCrop.width, curr.y)
                                                        if (ratio == null) proposed else applyAspectRatio(CropHandle.BottomLeft, proposed, ratio)
                                                    }

                                                    CropHandle.BottomRight -> {
                                                        val proposed = clampCrop(baseCrop.x, baseCrop.y, curr.x, curr.y)
                                                        if (ratio == null) proposed else applyAspectRatio(CropHandle.BottomRight, proposed, ratio)
                                                    }
                                                }
                                            onCropDraftChangeState?.invoke(next)
                                        }
                                    )
                                }
                    )
                }
            }

            if (isMaskMode) {
                val persistentOverlayVisible = showMaskOverlay && maskOverlay != null
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
                var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
                val overlayMaxDim = if (isPainting) 256 else 512

                LaunchedEffect(
                    maskOverlay,
                    shouldDrawOverlay,
                    overlayIsFlashing,
                    maskOverlayBlinkSubMaskId,
                    bitmap.width,
                    bitmap.height,
                    overlayMaxDim
                ) {
                    val overlayMask =
                        maskOverlay ?: run {
                            overlayBitmap = null
                            return@LaunchedEffect
                        }
                    if (!shouldDrawOverlay) {
                        overlayBitmap = null
                        return@LaunchedEffect
                    }

                    val w = bitmap.width
                    val h = bitmap.height
                    val scale =
                        if (w >= h) overlayMaxDim.toFloat() / w.coerceAtLeast(1)
                        else overlayMaxDim.toFloat() / h.coerceAtLeast(1)
                    val outW = (w * scale).toInt().coerceAtLeast(1)
                    val outH = (h * scale).toInt().coerceAtLeast(1)
                    val highlightSubMaskId = if (overlayIsFlashing) maskOverlayBlinkSubMaskId else null
                    overlayBitmap =
                        withContext(Dispatchers.Default) {
                            val overlayMode =
                                if (overlayIsFlashing && maskOverlayBlinkSubMaskId == null) {
                                    MaskOverlayMode.Result
                                } else {
                                    MaskOverlayMode.Composite
                                }
                            buildMaskOverlayBitmap(
                                overlayMask,
                                outW,
                                outH,
                                overlayMode = overlayMode,
                                highlightSubMaskId = highlightSubMaskId
                            )
                        }
                }

                val overlayBitmapSnapshot = overlayBitmap
                if (overlayBitmapSnapshot != null) {
                    Image(
                        bitmap = overlayBitmapSnapshot.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
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
                                val half =
                                    run {
                                        val base = minOf(bmpW, bmpH)
                                        val rangePx =
                                            if (activeSubMask.linear.range <= 1.5f) activeSubMask.linear.range * base else activeSubMask.linear.range
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
                                val radiusPx =
                                    run {
                                        val base = minOf(bmpW, bmpH)
                                        val r =
                                            if (activeSubMask.radial.radiusX <= 1.5f) activeSubMask.radial.radiusX * base
                                            else activeSubMask.radial.radiusX
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
                        modifier =
                            Modifier
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

                                            dragging =
                                                when (sub.type) {
                                                    SubMaskType.Radial.id -> {
                                                        val centerPx =
                                                            run {
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
                                                        val startPx =
                                                            run {
                                                                val maxX = (bmpW - 1f).coerceAtLeast(1f)
                                                                val maxY = (bmpH - 1f).coerceAtLeast(1f)
                                                                val sx = sub.linear.startX
                                                                val sy = sub.linear.startY
                                                                val px = if (sx <= 1.5f) sx.coerceIn(0f, 1f) * maxX else sx.coerceIn(0f, maxX)
                                                                val py = if (sy <= 1.5f) sy.coerceIn(0f, 1f) * maxY else sy.coerceIn(0f, maxY)
                                                                Offset(left + px * baseScale, top + py * baseScale)
                                                            }
                                                        val endPx =
                                                            run {
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(maskTapMode, bitmap) { detectTapGestures { pos -> onMaskTap(toImagePoint(pos)) } }
                    )
                }

                if (currentStroke.isNotEmpty()) {
                    Canvas(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    ) {
                        val maxX = (bmpW - 1f).coerceAtLeast(1f)
                        val maxY = (bmpH - 1f).coerceAtLeast(1f)
                        fun toDisplayOffset(p: MaskPoint): Offset {
                            val px = if (p.x <= 1.5f) p.x * maxX else p.x
                            val py = if (p.y <= 1.5f) p.y * maxY else p.y
                            return Offset(left + px * baseScale, top + py * baseScale)
                        }

                        fun pressureScale(pressure: Float): Float {
                            val p = pressure.coerceIn(0f, 1f)
                            return 0.2f + (0.8f * p)
                        }

                        when (activeSubMask?.type) {
                            SubMaskType.AiSubject.id -> {
                                val path =
                                    Path().apply {
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
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

                            else -> {
                                val baseStrokeWidth = (brushSize * baseScale).coerceAtLeast(1f)
                                val pts = currentStroke.toList()
                                if (pts.size == 1) {
                                    val p = pts[0]
                                    val o = toDisplayOffset(p)
                                    val w = baseStrokeWidth * pressureScale(p.pressure)
                                    drawCircle(color = Color(0x88FFFFFF), radius = w / 2f, center = o)
                                } else {
                                    pts.windowed(2, 1, false).forEach { (p0, p1) ->
                                        val o0 = toDisplayOffset(p0)
                                        val o1 = toDisplayOffset(p1)
                                        val w0 = baseStrokeWidth * pressureScale(p0.pressure)
                                        val w1 = baseStrokeWidth * pressureScale(p1.pressure)
                                        drawLine(
                                            color = Color(0x88FFFFFF),
                                            start = o0,
                                            end = o1,
                                            strokeWidth = ((w0 + w1) / 2f).coerceAtLeast(1f),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isMaskMode && isPainting && activeSubMask != null) {
                when (activeSubMask.type) {
                    SubMaskType.Brush.id -> {
                        val callback = onBrushStrokeFinished ?: return@BoxWithConstraints
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(isPainting, brushSize, bitmap) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (down.type != PointerType.Touch && down.type != PointerType.Stylus && down.type != PointerType.Eraser) return@awaitEachGesture

                                            val cancelOnMultiTouch = down.type == PointerType.Touch
                                            val brushSizeNorm = (brushSize / baseDim).coerceAtLeast(0.0001f)

                                            fun normalizedPressure(change: androidx.compose.ui.input.pointer.PointerInputChange): Float {
                                                if (change.type != PointerType.Stylus && change.type != PointerType.Eraser) return 1f
                                                return change.pressure.coerceIn(0f, 1f)
                                            }

                                            fun pressureScale(pressure: Float): Float {
                                                val p = pressure.coerceIn(0f, 1f)
                                                return 0.2f + (0.8f * p)
                                            }

                                            val baseMinStep = (brushSizeNorm / 6f).coerceAtLeast(0.003f)

                                            currentStroke.clear()
                                            currentStroke.add(toImagePoint(down.position).copy(pressure = normalizedPressure(down)))
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
                                                val pressure = normalizedPressure(change)
                                                val newPoint = toImagePoint(change.position).copy(pressure = pressure)
                                                val dx = newPoint.x - last.x
                                                val dy = newPoint.y - last.y
                                                val minStep = (baseMinStep * pressureScale(pressure)).coerceAtLeast(0.0015f)
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
                            modifier =
                                Modifier
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
    }
}
