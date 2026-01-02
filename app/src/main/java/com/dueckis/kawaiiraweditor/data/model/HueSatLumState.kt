package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONObject

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
