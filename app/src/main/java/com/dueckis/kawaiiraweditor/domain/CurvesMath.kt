package com.dueckis.kawaiiraweditor.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import kotlin.math.sqrt

internal object CurvesMath {
    internal fun buildCurvePath(points: List<CurvePointState>, size: Size): Path {
        if (points.size < 2) return Path()

        val pts = points.sortedBy { it.x }
        val n = pts.size
        val deltas = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            val dx = pts[i + 1].x - pts[i].x
            val dy = pts[i + 1].y - pts[i].y
            deltas[i] =
                if (dx == 0f) {
                    when {
                        dy > 0f -> 1e6f
                        dy < 0f -> -1e6f
                        else -> 0f
                    }
                } else {
                    dy / dx
                }
        }

        val ms = FloatArray(n)
        ms[0] = deltas[0]
        for (i in 1 until n - 1) {
            ms[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f else (deltas[i - 1] + deltas[i]) / 2f
        }
        ms[n - 1] = deltas[n - 2]

        for (i in 0 until n - 1) {
            if (deltas[i] == 0f) {
                ms[i] = 0f
                ms[i + 1] = 0f
            } else {
                val alpha = ms[i] / deltas[i]
                val beta = ms[i + 1] / deltas[i]
                val tau = alpha * alpha + beta * beta
                if (tau > 9f) {
                    val scale = 3f / sqrt(tau)
                    ms[i] = scale * alpha * deltas[i]
                    ms[i + 1] = scale * beta * deltas[i]
                }
            }
        }

        fun map(p: CurvePointState): Offset {
            val x = (p.x / 255f) * size.width
            val y = ((255f - p.y) / 255f) * size.height
            return Offset(x, y)
        }

        fun mapXY(x: Float, y: Float): Offset {
            val px = (x / 255f) * size.width
            val py = ((255f - y) / 255f) * size.height
            return Offset(px, py)
        }

        val path = Path()
        val first = pts.first()
        path.moveTo(map(first).x, map(first).y)
        for (i in 0 until n - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val m0 = ms[i]
            val m1 = ms[i + 1]
            val dx = p1.x - p0.x

            val cp1x = p0.x + dx / 3f
            val cp1y = p0.y + (m0 * dx) / 3f
            val cp2x = p1.x - dx / 3f
            val cp2y = p1.y - (m1 * dx) / 3f

            val cp1 = mapXY(cp1x, cp1y)
            val cp2 = mapXY(cp2x, cp2y)
            val end = map(p1)
            path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
        }
        return path
    }
}
