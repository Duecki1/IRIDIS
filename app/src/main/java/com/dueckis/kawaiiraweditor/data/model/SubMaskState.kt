package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class SubMaskState(
    val id: String,
    val type: String = SubMaskType.Brush.id,
    val visible: Boolean = true,
    val mode: SubMaskMode = SubMaskMode.Additive,
    val lines: List<BrushLineState> = emptyList(),
    val linear: LinearMaskParametersState = LinearMaskParametersState(),
    val radial: RadialMaskParametersState = RadialMaskParametersState(),
    val aiEnvironment: AiEnvironmentMaskParametersState = AiEnvironmentMaskParametersState(),
    val aiSubject: AiSubjectMaskParametersState = AiSubjectMaskParametersState()
)

internal fun SubMaskState.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("type", type)
        put("visible", visible)
        put("mode", mode.name.lowercase(Locale.US))
        put(
            "parameters",
            when (type) {
                SubMaskType.Brush.id -> JSONObject().apply {
                    put(
                        "lines",
                        JSONArray().apply {
                            lines.forEach { line ->
                                put(
                                    JSONObject().apply {
                                        put("tool", line.tool)
                                        put("brushSize", line.brushSize)
                                        put("feather", line.feather)
                                        put("order", line.order)
                                        put(
                                            "points",
                                            JSONArray().apply {
                                                line.points.forEach { point ->
                                                    put(
                                                        JSONObject().apply {
                                                            put("x", point.x)
                                                            put("y", point.y)
                                                            put("pressure", point.pressure)
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }

                SubMaskType.Linear.id -> JSONObject().apply {
                    put("startX", linear.startX)
                    put("startY", linear.startY)
                    put("endX", linear.endX)
                    put("endY", linear.endY)
                    put("range", linear.range)
                }

                SubMaskType.Radial.id -> JSONObject().apply {
                    put("centerX", radial.centerX)
                    put("centerY", radial.centerY)
                    put("radiusX", radial.radiusX)
                    put("radiusY", radial.radiusY)
                    put("rotation", radial.rotation)
                    put("feather", radial.feather)
                }

                SubMaskType.AiSubject.id -> JSONObject().apply {
                    aiSubject.maskDataBase64?.let { put("maskDataBase64", it) }
                    put("softness", aiSubject.softness.coerceIn(0f, 1f))
                    put("feather", aiSubject.feather.coerceIn(-1f, 1f))
                    aiSubject.baseTransform?.let { put("baseTransform", it.toJsonObject()) }
                    aiSubject.baseWidthPx?.takeIf { it > 0 }?.let { put("baseWidthPx", it) }
                    aiSubject.baseHeightPx?.takeIf { it > 0 }?.let { put("baseHeightPx", it) }
                }

                SubMaskType.AiEnvironment.id -> JSONObject().apply {
                    put("category", aiEnvironment.category)
                    aiEnvironment.maskDataBase64?.let { put("maskDataBase64", it) }
                    put("softness", aiEnvironment.softness.coerceIn(0f, 1f))
                    put("feather", aiEnvironment.feather.coerceIn(-1f, 1f))
                    aiEnvironment.baseTransform?.let { put("baseTransform", it.toJsonObject()) }
                    aiEnvironment.baseWidthPx?.takeIf { it > 0 }?.let { put("baseWidthPx", it) }
                    aiEnvironment.baseHeightPx?.takeIf { it > 0 }?.let { put("baseHeightPx", it) }
                }

                else -> JSONObject()
            }
        )
    }
}
