package com.dueckis.kawaiiraweditor.ui.editor.masking

import com.dueckis.kawaiiraweditor.data.model.AiSubjectMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.LinearMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.RadialMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import java.util.UUID

internal fun newSubMaskState(id: String, mode: SubMaskMode, type: SubMaskType): SubMaskState {
    return when (type) {
        SubMaskType.Brush -> SubMaskState(id = id, type = type.id, mode = mode)
        SubMaskType.Linear ->
            SubMaskState(id = id, type = type.id, mode = mode, linear = LinearMaskParametersState())
        SubMaskType.Radial ->
            SubMaskState(id = id, type = type.id, mode = mode, radial = RadialMaskParametersState())
        SubMaskType.AiEnvironment ->
            SubMaskState(id = id, type = type.id, mode = mode, aiEnvironment = AiEnvironmentMaskParametersState())
        SubMaskType.AiSubject ->
            SubMaskState(id = id, type = type.id, mode = mode, aiSubject = AiSubjectMaskParametersState())
    }
}

internal fun duplicateMaskState(mask: MaskState, invertDuplicate: Boolean): MaskState {
    val newId = UUID.randomUUID().toString()
    fun copySubMask(sub: SubMaskState): SubMaskState = sub.copy(id = UUID.randomUUID().toString())
    val newName = if (mask.name.endsWith(" Copy")) "${mask.name} 2" else "${mask.name} Copy"
    return mask.copy(
        id = newId,
        name = newName,
        invert = if (invertDuplicate) !mask.invert else mask.invert,
        subMasks = mask.subMasks.map(::copySubMask)
    )
}

internal fun duplicateSubMaskState(subMask: SubMaskState): SubMaskState {
    return subMask.copy(id = UUID.randomUUID().toString())
}
