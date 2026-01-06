use serde::Deserialize;
use serde_json::Value;

use super::adjustments::{
    AdjustmentValues,
    ColorGradeSettings,
    ColorGradingValues,
    HslColorValues,
    ToneMapper,
};

fn default_mask_opacity() -> f32 {
    100.0
}

fn default_brush_feather() -> f32 {
    0.5
}

fn default_brush_pressure() -> f32 {
    1.0
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct BrushPointPayload {
    pub x: f32,
    pub y: f32,
    #[serde(default = "default_brush_pressure")]
    pub pressure: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct BrushLinePayload {
    pub tool: String,
    pub brush_size: f32,
    pub points: Vec<BrushPointPayload>,
    #[serde(default = "default_brush_feather")]
    pub feather: f32,
    #[serde(default)]
    pub order: u64,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct BrushMaskParameters {
    pub lines: Vec<BrushLinePayload>,
}

fn default_linear_range() -> f32 {
    0.25
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct RadialMaskParameters {
    pub center_x: f32,
    pub center_y: f32,
    pub radius_x: f32,
    pub radius_y: f32,
    pub rotation: f32,
    pub feather: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct LinearMaskParameters {
    pub start_x: f32,
    pub start_y: f32,
    pub end_x: f32,
    pub end_y: f32,
    #[serde(default = "default_linear_range")]
    pub range: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct AiSubjectMaskParameters {
    pub mask_data_base64: Option<String>,
    pub softness: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct AiEnvironmentMaskParameters {
    pub category: Option<String>,
    pub mask_data_base64: Option<String>,
    pub softness: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct MaskDefinitionPayload {
    pub id: String,
    pub name: String,
    pub visible: bool,
    pub invert: bool,
    #[serde(default = "default_mask_opacity")]
    pub opacity: f32,
    pub adjustments: MaskAdjustmentsPayload,
    #[serde(rename = "subMasks")]
    pub sub_masks: Vec<SubMaskPayload>,
}

#[derive(Clone, Copy, Debug, Default, Deserialize)]
pub struct CurvePointPayload {
    pub x: f32,
    pub y: f32,
}

pub(crate) fn default_curve_points() -> Vec<CurvePointPayload> {
    vec![
        CurvePointPayload { x: 0.0, y: 0.0 },
        CurvePointPayload { x: 255.0, y: 255.0 },
    ]
}

#[derive(Clone, Debug, Deserialize)]
#[serde(default)]
pub struct CurvesPayload {
    pub luma: Vec<CurvePointPayload>,
    pub red: Vec<CurvePointPayload>,
    pub green: Vec<CurvePointPayload>,
    pub blue: Vec<CurvePointPayload>,
}

impl Default for CurvesPayload {
    fn default() -> Self {
        Self {
            luma: default_curve_points(),
            red: default_curve_points(),
            green: default_curve_points(),
            blue: default_curve_points(),
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct HueSatLumPayload {
    pub hue: f32,
    pub saturation: f32,
    pub luminance: f32,
}

#[derive(Clone, Copy, Debug, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct ColorGradingPayload {
    pub shadows: HueSatLumPayload,
    pub midtones: HueSatLumPayload,
    pub highlights: HueSatLumPayload,
    pub blending: f32,
    pub balance: f32,
}

impl Default for ColorGradingPayload {
    fn default() -> Self {
        Self {
            shadows: HueSatLumPayload::default(),
            midtones: HueSatLumPayload::default(),
            highlights: HueSatLumPayload::default(),
            blending: 50.0,
            balance: 0.0,
        }
    }
}

impl ColorGradingPayload {
    pub fn to_values(&self) -> ColorGradingValues {
        ColorGradingValues {
            shadows: ColorGradeSettings {
                hue: self.shadows.hue,
                saturation: self.shadows.saturation,
                luminance: self.shadows.luminance,
            },
            midtones: ColorGradeSettings {
                hue: self.midtones.hue,
                saturation: self.midtones.saturation,
                luminance: self.midtones.luminance,
            },
            highlights: ColorGradeSettings {
                hue: self.highlights.hue,
                saturation: self.highlights.saturation,
                luminance: self.highlights.luminance,
            },
            blending: self.blending,
            balance: self.balance,
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Deserialize)]
#[serde(default)]
pub struct HslPanelPayload {
    pub reds: HueSatLumPayload,
    pub oranges: HueSatLumPayload,
    pub yellows: HueSatLumPayload,
    pub greens: HueSatLumPayload,
    pub aquas: HueSatLumPayload,
    pub blues: HueSatLumPayload,
    pub purples: HueSatLumPayload,
    pub magentas: HueSatLumPayload,
}

impl HslPanelPayload {
    pub fn to_values(&self) -> [HslColorValues; 8] {
        [
            HslColorValues {
                hue: self.reds.hue,
                saturation: self.reds.saturation,
                luminance: self.reds.luminance,
            },
            HslColorValues {
                hue: self.oranges.hue,
                saturation: self.oranges.saturation,
                luminance: self.oranges.luminance,
            },
            HslColorValues {
                hue: self.yellows.hue,
                saturation: self.yellows.saturation,
                luminance: self.yellows.luminance,
            },
            HslColorValues {
                hue: self.greens.hue,
                saturation: self.greens.saturation,
                luminance: self.greens.luminance,
            },
            HslColorValues {
                hue: self.aquas.hue,
                saturation: self.aquas.saturation,
                luminance: self.aquas.luminance,
            },
            HslColorValues {
                hue: self.blues.hue,
                saturation: self.blues.saturation,
                luminance: self.blues.luminance,
            },
            HslColorValues {
                hue: self.purples.hue,
                saturation: self.purples.saturation,
                luminance: self.purples.luminance,
            },
            HslColorValues {
                hue: self.magentas.hue,
                saturation: self.magentas.saturation,
                luminance: self.magentas.luminance,
            },
        ]
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct CropPayload {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct PreviewPayload {
    pub use_zoom: bool,
    pub roi: Option<CropPayload>,
    pub max_dimension: Option<u32>,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct AdjustmentsPayload {
    #[serde(default)]
    pub rotation: f32,
    #[serde(default)]
    pub flip_horizontal: bool,
    #[serde(default)]
    pub flip_vertical: bool,
    #[serde(default)]
    pub orientation_steps: u8,
    #[serde(default)]
    pub crop: Option<CropPayload>,
    pub exposure: f32,
    pub brightness: f32,
    pub contrast: f32,
    pub highlights: f32,
    pub shadows: f32,
    pub whites: f32,
    pub blacks: f32,
    pub saturation: f32,
    pub temperature: f32,
    pub tint: f32,
    pub vibrance: f32,
    pub clarity: f32,
    pub dehaze: f32,
    pub structure: f32,
    pub centre: f32,
    pub vignette_amount: f32,
    pub vignette_midpoint: f32,
    pub vignette_roundness: f32,
    pub vignette_feather: f32,
    pub sharpness: f32,
    pub luma_noise_reduction: f32,
    pub color_noise_reduction: f32,
    pub chromatic_aberration_red_cyan: f32,
    pub chromatic_aberration_blue_yellow: f32,
    #[serde(default)]
    pub tone_mapper: ToneMapper,
    #[serde(default)]
    pub curves: CurvesPayload,
    #[serde(default)]
    pub color_grading: ColorGradingPayload,
    #[serde(default)]
    pub hsl: HslPanelPayload,
    #[serde(default)]
    pub preview: PreviewPayload,
    pub masks: Vec<Value>,
}

impl AdjustmentsPayload {
    pub fn to_values(&self) -> AdjustmentValues {
        AdjustmentValues {
            exposure: self.exposure,
            brightness: self.brightness,
            contrast: self.contrast,
            highlights: self.highlights,
            shadows: self.shadows,
            whites: self.whites,
            blacks: self.blacks,
            saturation: self.saturation,
            temperature: self.temperature,
            tint: self.tint,
            vibrance: self.vibrance,
            clarity: self.clarity,
            dehaze: self.dehaze,
            structure: self.structure,
            centre: self.centre,
            vignette_amount: self.vignette_amount,
            vignette_midpoint: self.vignette_midpoint,
            vignette_roundness: self.vignette_roundness,
            vignette_feather: self.vignette_feather,
            sharpness: self.sharpness,
            luma_noise_reduction: self.luma_noise_reduction,
            color_noise_reduction: self.color_noise_reduction,
            chromatic_aberration_red_cyan: self.chromatic_aberration_red_cyan,
            chromatic_aberration_blue_yellow: self.chromatic_aberration_blue_yellow,
            tone_mapper: self.tone_mapper,
            color_grading: self.color_grading.to_values(),
            hsl: self.hsl.to_values(),
        }
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default)]
pub struct LegacyMaskPayload {
    pub enabled: bool,
    pub exposure: f32,
    pub brightness: f32,
    pub contrast: f32,
    pub highlights: f32,
    pub shadows: f32,
    pub whites: f32,
    pub blacks: f32,
    pub saturation: f32,
    pub temperature: f32,
    pub tint: f32,
    pub vibrance: f32,
}

impl LegacyMaskPayload {
    pub fn to_values(&self) -> AdjustmentValues {
        AdjustmentValues {
            exposure: self.exposure,
            brightness: self.brightness,
            contrast: self.contrast,
            highlights: self.highlights,
            shadows: self.shadows,
            whites: self.whites,
            blacks: self.blacks,
            saturation: self.saturation,
            temperature: self.temperature,
            tint: self.tint,
            vibrance: self.vibrance,
            ..AdjustmentValues::default()
        }
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct MaskAdjustmentsPayload {
    pub exposure: f32,
    pub brightness: f32,
    pub contrast: f32,
    pub highlights: f32,
    pub shadows: f32,
    pub whites: f32,
    pub blacks: f32,
    pub saturation: f32,
    pub temperature: f32,
    pub tint: f32,
    pub vibrance: f32,
    pub clarity: f32,
    pub dehaze: f32,
    pub structure: f32,
    pub centre: f32,
    pub sharpness: f32,
    pub luma_noise_reduction: f32,
    pub color_noise_reduction: f32,
    pub chromatic_aberration_red_cyan: f32,
    pub chromatic_aberration_blue_yellow: f32,
    #[serde(default)]
    pub curves: CurvesPayload,
    #[serde(default)]
    pub color_grading: ColorGradingPayload,
    #[serde(default)]
    pub hsl: HslPanelPayload,
}

impl MaskAdjustmentsPayload {
    pub fn to_values(&self) -> AdjustmentValues {
        AdjustmentValues {
            exposure: self.exposure,
            brightness: self.brightness,
            contrast: self.contrast,
            highlights: self.highlights,
            shadows: self.shadows,
            whites: self.whites,
            blacks: self.blacks,
            saturation: self.saturation,
            temperature: self.temperature,
            tint: self.tint,
            vibrance: self.vibrance,
            clarity: self.clarity,
            dehaze: self.dehaze,
            structure: self.structure,
            centre: self.centre,
            sharpness: self.sharpness,
            luma_noise_reduction: self.luma_noise_reduction,
            color_noise_reduction: self.color_noise_reduction,
            chromatic_aberration_red_cyan: self.chromatic_aberration_red_cyan,
            chromatic_aberration_blue_yellow: self.chromatic_aberration_blue_yellow,
            color_grading: self.color_grading.to_values(),
            hsl: self.hsl.to_values(),
            ..AdjustmentValues::default()
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum SubMaskMode {
    #[default]
    Additive,
    Subtractive,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct SubMaskPayload {
    pub id: String,
    #[serde(rename = "type")]
    pub mask_type: String,
    pub visible: bool,
    pub mode: SubMaskMode,
    pub parameters: Value,
}
