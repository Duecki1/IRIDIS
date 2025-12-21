package com.dueckis.kawaiiraweditor

internal data class EditorHistoryEntry(
    val adjustments: AdjustmentState,
    val masks: List<MaskState>
)

