package com.dueckis.kawaiiraweditor.data.model

internal data class AiSubjectMaskParametersState(
    val maskDataBase64: String? = null,
    val softness: Float = 0.25f,
    val feather: Float = 0f,
    val baseTransform: MaskTransformState? = null,
    val baseWidthPx: Int? = null,
    val baseHeightPx: Int? = null
)
