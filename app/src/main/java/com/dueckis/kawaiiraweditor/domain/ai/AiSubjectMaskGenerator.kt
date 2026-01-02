package com.dueckis.kawaiiraweditor.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class AiSubjectMaskGenerator(appContext: Context) {
    private val context = appContext.applicationContext
    private val u2net = U2NetOnnxSegmenter(context)

    suspend fun generateSubjectMaskDataUrl(
        previewBitmap: Bitmap,
        lassoPoints: List<NormalizedPoint>,
        paddingFraction: Float = 0.08f
    ): String = withContext(Dispatchers.Default) {
        require(lassoPoints.size >= 3) { "Need at least 3 points for lasso" }
        val width = previewBitmap.width.coerceAtLeast(1)
        val height = previewBitmap.height.coerceAtLeast(1)

        val minX = lassoPoints.minOf { it.x }.coerceIn(0f, 1f)
        val minY = lassoPoints.minOf { it.y }.coerceIn(0f, 1f)
        val maxX = lassoPoints.maxOf { it.x }.coerceIn(0f, 1f)
        val maxY = lassoPoints.maxOf { it.y }.coerceIn(0f, 1f)

        val padX = ((maxX - minX) * paddingFraction).coerceAtLeast(0.02f)
        val padY = ((maxY - minY) * paddingFraction).coerceAtLeast(0.02f)

        val left = ((minX - padX) * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val top = ((minY - padY) * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val right = ((maxX + padX) * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val bottom = ((maxY + padY) * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val cropRect = Rect(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))

        val crop = Bitmap.createBitmap(
            previewBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        ).copy(Bitmap.Config.ARGB_8888, false)

        val cropMask = u2net.segmentForegroundMask(crop)

        val lassoMask = rasterizeLassoMask(
            lassoPoints = lassoPoints,
            fullWidth = width,
            fullHeight = height,
            cropRect = cropRect
        )

        val fullMask = ByteArray(width * height)
        val cropW = cropRect.width()
        val cropH = cropRect.height()
        for (y in 0 until cropH) {
            val outRow = (cropRect.top + y) * width + cropRect.left
            val cropRow = y * cropW
            val lassoRow = y * cropW
            for (x in 0 until cropW) {
                val cropVal = cropMask[cropRow + x].toInt() and 0xFF
                val lassoVal = lassoMask[lassoRow + x].toInt() and 0xFF
                val finalVal = (cropVal * lassoVal / 255).coerceIn(0, 255)
                fullMask[outRow + x] = finalVal.toByte()
            }
        }

        val maskBitmap = grayscaleMaskToBitmap(fullMask, width, height)
        val pngBytes = ByteArrayOutputStream().use { out ->
            maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        "data:image/png;base64,$b64"
    }

    private fun rasterizeLassoMask(
        lassoPoints: List<NormalizedPoint>,
        fullWidth: Int,
        fullHeight: Int,
        cropRect: Rect
    ): ByteArray {
        val cropW = cropRect.width().coerceAtLeast(1)
        val cropH = cropRect.height().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        val maxX = (fullWidth - 1).coerceAtLeast(1)
        val maxY = (fullHeight - 1).coerceAtLeast(1)
        val path = Path()
        val first = lassoPoints.first()
        path.moveTo(first.x * maxX - cropRect.left, first.y * maxY - cropRect.top)
        lassoPoints.drop(1).forEach { p ->
            path.lineTo(p.x * maxX - cropRect.left, p.y * maxY - cropRect.top)
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawPath(path, paint)

        val pixels = IntArray(cropW * cropH)
        bmp.getPixels(pixels, 0, cropW, 0, 0, cropW, cropH)
        val mask = ByteArray(cropW * cropH)
        for (i in pixels.indices) {
            mask[i] = if ((pixels[i] and 0x00FFFFFF) != 0) 0xFF.toByte() else 0
        }
        return mask
    }

    private fun grayscaleMaskToBitmap(mask: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = mask[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
