package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONObject

internal data class HslState(
    val reds: HueSatLumState = HueSatLumState(),
    val oranges: HueSatLumState = HueSatLumState(),
    val yellows: HueSatLumState = HueSatLumState(),
    val greens: HueSatLumState = HueSatLumState(),
    val aquas: HueSatLumState = HueSatLumState(),
    val blues: HueSatLumState = HueSatLumState(),
    val purples: HueSatLumState = HueSatLumState(),
    val magentas: HueSatLumState = HueSatLumState()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("reds", reds.toJsonObject())
            put("oranges", oranges.toJsonObject())
            put("yellows", yellows.toJsonObject())
            put("greens", greens.toJsonObject())
            put("aquas", aquas.toJsonObject())
            put("blues", blues.toJsonObject())
            put("purples", purples.toJsonObject())
            put("magentas", magentas.toJsonObject())
        }
    }

    fun isDefault(): Boolean {
        return reds.isDefault() &&
            oranges.isDefault() &&
            yellows.isDefault() &&
            greens.isDefault() &&
            aquas.isDefault() &&
            blues.isDefault() &&
            purples.isDefault() &&
            magentas.isDefault()
    }
}
