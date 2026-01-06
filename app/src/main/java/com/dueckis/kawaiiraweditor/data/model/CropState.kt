package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONObject

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
