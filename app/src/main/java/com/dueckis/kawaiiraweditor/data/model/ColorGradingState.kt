package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONObject

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
