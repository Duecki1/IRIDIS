//Code taken from RapidRAW by CyberTimon
//https://github.com/CyberTimon/RapidRAW

mod raw_processing;

use anyhow::{Context, Result};
use base64::Engine;
use image::{
    codecs::jpeg::JpegEncoder,
    imageops::FilterType,
    ExtendedColorType,
    ImageBuffer,
    Rgba,
};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jlong, jstring};
use jni::JNIEnv;
use log::error;
#[cfg(target_os = "android")]
use log::Level;
use raw_processing::develop_raw_image;
use rawler::decoders::RawDecodeParams;
use rawler::rawsource::RawSource;
use serde::Deserialize;
use serde_json::json;
use serde_json::from_str;
use serde_json::Value;
use std::borrow::Cow;
use std::collections::HashMap;
use std::ops::AddAssign;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, Once, OnceLock};

#[derive(Clone, Copy, Default)]
struct AdjustmentValues {
    exposure: f32,
    brightness: f32,
    contrast: f32,
    highlights: f32,
    shadows: f32,
    whites: f32,
    blacks: f32,
    saturation: f32,
    temperature: f32,
    tint: f32,
    vibrance: f32,
    clarity: f32,
    dehaze: f32,
    structure: f32,
    centre: f32,
    vignette_amount: f32,
    vignette_midpoint: f32,
    vignette_roundness: f32,
    vignette_feather: f32,
    sharpness: f32,
    luma_noise_reduction: f32,
    color_noise_reduction: f32,
    chromatic_aberration_red_cyan: f32,
    chromatic_aberration_blue_yellow: f32,
    tone_mapper: ToneMapper,
    color_grading: ColorGradingValues,
    hsl: [HslColorValues; 8],
}

#[derive(Clone, Copy, Default)]
struct HslColorValues {
    hue: f32,
    saturation: f32,
    luminance: f32,
}

#[derive(Clone, Copy, Default)]
struct ColorGradeSettings {
    hue: f32,
    saturation: f32,
    luminance: f32,
}

#[derive(Clone, Copy, Default)]
struct ColorGradingValues {
    shadows: ColorGradeSettings,
    midtones: ColorGradeSettings,
    highlights: ColorGradeSettings,
    blending: f32,
    balance: f32,
}

impl ColorGradingValues {
    fn normalized(self) -> Self {
        Self {
            shadows: ColorGradeSettings {
                hue: self.shadows.hue,
                saturation: self.shadows.saturation / 500.0,
                luminance: self.shadows.luminance / 500.0,
            },
            midtones: ColorGradeSettings {
                hue: self.midtones.hue,
                saturation: self.midtones.saturation / 500.0,
                luminance: self.midtones.luminance / 500.0,
            },
            highlights: ColorGradeSettings {
                hue: self.highlights.hue,
                saturation: self.highlights.saturation / 500.0,
                luminance: self.highlights.luminance / 500.0,
            },
            blending: self.blending / 100.0,
            balance: self.balance / 200.0,
        }
    }
}

#[derive(Clone, Copy, Default, Debug, Deserialize)]
#[serde(rename_all = "lowercase")]
enum ToneMapper {
    #[default]
    Basic,
    Agx,
}

struct AdjustmentScales {
    exposure: f32,
    brightness: f32,
    contrast: f32,
    highlights: f32,
    shadows: f32,
    whites: f32,
    blacks: f32,
    saturation: f32,
    temperature: f32,
    tint: f32,
    vibrance: f32,
    clarity: f32,
    dehaze: f32,
    structure: f32,
    centre: f32,
    vignette_amount: f32,
    vignette_midpoint: f32,
    vignette_roundness: f32,
    vignette_feather: f32,
    sharpness: f32,
    luma_noise_reduction: f32,
    color_noise_reduction: f32,
    chromatic_aberration_red_cyan: f32,
    chromatic_aberration_blue_yellow: f32,
    hsl_hue_multiplier: f32,
    hsl_saturation: f32,
    hsl_luminance: f32,
}

const ADJUSTMENT_SCALES: AdjustmentScales = AdjustmentScales {
    exposure: 0.8,
    brightness: 0.8,
    contrast: 100.0,
    highlights: 150.0,
    shadows: 100.0,
    whites: 30.0,
    blacks: 60.0,
    saturation: 100.0,
    temperature: 25.0,
    tint: 100.0,
    vibrance: 100.0,
    clarity: 200.0,
    dehaze: 750.0,
    structure: 200.0,
    centre: 250.0,  // RapidRAW uses 250.0
    vignette_amount: 100.0,
    vignette_midpoint: 100.0,
    vignette_roundness: 100.0,
    vignette_feather: 100.0,
    sharpness: 80.0,
    luma_noise_reduction: 100.0,
    color_noise_reduction: 100.0,
    chromatic_aberration_red_cyan: 10000.0,  // RapidRAW uses 10000.0
    chromatic_aberration_blue_yellow: 10000.0,  // RapidRAW uses 10000.0
    hsl_hue_multiplier: 0.3,
    hsl_saturation: 100.0,
    hsl_luminance: 100.0,
};

impl AdjustmentValues {
    fn normalized(self, scales: &AdjustmentScales) -> Self {
        fn scale(value: f32, divisor: f32) -> f32 {
            if divisor.abs() < std::f32::EPSILON {
                value
            } else {
                value / divisor
            }
        }

        let mut hsl = self.hsl;
        for color in hsl.iter_mut() {
            color.hue *= scales.hsl_hue_multiplier;
            color.saturation = scale(color.saturation, scales.hsl_saturation);
            color.luminance = scale(color.luminance, scales.hsl_luminance);
        }

        AdjustmentValues {
            exposure: scale(self.exposure, scales.exposure),
            brightness: scale(self.brightness, scales.brightness),
            contrast: scale(self.contrast, scales.contrast),
            highlights: scale(self.highlights, scales.highlights),
            shadows: scale(self.shadows, scales.shadows),
            whites: scale(self.whites, scales.whites),
            blacks: scale(self.blacks, scales.blacks),
            saturation: scale(self.saturation, scales.saturation),
            temperature: scale(self.temperature, scales.temperature),
            tint: scale(self.tint, scales.tint),
            vibrance: scale(self.vibrance, scales.vibrance),
            clarity: scale(self.clarity, scales.clarity),
            dehaze: scale(self.dehaze, scales.dehaze),
            structure: scale(self.structure, scales.structure),
            centre: scale(self.centre, scales.centre),
            vignette_amount: scale(self.vignette_amount, scales.vignette_amount),
            vignette_midpoint: scale(self.vignette_midpoint, scales.vignette_midpoint),
            vignette_roundness: scale(self.vignette_roundness, scales.vignette_roundness),
            vignette_feather: scale(self.vignette_feather, scales.vignette_feather),
            sharpness: scale(self.sharpness, scales.sharpness),
            luma_noise_reduction: scale(self.luma_noise_reduction, scales.luma_noise_reduction),
            color_noise_reduction: scale(self.color_noise_reduction, scales.color_noise_reduction),
            chromatic_aberration_red_cyan: scale(self.chromatic_aberration_red_cyan, scales.chromatic_aberration_red_cyan),
            chromatic_aberration_blue_yellow: scale(self.chromatic_aberration_blue_yellow, scales.chromatic_aberration_blue_yellow),
            tone_mapper: self.tone_mapper,
            color_grading: self.color_grading.normalized(),
            hsl,
        }
    }
}

impl AddAssign for AdjustmentValues {
    fn add_assign(&mut self, rhs: Self) {
        self.exposure += rhs.exposure;
        self.brightness += rhs.brightness;
        self.contrast += rhs.contrast;
        self.highlights += rhs.highlights;
        self.shadows += rhs.shadows;
        self.whites += rhs.whites;
        self.blacks += rhs.blacks;
        self.saturation += rhs.saturation;
        self.temperature += rhs.temperature;
        self.tint += rhs.tint;
        self.vibrance += rhs.vibrance;
        self.clarity += rhs.clarity;
        self.dehaze += rhs.dehaze;
        self.structure += rhs.structure;
        self.centre += rhs.centre;
        self.vignette_amount += rhs.vignette_amount;
        self.vignette_midpoint += rhs.vignette_midpoint;
        self.vignette_roundness += rhs.vignette_roundness;
        self.vignette_feather += rhs.vignette_feather;
        self.sharpness += rhs.sharpness;
        self.luma_noise_reduction += rhs.luma_noise_reduction;
        self.color_noise_reduction += rhs.color_noise_reduction;
        self.chromatic_aberration_red_cyan += rhs.chromatic_aberration_red_cyan;
        self.chromatic_aberration_blue_yellow += rhs.chromatic_aberration_blue_yellow;
        self.color_grading.shadows.hue += rhs.color_grading.shadows.hue;
        self.color_grading.shadows.saturation += rhs.color_grading.shadows.saturation;
        self.color_grading.shadows.luminance += rhs.color_grading.shadows.luminance;
        self.color_grading.midtones.hue += rhs.color_grading.midtones.hue;
        self.color_grading.midtones.saturation += rhs.color_grading.midtones.saturation;
        self.color_grading.midtones.luminance += rhs.color_grading.midtones.luminance;
        self.color_grading.highlights.hue += rhs.color_grading.highlights.hue;
        self.color_grading.highlights.saturation += rhs.color_grading.highlights.saturation;
        self.color_grading.highlights.luminance += rhs.color_grading.highlights.luminance;
        self.color_grading.blending += rhs.color_grading.blending;
        self.color_grading.balance += rhs.color_grading.balance;
        for i in 0..8 {
            self.hsl[i].hue += rhs.hsl[i].hue;
            self.hsl[i].saturation += rhs.hsl[i].saturation;
            self.hsl[i].luminance += rhs.hsl[i].luminance;
        }
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default)]
struct LegacyMaskPayload {
    enabled: bool,
    exposure: f32,
    brightness: f32,
    contrast: f32,
    highlights: f32,
    shadows: f32,
    whites: f32,
    blacks: f32,
    saturation: f32,
    temperature: f32,
    tint: f32,
    vibrance: f32,
}

impl LegacyMaskPayload {
    fn to_values(&self) -> AdjustmentValues {
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
            ..Default::default()
        }
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct MaskAdjustmentsPayload {
    exposure: f32,
    brightness: f32,
    contrast: f32,
    highlights: f32,
    shadows: f32,
    whites: f32,
    blacks: f32,
    saturation: f32,
    temperature: f32,
    tint: f32,
    vibrance: f32,
    clarity: f32,
    dehaze: f32,
    structure: f32,
    centre: f32,
    sharpness: f32,
    luma_noise_reduction: f32,
    color_noise_reduction: f32,
    chromatic_aberration_red_cyan: f32,
    chromatic_aberration_blue_yellow: f32,
    #[serde(default)]
    curves: CurvesPayload,
    #[serde(default)]
    color_grading: ColorGradingPayload,
    #[serde(default)]
    hsl: HslPanelPayload,
}

impl MaskAdjustmentsPayload {
    fn to_values(&self) -> AdjustmentValues {
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
            ..Default::default()
        }
    }
}

#[derive(Clone, Copy, Default, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
enum SubMaskMode {
    #[default]
    Additive,
    Subtractive,
}

fn default_mask_opacity() -> f32 {
    100.0
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct SubMaskPayload {
    id: String,
    #[serde(rename = "type")]
    mask_type: String,
    visible: bool,
    mode: SubMaskMode,
    parameters: serde_json::Value,
}

fn default_brush_feather() -> f32 {
    0.5
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct BrushPointPayload {
    x: f32,
    y: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct BrushLinePayload {
    tool: String,
    brush_size: f32,
    points: Vec<BrushPointPayload>,
    #[serde(default = "default_brush_feather")]
    feather: f32,
    #[serde(default)]
    order: u64,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct BrushMaskParameters {
    lines: Vec<BrushLinePayload>,
}

fn default_linear_range() -> f32 {
    0.25
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct RadialMaskParameters {
    center_x: f32,
    center_y: f32,
    radius_x: f32,
    radius_y: f32,
    rotation: f32,
    feather: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct LinearMaskParameters {
    start_x: f32,
    start_y: f32,
    end_x: f32,
    end_y: f32,
    #[serde(default = "default_linear_range")]
    range: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct AiSubjectMaskParameters {
    mask_data_base64: Option<String>,
    softness: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct MaskDefinitionPayload {
    id: String,
    name: String,
    visible: bool,
    invert: bool,
    #[serde(default = "default_mask_opacity")]
    opacity: f32,
    adjustments: MaskAdjustmentsPayload,
    #[serde(rename = "subMasks")]
    sub_masks: Vec<SubMaskPayload>,
}

#[derive(Clone, Copy, Debug, Default, Deserialize)]
struct CurvePointPayload {
    x: f32,
    y: f32,
}

fn default_curve_points() -> Vec<CurvePointPayload> {
    vec![
        CurvePointPayload { x: 0.0, y: 0.0 },
        CurvePointPayload {
            x: 255.0,
            y: 255.0,
        },
    ]
}

#[derive(Clone, Debug, Deserialize)]
#[serde(default)]
struct CurvesPayload {
    luma: Vec<CurvePointPayload>,
    red: Vec<CurvePointPayload>,
    green: Vec<CurvePointPayload>,
    blue: Vec<CurvePointPayload>,
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
struct HueSatLumPayload {
    hue: f32,
    saturation: f32,
    luminance: f32,
}

#[derive(Clone, Copy, Debug, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct ColorGradingPayload {
    shadows: HueSatLumPayload,
    midtones: HueSatLumPayload,
    highlights: HueSatLumPayload,
    blending: f32,
    balance: f32,
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
    fn to_values(&self) -> ColorGradingValues {
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
struct HslPanelPayload {
    reds: HueSatLumPayload,
    oranges: HueSatLumPayload,
    yellows: HueSatLumPayload,
    greens: HueSatLumPayload,
    aquas: HueSatLumPayload,
    blues: HueSatLumPayload,
    purples: HueSatLumPayload,
    magentas: HueSatLumPayload,
}

impl HslPanelPayload {
    fn to_values(&self) -> [HslColorValues; 8] {
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
struct CropPayload {
    x: f32,
    y: f32,
    width: f32,
    height: f32,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct AdjustmentsPayload {
    #[serde(default)]
    rotation: f32,
    #[serde(default)]
    flip_horizontal: bool,
    #[serde(default)]
    flip_vertical: bool,
    #[serde(default)]
    orientation_steps: u8,
    #[serde(default)]
    crop: Option<CropPayload>,
    exposure: f32,
    brightness: f32,
    contrast: f32,
    highlights: f32,
    shadows: f32,
    whites: f32,
    blacks: f32,
    saturation: f32,
    temperature: f32,
    tint: f32,
    vibrance: f32,
    clarity: f32,
    dehaze: f32,
    structure: f32,
    centre: f32,
    vignette_amount: f32,
    vignette_midpoint: f32,
    vignette_roundness: f32,
    vignette_feather: f32,
    sharpness: f32,
    luma_noise_reduction: f32,
    color_noise_reduction: f32,
    chromatic_aberration_red_cyan: f32,
    chromatic_aberration_blue_yellow: f32,
    #[serde(default)]
    tone_mapper: ToneMapper,
    #[serde(default)]
    curves: CurvesPayload,
    #[serde(default)]
    color_grading: ColorGradingPayload,
    #[serde(default)]
    hsl: HslPanelPayload,
    masks: Vec<Value>,
}

impl AdjustmentsPayload {
    fn to_values(&self) -> AdjustmentValues {
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

#[derive(Clone)]
struct CurvePoint {
    x: f32,
    y: f32,
}

#[derive(Clone)]
struct CurveSegment {
    p1: CurvePoint,
    p2: CurvePoint,
    m1: f32,
    m2: f32,
}

#[derive(Clone)]
struct CurveRuntime {
    points: Vec<CurvePoint>,
    segments: Vec<CurveSegment>,
}

impl CurveRuntime {
    fn from_payload(points: &[CurvePointPayload]) -> Self {
        let mut pts: Vec<CurvePoint> = points
            .iter()
            .map(|p| CurvePoint { x: p.x, y: p.y })
            .collect();

        if pts.len() < 2 {
            pts = default_curve_points()
                .into_iter()
                .map(|p| CurvePoint { x: p.x, y: p.y })
                .collect();
        }

        pts.sort_by(|a, b| a.x.partial_cmp(&b.x).unwrap_or(std::cmp::Ordering::Equal));
        pts.truncate(16);

        let mut segments = Vec::with_capacity(pts.len().saturating_sub(1));
        for i in 0..pts.len().saturating_sub(1) {
            let p1 = pts[i].clone();
            let p2 = pts[i + 1].clone();
            let p0 = pts[i.saturating_sub(1)].clone();
            let p3 = pts[(i + 2).min(pts.len() - 1)].clone();

            let delta_before = (p1.y - p0.y) / (p1.x - p0.x).max(0.001);
            let delta_current = (p2.y - p1.y) / (p2.x - p1.x).max(0.001);
            let delta_after = (p3.y - p2.y) / (p3.x - p2.x).max(0.001);

            let mut tangent_at_p1 = if i == 0 {
                delta_current
            } else if delta_before * delta_current <= 0.0 {
                0.0
            } else {
                (delta_before + delta_current) / 2.0
            };

            let mut tangent_at_p2 = if i + 1 == pts.len() - 1 {
                delta_current
            } else if delta_current * delta_after <= 0.0 {
                0.0
            } else {
                (delta_current + delta_after) / 2.0
            };

            if delta_current != 0.0 {
                let alpha = tangent_at_p1 / delta_current;
                let beta = tangent_at_p2 / delta_current;
                if alpha * alpha + beta * beta > 9.0 {
                    let tau = 3.0 / (alpha * alpha + beta * beta).sqrt();
                    tangent_at_p1 *= tau;
                    tangent_at_p2 *= tau;
                }
            }

            segments.push(CurveSegment {
                p1,
                p2,
                m1: tangent_at_p1,
                m2: tangent_at_p2,
            });
        }

        Self {
            points: pts,
            segments,
        }
    }

    fn is_default(&self) -> bool {
        if self.points.len() != 2 {
            return false;
        }
        let p0 = &self.points[0];
        let p1 = &self.points[1];
        (p0.y - 0.0).abs() < 0.1 && (p1.y - 255.0).abs() < 0.1
    }

    fn eval(&self, val: f32) -> f32 {
        if self.points.len() < 2 {
            return val;
        }

        let x = val * 255.0;
        let first = &self.points[0];
        let last = &self.points[self.points.len() - 1];
        if x <= first.x {
            return (first.y / 255.0).clamp(0.0, 1.0);
        }
        if x >= last.x {
            return (last.y / 255.0).clamp(0.0, 1.0);
        }

        for seg in &self.segments {
            if x <= seg.p2.x {
                let dx = seg.p2.x - seg.p1.x;
                if dx <= 0.0 {
                    return (seg.p1.y / 255.0).clamp(0.0, 1.0);
                }
                let t = (x - seg.p1.x) / dx;
                let t2 = t * t;
                let t3 = t2 * t;
                let h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
                let h10 = t3 - 2.0 * t2 + t;
                let h01 = -2.0 * t3 + 3.0 * t2;
                let h11 = t3 - t2;
                let result_y = h00 * seg.p1.y
                    + h10 * seg.m1 * dx
                    + h01 * seg.p2.y
                    + h11 * seg.m2 * dx;
                return (result_y / 255.0).clamp(0.0, 1.0);
            }
        }

        (last.y / 255.0).clamp(0.0, 1.0)
    }
}

#[derive(Clone)]
struct CurvesRuntime {
    luma: CurveRuntime,
    red: CurveRuntime,
    green: CurveRuntime,
    blue: CurveRuntime,
    rgb_curves_are_active: bool,
}

impl CurvesRuntime {
    fn from_payload(payload: &CurvesPayload) -> Self {
        let red = CurveRuntime::from_payload(&payload.red);
        let green = CurveRuntime::from_payload(&payload.green);
        let blue = CurveRuntime::from_payload(&payload.blue);
        let rgb_curves_are_active = !red.is_default() || !green.is_default() || !blue.is_default();
        Self {
            luma: CurveRuntime::from_payload(&payload.luma),
            red,
            green,
            blue,
            rgb_curves_are_active,
        }
    }

    fn apply_all(&self, color: [f32; 3]) -> [f32; 3] {
        if self.rgb_curves_are_active {
            let color_graded = [
                self.red.eval(color[0]),
                self.green.eval(color[1]),
                self.blue.eval(color[2]),
            ];
            let luma_initial = get_luma(color);
            let luma_target = self.luma.eval(luma_initial);
            let luma_graded = get_luma(color_graded);
            let mut final_color = if luma_graded > 0.001 {
                let scale = luma_target / luma_graded;
                [color_graded[0] * scale, color_graded[1] * scale, color_graded[2] * scale]
            } else {
                [luma_target, luma_target, luma_target]
            };
            let max_comp = final_color[0].max(final_color[1]).max(final_color[2]);
            if max_comp > 1.0 {
                final_color = [
                    final_color[0] / max_comp,
                    final_color[1] / max_comp,
                    final_color[2] / max_comp,
                ];
            }
            final_color
        } else {
            [
                self.luma.eval(color[0]),
                self.luma.eval(color[1]),
                self.luma.eval(color[2]),
            ]
        }
    }

    fn is_default(&self) -> bool {
        self.luma.is_default() && !self.rgb_curves_are_active
    }
}

fn clamp_to_u8(value: f32) -> u8 {
    if !value.is_finite() {
        0
    } else {
        value.clamp(0.0, 255.0).round() as u8
    }
}

fn smoothstep(edge0: f32, edge1: f32, x: f32) -> f32 {
    if (edge1 - edge0).abs() < std::f32::EPSILON {
        return 0.0;
    }
    let t = ((x - edge0) / (edge1 - edge0)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

fn parse_adjustments_payload(json: Option<&str>) -> AdjustmentsPayload {
    if let Some(data) = json {
        let trimmed = data.trim();
        if trimmed.is_empty() {
            return AdjustmentsPayload::default();
        }
        match from_str::<AdjustmentsPayload>(trimmed) {
            Ok(payload) => payload,
            Err(err) => {
                error!("Failed to parse adjustments JSON: {}", err);
                AdjustmentsPayload::default()
            }
        }
    } else {
        AdjustmentsPayload::default()
    }
}

struct MaskRuntime {
    opacity_factor: f32,
    invert: bool,
    adjustments: AdjustmentValues,
    curves: CurvesRuntime,
    curves_are_active: bool,
    bitmap: Option<Vec<u8>>,
}

fn parse_masks(values: Vec<Value>, width: u32, height: u32) -> Vec<MaskRuntime> {
    values
        .into_iter()
        .filter_map(|value| {
            // RapidRAW-style mask definitions include `subMasks` and `visible`.
            if value.get("subMasks").is_some() {
                let def: MaskDefinitionPayload = serde_json::from_value(value).ok()?;
                if !def.visible {
                    return None;
                }
                let opacity_factor = (def.opacity / 100.0).clamp(0.0, 1.0);
                let curves = CurvesRuntime::from_payload(&def.adjustments.curves);
                let curves_are_active = !curves.is_default();
                let bitmap = Some(generate_mask_bitmap(&def.sub_masks, width, height));
                return Some(MaskRuntime {
                    opacity_factor,
                    invert: def.invert,
                    adjustments: def.adjustments.to_values().normalized(&ADJUSTMENT_SCALES),
                    curves,
                    curves_are_active,
                    bitmap,
                });
            }

            // Backwards-compatible legacy format (top-level enabled + adjustment fields).
            if value.get("enabled").is_some() {
                let legacy: LegacyMaskPayload = serde_json::from_value(value).ok()?;
                if !legacy.enabled {
                    return None;
                }
                return Some(MaskRuntime {
                    opacity_factor: 1.0,
                    invert: false,
                    adjustments: legacy.to_values().normalized(&ADJUSTMENT_SCALES),
                    curves: CurvesRuntime::from_payload(&CurvesPayload::default()),
                    curves_are_active: false,
                    bitmap: None,
                });
            }

            None
        })
        .collect()
}

fn apply_feathered_circle_add(target: &mut [u8], width: u32, height: u32, cx: f32, cy: f32, radius: f32, feather: f32) {
    if radius <= 0.5 {
        return;
    }
    let feather_amount = feather.clamp(0.0, 1.0);
    let inner_radius = radius * (1.0 - feather_amount);
    let outer_radius = radius;
    let outer_sq = outer_radius * outer_radius;

    let x0 = (cx - outer_radius).floor().max(0.0) as i32;
    let y0 = (cy - outer_radius).floor().max(0.0) as i32;
    let x1 = (cx + outer_radius).ceil().min((width - 1) as f32) as i32;
    let y1 = (cy + outer_radius).ceil().min((height - 1) as f32) as i32;

    for y in y0..=y1 {
        for x in x0..=x1 {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;
            let dist_sq = dx * dx + dy * dy;
            if dist_sq > outer_sq {
                continue;
            }

            let dist = dist_sq.sqrt();
            let intensity = if dist <= inner_radius {
                1.0
            } else if outer_radius > inner_radius {
                1.0 - ((dist - inner_radius) / (outer_radius - inner_radius)).clamp(0.0, 1.0)
            } else {
                0.0
            };

            if intensity <= 0.0 {
                continue;
            }

            let idx = (y as u32 * width + x as u32) as usize;
            let current = target[idx] as f32 / 255.0;
            let next = 1.0 - (1.0 - current) * (1.0 - intensity);
            target[idx] = (next.clamp(0.0, 1.0) * 255.0).round() as u8;
        }
    }
}

fn apply_feathered_circle_sub(target: &mut [u8], width: u32, height: u32, cx: f32, cy: f32, radius: f32, feather: f32) {
    if radius <= 0.5 {
        return;
    }
    let feather_amount = feather.clamp(0.0, 1.0);
    let inner_radius = radius * (1.0 - feather_amount);
    let outer_radius = radius;
    let outer_sq = outer_radius * outer_radius;

    let x0 = (cx - outer_radius).floor().max(0.0) as i32;
    let y0 = (cy - outer_radius).floor().max(0.0) as i32;
    let x1 = (cx + outer_radius).ceil().min((width - 1) as f32) as i32;
    let y1 = (cy + outer_radius).ceil().min((height - 1) as f32) as i32;

    for y in y0..=y1 {
        for x in x0..=x1 {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;
            let dist_sq = dx * dx + dy * dy;
            if dist_sq > outer_sq {
                continue;
            }

            let dist = dist_sq.sqrt();
            let intensity = if dist <= inner_radius {
                1.0
            } else if outer_radius > inner_radius {
                1.0 - ((dist - inner_radius) / (outer_radius - inner_radius)).clamp(0.0, 1.0)
            } else {
                0.0
            };

            if intensity <= 0.0 {
                continue;
            }

            let idx = (y as u32 * width + x as u32) as usize;
            let current = target[idx] as f32 / 255.0;
            let next = (current * (1.0 - intensity)).clamp(0.0, 1.0);
            target[idx] = (next * 255.0).round() as u8;
        }
    }
}

#[derive(Clone)]
struct BrushEvent {
    order: u64,
    mode: SubMaskMode,
    feather: f32,
    radius: f32,
    points: Vec<(f32, f32)>,
}

fn apply_brush_submask(target: &mut [u8], sub_mask: &SubMaskPayload, width: u32, height: u32) {
    if sub_mask.mask_type != "brush" || !sub_mask.visible {
        return;
    }
    if target.len() != (width * height) as usize {
        return;
    }

    let w_f = width as f32;
    let h_f = height as f32;
    let base_dim = width.min(height) as f32;

    fn denorm(value: f32, max: f32) -> f32 {
        let max_coord = (max - 1.0).max(1.0);
        if value <= 1.5 {
            (value * max_coord).clamp(0.0, max_coord)
        } else {
            value
        }
    }

    let params: BrushMaskParameters = serde_json::from_value(sub_mask.parameters.clone()).unwrap_or_default();
    let mut events: Vec<BrushEvent> = Vec::new();

    for line in params.lines {
        if line.points.is_empty() {
            continue;
        }

        let effective_mode = if line.tool == "eraser" {
            SubMaskMode::Subtractive
        } else {
            sub_mask.mode
        };

        let brush_size_px = if line.brush_size <= 1.5 {
            (line.brush_size * base_dim).max(0.0)
        } else {
            line.brush_size
        };
        let radius = (brush_size_px / 2.0).max(1.0);
        let feather = line.feather.clamp(0.0, 1.0);

        let points: Vec<(f32, f32)> = line
            .points
            .into_iter()
            .map(|p| (denorm(p.x, w_f), denorm(p.y, h_f)))
            .collect();

        events.push(BrushEvent {
            order: line.order,
            mode: effective_mode,
            feather,
            radius,
            points,
        });
    }

    events.sort_by_key(|e| e.order);

    for event in events {
        let inner = event.radius * (1.0 - event.feather);
        let mut step_size = (inner / 3.0).max(0.75);
        step_size = step_size.min(event.radius * 0.25).max(0.75);

        let apply_circle = |mask: &mut [u8], cx: f32, cy: f32| {
            if event.mode == SubMaskMode::Additive {
                apply_feathered_circle_add(mask, width, height, cx, cy, event.radius, event.feather);
            } else {
                apply_feathered_circle_sub(mask, width, height, cx, cy, event.radius, event.feather);
            }
        };

        if event.points.len() == 1 {
            let (x, y) = event.points[0];
            apply_circle(target, x, y);
            continue;
        }

        for pair in event.points.windows(2) {
            let (x1, y1) = pair[0];
            let (x2, y2) = pair[1];
            let dx = x2 - x1;
            let dy = y2 - y1;
            let dist = (dx * dx + dy * dy).sqrt().max(0.001);
            let steps = (dist / step_size).ceil() as i32;
            for i in 0..=steps {
                let t = i as f32 / steps.max(1) as f32;
                apply_circle(target, x1 + dx * t, y1 + dy * t);
            }
        }
    }
}

fn apply_submask_bitmap(target: &mut [u8], sub_bitmap: &[u8], mode: SubMaskMode) {
    if target.len() != sub_bitmap.len() {
        return;
    }
    match mode {
        SubMaskMode::Additive => {
            for (dst, src) in target.iter_mut().zip(sub_bitmap.iter()) {
                *dst = (*dst).max(*src);
            }
        }
        SubMaskMode::Subtractive => {
            for (dst, src) in target.iter_mut().zip(sub_bitmap.iter()) {
                let current = *dst as f32 / 255.0;
                let intensity = *src as f32 / 255.0;
                let next = (current * (1.0 - intensity)).clamp(0.0, 1.0);
                *dst = (next * 255.0).round() as u8;
            }
        }
    }
}

fn generate_radial_mask(params_value: &Value, width: u32, height: u32) -> Vec<u8> {
    let params: RadialMaskParameters = serde_json::from_value(params_value.clone()).unwrap_or_default();
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];

    let w_f = width as f32;
    let h_f = height as f32;
    let base_dim = width.min(height) as f32;

    fn denorm(value: f32, max: f32) -> f32 {
        let max_coord = (max - 1.0).max(1.0);
        if value <= 1.5 {
            (value * max_coord).clamp(0.0, max_coord)
        } else {
            value
        }
    }

    fn denorm_len(value: f32, base_dim: f32) -> f32 {
        if value <= 1.5 {
            (value * base_dim).max(0.0)
        } else {
            value
        }
    }

    let cx = denorm(params.center_x, w_f);
    let cy = denorm(params.center_y, h_f);
    let rx = denorm_len(params.radius_x, base_dim).max(0.01);
    let ry = denorm_len(params.radius_y, base_dim).max(0.01);
    let feather = params.feather.clamp(0.0, 1.0);
    let inner_bound = 1.0 - feather;

    let rotation = params.rotation.to_radians();
    let cos_rot = rotation.cos();
    let sin_rot = rotation.sin();

    for y in 0..height {
        for x in 0..width {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;

            let rot_dx = dx * cos_rot + dy * sin_rot;
            let rot_dy = -dx * sin_rot + dy * cos_rot;

            let norm_x = rot_dx / rx;
            let norm_y = rot_dy / ry;
            let dist = (norm_x * norm_x + norm_y * norm_y).sqrt();

            let intensity = if dist <= inner_bound {
                1.0
            } else {
                1.0 - (dist - inner_bound) / (1.0 - inner_bound).max(0.01)
            };

            let idx = (y * width + x) as usize;
            mask[idx] = (intensity.clamp(0.0, 1.0) * 255.0).round() as u8;
        }
    }

    mask
}

fn generate_linear_mask(params_value: &Value, width: u32, height: u32) -> Vec<u8> {
    let params: LinearMaskParameters = serde_json::from_value(params_value.clone()).unwrap_or_default();
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];

    let w_f = width as f32;
    let h_f = height as f32;
    let base_dim = width.min(height) as f32;

    fn denorm(value: f32, max: f32) -> f32 {
        let max_coord = (max - 1.0).max(1.0);
        if value <= 1.5 {
            (value * max_coord).clamp(0.0, max_coord)
        } else {
            value
        }
    }

    fn denorm_len(value: f32, base_dim: f32) -> f32 {
        if value <= 1.5 {
            (value * base_dim).max(0.0)
        } else {
            value
        }
    }

    let start_x = denorm(params.start_x, w_f);
    let start_y = denorm(params.start_y, h_f);
    let end_x = denorm(params.end_x, w_f);
    let end_y = denorm(params.end_y, h_f);
    let range = denorm_len(params.range, base_dim).max(0.01);

    let line_vec_x = end_x - start_x;
    let line_vec_y = end_y - start_y;
    let len_sq = line_vec_x * line_vec_x + line_vec_y * line_vec_y;
    if len_sq < 0.01 {
        return mask;
    }
    let inv_len = 1.0 / len_sq.sqrt();
    let perp_vec_x = -line_vec_y * inv_len;
    let perp_vec_y = line_vec_x * inv_len;

    for y in 0..height {
        for x in 0..width {
            let pixel_vec_x = x as f32 - start_x;
            let pixel_vec_y = y as f32 - start_y;
            let dist_perp = pixel_vec_x * perp_vec_x + pixel_vec_y * perp_vec_y;
            let t = dist_perp / range;
            let intensity = (0.5 - t * 0.5).clamp(0.0, 1.0);
            let idx = (y * width + x) as usize;
            mask[idx] = (intensity * 255.0).round() as u8;
        }
    }

    mask
}

fn decode_data_url_base64(data_url: &str) -> Option<Vec<u8>> {
    let idx = data_url.find("base64,")?;
    let b64 = &data_url[(idx + "base64,".len())..];
    base64::engine::general_purpose::STANDARD.decode(b64).ok()
}

fn generate_ai_subject_mask(params_value: &Value, width: u32, height: u32) -> Option<Vec<u8>> {
    let params: AiSubjectMaskParameters = serde_json::from_value(params_value.clone()).ok()?;
    let data_url = params.mask_data_base64?;
    let bytes = decode_data_url_base64(&data_url)?;

    let decoded = image::load_from_memory(&bytes).ok()?;
    let gray = decoded.to_luma8();
    let resized = if gray.width() == width && gray.height() == height {
        gray
    } else {
        image::imageops::resize(&gray, width, height, FilterType::Triangle)
    };

    let mut raw = resized.into_raw();

    let softness = params.softness.clamp(0.0, 1.0);
    let radius = (softness * 10.0).round() as i32;
    if radius >= 1 {
        raw = box_blur_u8(&raw, width as usize, height as usize, radius as usize);
    }

    Some(raw)
}

fn box_blur_u8(src: &[u8], width: usize, height: usize, radius: usize) -> Vec<u8> {
    if radius == 0 || width == 0 || height == 0 {
        return src.to_vec();
    }
    let w = width;
    let h = height;
    let r = radius;
    let mut tmp = vec![0u8; w * h];
    let mut dst = vec![0u8; w * h];

    // Horizontal pass
    for y in 0..h {
        let row = y * w;
        let denom = (2 * r + 1) as i32;
        let mut sum: i32 = 0;

        // x = 0 window: replicate edge pixels.
        sum += src[row] as i32 * (r as i32 + 1);
        let max_ix = r.min(w - 1);
        for ix in 1..=max_ix {
            sum += src[row + ix] as i32;
        }
        let repeats = r.saturating_sub(max_ix) as i32;
        if repeats > 0 {
            sum += src[row + (w - 1)] as i32 * repeats;
        }
        tmp[row] = (sum as f32 / denom as f32).round().clamp(0.0, 255.0) as u8;

        for x in 1..w {
            let add_x = (x + r).min(w - 1);
            let sub_x = x.saturating_sub(r + 1);
            let sub_x = sub_x.min(w - 1);
            sum += src[row + add_x] as i32;
            sum -= src[row + sub_x] as i32;
            tmp[row + x] = (sum as f32 / denom as f32).round().clamp(0.0, 255.0) as u8;
        }
    }

    // Vertical pass
    for x in 0..w {
        let denom = (2 * r + 1) as i32;
        let mut sum: i32 = 0;

        // y = 0 window: replicate edge pixels.
        sum += tmp[x] as i32 * (r as i32 + 1);
        let max_iy = r.min(h - 1);
        for iy in 1..=max_iy {
            sum += tmp[iy * w + x] as i32;
        }
        let repeats = r.saturating_sub(max_iy) as i32;
        if repeats > 0 {
            sum += tmp[(h - 1) * w + x] as i32 * repeats;
        }
        dst[x] = (sum as f32 / denom as f32).round().clamp(0.0, 255.0) as u8;

        for y in 1..h {
            let add_y = (y + r).min(h - 1);
            let sub_y = y.saturating_sub(r + 1);
            let sub_y = sub_y.min(h - 1);
            sum += tmp[add_y * w + x] as i32;
            sum -= tmp[sub_y * w + x] as i32;
            dst[y * w + x] = (sum as f32 / denom as f32).round().clamp(0.0, 255.0) as u8;
        }
    }

    dst
}

fn generate_mask_bitmap(sub_masks: &[SubMaskPayload], width: u32, height: u32) -> Vec<u8> {
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];
    for sub_mask in sub_masks.iter().filter(|s| s.visible) {
        match sub_mask.mask_type.as_str() {
            "brush" => apply_brush_submask(&mut mask, sub_mask, width, height),
            "radial" => {
                let bitmap = generate_radial_mask(&sub_mask.parameters, width, height);
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "linear" => {
                let bitmap = generate_linear_mask(&sub_mask.parameters, width, height);
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "ai-subject" => {
                if let Some(bitmap) = generate_ai_subject_mask(&sub_mask.parameters, width, height) {
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                }
            }
            _ => {}
        }
    }
    mask
}

fn get_luma(color: [f32; 3]) -> f32 {
    color[0] * 0.2126 + color[1] * 0.7152 + color[2] * 0.0722
}

fn rgb_to_hue(color: [f32; 3]) -> f32 {
    let r = color[0];
    let g = color[1];
    let b = color[2];
    let max = r.max(g).max(b);
    let min = r.min(g).min(b);
    let delta = max - min;
    
    if delta < 0.0001 {
        return 0.0;
    }
    
    let hue = if max == r {
        60.0 * (((g - b) / delta) % 6.0)
    } else if max == g {
        60.0 * (((b - r) / delta) + 2.0)
    } else {
        60.0 * (((r - g) / delta) + 4.0)
    };
    
    if hue < 0.0 { hue + 360.0 } else { hue }
}

fn rgb_to_hsv(color: [f32; 3]) -> [f32; 3] {
    let r = color[0];
    let g = color[1];
    let b = color[2];
    let max = r.max(g).max(b);
    let min = r.min(g).min(b);
    let delta = max - min;
    let hue = rgb_to_hue(color);
    let saturation = if max <= 1e-10 { 0.0 } else { delta / max };
    [hue, saturation, max]
}

fn hsv_to_rgb(h: f32, s: f32, v: f32) -> [f32; 3] {
    let c = v * s;
    let x = c * (1.0 - ((h / 60.0) % 2.0 - 1.0).abs());
    let m = v - c;

    let (rp, gp, bp) = if h < 60.0 {
        (c, x, 0.0)
    } else if h < 120.0 {
        (x, c, 0.0)
    } else if h < 180.0 {
        (0.0, c, x)
    } else if h < 240.0 {
        (0.0, x, c)
    } else if h < 300.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };

    [rp + m, gp + m, bp + m]
}

const HSL_RANGES: [(f32, f32); 8] = [
    (358.0, 35.0), // Reds
    (25.0, 45.0),  // Oranges
    (60.0, 40.0),  // Yellows
    (115.0, 90.0), // Greens
    (180.0, 60.0), // Aquas
    (225.0, 60.0), // Blues
    (280.0, 55.0), // Purples
    (330.0, 50.0), // Magentas
];

fn apply_hsl_panel(color: [f32; 3], hsl_adjustments: &[HslColorValues; 8]) -> [f32; 3] {
    if hsl_adjustments.iter().all(|adj| {
        adj.hue.abs() <= 0.000001
            && adj.saturation.abs() <= 0.000001
            && adj.luminance.abs() <= 0.000001
    }) {
        return color;
    }

    // Quick chroma check (avoid work for greys)
    if (color[0] - color[1]).abs() < 0.001 && (color[1] - color[2]).abs() < 0.001 {
        return color;
    }

    let hsv = rgb_to_hsv(color);
    let original_hue = hsv[0];
    let original_sat = hsv[1];
    let original_val = hsv[2];

    if original_sat < 0.05 {
        return color;
    }

    let saturation_mask = smoothstep(0.05, 0.20, original_sat);
    let luminance_weight = smoothstep(0.0, 1.0, original_sat);

    let mut total_hue_shift = 0.0;
    let mut total_sat_mult = 0.0;
    let mut total_lum_adj = 0.0;
    let mut total_weight = 0.0;

    for (i, (center, width)) in HSL_RANGES.iter().enumerate() {
        let dist = (original_hue - center).abs();
        let dist = dist.min(360.0 - dist);
        let falloff = dist / (width * 0.5);
        let influence = (-1.5 * falloff * falloff).exp();

        total_weight += influence;

        let adj = &hsl_adjustments[i];
        total_hue_shift += adj.hue * influence;
        total_sat_mult += adj.saturation * influence;
        total_lum_adj += adj.luminance * influence;
    }

    if total_weight <= 0.0001 {
        return color;
    }

    let inv_weight = 1.0 / total_weight;
    let final_hue_shift = (total_hue_shift * inv_weight) * 2.0 * saturation_mask;
    let final_sat_mult = (total_sat_mult * inv_weight) * saturation_mask;
    let final_lum_adj = (total_lum_adj * inv_weight) * luminance_weight;

    let mut hue = (original_hue + final_hue_shift) % 360.0;
    if hue < 0.0 {
        hue += 360.0;
    }
    let sat = (original_sat * (1.0 + final_sat_mult)).clamp(0.0, 1.0);

    let original_luma = get_luma(color);
    let target_luma = original_luma * (1.0 + final_lum_adj);

    if sat < 0.0001 {
        let v = target_luma.max(0.0);
        return [v, v, v];
    }

    let rgb_shifted = hsv_to_rgb(hue, sat, original_val);
    let new_luma = get_luma(rgb_shifted);
    if new_luma < 0.0001 {
        let v = target_luma.max(0.0);
        return [v, v, v];
    }

    let scale = target_luma / new_luma;
    [rgb_shifted[0] * scale, rgb_shifted[1] * scale, rgb_shifted[2] * scale]
}

fn apply_color_grading(
    color: [f32; 3],
    shadows: ColorGradeSettings,
    midtones: ColorGradeSettings,
    highlights: ColorGradeSettings,
    blending: f32,
    balance: f32,
) -> [f32; 3] {
    let luma = get_luma([color[0].max(0.0), color[1].max(0.0), color[2].max(0.0)]);
    let base_shadow_crossover = 0.1;
    let base_highlight_crossover = 0.5;
    let balance_range = 0.5;
    let shadow_crossover = base_shadow_crossover + (-balance).max(0.0) * balance_range;
    let highlight_crossover = base_highlight_crossover - balance.max(0.0) * balance_range;
    let feather = 0.2 * blending;
    let final_shadow_crossover = shadow_crossover.min(highlight_crossover - 0.01);
    let shadow_mask =
        1.0 - smoothstep(final_shadow_crossover - feather, final_shadow_crossover + feather, luma);
    let highlight_mask = smoothstep(highlight_crossover - feather, highlight_crossover + feather, luma);
    let midtone_mask = (1.0 - shadow_mask - highlight_mask).max(0.0);

    let mut graded_color = color;
    let shadow_sat_strength = 0.3;
    let shadow_lum_strength = 0.5;
    let midtone_sat_strength = 0.6;
    let midtone_lum_strength = 0.8;
    let highlight_sat_strength = 0.8;
    let highlight_lum_strength = 1.0;

    if shadows.saturation > 0.001 {
        let tint_rgb = hsv_to_rgb(shadows.hue, 1.0, 1.0);
        graded_color[0] += (tint_rgb[0] - 0.5) * shadows.saturation * shadow_mask * shadow_sat_strength;
        graded_color[1] += (tint_rgb[1] - 0.5) * shadows.saturation * shadow_mask * shadow_sat_strength;
        graded_color[2] += (tint_rgb[2] - 0.5) * shadows.saturation * shadow_mask * shadow_sat_strength;
    }
    graded_color[0] += shadows.luminance * shadow_mask * shadow_lum_strength;
    graded_color[1] += shadows.luminance * shadow_mask * shadow_lum_strength;
    graded_color[2] += shadows.luminance * shadow_mask * shadow_lum_strength;

    if midtones.saturation > 0.001 {
        let tint_rgb = hsv_to_rgb(midtones.hue, 1.0, 1.0);
        graded_color[0] += (tint_rgb[0] - 0.5) * midtones.saturation * midtone_mask * midtone_sat_strength;
        graded_color[1] += (tint_rgb[1] - 0.5) * midtones.saturation * midtone_mask * midtone_sat_strength;
        graded_color[2] += (tint_rgb[2] - 0.5) * midtones.saturation * midtone_mask * midtone_sat_strength;
    }
    graded_color[0] += midtones.luminance * midtone_mask * midtone_lum_strength;
    graded_color[1] += midtones.luminance * midtone_mask * midtone_lum_strength;
    graded_color[2] += midtones.luminance * midtone_mask * midtone_lum_strength;

    if highlights.saturation > 0.001 {
        let tint_rgb = hsv_to_rgb(highlights.hue, 1.0, 1.0);
        graded_color[0] += (tint_rgb[0] - 0.5) * highlights.saturation * highlight_mask * highlight_sat_strength;
        graded_color[1] += (tint_rgb[1] - 0.5) * highlights.saturation * highlight_mask * highlight_sat_strength;
        graded_color[2] += (tint_rgb[2] - 0.5) * highlights.saturation * highlight_mask * highlight_sat_strength;
    }
    graded_color[0] += highlights.luminance * highlight_mask * highlight_lum_strength;
    graded_color[1] += highlights.luminance * highlight_mask * highlight_lum_strength;
    graded_color[2] += highlights.luminance * highlight_mask * highlight_lum_strength;

    graded_color
}

fn apply_filmic_brightness(colors: [f32; 3], brightness_adj: f32) -> [f32; 3] {
    if brightness_adj.abs() < 0.00001 {
        return colors;
    }
    
    const RATIONAL_CURVE_MIX: f32 = 0.95;
    const MIDTONE_STRENGTH: f32 = 1.2;
    let original_luma = get_luma(colors);
    
    if original_luma.abs() < 0.00001 {
        return colors;
    }
    
    let direct_adj = brightness_adj * (1.0 - RATIONAL_CURVE_MIX);
    let rational_adj = brightness_adj * RATIONAL_CURVE_MIX;
    let scale = 2f32.powf(direct_adj);
    let k = 2f32.powf(-rational_adj * MIDTONE_STRENGTH);
    
    let luma_abs = original_luma.abs();
    let luma_floor = luma_abs.floor();
    let luma_fract = luma_abs - luma_floor;
    let shaped_fract = luma_fract / (luma_fract + (1.0 - luma_fract) * k);
    let shaped_luma_abs = luma_floor + shaped_fract;
    let new_luma = original_luma.signum() * shaped_luma_abs * scale;
    
    let chroma = [colors[0] - original_luma, colors[1] - original_luma, colors[2] - original_luma];
    let total_luma_scale = new_luma / original_luma;
    let chroma_scale = total_luma_scale.powf(0.8);
    
    [
        new_luma + chroma[0] * chroma_scale,
        new_luma + chroma[1] * chroma_scale,
        new_luma + chroma[2] * chroma_scale,
    ]
}

fn apply_tonal_adjustments(mut colors: [f32; 3], contrast: f32, shadows: f32, whites: f32, blacks: f32) -> [f32; 3] {
    // Whites adjustment
    if whites.abs() > 0.00001 {
        let white_level = 1.0 - whites * 0.25;
        for channel in colors.iter_mut() {
            *channel /= white_level.max(0.01);
        }
    }
    
    // Blacks adjustment
    if blacks.abs() > 0.00001 {
        let luma = get_luma(colors).max(0.0);
        let mask = 1.0 - smoothstep(0.0, 0.25, luma);
        if mask > 0.001 {
            let adjustment = blacks * 0.75;
            let factor = 2f32.powf(adjustment);
            for channel in colors.iter_mut() {
                let adjusted = *channel * factor;
                *channel = *channel + (adjusted - *channel) * mask;
            }
        }
    }
    
    // Shadows adjustment
    if shadows.abs() > 0.00001 {
        let luma = get_luma(colors).max(0.0);
        let mask = (1.0 - smoothstep(0.0, 0.4, luma)).powf(3.0);
        if mask > 0.001 {
            let adjustment = shadows * 1.5;
            let factor = 2f32.powf(adjustment);
            for channel in colors.iter_mut() {
                let adjusted = *channel * factor;
                *channel = *channel + (adjusted - *channel) * mask;
            }
        }
    }
    
    // Contrast adjustment
    if contrast.abs() > 0.00001 {
        const GAMMA: f32 = 2.2;
        let safe_rgb = [colors[0].max(0.0), colors[1].max(0.0), colors[2].max(0.0)];
        let mut perceptual = [
            safe_rgb[0].powf(1.0 / GAMMA),
            safe_rgb[1].powf(1.0 / GAMMA),
            safe_rgb[2].powf(1.0 / GAMMA),
        ];
        
        for p in perceptual.iter_mut() {
            *p = p.clamp(0.0, 1.0);
        }
        
        let strength = 2f32.powf(contrast * 1.25);
        for i in 0..3 {
            if perceptual[i] < 0.5 {
                perceptual[i] = 0.5 * (2.0 * perceptual[i]).powf(strength);
            } else {
                perceptual[i] = 1.0 - 0.5 * (2.0 * (1.0 - perceptual[i])).powf(strength);
            }
        }
        
        let contrast_adjusted = [
            perceptual[0].powf(GAMMA),
            perceptual[1].powf(GAMMA),
            perceptual[2].powf(GAMMA),
        ];
        
        // Mix based on whether we're over 1.0
        for i in 0..3 {
            let mix_factor = smoothstep(1.0, 1.01, safe_rgb[i]);
            colors[i] = contrast_adjusted[i] + (colors[i] - contrast_adjusted[i]) * mix_factor;
        }
    }
    
    colors
}

fn apply_highlights_adjustment(mut colors: [f32; 3], highlights: f32) -> [f32; 3] {
    if highlights.abs() < 0.00001 {
        return colors;
    }
    
    let luma = get_luma(colors).max(0.0);
    let mask_input = (luma * 1.5).tanh();
    let highlight_mask = smoothstep(0.3, 0.95, mask_input);
    
    if highlight_mask < 0.001 {
        return colors;
    }
    
    if highlights < 0.0 {
        let new_luma = if luma <= 1.0 {
            let gamma = 1.0 - highlights * 1.75;
            luma.powf(gamma)
        } else {
            let luma_excess = luma - 1.0;
            let compression_strength = -highlights * 6.0;
            let compressed_excess = luma_excess / (1.0 + luma_excess * compression_strength);
            1.0 + compressed_excess
        };
        
        let tonally_adjusted = [
            colors[0] * (new_luma / luma.max(0.0001)),
            colors[1] * (new_luma / luma.max(0.0001)),
            colors[2] * (new_luma / luma.max(0.0001)),
        ];
        
        let desaturation_amount = smoothstep(1.0, 10.0, luma);
        let white_point = [new_luma, new_luma, new_luma];
        
        for i in 0..3 {
            let final_adjusted = tonally_adjusted[i] + (white_point[i] - tonally_adjusted[i]) * desaturation_amount;
            colors[i] = colors[i] + (final_adjusted - colors[i]) * highlight_mask;
        }
    } else {
        let adjustment = highlights * 1.75;
        let factor = 2f32.powf(adjustment);
        for i in 0..3 {
            let final_adjusted = colors[i] * factor;
            colors[i] = colors[i] + (final_adjusted - colors[i]) * highlight_mask;
        }
    }
    
    colors
}

fn apply_color_adjustments(mut colors: [f32; 3], settings: &AdjustmentValues) -> [f32; 3] {
    // Temperature and Tint adjustment (applied early like RapidRAW)
    // RapidRAW: temp_kelvin_mult = vec3(1.0 + temp * 0.2, 1.0 + temp * 0.05, 1.0 - temp * 0.2)
    // RapidRAW: tint_mult = vec3(1.0 + tnt * 0.25, 1.0 - tnt * 0.25, 1.0 + tnt * 0.25)
    let temp_kelvin_mult = [
        1.0 + settings.temperature * 0.2,
        1.0 + settings.temperature * 0.05,
        1.0 - settings.temperature * 0.2,
    ];
    let tint_mult = [
        1.0 + settings.tint * 0.25,
        1.0 - settings.tint * 0.25,
        1.0 + settings.tint * 0.25,
    ];
    colors[0] *= temp_kelvin_mult[0] * tint_mult[0];
    colors[1] *= temp_kelvin_mult[1] * tint_mult[1];
    colors[2] *= temp_kelvin_mult[2] * tint_mult[2];
    
    // Brightness (filmic curve)
    colors = apply_filmic_brightness(colors, settings.brightness);
    
    // Tonal adjustments (contrast, shadows, whites, blacks)
    colors = apply_tonal_adjustments(colors, settings.contrast, settings.shadows, settings.whites, settings.blacks);
    
    // Highlights
    colors = apply_highlights_adjustment(colors, settings.highlights);

    // RapidRAW-like HSL panel (Reds/Oranges/.../Magentas)
    colors = apply_hsl_panel(colors, &settings.hsl);

    // RapidRAW-like color grading wheels (shadows/midtones/highlights)
    colors = apply_color_grading(
        colors,
        settings.color_grading.shadows,
        settings.color_grading.midtones,
        settings.color_grading.highlights,
        settings.color_grading.blending,
        settings.color_grading.balance,
    );

    let luma = get_luma(colors);
    
    // Saturation adjustment - RapidRAW: mix(vec3(luma), processed, 1.0 + sat)
    if settings.saturation != 0.0 {
        let sat_mix = 1.0 + settings.saturation;
        colors[0] = luma + (colors[0] - luma) * sat_mix;
        colors[1] = luma + (colors[1] - luma) * sat_mix;
        colors[2] = luma + (colors[2] - luma) * sat_mix;
    }

    // Vibrance adjustment - RapidRAW implementation with skin tone protection
    if settings.vibrance != 0.0 {
        let c_max = colors[0].max(colors[1]).max(colors[2]);
        let c_min = colors[0].min(colors[1]).min(colors[2]);
        let delta = c_max - c_min;
        
        if delta >= 0.02 {
            let current_sat = delta / c_max.max(0.001);
            
            if settings.vibrance > 0.0 {
                // Positive vibrance: affect low saturation colors more, protect skin tones
                let sat_mask = 1.0 - smoothstep(0.4, 0.9, current_sat);
                
                // Skin tone protection (hue around 25 degrees)
                let hue = rgb_to_hue(colors);
                let skin_center = 25.0;
                let hue_dist = (hue - skin_center).abs().min(360.0 - (hue - skin_center).abs());
                let is_skin = smoothstep(35.0, 10.0, hue_dist);
                let skin_dampener = 1.0 - is_skin * 0.4; // mix(1.0, 0.6, is_skin)
                
                let amount = settings.vibrance * sat_mask * skin_dampener * 3.0;
                let vib_mix = 1.0 + amount;
                colors[0] = luma + (colors[0] - luma) * vib_mix;
                colors[1] = luma + (colors[1] - luma) * vib_mix;
                colors[2] = luma + (colors[2] - luma) * vib_mix;
            } else {
                // Negative vibrance: desaturate already saturated colors
                let desat_mask = 1.0 - smoothstep(0.2, 0.8, current_sat);
                let amount = settings.vibrance * desat_mask;
                let vib_mix = 1.0 + amount;
                colors[0] = luma + (colors[0] - luma) * vib_mix;
                colors[1] = luma + (colors[1] - luma) * vib_mix;
                colors[2] = luma + (colors[2] - luma) * vib_mix;
            }
        }
    }

    // Clarity, Dehaze, Structure, and Centré adjustments
    let clarity_gain = settings.clarity / 100.0 * 0.15;
    let dehaze_gain = settings.dehaze / 500.0;
    let structure_gain = settings.structure / 200.0 * 0.1;
    let centre_gain = settings.centre / 200.0 * 0.08;
    let detail_mask = ((luma - 0.5) * 2.0).clamp(-1.0, 1.0);
    
    for channel in colors.iter_mut() {
        *channel += clarity_gain * detail_mask;
        *channel += dehaze_gain * (luma - 0.5);
        *channel += structure_gain * detail_mask;
        *channel += centre_gain * detail_mask.abs();
    }

    // Sharpness (basic local contrast enhancement)
    let sharpness_gain = settings.sharpness / 100.0 * 0.12;
    for channel in colors.iter_mut() {
        *channel += sharpness_gain * detail_mask;
    }
    
    // Clamp final values
    for channel in colors.iter_mut() {
        *channel = channel.clamp(0.0, 1.0);
    }

    colors
}

fn linear_to_srgb(linear: f32) -> f32 {
    // Match RapidRAW shader behavior: clamp linear input to display range before encoding.
    // Prevents artifacts on blown highlights when values have headroom (> 1.0).
    let v = linear.clamp(0.0, 1.0);
    if v <= 0.0031308 {
        12.92 * v
    } else {
        1.055 * v.powf(1.0 / 2.4) - 0.055
    }
}

fn srgb_to_linear(srgb: f32) -> f32 {
    let v = srgb.max(0.0);
    if v <= 0.04045 {
        v / 12.92
    } else {
        ((v + 0.055) / 1.055).powf(2.4)
    }
}

fn apply_default_raw_processing(colors: [f32; 3], use_basic_tone_mapper: bool) -> [f32; 3] {
    if !use_basic_tone_mapper {
        return colors; // AgX doesn't need default processing
    }
    
    // RapidRAW applies default brightness and contrast to RAW images
    // when using Basic tone mapper
    const BRIGHTNESS_GAMMA: f32 = 1.1;
    const CONTRAST_MIX: f32 = 0.75;
    
    // Convert to sRGB
    let srgb = [
        linear_to_srgb(colors[0]),
        linear_to_srgb(colors[1]),
        linear_to_srgb(colors[2]),
    ];
    
    // Apply brightness gamma
    let brightened = [
        srgb[0].powf(1.0 / BRIGHTNESS_GAMMA),
        srgb[1].powf(1.0 / BRIGHTNESS_GAMMA),
        srgb[2].powf(1.0 / BRIGHTNESS_GAMMA),
    ];
    
    // Apply contrast S-curve
    let contrast_curve = [
        brightened[0] * brightened[0] * (3.0 - 2.0 * brightened[0]),
        brightened[1] * brightened[1] * (3.0 - 2.0 * brightened[1]),
        brightened[2] * brightened[2] * (3.0 - 2.0 * brightened[2]),
    ];
    
    // Mix in the contrast
    let contrasted = [
        brightened[0] + (contrast_curve[0] - brightened[0]) * CONTRAST_MIX,
        brightened[1] + (contrast_curve[1] - brightened[1]) * CONTRAST_MIX,
        brightened[2] + (contrast_curve[2] - brightened[2]) * CONTRAST_MIX,
    ];
    
    // Convert back to linear
    [
        srgb_to_linear(contrasted[0]),
        srgb_to_linear(contrasted[1]),
        srgb_to_linear(contrasted[2]),
    ]
}

type LinearImage = ImageBuffer<Rgba<f32>, Vec<f32>>;

fn develop_preview_linear(
    raw_bytes: &[u8],
    fast_demosaic: bool,
    max_width: Option<u32>,
    max_height: Option<u32>,
) -> Result<LinearImage> {
    let highlight_compression = 2.5; // RapidRAW default
    let mut dynamic_image = develop_raw_image(raw_bytes, fast_demosaic, highlight_compression)
        .context("Failed to decode RAW image")?;

    // If a maximum size was requested, downscale BEFORE the per-pixel adjustments
    // to avoid performing expensive color math at full sensor resolution.
    if let (Some(max_w), Some(max_h)) = (max_width, max_height) {
        if dynamic_image.width() > max_w || dynamic_image.height() > max_h {
            // Fit within the requested box while preserving aspect ratio.
            let w = dynamic_image.width() as f32;
            let h = dynamic_image.height() as f32;
            let scale = (max_w as f32 / w).min(max_h as f32 / h);
            let target_w = ((w * scale).max(1.0)) as u32;
            let target_h = ((h * scale).max(1.0)) as u32;
            // Use a faster filter for preview downscale (Triangle is a good quality/speed tradeoff).
            dynamic_image = dynamic_image.resize(target_w, target_h, FilterType::Triangle);
        }
    }

    Ok(dynamic_image.to_rgba32f())
}

fn rotate_about_center_rgba32f(image: &LinearImage, rotation_degrees: f32) -> LinearImage {
    let angle = rotation_degrees % 360.0;
    if !angle.is_finite() || angle.abs() <= 0.0001 {
        return image.clone();
    }

    let (width, height) = image.dimensions();
    if width == 0 || height == 0 {
        return image.clone();
    }

    let cx = (width as f32 - 1.0) / 2.0;
    let cy = (height as f32 - 1.0) / 2.0;

    let rad = angle.to_radians();
    let cos_a = rad.cos();
    let sin_a = rad.sin();

    let src = image.as_raw();
    let mut out = vec![0.0f32; (width as usize) * (height as usize) * 4];

    fn get_pixel(src: &[f32], width: u32, x: u32, y: u32) -> [f32; 4] {
        let idx = ((y as usize) * (width as usize) + (x as usize)) * 4;
        [src[idx], src[idx + 1], src[idx + 2], src[idx + 3]]
    }

    fn bilinear(src: &[f32], width: u32, height: u32, x: f32, y: f32) -> [f32; 4] {
        if !x.is_finite() || !y.is_finite() {
            return [0.0, 0.0, 0.0, 0.0];
        }
        let max_x = width.saturating_sub(1) as f32;
        let max_y = height.saturating_sub(1) as f32;
        if x < 0.0 || y < 0.0 || x > max_x || y > max_y {
            return [0.0, 0.0, 0.0, 0.0];
        }

        let x0 = x.floor() as u32;
        let y0 = y.floor() as u32;
        let x1 = (x0 + 1).min(width.saturating_sub(1));
        let y1 = (y0 + 1).min(height.saturating_sub(1));

        let tx = (x - x0 as f32).clamp(0.0, 1.0);
        let ty = (y - y0 as f32).clamp(0.0, 1.0);

        let p00 = get_pixel(src, width, x0, y0);
        let p10 = get_pixel(src, width, x1, y0);
        let p01 = get_pixel(src, width, x0, y1);
        let p11 = get_pixel(src, width, x1, y1);

        let mut out = [0.0f32; 4];
        for c in 0..4 {
            let a = p00[c] + (p10[c] - p00[c]) * tx;
            let b = p01[c] + (p11[c] - p01[c]) * tx;
            out[c] = a + (b - a) * ty;
        }
        out
    }

    for y in 0..height {
        for x in 0..width {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;

            let src_x = cos_a * dx + sin_a * dy + cx;
            let src_y = -sin_a * dx + cos_a * dy + cy;

            let rgba = bilinear(src, width, height, src_x, src_y);
            let idx = ((y as usize) * (width as usize) + (x as usize)) * 4;
            out[idx..idx + 4].copy_from_slice(&rgba);
        }
    }

    ImageBuffer::from_vec(width, height, out).unwrap_or_else(|| image.clone())
}

fn apply_crop_linear(image: &LinearImage, crop: &CropPayload) -> LinearImage {
    let (img_w, img_h) = image.dimensions();
    if img_w == 0 || img_h == 0 {
        return image.clone();
    }

    let is_normalized = crop.x <= 1.5
        && crop.y <= 1.5
        && crop.width <= 1.5
        && crop.height <= 1.5;

    let fw = img_w as f32;
    let fh = img_h as f32;

    let mut x = crop.x;
    let mut y = crop.y;
    let mut w = crop.width;
    let mut h = crop.height;

    if is_normalized {
        x *= fw;
        y *= fh;
        w *= fw;
        h *= fh;
    }

    let x_u = x.round().clamp(0.0, (img_w.saturating_sub(1)) as f32) as u32;
    let y_u = y.round().clamp(0.0, (img_h.saturating_sub(1)) as f32) as u32;

    let max_w = (img_w - x_u).max(1);
    let max_h = (img_h - y_u).max(1);

    let w_u = w.round().clamp(1.0, max_w as f32) as u32;
    let h_u = h.round().clamp(1.0, max_h as f32) as u32;

    if w_u == img_w && h_u == img_h && x_u == 0 && y_u == 0 {
        return image.clone();
    }

    image::imageops::crop_imm(image, x_u, y_u, w_u, h_u).to_image()
}

fn apply_transformations<'a>(linear: &'a LinearImage, payload: &AdjustmentsPayload) -> Cow<'a, LinearImage> {
    let steps = payload.orientation_steps % 4;
    let flip_h = payload.flip_horizontal;
    let flip_v = payload.flip_vertical;
    let rotation = payload.rotation;
    let has_crop = payload.crop.is_some();

    let has_transform = steps != 0 || flip_h || flip_v || rotation.abs() > 0.0001 || has_crop;
    if !has_transform {
        return Cow::Borrowed(linear);
    }

    let mut img = linear.clone();
    img = match steps {
        1 => image::imageops::rotate90(&img),
        2 => image::imageops::rotate180(&img),
        3 => image::imageops::rotate270(&img),
        _ => img,
    };

    if flip_h {
        img = image::imageops::flip_horizontal(&img);
    }
    if flip_v {
        img = image::imageops::flip_vertical(&img);
    }

    if rotation.abs() > 0.0001 {
        img = rotate_about_center_rgba32f(&img, rotation);
    }

    if let Some(crop) = payload.crop.as_ref() {
        img = apply_crop_linear(&img, crop);
    }

    Cow::Owned(img)
}

fn render_linear_with_payload(
    linear_buffer: &LinearImage,
    payload: &AdjustmentsPayload,
    mask_runtimes: &[MaskRuntime],
    fast_demosaic: bool,
) -> Result<Vec<u8>> {
    let adjustment_values = payload.to_values().normalized(&ADJUSTMENT_SCALES);
    let use_basic_tone_mapper = matches!(adjustment_values.tone_mapper, ToneMapper::Basic);
    let global_curves = CurvesRuntime::from_payload(&payload.curves);
    let curves_are_active = !global_curves.is_default() || mask_runtimes.iter().any(|m| m.curves_are_active);

    let width = linear_buffer.width();
    let height = linear_buffer.height();

    let linear = linear_buffer.as_raw();
    debug_assert_eq!(linear.len(), (width as usize) * (height as usize) * 4);

    let mut rgb = vec![0u8; (width as usize) * (height as usize) * 3];

    for (idx, out) in rgb.chunks_exact_mut(3).enumerate() {
        let base = idx * 4;

        // Start with linear RAW pixel data
        let mut colors = [linear[base], linear[base + 1], linear[base + 2]];

        // Apply default RAW processing (brightness + contrast boost for Basic tone mapper)
        colors = apply_default_raw_processing(colors, use_basic_tone_mapper);

        // RapidRAW-like mask compositing: apply globals once, then for each mask
        // mix toward a separately-adjusted result by mask influence.
        let mut composite = apply_color_adjustments(colors, &adjustment_values);
        for mask in mask_runtimes {
            let mut selection = if let Some(bitmap) = &mask.bitmap {
                bitmap.get(idx).copied().unwrap_or(0) as f32 / 255.0
            } else {
                1.0
            };
            if mask.invert {
                selection = 1.0 - selection;
            }
            let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
            if influence <= 0.001 {
                continue;
            }

            let mask_adjusted = apply_color_adjustments(composite, &mask.adjustments);
            composite = [
                composite[0] + (mask_adjusted[0] - composite[0]) * influence,
                composite[1] + (mask_adjusted[1] - composite[1]) * influence,
                composite[2] + (mask_adjusted[2] - composite[2]) * influence,
            ];
        }

        let mut srgb = [
            linear_to_srgb(composite[0]),
            linear_to_srgb(composite[1]),
            linear_to_srgb(composite[2]),
        ];

        if curves_are_active {
            srgb = global_curves.apply_all(srgb);

            for mask in mask_runtimes {
                if !mask.curves_are_active {
                    continue;
                }

                let mut selection = if let Some(bitmap) = &mask.bitmap {
                    bitmap.get(idx).copied().unwrap_or(0) as f32 / 255.0
                } else {
                    1.0
                };
                if mask.invert {
                    selection = 1.0 - selection;
                }
                let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                if influence <= 0.001 {
                    continue;
                }

                let mask_curved = mask.curves.apply_all(srgb);
                srgb = [
                    srgb[0] + (mask_curved[0] - srgb[0]) * influence,
                    srgb[1] + (mask_curved[1] - srgb[1]) * influence,
                    srgb[2] + (mask_curved[2] - srgb[2]) * influence,
                ];
            }
        }

        if adjustment_values.vignette_amount.abs() > 0.00001 {
            let v_amount = adjustment_values.vignette_amount.clamp(-1.0, 1.0);
            let v_mid = adjustment_values.vignette_midpoint.clamp(0.0, 1.0);
            let v_round = (1.0 - adjustment_values.vignette_roundness).clamp(0.01, 4.0);
            let v_feather = adjustment_values.vignette_feather.clamp(0.0, 1.0) * 0.5;

            let x = (idx as u32) % width;
            let y = (idx as u32) / width;
            let full_w = width as f32;
            let full_h = height as f32;
            let aspect = if full_w > 0.0 { full_h / full_w } else { 1.0 };

            let uv_x = (x as f32 / full_w - 0.5) * 2.0;
            let uv_y = (y as f32 / full_h - 0.5) * 2.0;

            let sign_x = if uv_x > 0.0 { 1.0 } else if uv_x < 0.0 { -1.0 } else { 0.0 };
            let sign_y = if uv_y > 0.0 { 1.0 } else if uv_y < 0.0 { -1.0 } else { 0.0 };

            let uv_round_x = sign_x * uv_x.abs().powf(v_round);
            let uv_round_y = sign_y * uv_y.abs().powf(v_round);
            let d = ((uv_round_x * uv_round_x) + (uv_round_y * aspect) * (uv_round_y * aspect)).sqrt() * 0.5;

            let vignette_mask = smoothstep(v_mid - v_feather, v_mid + v_feather, d);
            if v_amount < 0.0 {
                let mult = (1.0 + v_amount * vignette_mask).clamp(0.0, 2.0);
                srgb = [srgb[0] * mult, srgb[1] * mult, srgb[2] * mult];
            } else {
                let t = (v_amount * vignette_mask).clamp(0.0, 1.0);
                srgb = [
                    srgb[0] + (1.0 - srgb[0]) * t,
                    srgb[1] + (1.0 - srgb[1]) * t,
                    srgb[2] + (1.0 - srgb[2]) * t,
                ];
            }
            srgb = [srgb[0].clamp(0.0, 1.0), srgb[1].clamp(0.0, 1.0), srgb[2].clamp(0.0, 1.0)];
        }

        out[0] = clamp_to_u8(srgb[0] * 255.0);
        out[1] = clamp_to_u8(srgb[1] * 255.0);
        out[2] = clamp_to_u8(srgb[2] * 255.0);
    }

    let mut encoded = Vec::new();
    let quality = if fast_demosaic { 88 } else { 96 };
    let mut encoder = JpegEncoder::new_with_quality(&mut encoded, quality);
    encoder.encode(&rgb, width, height, ExtendedColorType::Rgb8)?;
    Ok(encoded)
}

fn render_raw(
    raw_bytes: &[u8],
    adjustments_json: Option<&str>,
    fast_demosaic: bool,
    max_width: Option<u32>,
    max_height: Option<u32>,
) -> Result<Vec<u8>> {
    let linear_buffer = develop_preview_linear(raw_bytes, fast_demosaic, max_width, max_height)?;
    let payload = parse_adjustments_payload(adjustments_json);
    let transformed = apply_transformations(&linear_buffer, &payload);
    let width = transformed.width();
    let height = transformed.height();
    let mask_runtimes = parse_masks(payload.masks.clone(), width, height);
    render_linear_with_payload(transformed.as_ref(), &payload, &mask_runtimes, fast_demosaic)
}

static LOGGER_INIT: Once = Once::new();

fn ensure_logger() {
    LOGGER_INIT.call_once(|| {
        #[cfg(target_os = "android")]
        {
            android_logger::init_once(
                android_logger::Config::default()
                    .with_min_level(Level::Info)
                    .with_tag("kawaiiraweditor"),
            );
        }
    });
}

#[derive(Clone, Copy)]
enum PreviewKind {
    SuperLow,
    Low,
    Preview,
}

impl PreviewKind {
    fn max_dims(self) -> (u32, u32) {
        match self {
            PreviewKind::SuperLow => (64, 64),
            PreviewKind::Low => (256, 256),
            PreviewKind::Preview => (1280, 720),
        }
    }
}

struct MasksCache {
    masks: Vec<Value>,
    width: u32,
    height: u32,
    runtimes: Vec<MaskRuntime>,
}

fn extract_metadata_json(raw_bytes: &[u8]) -> Result<String> {
    let source = RawSource::new_from_slice(raw_bytes);
    let decoder = rawler::get_decoder(&source).context("No decoder for RAW")?;
    let metadata = decoder
        .raw_metadata(&source, &RawDecodeParams::default())
        .context("Failed to read RAW metadata")?;

    let exif = &metadata.exif;
    let iso = exif
        .iso_speed
        .map(|v| v.to_string())
        .or_else(|| exif.iso_speed_ratings.map(|v| v.to_string()))
        .unwrap_or_default();

    let lens = metadata
        .lens
        .as_ref()
        .map(|l| l.lens_name.clone())
        .or_else(|| exif.lens_model.clone())
        .unwrap_or_default();

    let payload = json!({
        "make": metadata.make,
        "model": metadata.model,
        "lens": lens,
        "iso": iso,
        "exposureTime": exif.exposure_time.map(|v| v.to_string()).unwrap_or_default(),
        "fNumber": exif.fnumber.map(|v| v.to_string()).unwrap_or_default(),
        "focalLength": exif.focal_length.map(|v| v.to_string()).unwrap_or_default(),
        "dateTimeOriginal": exif.date_time_original.clone().or(exif.create_date.clone()).unwrap_or_default(),
    });

    Ok(payload.to_string())
}

struct Session {
    raw_bytes: Vec<u8>,
    metadata_json: String,

    super_low: Option<Arc<LinearImage>>,
    low: Option<Arc<LinearImage>>,
    preview: Option<Arc<LinearImage>>,

    masks_super_low: Option<MasksCache>,
    masks_low: Option<MasksCache>,
    masks_preview: Option<MasksCache>,
}

impl Session {
    fn new(raw_bytes: Vec<u8>) -> Self {
        let metadata_json = extract_metadata_json(&raw_bytes).unwrap_or_else(|_| "{}".to_string());
        Self {
            raw_bytes,
            metadata_json,
            super_low: None,
            low: None,
            preview: None,
            masks_super_low: None,
            masks_low: None,
            masks_preview: None,
        }
    }

    fn linear_for(&mut self, kind: PreviewKind) -> Result<Arc<LinearImage>> {
        let slot = match kind {
            PreviewKind::SuperLow => &mut self.super_low,
            PreviewKind::Low => &mut self.low,
            PreviewKind::Preview => &mut self.preview,
        };
        if let Some(img) = slot.as_ref() {
            return Ok(Arc::clone(img));
        }

        let (max_w, max_h) = kind.max_dims();
        let linear = develop_preview_linear(&self.raw_bytes, true, Some(max_w), Some(max_h))?;
        let shared = Arc::new(linear);
        *slot = Some(Arc::clone(&shared));
        Ok(shared)
    }

    fn masks_for<'a>(
        &'a mut self,
        kind: PreviewKind,
        masks: &[Value],
        width: u32,
        height: u32,
    ) -> &'a [MaskRuntime] {
        let slot = match kind {
            PreviewKind::SuperLow => &mut self.masks_super_low,
            PreviewKind::Low => &mut self.masks_low,
            PreviewKind::Preview => &mut self.masks_preview,
        };

        let cache_hit = slot.as_ref().is_some_and(|cache| {
            cache.width == width && cache.height == height && cache.masks == masks
        });
        if cache_hit {
            return &slot.as_ref().expect("cache_hit implies Some").runtimes;
        }

        let runtimes = parse_masks(masks.to_vec(), width, height);
        *slot = Some(MasksCache {
            masks: masks.to_vec(),
            width,
            height,
            runtimes,
        });
        &slot.as_ref().expect("cache just set").runtimes
    }
}

static NEXT_SESSION_ID: AtomicU64 = AtomicU64::new(1);
static SESSIONS: OnceLock<Mutex<HashMap<u64, Arc<Mutex<Session>>>>> = OnceLock::new();

fn sessions() -> &'static Mutex<HashMap<u64, Arc<Mutex<Session>>>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn get_session(handle: jlong) -> Option<Arc<Mutex<Session>>> {
    if handle <= 0 {
        return None;
    }
    sessions()
        .lock()
        .ok()
        .and_then(|map| map.get(&(handle as u64)).cloned())
}

fn convert_raw_array(env: &JNIEnv, raw_array: JByteArray) -> Option<Vec<u8>> {
    match env.convert_byte_array(raw_array) {
        Ok(bytes) => Some(bytes),
        Err(err) => {
            error!("Failed to read raw byte array: {}", err);
            None
        }
    }
}

fn make_byte_array(env: &JNIEnv, bytes: &[u8]) -> jbyteArray {
    match env.byte_array_from_slice(bytes) {
        Ok(array) => array.into_raw() as jbyteArray,
        Err(err) => {
            error!("Failed to build byte array: {}", err);
            ptr::null_mut()
        }
    }
}

fn read_adjustments_json(env: &mut JNIEnv, adjustments: JString) -> Option<String> {
    match env.get_string(&adjustments) {
        Ok(js) => {
            let trimmed = js.to_string_lossy().trim().to_string();
            if trimmed.is_empty() {
                None
            } else {
                Some(trimmed)
            }
        }
        Err(err) => {
            error!("Failed to read adjustments JSON: {}", err);
            None
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_createSession(
    env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
) -> jlong {
    ensure_logger();
    let bytes = match convert_raw_array(&env, raw_data) {
        Some(b) => b,
        None => return 0,
    };

    let id = NEXT_SESSION_ID.fetch_add(1, Ordering::Relaxed);
    let session = Arc::new(Mutex::new(Session::new(bytes)));
    if let Ok(mut map) = sessions().lock() {
        map.insert(id, session);
    } else {
        error!("Failed to lock session registry");
        return 0;
    }

    id as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_releaseSession(
    _env: JNIEnv,
    _: JClass,
    handle: jlong,
) {
    if handle <= 0 {
        return;
    }
    if let Ok(mut map) = sessions().lock() {
        map.remove(&(handle as u64));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_getMetadataJsonFromSession(
    env: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jstring {
    ensure_logger();
    let session = match get_session(handle) {
        Some(s) => s,
        None => return ptr::null_mut(),
    };
    let session = match session.lock() {
        Ok(s) => s,
        Err(_) => return ptr::null_mut(),
    };
    match env.new_string(session.metadata_json.as_str()) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn render_from_session(
    handle: jlong,
    adjustments_json: Option<&str>,
    kind: PreviewKind,
) -> Result<Vec<u8>> {
    let session = get_session(handle).context("Invalid session handle")?;
    let mut session = session.lock().map_err(|_| anyhow::anyhow!("Session lock poisoned"))?;

    let linear = session.linear_for(kind)?;
    let payload = parse_adjustments_payload(adjustments_json);
    let transformed = apply_transformations(linear.as_ref(), &payload);
    let width = transformed.width();
    let height = transformed.height();
    let masks = session.masks_for(kind, &payload.masks, width, height);

    render_linear_with_payload(transformed.as_ref(), &payload, masks, true)
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_lowlowdecodeFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let adjustments = read_adjustments_json(&mut env, adjustments_json);
    match render_from_session(handle, adjustments.as_deref(), PreviewKind::SuperLow) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render session preview: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_lowdecodeFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let adjustments = read_adjustments_json(&mut env, adjustments_json);
    match render_from_session(handle, adjustments.as_deref(), PreviewKind::Low) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render session preview: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decodeFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let adjustments = read_adjustments_json(&mut env, adjustments_json);
    match render_from_session(handle, adjustments.as_deref(), PreviewKind::Preview) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render session preview: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decodeFullResFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let adjustments = read_adjustments_json(&mut env, adjustments_json);
    let session = match get_session(handle) {
        Some(s) => s,
        None => {
            error!("Invalid session handle: {}", handle);
            return ptr::null_mut();
        }
    };
    let session = match session.lock() {
        Ok(s) => s,
        Err(_) => {
            error!("Session lock poisoned");
            return ptr::null_mut();
        }
    };

    match render_raw(&session.raw_bytes, adjustments.as_deref(), false, None, None) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render full-resolution image: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_lowdecode(
    mut env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let bytes = match convert_raw_array(&env, raw_data) {
        Some(b) => b,
        None => return ptr::null_mut(),
    };

    let adjustments = read_adjustments_json(&mut env, adjustments_json);

    // Request a small fast preview for interactive slider updates.
    match render_raw(&bytes, adjustments.as_deref(), true, Some(256), Some(256)) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render preview: {}", err);
            ptr::null_mut()
        }
    }
}


#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_lowlowdecode(
    mut env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let bytes = match convert_raw_array(&env, raw_data) {
        Some(b) => b,
        None => return ptr::null_mut(),
    };

    let adjustments = read_adjustments_json(&mut env, adjustments_json);

    // Request a tiny preview for interactive slider updates while dragging.
    match render_raw(&bytes, adjustments.as_deref(), true, Some(64), Some(64)) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render preview: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decode(
    mut env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let bytes = match convert_raw_array(&env, raw_data) {
        Some(b) => b,
        None => return ptr::null_mut(),
    };

    let adjustments = read_adjustments_json(&mut env, adjustments_json);

    // Request a preview render (export uses decodeFullRes).
    match render_raw(&bytes, adjustments.as_deref(), true, Some(1280), Some(720)) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render preview: {}", err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decodeFullRes(
    mut env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let bytes = match convert_raw_array(&env, raw_data) {
        Some(b) => b,
        None => return ptr::null_mut(),
    };

    let adjustments = read_adjustments_json(&mut env, adjustments_json);

    match render_raw(&bytes, adjustments.as_deref(), false, None, None) {
        Ok(payload) => make_byte_array(&env, &payload),
        Err(err) => {
            error!("Failed to render full-resolution image: {}", err);
            ptr::null_mut()
        }
    }
}
