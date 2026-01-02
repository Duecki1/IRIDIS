package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONArray
import org.json.JSONObject

internal data class MaskState(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val invert: Boolean = false,
    val opacity: Float = 100f,
    val adjustments: AdjustmentState = AdjustmentState(),
    val subMasks: List<SubMaskState> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("visible", visible)
            put("invert", invert)
            put("opacity", opacity)
            put("adjustments", adjustments.toJsonObject(includeToneMapper = false))
            put(
                "subMasks",
                JSONArray().apply {
                    subMasks.forEach { put(it.toJsonObject()) }
                }
            )
        }
    }
}
