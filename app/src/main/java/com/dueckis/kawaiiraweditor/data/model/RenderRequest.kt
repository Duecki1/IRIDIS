package com.dueckis.kawaiiraweditor.data.model

internal data class RenderRequest(
    val version: Long,
    val adjustmentsJson: String,
    val target: RenderTarget = RenderTarget.Edited,
    val rotationDegrees: Float = 0f,
    val previewRoi: CropState? = null
)
