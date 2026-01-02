package com.dueckis.kawaiiraweditor.data.model

internal enum class SubMaskMode {
    Additive,
    Subtractive,
}

internal fun SubMaskMode.inverted(): SubMaskMode {
    return when (this) {
        SubMaskMode.Additive -> SubMaskMode.Subtractive
        SubMaskMode.Subtractive -> SubMaskMode.Additive
    }
}
