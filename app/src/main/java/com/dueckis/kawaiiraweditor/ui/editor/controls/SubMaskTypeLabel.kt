package com.dueckis.kawaiiraweditor.ui.editor.controls

import com.dueckis.kawaiiraweditor.data.model.SubMaskType

internal fun SubMaskType.label(): String =
    when (this) {
        SubMaskType.AiEnvironment -> "AI Environment"
        SubMaskType.AiSubject -> "AI Subject"
        SubMaskType.Brush -> "Brush"
        SubMaskType.Linear -> "Linear Gradient"
        SubMaskType.Radial -> "Radial Gradient"
    }
