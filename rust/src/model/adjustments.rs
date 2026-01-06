use serde::Deserialize;
use std::ops::AddAssign;

#[derive(Clone, Copy, Default)]
pub struct AdjustmentValues {
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
    pub tone_mapper: ToneMapper,
    pub color_grading: ColorGradingValues,
    pub hsl: [HslColorValues; 8],
}

#[derive(Clone, Copy, Default)]
pub struct HslColorValues {
    pub hue: f32,
    pub saturation: f32,
    pub luminance: f32,
}

#[derive(Clone, Copy, Default)]
pub struct ColorGradeSettings {
    pub hue: f32,
    pub saturation: f32,
    pub luminance: f32,
}

#[derive(Clone, Copy, Default)]
pub struct ColorGradingValues {
    pub shadows: ColorGradeSettings,
    pub midtones: ColorGradeSettings,
    pub highlights: ColorGradeSettings,
    pub blending: f32,
    pub balance: f32,
}

impl ColorGradingValues {
    pub fn normalized(self) -> Self {
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
pub enum ToneMapper {
    #[default]
    Basic,
    Agx,
}

#[derive(Clone, Copy)]
pub struct AdjustmentScales {
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
    pub hsl_hue_multiplier: f32,
    pub hsl_saturation: f32,
    pub hsl_luminance: f32,
}

pub const ADJUSTMENT_SCALES: AdjustmentScales = AdjustmentScales {
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
    centre: 250.0,
    vignette_amount: 100.0,
    vignette_midpoint: 100.0,
    vignette_roundness: 100.0,
    vignette_feather: 100.0,
    sharpness: 80.0,
    luma_noise_reduction: 100.0,
    color_noise_reduction: 100.0,
    chromatic_aberration_red_cyan: 10000.0,
    chromatic_aberration_blue_yellow: 10000.0,
    hsl_hue_multiplier: 0.3,
    hsl_saturation: 100.0,
    hsl_luminance: 100.0,
};

impl AdjustmentValues {
    pub fn normalized(self, scales: &AdjustmentScales) -> Self {
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
