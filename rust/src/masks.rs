use std::collections::HashMap;

use base64::Engine;
use image::imageops::FilterType;
use serde_json::Value;

use crate::model::{
    AdjustmentValues,
    AiEnvironmentMaskParameters,
    AiSubjectMaskParameters,
    BrushMaskParameters,
    CurvesPayload,
    CurvesRuntime,
    LegacyMaskPayload,
    LinearMaskParameters,
    MaskDefinitionPayload,
    RadialMaskParameters,
    SubMaskMode,
    SubMaskPayload,
    ADJUSTMENT_SCALES,
};

#[derive(Clone)]
pub struct MaskRuntime {
    pub opacity_factor: f32,
    pub invert: bool,
    pub adjustments: AdjustmentValues,
    pub curves: CurvesRuntime,
    pub curves_are_active: bool,
    pub bitmap: Option<Vec<u8>>,
    pub origin_x: u32,
    pub origin_y: u32,
    pub width: u32,
    pub height: u32,
}

#[derive(Clone)]
pub struct MaskRuntimeDef {
    pub opacity_factor: f32,
    pub invert: bool,
    pub adjustments: AdjustmentValues,
    pub curves: CurvesRuntime,
    pub curves_are_active: bool,
    pub sub_masks: Option<Vec<SubMaskPayload>>,
}

#[derive(Clone, Copy)]
enum BrushTool {
    Brush,
    Eraser,
}

#[derive(Clone)]
struct BrushEvent {
    order: u64,
    tool: BrushTool,
    feather: f32,
    base_radius: f32,
    points: Vec<(f32, f32, f32)>,
}

pub fn parse_mask_defs(values: Vec<Value>) -> Vec<MaskRuntimeDef> {
    values
        .into_iter()
        .filter_map(|value| {
            if value.get("subMasks").is_some() {
                let def: MaskDefinitionPayload = serde_json::from_value(value).ok()?;
                if !def.visible {
                    return None;
                }
                let opacity_factor = (def.opacity / 100.0).clamp(0.0, 1.0);
                let curves = CurvesRuntime::from_payload(&def.adjustments.curves);
                let curves_are_active = !curves.is_default();
                return Some(MaskRuntimeDef {
                    opacity_factor,
                    invert: def.invert,
                    adjustments: def.adjustments.to_values().normalized(&ADJUSTMENT_SCALES),
                    curves,
                    curves_are_active,
                    sub_masks: Some(def.sub_masks),
                });
            }

            if value.get("enabled").is_some() {
                let legacy: LegacyMaskPayload = serde_json::from_value(value).ok()?;
                if !legacy.enabled {
                    return None;
                }
                return Some(MaskRuntimeDef {
                    opacity_factor: 1.0,
                    invert: false,
                    adjustments: legacy.to_values().normalized(&ADJUSTMENT_SCALES),
                    curves: CurvesRuntime::from_payload(&CurvesPayload::default()),
                    curves_are_active: false,
                    sub_masks: None,
                });
            }

            None
        })
        .collect()
}

pub fn parse_masks(values: Vec<Value>, width: u32, height: u32) -> Vec<MaskRuntime> {
    parse_mask_defs(values)
        .into_iter()
        .map(|def| {
            let bitmap = def
                .sub_masks
                .as_ref()
                .map(|sub_masks| generate_mask_bitmap(sub_masks, width, height));
            MaskRuntime {
                opacity_factor: def.opacity_factor,
                invert: def.invert,
                adjustments: def.adjustments,
                curves: def.curves,
                curves_are_active: def.curves_are_active,
                bitmap,
                origin_x: 0,
                origin_y: 0,
                width,
                height,
            }
        })
        .collect()
}

pub fn build_mask_runtimes_for_region(
    mask_defs: &[MaskRuntimeDef],
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
    ai_cache: &HashMap<String, Vec<u8>>,
) -> Vec<MaskRuntime> {
    mask_defs
        .iter()
        .map(|def| {
            let bitmap = def.sub_masks.as_ref().map(|sub_masks| {
                generate_mask_bitmap_region(
                    sub_masks,
                    full_width,
                    full_height,
                    origin_x,
                    origin_y,
                    width,
                    height,
                    ai_cache,
                )
            });
            MaskRuntime {
                opacity_factor: def.opacity_factor,
                invert: def.invert,
                adjustments: def.adjustments,
                curves: def.curves.clone(),
                curves_are_active: def.curves_are_active,
                bitmap,
                origin_x,
                origin_y,
                width,
                height,
            }
        })
        .collect()
}

pub fn build_ai_mask_cache(mask_defs: &[MaskRuntimeDef], width: u32, height: u32) -> HashMap<String, Vec<u8>> {
    let mut cache = HashMap::new();
    for def in mask_defs {
        let Some(sub_masks) = def.sub_masks.as_ref() else { continue };
        for sub_mask in sub_masks {
            match sub_mask.mask_type.as_str() {
                "ai-subject" => {
                    if cache.contains_key(&sub_mask.id) {
                        continue;
                    }
                    if let Some(mask) = generate_ai_subject_mask(&sub_mask.parameters, width, height) {
                        cache.insert(sub_mask.id.clone(), mask);
                    }
                }
                "ai-environment" => {
                    if cache.contains_key(&sub_mask.id) {
                        continue;
                    }
                    if let Some(mask) = generate_ai_environment_mask(&sub_mask.parameters, width, height) {
                        cache.insert(sub_mask.id.clone(), mask);
                    }
                }
                _ => {}
            }
        }
    }
    cache
}

pub fn mask_selection_at(mask: &MaskRuntime, full_x: u32, full_y: u32) -> f32 {
    let bitmap = match mask.bitmap.as_ref() {
        Some(bitmap) => bitmap,
        None => return 1.0,
    };
    if full_x < mask.origin_x || full_y < mask.origin_y {
        return 0.0;
    }
    let local_x = full_x - mask.origin_x;
    let local_y = full_y - mask.origin_y;
    if local_x >= mask.width || local_y >= mask.height {
        return 0.0;
    }
    let idx = (local_y * mask.width + local_x) as usize;
    bitmap.get(idx).copied().unwrap_or(0) as f32 / 255.0
}

fn apply_feathered_circle_add(
    target: &mut [u8],
    width: u32,
    height: u32,
    cx: f32,
    cy: f32,
    radius: f32,
    feather: f32,
) {
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

fn apply_feathered_circle_sub(
    target: &mut [u8],
    width: u32,
    height: u32,
    cx: f32,
    cy: f32,
    radius: f32,
    feather: f32,
) {
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

    fn pressure_scale(pressure: f32) -> f32 {
        0.2 + 0.8 * pressure.clamp(0.0, 1.0)
    }

    let params: BrushMaskParameters = serde_json::from_value(sub_mask.parameters.clone()).unwrap_or_default();
    let mut events: Vec<BrushEvent> = Vec::new();

    for line in params.lines {
        if line.points.is_empty() {
            continue;
        }

        let tool = if line.tool == "eraser" {
            BrushTool::Eraser
        } else {
            BrushTool::Brush
        };

        let brush_size_px = if line.brush_size <= 1.5 {
            (line.brush_size * base_dim).max(0.0)
        } else {
            line.brush_size
        };
        let base_radius = (brush_size_px / 2.0).max(1.0);
        let feather = line.feather.clamp(0.0, 1.0);

        let points: Vec<(f32, f32, f32)> = line
            .points
            .into_iter()
            .map(|p| (denorm(p.x, w_f), denorm(p.y, h_f), p.pressure.clamp(0.0, 1.0)))
            .collect();

        events.push(BrushEvent {
            order: line.order,
            tool,
            feather,
            base_radius,
            points,
        });
    }

    events.sort_by_key(|e| e.order);

    for event in events {
        let apply_circle = |mask: &mut [u8], cx: f32, cy: f32, radius: f32| {
            match (sub_mask.mode, event.tool) {
                (SubMaskMode::Additive, BrushTool::Brush) => {
                    apply_feathered_circle_add(mask, width, height, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Additive, BrushTool::Eraser) => {
                    apply_feathered_circle_sub(mask, width, height, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Subtractive, BrushTool::Brush) => {
                    apply_feathered_circle_sub(mask, width, height, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Subtractive, BrushTool::Eraser) => {
                    apply_feathered_circle_add(mask, width, height, cx, cy, radius, event.feather);
                }
            }
        };

        if event.points.len() == 1 {
            let (x, y, p) = event.points[0];
            let radius = (event.base_radius * pressure_scale(p)).max(1.0);
            apply_circle(target, x, y, radius);
            continue;
        }

        for pair in event.points.windows(2) {
            let (x1, y1, p1) = pair[0];
            let (x2, y2, p2) = pair[1];
            let dx = x2 - x1;
            let dy = y2 - y1;
            let dist = (dx * dx + dy * dy).sqrt().max(0.001);
            let r1 = (event.base_radius * pressure_scale(p1)).max(1.0);
            let r2 = (event.base_radius * pressure_scale(p2)).max(1.0);
            let step_size = ((r1.max(r2)) * 0.5).max(0.75);
            let steps = (dist / step_size).ceil() as i32;
            for i in 0..=steps {
                let t = i as f32 / steps.max(1) as f32;
                let radius = r1 + (r2 - r1) * t;
                apply_circle(target, x1 + dx * t, y1 + dy * t, radius);
            }
        }
    }
}

fn apply_feathered_circle_add_region(
    target: &mut [u8],
    width: u32,
    height: u32,
    origin_x: u32,
    origin_y: u32,
    cx: f32,
    cy: f32,
    radius: f32,
    feather: f32,
) {
    if radius <= 0.5 || width == 0 || height == 0 {
        return;
    }
    let feather_amount = feather.clamp(0.0, 1.0);
    let inner_radius = radius * (1.0 - feather_amount);
    let outer_radius = radius;
    let outer_sq = outer_radius * outer_radius;

    let min_x = (cx - outer_radius).floor().max(origin_x as f32) as i32;
    let min_y = (cy - outer_radius).floor().max(origin_y as f32) as i32;
    let max_x = (cx + outer_radius).ceil().min((origin_x + width - 1) as f32) as i32;
    let max_y = (cy + outer_radius).ceil().min((origin_y + height - 1) as f32) as i32;

    for y in min_y..=max_y {
        for x in min_x..=max_x {
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

            let local_x = (x as u32).saturating_sub(origin_x);
            let local_y = (y as u32).saturating_sub(origin_y);
            let idx = (local_y * width + local_x) as usize;
            let current = target[idx] as f32 / 255.0;
            let next = 1.0 - (1.0 - current) * (1.0 - intensity);
            target[idx] = (next.clamp(0.0, 1.0) * 255.0).round() as u8;
        }
    }
}

fn apply_feathered_circle_sub_region(
    target: &mut [u8],
    width: u32,
    height: u32,
    origin_x: u32,
    origin_y: u32,
    cx: f32,
    cy: f32,
    radius: f32,
    feather: f32,
) {
    if radius <= 0.5 || width == 0 || height == 0 {
        return;
    }
    let feather_amount = feather.clamp(0.0, 1.0);
    let inner_radius = radius * (1.0 - feather_amount);
    let outer_radius = radius;
    let outer_sq = outer_radius * outer_radius;

    let min_x = (cx - outer_radius).floor().max(origin_x as f32) as i32;
    let min_y = (cy - outer_radius).floor().max(origin_y as f32) as i32;
    let max_x = (cx + outer_radius).ceil().min((origin_x + width - 1) as f32) as i32;
    let max_y = (cy + outer_radius).ceil().min((origin_y + height - 1) as f32) as i32;

    for y in min_y..=max_y {
        for x in min_x..=max_x {
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

            let local_x = (x as u32).saturating_sub(origin_x);
            let local_y = (y as u32).saturating_sub(origin_y);
            let idx = (local_y * width + local_x) as usize;
            let current = target[idx] as f32 / 255.0;
            let next = (current * (1.0 - intensity)).clamp(0.0, 1.0);
            target[idx] = (next * 255.0).round() as u8;
        }
    }
}

fn apply_brush_submask_region(
    target: &mut [u8],
    sub_mask: &SubMaskPayload,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) {
    if sub_mask.mask_type != "brush" || !sub_mask.visible {
        return;
    }
    if target.len() != (width * height) as usize {
        return;
    }

    let w_f = full_width as f32;
    let h_f = full_height as f32;
    let base_dim = full_width.min(full_height) as f32;

    fn denorm(value: f32, max: f32) -> f32 {
        let max_coord = (max - 1.0).max(1.0);
        if value <= 1.5 {
            (value * max_coord).clamp(0.0, max_coord)
        } else {
            value
        }
    }

    fn pressure_scale(pressure: f32) -> f32 {
        0.2 + 0.8 * pressure.clamp(0.0, 1.0)
    }

    let params: BrushMaskParameters = serde_json::from_value(sub_mask.parameters.clone()).unwrap_or_default();
    let mut events: Vec<BrushEvent> = Vec::new();

    for line in params.lines {
        if line.points.is_empty() {
            continue;
        }

        let tool = if line.tool == "eraser" {
            BrushTool::Eraser
        } else {
            BrushTool::Brush
        };

        let brush_size_px = if line.brush_size <= 1.5 {
            (line.brush_size * base_dim).max(0.0)
        } else {
            line.brush_size
        };
        let base_radius = (brush_size_px / 2.0).max(1.0);
        let feather = line.feather.clamp(0.0, 1.0);

        let points: Vec<(f32, f32, f32)> = line
            .points
            .into_iter()
            .map(|p| (denorm(p.x, w_f), denorm(p.y, h_f), p.pressure.clamp(0.0, 1.0)))
            .collect();

        events.push(BrushEvent {
            order: line.order,
            tool,
            feather,
            base_radius,
            points,
        });
    }

    events.sort_by_key(|e| e.order);

    for event in events {
        let apply_circle = |mask: &mut [u8], cx: f32, cy: f32, radius: f32| {
            match (sub_mask.mode, event.tool) {
                (SubMaskMode::Additive, BrushTool::Brush) => {
                    apply_feathered_circle_add_region(mask, width, height, origin_x, origin_y, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Additive, BrushTool::Eraser) => {
                    apply_feathered_circle_sub_region(mask, width, height, origin_x, origin_y, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Subtractive, BrushTool::Brush) => {
                    apply_feathered_circle_sub_region(mask, width, height, origin_x, origin_y, cx, cy, radius, event.feather);
                }
                (SubMaskMode::Subtractive, BrushTool::Eraser) => {
                    apply_feathered_circle_add_region(mask, width, height, origin_x, origin_y, cx, cy, radius, event.feather);
                }
            }
        };

        if event.points.len() == 1 {
            let (x, y, p) = event.points[0];
            let radius = (event.base_radius * pressure_scale(p)).max(1.0);
            apply_circle(target, x, y, radius);
            continue;
        }

        for pair in event.points.windows(2) {
            let (x1, y1, p1) = pair[0];
            let (x2, y2, p2) = pair[1];
            let dx = x2 - x1;
            let dy = y2 - y1;
            let dist = (dx * dx + dy * dy).sqrt().max(0.001);
            let r1 = (event.base_radius * pressure_scale(p1)).max(1.0);
            let r2 = (event.base_radius * pressure_scale(p2)).max(1.0);
            let step_size = ((r1.max(r2)) * 0.5).max(0.75);
            let steps = (dist / step_size).ceil() as i32;
            for i in 0..=steps {
                let t = i as f32 / steps.max(1) as f32;
                let radius = r1 + (r2 - r1) * t;
                apply_circle(target, x1 + dx * t, y1 + dy * t, radius);
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
            (value * max_coord).clamp(0.0, 1.0)
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

fn generate_radial_mask_region(
    params_value: &Value,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Vec<u8> {
    let params: RadialMaskParameters = serde_json::from_value(params_value.clone()).unwrap_or_default();
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];

    let w_f = full_width as f32;
    let h_f = full_height as f32;
    let base_dim = full_width.min(full_height) as f32;

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
        let full_y = origin_y + y;
        for x in 0..width {
            let full_x = origin_x + x;
            let dx = full_x as f32 - cx;
            let dy = full_y as f32 - cy;

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

fn generate_linear_mask_region(
    params_value: &Value,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Vec<u8> {
    let params: LinearMaskParameters = serde_json::from_value(params_value.clone()).unwrap_or_default();
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];

    let w_f = full_width as f32;
    let h_f = full_height as f32;
    let base_dim = full_width.min(full_height) as f32;

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
        let full_y = origin_y + y;
        for x in 0..width {
            let full_x = origin_x + x;
            let pixel_vec_x = full_x as f32 - start_x;
            let pixel_vec_y = full_y as f32 - start_y;
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

fn generate_ai_png_mask(mask_data_base64: Option<String>, softness: f32, width: u32, height: u32) -> Option<Vec<u8>> {
    let data_url = mask_data_base64?;
    let bytes = decode_data_url_base64(&data_url)?;

    let decoded = image::load_from_memory(&bytes).ok()?;
    let gray = decoded.to_luma8();
    let resized = if gray.width() == width && gray.height() == height {
        gray
    } else {
        image::imageops::resize(&gray, width, height, FilterType::Triangle)
    };

    let mut raw = resized.into_raw();

    let softness = softness.clamp(0.0, 1.0);
    let radius = (softness * 10.0).round() as i32;
    if radius >= 1 {
        raw = box_blur_u8(&raw, width as usize, height as usize, radius as usize);
    }

    Some(raw)
}

fn generate_ai_subject_mask(params_value: &Value, width: u32, height: u32) -> Option<Vec<u8>> {
    let params: AiSubjectMaskParameters = serde_json::from_value(params_value.clone()).ok()?;
    generate_ai_png_mask(params.mask_data_base64, params.softness, width, height)
}

fn generate_ai_environment_mask(params_value: &Value, width: u32, height: u32) -> Option<Vec<u8>> {
    let params: AiEnvironmentMaskParameters = serde_json::from_value(params_value.clone()).ok()?;
    generate_ai_png_mask(params.mask_data_base64, params.softness, width, height)
}

fn generate_ai_png_mask_region(
    mask_data_base64: Option<String>,
    softness: f32,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Option<Vec<u8>> {
    let data_url = mask_data_base64?;
    let bytes = decode_data_url_base64(&data_url)?;

    let decoded = image::load_from_memory(&bytes).ok()?;
    let gray = decoded.to_luma8();
    let resized = if gray.width() == full_width && gray.height() == full_height {
        gray
    } else {
        image::imageops::resize(&gray, full_width, full_height, FilterType::Triangle)
    };

    let mut raw = resized.into_raw();

    let softness = softness.clamp(0.0, 1.0);
    let radius = (softness * 10.0).round() as i32;
    if radius >= 1 {
        raw = box_blur_u8(&raw, full_width as usize, full_height as usize, radius as usize);
    }

    if origin_x == 0 && origin_y == 0 && width == full_width && height == full_height {
        return Some(raw);
    }

    Some(crop_mask_region(&raw, full_width, full_height, origin_x, origin_y, width, height))
}

fn generate_ai_subject_mask_region(
    params_value: &Value,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Option<Vec<u8>> {
    let params: AiSubjectMaskParameters = serde_json::from_value(params_value.clone()).ok()?;
    generate_ai_png_mask_region(
        params.mask_data_base64,
        params.softness,
        full_width,
        full_height,
        origin_x,
        origin_y,
        width,
        height,
    )
}

fn generate_ai_environment_mask_region(
    params_value: &Value,
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Option<Vec<u8>> {
    let params: AiEnvironmentMaskParameters = serde_json::from_value(params_value.clone()).ok()?;
    generate_ai_png_mask_region(
        params.mask_data_base64,
        params.softness,
        full_width,
        full_height,
        origin_x,
        origin_y,
        width,
        height,
    )
}

fn crop_mask_region(
    full_mask: &[u8],
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Vec<u8> {
    let mut tile = vec![0u8; (width * height) as usize];
    if full_width == 0 || full_height == 0 || width == 0 || height == 0 {
        return tile;
    }
    for y in 0..height {
        let src_y = origin_y + y;
        if src_y >= full_height {
            continue;
        }
        if origin_x >= full_width {
            continue;
        }
        let src_start = (src_y * full_width + origin_x) as usize;
        let dst_start = (y * width) as usize;
        let copy_len = width.min(full_width - origin_x) as usize;
        let src_end = src_start + copy_len;
        if src_end <= full_mask.len() && dst_start + copy_len <= tile.len() {
            tile[dst_start..dst_start + copy_len].copy_from_slice(&full_mask[src_start..src_end]);
        }
    }
    tile
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

    for y in 0..h {
        let row = y * w;
        let denom = (2 * r + 1) as i32;
        let mut sum: i32 = 0;

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
            let sub_x = x.saturating_sub(r + 1).min(w - 1);
            sum += src[row + add_x] as i32;
            sum -= src[row + sub_x] as i32;
            tmp[row + x] = (sum as f32 / denom as f32).round().clamp(0.0, 255.0) as u8;
        }
    }

    for x in 0..w {
        let denom = (2 * r + 1) as i32;
        let mut sum: i32 = 0;

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
            let sub_y = y.saturating_sub(r + 1).min(h - 1);
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
            "ai-environment" => {
                if let Some(bitmap) = generate_ai_environment_mask(&sub_mask.parameters, width, height) {
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                }
            }
            _ => {}
        }
    }
    mask
}

fn generate_mask_bitmap_region(
    sub_masks: &[SubMaskPayload],
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
    ai_cache: &HashMap<String, Vec<u8>>,
) -> Vec<u8> {
    let len = (width * height) as usize;
    let mut mask = vec![0u8; len];
    for sub_mask in sub_masks.iter().filter(|s| s.visible) {
        match sub_mask.mask_type.as_str() {
            "brush" => apply_brush_submask_region(
                &mut mask,
                sub_mask,
                full_width,
                full_height,
                origin_x,
                origin_y,
                width,
                height,
            ),
            "radial" => {
                let bitmap = generate_radial_mask_region(
                    &sub_mask.parameters,
                    full_width,
                    full_height,
                    origin_x,
                    origin_y,
                    width,
                    height,
                );
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "linear" => {
                let bitmap = generate_linear_mask_region(
                    &sub_mask.parameters,
                    full_width,
                    full_height,
                    origin_x,
                    origin_y,
                    width,
                    height,
                );
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "ai-subject" => {
                if let Some(full_mask) = ai_cache.get(&sub_mask.id) {
                    let bitmap = crop_mask_region(full_mask, full_width, full_height, origin_x, origin_y, width, height);
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                } else if let Some(bitmap) = generate_ai_subject_mask_region(
                    &sub_mask.parameters,
                    full_width,
                    full_height,
                    origin_x,
                    origin_y,
                    width,
                    height,
                ) {
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                }
            }
            "ai-environment" => {
                if let Some(full_mask) = ai_cache.get(&sub_mask.id) {
                    let bitmap = crop_mask_region(full_mask, full_width, full_height, origin_x, origin_y, width, height);
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                } else if let Some(bitmap) = generate_ai_environment_mask_region(
                    &sub_mask.parameters,
                    full_width,
                    full_height,
                    origin_x,
                    origin_y,
                    width,
                    height,
                ) {
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                }
            }
            _ => {}
        }
    }
    mask
}
