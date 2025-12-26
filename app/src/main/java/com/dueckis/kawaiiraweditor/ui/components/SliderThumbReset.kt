package com.dueckis.kawaiiraweditor.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs

internal fun Modifier.doubleTapSliderThumbToReset(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onReset: () -> Unit
): Modifier =
    composed {
        val viewConfiguration = LocalViewConfiguration.current
        var lastTap by remember { mutableStateOf<Tap?>(null) }

        val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis.toLong()
        val slopPx = viewConfiguration.touchSlop

        pointerInput(enabled, value, valueRange) {
            if (!enabled) return@pointerInput

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val now = down.uptimeMillis
                val pos = down.position

                val previous = lastTap
                if (previous != null &&
                    (now - previous.timeMs) <= doubleTapTimeoutMs &&
                    previous.pos.isNear(pos, slopPx)
                ) {
                    lastTap = null
                    down.consume()
                    onReset()
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    return@awaitEachGesture
                } else {
                    lastTap = Tap(timeMs = now, pos = pos)
                }

                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                }
            }
    }

private data class Tap(val timeMs: Long, val pos: androidx.compose.ui.geometry.Offset)

private fun androidx.compose.ui.geometry.Offset.isNear(other: androidx.compose.ui.geometry.Offset, slopPx: Float): Boolean {
    return abs(x - other.x) <= slopPx && abs(y - other.y) <= slopPx
}
