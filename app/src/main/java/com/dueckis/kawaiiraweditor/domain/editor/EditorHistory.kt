package com.dueckis.kawaiiraweditor.domain.editor

import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.MaskState

internal data class EditorHistoryEntry(
    val adjustments: AdjustmentState,
    val masks: List<MaskState>
)
