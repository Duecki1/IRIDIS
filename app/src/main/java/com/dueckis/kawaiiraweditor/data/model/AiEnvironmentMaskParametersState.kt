package com.dueckis.kawaiiraweditor.data.model

internal data class AiEnvironmentMaskParametersState(
    val category: String = AiEnvironmentCategory.Sky.id,
    val maskDataBase64: String? = null,
    val softness: Float = 0.25f,
    val baseTransform: MaskTransformState? = null,
    val baseWidthPx: Int? = null,
    val baseHeightPx: Int? = null
)
