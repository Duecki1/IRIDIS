package com.dueckis.kawaiiraweditor.data.model

import org.json.JSONArray
import org.json.JSONObject

internal data class AdjustmentState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val orientationSteps: Int = 0,
    val aspectRatio: Float? = null,
    val crop: CropState? = null,
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val structure: Float = 0f,
    val centre: Float = 0f,
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val sharpness: Float = 0f,
    val lumaNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val chromaticAberrationRedCyan: Float = 0f,
    val chromaticAberrationBlueYellow: Float = 0f,
    val toneMapper: String = "agx",
    val curves: CurvesState = CurvesState(),
    val colorGrading: ColorGradingState = ColorGradingState(),
    val hsl: HslState = HslState()
) {
    fun toJsonObject(includeToneMapper: Boolean = true): JSONObject {
        return JSONObject().apply {
            if (includeToneMapper) {
                put("rotation", rotation)
                put("flipHorizontal", flipHorizontal)
                put("flipVertical", flipVertical)
                put("orientationSteps", orientationSteps)
                put("aspectRatio", aspectRatio ?: JSONObject.NULL)
                put("crop", crop?.toJsonObject() ?: JSONObject.NULL)
            }
            put("exposure", exposure)
            put("brightness", brightness)
            put("contrast", contrast)
            put("highlights", highlights)
            put("shadows", shadows)
            put("whites", whites)
            put("blacks", blacks)
            put("saturation", saturation)
            put("temperature", temperature)
            put("tint", tint)
            put("vibrance", vibrance)
            put("clarity", clarity)
            put("dehaze", dehaze)
            put("structure", structure)
            put("centre", centre)
            put("vignetteAmount", vignetteAmount)
            put("vignetteMidpoint", vignetteMidpoint)
            put("vignetteRoundness", vignetteRoundness)
            put("vignetteFeather", vignetteFeather)
            put("sharpness", sharpness)
            put("lumaNoiseReduction", lumaNoiseReduction)
            put("colorNoiseReduction", colorNoiseReduction)
            put("chromaticAberrationRedCyan", chromaticAberrationRedCyan)
            put("chromaticAberrationBlueYellow", chromaticAberrationBlueYellow)
            put("curves", curves.toJsonObject())
            put("colorGrading", colorGrading.toJsonObject())
            put("hsl", hsl.toJsonObject())
            if (includeToneMapper) {
                put("toneMapper", toneMapper)
            }
        }
    }

    fun valueFor(field: AdjustmentField): Float {
        return when (field) {
            AdjustmentField.Exposure -> exposure
            AdjustmentField.Brightness -> brightness
            AdjustmentField.Contrast -> contrast
            AdjustmentField.Highlights -> highlights
            AdjustmentField.Shadows -> shadows
            AdjustmentField.Whites -> whites
            AdjustmentField.Blacks -> blacks
            AdjustmentField.Saturation -> saturation
            AdjustmentField.Temperature -> temperature
            AdjustmentField.Tint -> tint
            AdjustmentField.Vibrance -> vibrance
            AdjustmentField.Clarity -> clarity
            AdjustmentField.Dehaze -> dehaze
            AdjustmentField.Structure -> structure
            AdjustmentField.Centre -> centre
            AdjustmentField.VignetteAmount -> vignetteAmount
            AdjustmentField.VignetteMidpoint -> vignetteMidpoint
            AdjustmentField.VignetteRoundness -> vignetteRoundness
            AdjustmentField.VignetteFeather -> vignetteFeather
            AdjustmentField.Sharpness -> sharpness
            AdjustmentField.LumaNoiseReduction -> lumaNoiseReduction
            AdjustmentField.ColorNoiseReduction -> colorNoiseReduction
            AdjustmentField.ChromaticAberrationRedCyan -> chromaticAberrationRedCyan
            AdjustmentField.ChromaticAberrationBlueYellow -> chromaticAberrationBlueYellow
            AdjustmentField.ToneMapper -> 0f
        }
    }

    fun withValue(field: AdjustmentField, value: Float): AdjustmentState {
        return when (field) {
            AdjustmentField.Exposure -> copy(exposure = value)
            AdjustmentField.Brightness -> copy(brightness = value)
            AdjustmentField.Contrast -> copy(contrast = value)
            AdjustmentField.Highlights -> copy(highlights = value)
            AdjustmentField.Shadows -> copy(shadows = value)
            AdjustmentField.Whites -> copy(whites = value)
            AdjustmentField.Blacks -> copy(blacks = value)
            AdjustmentField.Saturation -> copy(saturation = value)
            AdjustmentField.Temperature -> copy(temperature = value)
            AdjustmentField.Tint -> copy(tint = value)
            AdjustmentField.Vibrance -> copy(vibrance = value)
            AdjustmentField.Clarity -> copy(clarity = value)
            AdjustmentField.Dehaze -> copy(dehaze = value)
            AdjustmentField.Structure -> copy(structure = value)
            AdjustmentField.Centre -> copy(centre = value)
            AdjustmentField.VignetteAmount -> copy(vignetteAmount = value)
            AdjustmentField.VignetteMidpoint -> copy(vignetteMidpoint = value)
            AdjustmentField.VignetteRoundness -> copy(vignetteRoundness = value)
            AdjustmentField.VignetteFeather -> copy(vignetteFeather = value)
            AdjustmentField.Sharpness -> copy(sharpness = value)
            AdjustmentField.LumaNoiseReduction -> copy(lumaNoiseReduction = value)
            AdjustmentField.ColorNoiseReduction -> copy(colorNoiseReduction = value)
            AdjustmentField.ChromaticAberrationRedCyan -> copy(chromaticAberrationRedCyan = value)
            AdjustmentField.ChromaticAberrationBlueYellow -> copy(chromaticAberrationBlueYellow = value)
            AdjustmentField.ToneMapper -> this
        }
    }

    fun withToneMapper(mapper: String): AdjustmentState {
        return copy(toneMapper = mapper)
    }

    fun toJson(masks: List<MaskState> = emptyList()): String {
        val payload = toJsonObject(includeToneMapper = true).apply {
            put(
                "masks",
                JSONArray().apply {
                    masks.forEach { put(it.toJsonObject()) }
                }
            )
        }
        return payload.toString()
    }
}
