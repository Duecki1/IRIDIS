package com.dueckis.kawaiiraweditor.data.model

import java.util.Locale
import kotlin.ranges.ClosedFloatingPointRange

internal data class AdjustmentControl(
    val field: AdjustmentField,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val defaultValue: Float = 0f,
    val formatter: (Float) -> String = { value -> String.format(Locale.US, "%.0f", value) }
)
