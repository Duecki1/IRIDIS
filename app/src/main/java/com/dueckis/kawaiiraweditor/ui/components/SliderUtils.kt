package com.dueckis.kawaiiraweditor.ui.components

import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange

internal fun snapToStep(
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (step <= 0f) return clamped
    val steps = ((clamped - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}
