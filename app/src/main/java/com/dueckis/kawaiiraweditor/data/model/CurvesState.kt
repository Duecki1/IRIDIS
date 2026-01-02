package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONArray
import org.json.JSONObject

internal fun defaultCurvePoints(): List<CurvePointState> {
    return listOf(CurvePointState(0f, 0f), CurvePointState(255f, 255f))
}

internal data class CurvesState(
    val luma: List<CurvePointState> = defaultCurvePoints(),
    val red: List<CurvePointState> = defaultCurvePoints(),
    val green: List<CurvePointState> = defaultCurvePoints(),
    val blue: List<CurvePointState> = defaultCurvePoints()
) {
    fun toJsonObject(): JSONObject {
        fun channel(points: List<CurvePointState>): JSONArray {
            return JSONArray().apply {
                points.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("x", p.x)
                            put("y", p.y)
                        }
                    )
                }
            }
        }

        return JSONObject().apply {
            put("luma", channel(luma))
            put("red", channel(red))
            put("green", channel(green))
            put("blue", channel(blue))
        }
    }

    fun isDefault(): Boolean {
        fun isDefaultChannel(points: List<CurvePointState>): Boolean {
            if (points.size != 2) return false
            fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= 0.1f
            return near(points[0].y, 0f) && near(points[1].y, 255f)
        }
        return isDefaultChannel(luma) && isDefaultChannel(red) && isDefaultChannel(green) && isDefaultChannel(blue)
    }
}
