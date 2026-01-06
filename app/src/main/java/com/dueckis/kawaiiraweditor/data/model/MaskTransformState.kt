package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONObject

internal data class MaskTransformState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val orientationSteps: Int = 0,
    val crop: CropState? = null
) {
    fun normalized(): MaskTransformState = copy(crop = crop?.normalized())

    fun toJsonObject(): JSONObject {
        val normalized = normalized()
        return JSONObject().apply {
            put("rotation", normalized.rotation)
            put("flipHorizontal", normalized.flipHorizontal)
            put("flipVertical", normalized.flipVertical)
            put("orientationSteps", normalized.orientationSteps)
            put("crop", normalized.crop?.toJsonObject() ?: JSONObject.NULL)
        }
    }

    fun matches(other: MaskTransformState): Boolean {
        val a = normalized()
        val b = other.normalized()
        return a.rotation == b.rotation &&
            a.flipHorizontal == b.flipHorizontal &&
            a.flipVertical == b.flipVertical &&
            a.orientationSteps == b.orientationSteps &&
            a.crop == b.crop
    }
}
