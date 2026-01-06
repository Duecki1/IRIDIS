//Code taken from RapidRAW by CyberTimon
//https://github.com/CyberTimon/RapidRAW

mod model;
mod raw_processing;

use anyhow::{Context, Result};
use base64::Engine;
use image::{
    codecs::jpeg::JpegEncoder,
    imageops::FilterType,
    ExtendedColorType,
    ImageBuffer,
    DynamicImage,
};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jlong, jstring, jint, jboolean};
use jni::JNIEnv;
use log::error;
#[cfg(target_os = "android")]
use log::Level;
use raw_processing::develop_raw_image;
use rawler::decoders::{RawDecodeParams, Orientation};
use rawler::rawsource::RawSource;
use rayon::prelude::*;
use serde_json::json;
use serde_json::from_str;
use serde_json::Value;
use std::collections::HashMap;
use std::panic;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, Once, OnceLock};

use model::*;

fn clamp_to_u8(value: f32) -> u8 {
    if !value.is_finite() {
        0
    } else {
        value.clamp(0.0, 255.0).round() as u8
    }
}

fn try_alloc_vec<T: Clone>(len: usize, default: T) -> Result<Vec<T>> {
    let mut vec: Vec<T> = Vec::new();
    vec.try_reserve_exact(len)
        .context("Out of memory during allocation")?;
    vec.resize(len, default);
    Ok(vec)
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
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
}

struct MaskRuntimeDef {
    opacity_factor: f32,
    invert: bool,
    adjustments: AdjustmentValues,
    curves: CurvesRuntime,
    curves_are_active: bool,
    sub_masks: Option<Vec<SubMaskPayload>>,
}

fn parse_mask_defs(values: Vec<Value>) -> Vec<MaskRuntimeDef> {
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
                return Some(MaskRuntimeDef {
                    opacity_factor,
                    invert: def.invert,
                    adjustments: def.adjustments.to_values().normalized(&ADJUSTMENT_SCALES),
                    curves,
                    curves_are_active,
                    sub_masks: Some(def.sub_masks),
                });
            }

            // Backwards-compatible legacy format (top-level enabled + adjustment fields).
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

fn parse_masks(values: Vec<Value>, width: u32, height: u32) -> Vec<MaskRuntime> {
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

fn build_mask_runtimes_for_region(
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
                generate_mask_bitmap_region(sub_masks, full_width, full_height, origin_x, origin_y, width, height, ai_cache)
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

fn build_ai_mask_cache(mask_defs: &[MaskRuntimeDef], width: u32, height: u32) -> HashMap<String, Vec<u8>> {
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

fn mask_selection_at(mask: &MaskRuntime, full_x: u32, full_y: u32) -> f32 {
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

    fn pressure_scale(pressure: f32) -> f32 {
        0.2 + 0.8 * pressure.clamp(0.0, 1.0)
    }

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

    let params: BrushMaskParameters = serde_json::from_value(sub_mask.parameters.clone()).unwrap_or_default();
    let mut events: Vec<BrushEvent> = Vec::new();

    fn pressure_scale(pressure: f32) -> f32 {
        0.2 + 0.8 * pressure.clamp(0.0, 1.0)
    }

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

fn box_blur_f32(src: &[f32], width: usize, height: usize, radius: usize) -> Vec<f32> {
    if radius == 0 || width == 0 || height == 0 {
        return src.to_vec();
    }
    let w = width;
    let h = height;
    let r = radius;
    
    // Allocate buffers
    let mut tmp = vec![0.0f32; w * h];
    let mut dst = vec![0.0f32; w * h];

    // 1. Parallel Horizontal Pass
    // Split the 'tmp' buffer into rows and process them in parallel
    tmp.par_chunks_exact_mut(w)
       .enumerate()
       .for_each(|(y, row_dst)| {
           let row_src_start = y * w;
           // We need to access 'src', which is a slice. Rayon allows concurrent reads.
           let row_src = &src[row_src_start..row_src_start + w];
           
           let denom = (2 * r + 1) as f32;
           let mut sum: f32 = 0.0;

           // Initialize window
           sum += row_src[0] * (r as f32 + 1.0);
           let max_ix = r.min(w - 1);
           for ix in 1..=max_ix {
               sum += row_src[ix];
           }
           let repeats = r.saturating_sub(max_ix) as f32;
           if repeats > 0.0 {
               sum += row_src[w - 1] * repeats;
           }
           row_dst[0] = sum / denom;

           // Slide window
           for x in 1..w {
               let add_x = (x + r).min(w - 1);
               let sub_x = x.saturating_sub(r + 1).min(w - 1);
               sum += row_src[add_x];
               sum -= row_src[sub_x];
               row_dst[x] = sum / denom;
           }
       });

    // 2. Vertical Pass (Still serial for simplicity, but optimized cache access)
    for x in 0..w {
        let denom = (2 * r + 1) as f32;
        let mut sum: f32 = 0.0;
        sum += tmp[x] * (r as f32 + 1.0);
        let max_iy = r.min(h - 1);
        for iy in 1..=max_iy {
            sum += tmp[iy * w + x];
        }
        let repeats = r.saturating_sub(max_iy) as f32;
        if repeats > 0.0 {
            sum += tmp[(h - 1) * w + x] * repeats;
        }
        dst[x] = sum / denom;

        for y in 1..h {
            let add_y = (y + r).min(h - 1);
            let sub_y = y.saturating_sub(r + 1).min(h - 1);
            sum += tmp[add_y * w + x];
            sum -= tmp[sub_y * w + x];
            dst[y * w + x] = sum / denom;
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
            "brush" => apply_brush_submask_region(&mut mask, sub_mask, full_width, full_height, origin_x, origin_y, width, height),
            "radial" => {
                let bitmap = generate_radial_mask_region(&sub_mask.parameters, full_width, full_height, origin_x, origin_y, width, height);
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "linear" => {
                let bitmap = generate_linear_mask_region(&sub_mask.parameters, full_width, full_height, origin_x, origin_y, width, height);
                apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
            }
            "ai-subject" => {
                if let Some(full_mask) = ai_cache.get(&sub_mask.id) {
                    let bitmap = crop_mask_region(full_mask, full_width, full_height, origin_x, origin_y, width, height);
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                } else if let Some(bitmap) =
                    generate_ai_subject_mask_region(&sub_mask.parameters, full_width, full_height, origin_x, origin_y, width, height)
                {
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                }
            }
            "ai-environment" => {
                if let Some(full_mask) = ai_cache.get(&sub_mask.id) {
                    let bitmap = crop_mask_region(full_mask, full_width, full_height, origin_x, origin_y, width, height);
                    apply_submask_bitmap(&mut mask, &bitmap, sub_mask.mode);
                } else if let Some(bitmap) =
                    generate_ai_environment_mask_region(&sub_mask.parameters, full_width, full_height, origin_x, origin_y, width, height)
                {
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

struct DetailBlurLuma {
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
    sharpness: Option<Vec<f32>>,
    clarity: Option<Vec<f32>>,
    structure: Option<Vec<f32>>,
}

const DETAIL_SHARPNESS_RADIUS: usize = 2;
const DETAIL_CLARITY_RADIUS: usize = 8;
const DETAIL_STRUCTURE_RADIUS: usize = 40;

fn build_luma_buffer_region(
    linear: &[f32],
    full_width: u32,
    full_height: u32,
    origin_x: u32,
    origin_y: u32,
    width: u32,
    height: u32,
) -> Vec<f32> {
    let mut luma = Vec::with_capacity((width as usize) * (height as usize));
    if width == 0 || height == 0 || full_width == 0 || full_height == 0 {
        return luma;
    }
    for y in 0..height {
        let full_y = origin_y + y;
        if full_y >= full_height {
            luma.extend(std::iter::repeat(0.0).take(width as usize));
            continue;
        }
        for x in 0..width {
            let full_x = origin_x + x;
            if full_x >= full_width {
                luma.push(0.0);
                continue;
            }
            let idx = (full_y * full_width + full_x) as usize;
            let base = idx * 3;
            let color = [linear[base], linear[base + 1], linear[base + 2]];
            luma.push(get_luma(color));
        }
    }
    luma
}

fn build_detail_blurs(
    linear: &[f32],
    width: u32,
    height: u32,
    want_sharpness: bool,
    want_clarity: bool,
    want_structure: bool,
) -> Option<DetailBlurLuma> {
    if width == 0 || height == 0 {
        return None;
    }
    if !want_sharpness && !want_clarity && !want_structure {
        return None;
    }

    build_detail_blurs_region(
        linear,
        width,
        height,
        0,
        0,
        width,
        height,
        DETAIL_SHARPNESS_RADIUS,
        DETAIL_CLARITY_RADIUS,
        DETAIL_STRUCTURE_RADIUS,
        want_sharpness,
        want_clarity,
        want_structure,
    )
}

fn build_detail_blurs_region(
    linear: &[f32],
    full_width: u32,
    full_height: u32,
    region_x: u32,
    region_y: u32,
    region_w: u32,
    region_h: u32,
    sharpness_radius: usize,
    clarity_radius: usize,
    structure_radius: usize,
    want_sharpness: bool,
    want_clarity: bool,
    want_structure: bool,
) -> Option<DetailBlurLuma> {
    if full_width == 0 || full_height == 0 || region_w == 0 || region_h == 0 {
        return None;
    }
    if !want_sharpness && !want_clarity && !want_structure {
        return None;
    }

    let mut max_radius = 0usize;
    if want_sharpness {
        max_radius = max_radius.max(sharpness_radius);
    }
    if want_clarity {
        max_radius = max_radius.max(clarity_radius);
    }
    if want_structure {
        max_radius = max_radius.max(structure_radius);
    }
    let max_radius = max_radius as u32;
    let pad_x = max_radius;
    let pad_y = max_radius;

    let start_x = region_x.saturating_sub(pad_x);
    let start_y = region_y.saturating_sub(pad_y);
    let end_x = (region_x + region_w).saturating_add(pad_x).min(full_width);
    let end_y = (region_y + region_h).saturating_add(pad_y).min(full_height);
    let padded_w = (end_x - start_x).max(1);
    let padded_h = (end_y - start_y).max(1);

    let luma = build_luma_buffer_region(linear, full_width, full_height, start_x, start_y, padded_w, padded_h);
    let sharpness = if want_sharpness {
        Some(box_blur_f32(&luma, padded_w as usize, padded_h as usize, sharpness_radius))
    } else {
        None
    };
    let clarity = if want_clarity {
        Some(box_blur_f32(&luma, padded_w as usize, padded_h as usize, clarity_radius))
    } else {
        None
    };
    let structure = if want_structure {
        Some(box_blur_f32(&luma, padded_w as usize, padded_h as usize, structure_radius))
    } else {
        None
    };

    Some(DetailBlurLuma {
        origin_x: start_x,
        origin_y: start_y,
        width: padded_w,
        height: padded_h,
        sharpness,
        clarity,
        structure,
    })
}

fn detail_blur_index(blurs: &DetailBlurLuma, full_x: u32, full_y: u32) -> Option<usize> {
    if full_x < blurs.origin_x || full_y < blurs.origin_y {
        return None;
    }
    let local_x = full_x - blurs.origin_x;
    let local_y = full_y - blurs.origin_y;
    if local_x >= blurs.width || local_y >= blurs.height {
        return None;
    }
    Some((local_y * blurs.width + local_x) as usize)
}

fn sample_linear_color(linear: &[f32], width: u32, height: u32, x: u32, y: u32) -> [f32; 3] {
    if width == 0 || height == 0 {
        return [0.0, 0.0, 0.0];
    }
    let clamped_x = x.min(width.saturating_sub(1));
    let clamped_y = y.min(height.saturating_sub(1));
    let idx = (clamped_y * width + clamped_x) as usize * 3;
    [linear[idx], linear[idx + 1], linear[idx + 2]]
}

fn sample_ca_corrected_color(
    linear: &[f32],
    width: u32,
    height: u32,
    x: u32,
    y: u32,
    ca_rc: f32,
    ca_by: f32,
) -> [f32; 3] {
    if width == 0 || height == 0 {
        return [0.0, 0.0, 0.0];
    }
    if ca_rc.abs() < 0.000001 && ca_by.abs() < 0.000001 {
        return sample_linear_color(linear, width, height, x, y);
    }

    let center_x = width as f32 / 2.0;
    let center_y = height as f32 / 2.0;
    let current_x = x as f32;
    let current_y = y as f32;
    let dx = current_x - center_x;
    let dy = current_y - center_y;
    let dist = (dx * dx + dy * dy).sqrt();
    if dist <= 0.000001 {
        return sample_linear_color(linear, width, height, x, y);
    }

    let dir_x = dx / dist;
    let dir_y = dy / dist;
    let red_shift_x = dir_x * dist * ca_rc;
    let red_shift_y = dir_y * dist * ca_rc;
    let blue_shift_x = dir_x * dist * ca_by;
    let blue_shift_y = dir_y * dist * ca_by;

    let red_x = (current_x - red_shift_x).round().clamp(0.0, (width - 1) as f32) as u32;
    let red_y = (current_y - red_shift_y).round().clamp(0.0, (height - 1) as f32) as u32;
    let blue_x = (current_x - blue_shift_x).round().clamp(0.0, (width - 1) as f32) as u32;
    let blue_y = (current_y - blue_shift_y).round().clamp(0.0, (height - 1) as f32) as u32;

    let r = sample_linear_color(linear, width, height, red_x, red_y)[0];
    let g = sample_linear_color(linear, width, height, x, y)[1];
    let b = sample_linear_color(linear, width, height, blue_x, blue_y)[2];

    [r, g, b]
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
    let threshold = 0.5;
    let mask = smoothstep(threshold, 1.2, luma);

    if mask < 0.001 {
        return colors;
    }

    if highlights < 0.0 {
        // Multiplicative recovery keeps chroma while darkening clipped highlights.
        let recovery_strength = 1.0 + (highlights * 0.5);
        let factor = 1.0 * (1.0 - mask) + recovery_strength * mask;

        colors[0] *= factor;
        colors[1] *= factor;
        colors[2] *= factor;
    } else {
        let adjustment = highlights * 1.75;
        let factor = 2f32.powf(adjustment);
        for i in 0..3 {
            let final_adjusted = colors[i] * factor;
            colors[i] = colors[i] + (final_adjusted - colors[i]) * mask;
        }
    }

    colors
}

fn apply_dehaze(color: [f32; 3], amount: f32) -> [f32; 3] {
    if amount.abs() < 0.00001 {
        return color;
    }

    let atmospheric_light = [0.95, 0.97, 1.0];
    if amount > 0.0 {
        let dark_channel = color[0].min(color[1]).min(color[2]);
        let transmission_estimate = 1.0 - dark_channel;
        let t = 1.0 - amount * transmission_estimate;
        let recovered = [
            (color[0] - atmospheric_light[0]) / t.max(0.1) + atmospheric_light[0],
            (color[1] - atmospheric_light[1]) / t.max(0.1) + atmospheric_light[1],
            (color[2] - atmospheric_light[2]) / t.max(0.1) + atmospheric_light[2],
        ];
        let mut result = [
            color[0] + (recovered[0] - color[0]) * amount,
            color[1] + (recovered[1] - color[1]) * amount,
            color[2] + (recovered[2] - color[2]) * amount,
        ];
        result = [
            0.5 + (result[0] - 0.5) * (1.0 + amount * 0.15),
            0.5 + (result[1] - 0.5) * (1.0 + amount * 0.15),
            0.5 + (result[2] - 0.5) * (1.0 + amount * 0.15),
        ];
        let luma = get_luma(result);
        let sat_mix = 1.0 + amount * 0.1;
        [
            luma + (result[0] - luma) * sat_mix,
            luma + (result[1] - luma) * sat_mix,
            luma + (result[2] - luma) * sat_mix,
        ]
    } else {
        let mix = amount.abs() * 0.7;
        [
            color[0] + (atmospheric_light[0] - color[0]) * mix,
            color[1] + (atmospheric_light[1] - color[1]) * mix,
            color[2] + (atmospheric_light[2] - color[2]) * mix,
        ]
    }
}

fn apply_creative_color(mut colors: [f32; 3], saturation: f32, vibrance: f32) -> [f32; 3] {
    let luma = get_luma(colors);

    if saturation != 0.0 {
        let sat_mix = 1.0 + saturation;
        colors[0] = luma + (colors[0] - luma) * sat_mix;
        colors[1] = luma + (colors[1] - luma) * sat_mix;
        colors[2] = luma + (colors[2] - luma) * sat_mix;
    }

    if vibrance != 0.0 {
        let c_max = colors[0].max(colors[1]).max(colors[2]);
        let c_min = colors[0].min(colors[1]).min(colors[2]);
        let delta = c_max - c_min;

        if delta >= 0.02 {
            let current_sat = delta / c_max.max(0.001);

            if vibrance > 0.0 {
                let sat_mask = 1.0 - smoothstep(0.4, 0.9, current_sat);

                let hue = rgb_to_hue(colors);
                let skin_center = 25.0;
                let hue_dist = (hue - skin_center).abs().min(360.0 - (hue - skin_center).abs());
                let is_skin = smoothstep(35.0, 10.0, hue_dist);
                let skin_dampener = 1.0 - is_skin * 0.4;

                let amount = vibrance * sat_mask * skin_dampener * 3.0;
                let vib_mix = 1.0 + amount;
                colors[0] = luma + (colors[0] - luma) * vib_mix;
                colors[1] = luma + (colors[1] - luma) * vib_mix;
                colors[2] = luma + (colors[2] - luma) * vib_mix;
            } else {
                let desat_mask = 1.0 - smoothstep(0.2, 0.8, current_sat);
                let amount = vibrance * desat_mask;
                let vib_mix = 1.0 + amount;
                colors[0] = luma + (colors[0] - luma) * vib_mix;
                colors[1] = luma + (colors[1] - luma) * vib_mix;
                colors[2] = luma + (colors[2] - luma) * vib_mix;
            }
        }
    }

    colors
}

fn compute_centre_mask(x: u32, y: u32, width: u32, height: u32) -> f32 {
    if width == 0 || height == 0 {
        return 0.0;
    }
    let full_w = width as f32;
    let full_h = height as f32;
    let aspect = full_h / full_w;
    let uv_x = (x as f32 / full_w - 0.5) * 2.0;
    let uv_y = (y as f32 / full_h - 0.5) * 2.0;
    let d = ((uv_x * uv_x) + (uv_y * aspect) * (uv_y * aspect)).sqrt() * 0.5;
    let midpoint = 0.4;
    let feather = 0.375;
    let vignette_mask = smoothstep(midpoint - feather, midpoint + feather, d);
    1.0 - vignette_mask
}

fn apply_centre_tonal_and_color(colors: [f32; 3], centre_amount: f32, centre_mask: f32) -> [f32; 3] {
    if centre_amount.abs() < 0.00001 {
        return colors;
    }

    let exposure_boost = centre_mask * centre_amount * 0.5;
    let mut processed = apply_filmic_brightness(colors, exposure_boost);

    let vibrance_boost = centre_mask * centre_amount * 0.4;
    let saturation_center_boost = centre_mask * centre_amount * 0.3;
    let saturation_edge_effect = -(1.0 - centre_mask) * centre_amount * 0.8;
    let total_saturation = saturation_center_boost + saturation_edge_effect;

    processed = apply_creative_color(processed, total_saturation, vibrance_boost);
    processed
}

fn apply_local_contrast_from_luma(color: [f32; 3], blurred_luma: f32, amount: f32) -> [f32; 3] {
    if amount.abs() < 0.00001 {
        return color;
    }

    let center_luma = get_luma(color);
    let shadow_protection = smoothstep(0.0, 0.1, center_luma);
    let highlight_protection = 1.0 - smoothstep(0.6, 1.0, center_luma);
    let midtone_mask = shadow_protection * highlight_protection;
    if midtone_mask < 0.001 {
        return color;
    }

    let safe_center_luma = center_luma.max(0.0001);
    let blurred_color = [
        color[0] * (blurred_luma / safe_center_luma),
        color[1] * (blurred_luma / safe_center_luma),
        color[2] * (blurred_luma / safe_center_luma),
    ];

    let final_color = if amount < 0.0 {
        [
            color[0] + (blurred_color[0] - color[0]) * -amount,
            color[1] + (blurred_color[1] - color[1]) * -amount,
            color[2] + (blurred_color[2] - color[2]) * -amount,
        ]
    } else {
        let detail = [
            color[0] - blurred_color[0],
            color[1] - blurred_color[1],
            color[2] - blurred_color[2],
        ];
        [
            color[0] + detail[0] * amount * 1.5,
            color[1] + detail[1] * amount * 1.5,
            color[2] + detail[2] * amount * 1.5,
        ]
    };

    [
        color[0] + (final_color[0] - color[0]) * midtone_mask,
        color[1] + (final_color[1] - color[1]) * midtone_mask,
        color[2] + (final_color[2] - color[2]) * midtone_mask,
    ]
}

fn apply_color_adjustments(mut colors: [f32; 3], settings: &AdjustmentValues, centre_mask: f32) -> [f32; 3] {
    // Exposure (linear, RapidRAW-like): color *= 2^exposure
    if settings.exposure != 0.0 {
        let factor = 2f32.powf(settings.exposure);
        colors[0] *= factor;
        colors[1] *= factor;
        colors[2] *= factor;
    }

    colors = apply_dehaze(colors, settings.dehaze);
    colors = apply_centre_tonal_and_color(colors, settings.centre, centre_mask);

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

    colors = apply_creative_color(colors, settings.saturation, settings.vibrance);
    
    // Keep HDR headroom for tone mapping later, but clamp for safety to avoid NaNs/inf and
    // runaway values on extreme slider settings.
    const MAX_HDR: f32 = 64.0;
    for channel in colors.iter_mut() {
        if !channel.is_finite() {
            *channel = 0.0;
            continue;
        }
        *channel = channel.clamp(0.0, MAX_HDR);
    }

    colors
}

fn apply_local_contrast_stack(
    mut colors: [f32; 3],
    full_x: u32,
    full_y: u32,
    centre_mask: f32,
    settings: &AdjustmentValues,
    blurs: &DetailBlurLuma,
) -> [f32; 3] {
    let idx = match detail_blur_index(blurs, full_x, full_y) {
        Some(idx) => idx,
        None => return colors,
    };
    if settings.sharpness.abs() > 0.00001 {
        if let Some(blurred) = blurs.sharpness.as_ref() {
            if let Some(luma) = blurred.get(idx) {
                colors = apply_local_contrast_from_luma(colors, *luma, settings.sharpness);
            }
        }
    }
    if settings.clarity.abs() > 0.00001 {
        if let Some(blurred) = blurs.clarity.as_ref() {
            if let Some(luma) = blurred.get(idx) {
                colors = apply_local_contrast_from_luma(colors, *luma, settings.clarity);
            }
        }
    }
    if settings.structure.abs() > 0.00001 {
        if let Some(blurred) = blurs.structure.as_ref() {
            if let Some(luma) = blurred.get(idx) {
                colors = apply_local_contrast_from_luma(colors, *luma, settings.structure);
            }
        }
    }
    if settings.centre.abs() > 0.00001 {
        if let Some(blurred) = blurs.clarity.as_ref() {
            if let Some(luma) = blurred.get(idx) {
                let clarity_strength = settings.centre * (2.0 * centre_mask - 1.0) * 0.9;
                if clarity_strength.abs() > 0.001 {
                    colors = apply_local_contrast_from_luma(colors, *luma, clarity_strength);
                }
            }
        }
    }
    colors
}

fn tonemap_aces_fitted(x: f32) -> f32 {
    // Narkowicz 2015, "ACES Filmic Tone Mapping Curve"
    // https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/
    let x = x.max(0.0);
    let a = 2.51;
    let b = 0.03;
    let c = 2.43;
    let d = 0.59;
    let e = 0.14;
    let y = (x * (a * x + b)) / (x * (c * x + d) + e);
    if y.is_finite() { y.clamp(0.0, 1.0) } else { 0.0 }
}

fn tone_map_basic(colors: [f32; 3]) -> [f32; 3] {
    [
        tonemap_aces_fitted(colors[0]),
        tonemap_aces_fitted(colors[1]),
        tonemap_aces_fitted(colors[2]),
    ]
}

fn tone_map_agx(colors: [f32; 3]) -> [f32; 3] {
    // Luma-preserving ACES fit + gentle highlight desaturation.
    let rgb = [colors[0].max(0.0), colors[1].max(0.0), colors[2].max(0.0)];
    let luma = get_luma(rgb);
    if !luma.is_finite() || luma <= 1.0e-6 {
        return [0.0, 0.0, 0.0];
    }

    let mapped_luma = tonemap_aces_fitted(luma);
    let scale = mapped_luma / luma;
    let mut out = [rgb[0] * scale, rgb[1] * scale, rgb[2] * scale];

    let out_luma = get_luma(out);
    let desat = smoothstep(0.75, 1.0, out_luma);
    for c in out.iter_mut() {
        *c = *c + (out_luma - *c) * desat * 0.25;
        *c = c.clamp(0.0, 1.0);
    }

    out
}

fn tone_map(colors: [f32; 3], mapper: ToneMapper) -> [f32; 3] {
    match mapper {
        ToneMapper::Basic => tone_map_basic(colors),
        ToneMapper::Agx => tone_map_agx(colors),
    }
}

fn linear_to_srgb(linear: f32) -> f32 {
    // Encode scene-linear (extended range allowed) into sRGB.
    let v = linear.max(0.0);
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

    // RapidRAW applies default brightness and contrast to RAW images when using the Basic tone mapper.
    const BRIGHTNESS_GAMMA: f32 = 1.1;
    const CONTRAST_MIX: f32 = 0.75;

    // Keep HDR headroom by only removing negative values before applying the display-style curve.
    let srgb = [
        linear_to_srgb(colors[0].max(0.0)),
        linear_to_srgb(colors[1].max(0.0)),
        linear_to_srgb(colors[2].max(0.0)),
    ];

    // Apply brightness gamma
    let brightened = [
        srgb[0].signum() * srgb[0].abs().powf(1.0 / BRIGHTNESS_GAMMA),
        srgb[1].signum() * srgb[1].abs().powf(1.0 / BRIGHTNESS_GAMMA),
        srgb[2].signum() * srgb[2].abs().powf(1.0 / BRIGHTNESS_GAMMA),
    ];

    // Apply contrast S-curve while leaving >1.0 values untouched by the non-linear section.
    let apply_contrast = |v: f32| -> f32 {
        if v <= 1.0 {
            v * v * (3.0 - 2.0 * v)
        } else {
            v
        }
    };

    let contrast_curve = [
        apply_contrast(brightened[0]),
        apply_contrast(brightened[1]),
        apply_contrast(brightened[2]),
    ];

    let contrasted = [
        brightened[0] + (contrast_curve[0] - brightened[0]) * CONTRAST_MIX,
        brightened[1] + (contrast_curve[1] - brightened[1]) * CONTRAST_MIX,
        brightened[2] + (contrast_curve[2] - brightened[2]) * CONTRAST_MIX,
    ];

    [
        srgb_to_linear(contrasted[0]),
        srgb_to_linear(contrasted[1]),
        srgb_to_linear(contrasted[2]),
    ]
}

type LinearImage = ImageBuffer<image::Rgb<f32>, Vec<f32>>;

// Compact u16 storage for RAM efficiency (144MB vs 288MB for 24MP image)
type CompactImage = ImageBuffer<image::Rgb<u16>, Vec<u16>>;

// Convert f32 linear image to u16 compact format (50% memory reduction)
fn compact_image(input: &LinearImage) -> CompactImage {
    let (w, h) = input.dimensions();
    let raw_f32 = input.as_raw();
    let mut raw_u16 = Vec::with_capacity((w * h * 3) as usize);

    for chunk in raw_f32.chunks_exact(3) {
        let r = (chunk[0] * 65535.0).clamp(0.0, 65535.0) as u16;
        let g = (chunk[1] * 65535.0).clamp(0.0, 65535.0) as u16;
        let b = (chunk[2] * 65535.0).clamp(0.0, 65535.0) as u16;
        raw_u16.extend_from_slice(&[r, g, b]);
    }

    ImageBuffer::from_vec(w, h, raw_u16).unwrap()
}

// Decode RAW to compact u16 format without rotation (for export)
fn decode_raw_to_compact(
    raw_bytes: &[u8],
    fast_demosaic: bool,
    max_w: Option<u32>,
    max_h: Option<u32>
) -> Result<(CompactImage, Orientation)> {
    let highlight_compression = 2.5;

    // 1. Decode RAW (returns unrotated u16 + orientation)
    let (dyn_img, orientation) = develop_raw_image(raw_bytes, fast_demosaic, highlight_compression)
        .context("Failed to decode RAW")?;

    let src_w = dyn_img.width();
    let src_h = dyn_img.height();

    // 2. Calculate target dimensions
    let mut dst_w = src_w;
    let mut dst_h = src_h;

    if let (Some(mw), Some(mh)) = (max_w, max_h) {
        if src_w > mw || src_h > mh {
            let scale = (mw as f32 / src_w as f32).min(mh as f32 / src_h as f32);
            dst_w = (src_w as f32 * scale).max(1.0) as u32;
            dst_h = (src_h as f32 * scale).max(1.0) as u32;
        }
    }

    // 3. Resize if needed (using nearest neighbor for speed)
    let output_len = (dst_w as usize) * (dst_h as usize) * 3;
    let mut raw_u16 = Vec::with_capacity(output_len);

    match dyn_img {
        DynamicImage::ImageRgb16(buf) => {
            let s = buf.as_raw();
            let x_ratio = src_w as f32 / dst_w as f32;
            let y_ratio = src_h as f32 / dst_h as f32;

            for y in 0..dst_h {
                let sy = ((y as f32) * y_ratio).min((src_h - 1) as f32) as u32;
                let y_offset = (sy * src_w) as usize * 3;
                for x in 0..dst_w {
                    let sx = ((x as f32) * x_ratio).min((src_w - 1) as f32) as u32;
                    let idx = y_offset + (sx as usize * 3);
                    raw_u16.push(s[idx]);
                    raw_u16.push(s[idx+1]);
                    raw_u16.push(s[idx+2]);
                }
            }
        },
        _ => return Err(anyhow::anyhow!("Unexpected image format")),
    }

    Ok((ImageBuffer::from_vec(dst_w, dst_h, raw_u16).unwrap(), orientation))
}

// Virtual transformation state - no physical rotation needed
struct TransformState {
    source_w: u32,
    source_h: u32,
    output_w: u32,
    output_h: u32,
    orientation_steps: u8,
    flip_h: bool,
    flip_v: bool,
    rotation_rad: f32,
    crop: Option<CropPayload>,
    center_x: f32,
    center_y: f32,
}

impl TransformState {
    fn new(w: u32, h: u32, payload: &AdjustmentsPayload, base_orientation: Orientation) -> Self {
        // 1. Convert base orientation to steps and flips
        let (base_steps, base_flip_h, base_flip_v) = match base_orientation {
            Orientation::Normal | Orientation::Unknown => (0, false, false),
            Orientation::Rotate90 => (1, false, false),
            Orientation::Rotate180 => (2, false, false),
            Orientation::Rotate270 => (3, false, false),
            Orientation::HorizontalFlip => (0, true, false),
            Orientation::VerticalFlip => (0, false, true),
            Orientation::Transpose => (1, true, false),
            Orientation::Transverse => (3, true, false),
        };

        // 2. Combine with user adjustments
        let user_steps = payload.orientation_steps % 4;
        let total_steps = (base_steps + user_steps) % 4;
        let total_flip_h = base_flip_h ^ payload.flip_horizontal;
        let total_flip_v = base_flip_v ^ payload.flip_vertical;
        
        // --- IMPORTANT CHANGE HERE ---
        // Calculate final dimensions *before* applying crop logic
        let (rotated_w, rotated_h) = if total_steps % 2 == 1 { (h, w) } else { (w, h) };
        
        let (mut final_w, mut final_h) = (rotated_w, rotated_h);

        // Now apply crop based on these potentially swapped dimensions
        if let Some(crop) = &payload.crop {
            // Use the rotated dimensions to calculate the crop rectangle
            let (_, _, cw, ch) = crop_rect_pixels(rotated_w, rotated_h, crop);
            final_w = cw;
            final_h = ch;
        }
        // --- END OF CHANGE ---

        Self {
            source_w: w,
            source_h: h,
            output_w: final_w,
            output_h: final_h,
            orientation_steps: total_steps,
            flip_h: total_flip_h,
            flip_v: total_flip_v,
            rotation_rad: payload.rotation.to_radians(),
            crop: payload.crop.clone(),
            center_x: (final_w as f32 - 1.0) / 2.0,
            center_y: (final_h as f32 - 1.0) / 2.0,
        }
    }

    // Maps output (x,y) to source (sx,sy) coordinates - the magic of virtual rotation
    #[inline(always)]
    fn map_coord(&self, x: u32, y: u32) -> Option<(f32, f32)> {
        let mut fx = x as f32;
        let mut fy = y as f32;

        // 1. Inverse Crop (Add offset)
        if let Some(crop) = &self.crop {
            let (oriented_w, oriented_h) = if self.orientation_steps % 2 == 1 { 
                (self.source_h, self.source_w) 
            } else { 
                (self.source_w, self.source_h) 
            };
            let (cx, cy, _, _) = crop_rect_pixels(oriented_w, oriented_h, crop);
            fx += cx as f32;
            fy += cy as f32;
        }

        // 2. Inverse Arbitrary Rotation (Rotate about center)
        if self.rotation_rad.abs() > 0.0001 {
            let dx = fx - self.center_x;
            let dy = fy - self.center_y;
            let cos_a = self.rotation_rad.cos();
            let sin_a = self.rotation_rad.sin();
            // Inverse rotation: -angle
            fx = dx * cos_a + dy * sin_a + self.center_x;
            fy = -dx * sin_a + dy * cos_a + self.center_y;
        }

        // 3. Inverse Flips
        let (cur_w, cur_h) = if self.orientation_steps % 2 == 1 {
            (self.source_h, self.source_w)
        } else {
            (self.source_w, self.source_h)
        };

        if self.flip_h { fx = cur_w as f32 - 1.0 - fx; }
        if self.flip_v { fy = cur_h as f32 - 1.0 - fy; }

        // 4. Inverse Orientation (Swap/Flip coords to match source)
        // CRITICAL FIX: Use correct dimensions after swap for each rotation
        let (sx, sy) = match self.orientation_steps {
            0 => (fx, fy),
            1 => (fy, (self.source_h as f32 - 1.0) - fx), // 90° CW: x maps to y, y maps to (H-1-x)
            2 => ((self.source_w as f32 - 1.0) - fx, (self.source_h as f32 - 1.0) - fy), // 180°
            3 => ((self.source_w as f32 - 1.0) - fy, fx), // 270° CW: x maps to (W-1-y), y maps to x
            _ => (fx, fy),
        };

        // Bounds check
        if sx < 0.0 || sy < 0.0 || sx >= self.source_w as f32 || sy >= self.source_h as f32 {
            return None;
        }

        Some((sx, sy))
    }
}

// Sample from u16 image using bilinear interpolation, return f32
fn sample_virtual(img: &CompactImage, x: f32, y: f32) -> [f32; 3] {
    let w = img.width();
    let h = img.height();
    
    let x0 = x.floor() as u32;
    let y0 = y.floor() as u32;
    let x1 = (x0 + 1).min(w - 1);
    let y1 = (y0 + 1).min(h - 1);
    
    let wx = x - x0 as f32;
    let wy = y - y0 as f32;
    
    let get_p = |xx, yy| {
        let p = img.get_pixel(xx, yy);
        [p[0] as f32 / 65535.0, p[1] as f32 / 65535.0, p[2] as f32 / 65535.0]
    };
    
    let p00 = get_p(x0, y0);
    let p10 = get_p(x1, y0);
    let p01 = get_p(x0, y1);
    let p11 = get_p(x1, y1);
    
    let mut out = [0.0; 3];
    for i in 0..3 {
        let top = p00[i] * (1.0 - wx) + p10[i] * wx;
        let bot = p01[i] * (1.0 - wx) + p11[i] * wx;
        out[i] = top * (1.0 - wy) + bot * wy;
    }
    out
}

// Helper to extract a small f32 tile for detail blur calculations
fn extract_tile_f32(
    source: &CompactImage, 
    transform: &TransformState, 
    tx: u32, 
    ty: u32, 
    tw: u32, 
    th: u32
) -> Vec<f32> {
    let mut tile = Vec::with_capacity((tw * th * 3) as usize);
    for y in 0..th {
        for x in 0..tw {
            if let Some((sx, sy)) = transform.map_coord(tx + x, ty + y) {
                let c = sample_virtual(source, sx, sy);
                tile.extend_from_slice(&c);
            } else {
                tile.extend_from_slice(&[0.0, 0.0, 0.0]);
            }
        }
    }
    tile
}

// Physical rotation helper (used for preview only)
fn apply_orientation_physical(image: DynamicImage, orientation: Orientation) -> DynamicImage {
    match orientation {
        Orientation::Normal | Orientation::Unknown => image,
        Orientation::HorizontalFlip => image.fliph(),
        Orientation::Rotate180 => image.rotate180(),
        Orientation::VerticalFlip => image.flipv(),
        Orientation::Transpose => image.rotate90().flipv(),
        Orientation::Rotate90 => image.rotate90(),
        Orientation::Transverse => image.rotate90().fliph(),
        Orientation::Rotate270 => image.rotate270(),
    }
}

fn develop_preview_linear(
    raw_bytes: &[u8],
    fast_demosaic: bool,
    max_width: Option<u32>,
    max_height: Option<u32>,
) -> Result<LinearImage> {
    let highlight_compression = 2.5; // RapidRAW default
    
    // 1. Decode (returns unrotated u16 image + orientation)
    let (mut dynamic_image, orientation) = develop_raw_image(raw_bytes, fast_demosaic, highlight_compression)
        .context("Failed to decode RAW image")?;

    // 2. If a maximum size was requested, downscale BEFORE the per-pixel adjustments
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

    // 3. Convert to f32
    let linear_img = dynamic_image.to_rgb32f();

    // 4. Apply physical orientation (since this is preview, the image is small)
    let rotated = apply_orientation_physical(DynamicImage::ImageRgb32F(linear_img), orientation);
    
    Ok(rotated.to_rgb32f())
}

fn rotate_about_center_rgb32f(image: &LinearImage, rotation_degrees: f32) -> LinearImage {
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
    let len = match (width as usize)
        .checked_mul(height as usize)
        .and_then(|v| v.checked_mul(3))
    {
        Some(v) => v,
        None => {
            error!("Rotation buffer size overflow for {}x{}", width, height);
            return image.clone();
        }
    };
    let mut out = match try_alloc_vec(len, 0.0f32) {
        Ok(v) => v,
        Err(err) => {
            error!("OOM during rotation: {}", err);
            return image.clone();
        }
    };

    fn get_pixel(src: &[f32], width: u32, x: u32, y: u32) -> [f32; 3] {
        let idx = ((y as usize) * (width as usize) + (x as usize)) * 3;
        [src[idx], src[idx + 1], src[idx + 2]]
    }

    fn bilinear(src: &[f32], width: u32, height: u32, x: f32, y: f32) -> [f32; 3] {
        if !x.is_finite() || !y.is_finite() {
            return [0.0, 0.0, 0.0];
        }
        let max_x = width.saturating_sub(1) as f32;
        let max_y = height.saturating_sub(1) as f32;
        if x < 0.0 || y < 0.0 || x > max_x || y > max_y {
            return [0.0, 0.0, 0.0];
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

        let mut out = [0.0f32; 3];
        for c in 0..3 {
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

            let rgb = bilinear(src, width, height, src_x, src_y);
            let idx = ((y as usize) * (width as usize) + (x as usize)) * 3;
            out[idx..idx + 3].copy_from_slice(&rgb);
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

fn crop_rect_pixels(img_w: u32, img_h: u32, crop: &CropPayload) -> (u32, u32, u32, u32) {
    if img_w == 0 || img_h == 0 {
        return (0, 0, 0, 0);
    }

    let is_normalized = crop.x <= 1.5 && crop.y <= 1.5 && crop.width <= 1.5 && crop.height <= 1.5;

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

    (x_u, y_u, w_u, h_u)
}

fn apply_transformations(mut linear: LinearImage, payload: &AdjustmentsPayload) -> LinearImage {
    let steps = payload.orientation_steps % 4;
    let flip_h = payload.flip_horizontal;
    let flip_v = payload.flip_vertical;
    let rotation = payload.rotation;
    let has_crop = payload.crop.is_some();

    let has_transform = steps != 0 || flip_h || flip_v || rotation.abs() > 0.0001 || has_crop;
    if !has_transform {
        return linear;
    }

    linear = match steps {
        1 => image::imageops::rotate90(&linear),
        2 => image::imageops::rotate180(&linear),
        3 => image::imageops::rotate270(&linear),
        _ => linear,
    };

    if flip_h {
        linear = image::imageops::flip_horizontal(&linear);
    }
    if flip_v {
        linear = image::imageops::flip_vertical(&linear);
    }

    if rotation.abs() > 0.0001 {
        linear = rotate_about_center_rgb32f(&linear, rotation);
    }

    if let Some(crop) = payload.crop.as_ref() {
        linear = apply_crop_linear(&linear, crop);
    }

    linear
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
    debug_assert_eq!(linear.len(), (width as usize) * (height as usize) * 3);

    let need_sharpness =
        adjustment_values.sharpness.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.sharpness.abs() > 0.00001);
    let need_clarity =
        adjustment_values.clarity.abs() > 0.00001 ||
            adjustment_values.centre.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| {
                m.adjustments.clarity.abs() > 0.00001 || m.adjustments.centre.abs() > 0.00001
            });
    let need_structure =
        adjustment_values.structure.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.structure.abs() > 0.00001);
    let need_centre_mask =
        adjustment_values.centre.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.centre.abs() > 0.00001);

    let detail_blurs = build_detail_blurs(linear, width, height, need_sharpness, need_clarity, need_structure);

    let ca_rc = adjustment_values.chromatic_aberration_red_cyan;
    let ca_by = adjustment_values.chromatic_aberration_blue_yellow;
    let ca_active = ca_rc.abs() > 0.000001 || ca_by.abs() > 0.000001;

    let len = (width as usize)
        .checked_mul(height as usize)
        .and_then(|v| v.checked_mul(3))
        .context("RGB buffer size overflow")?;
    let mut rgb = try_alloc_vec(len, 0u8)?;

    // --- PARALLEL PROCESSING START ---
    // Use Rayon to split the image into chunks and process on all cores
    rgb.par_chunks_exact_mut(3)
        .enumerate()
        .for_each(|(idx, out)| {
            // Re-calculate x, y from linear index
            let x = (idx as u32) % width;
            let y = (idx as u32) / width;
            let base = idx * 3;

            // 1. Fetch Linear Pixel
            let mut colors = if ca_active {
                sample_ca_corrected_color(linear, width, height, x, y, ca_rc, ca_by)
            } else {
                [linear[base], linear[base + 1], linear[base + 2]]
            };

            // 2. Default Processing
            colors = apply_default_raw_processing(colors, use_basic_tone_mapper);

            // 3. Detail & Local Contrast
            let centre_mask = if need_centre_mask { compute_centre_mask(x, y, width, height) } else { 0.0 };
            
            if let Some(blurs) = &detail_blurs {
                colors = apply_local_contrast_stack(colors, x, y, centre_mask, &adjustment_values, blurs);
            }

            // 4. Global Adjustments
            let mut composite = apply_color_adjustments(colors, &adjustment_values, centre_mask);

            // 5. Mask Blending
            for mask in mask_runtimes {
                let mut selection = mask_selection_at(mask, x, y);
                if mask.invert { selection = 1.0 - selection; }
                
                let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                if influence > 0.001 {
                    let mut mask_base = composite;
                    if let Some(blurs) = &detail_blurs {
                        mask_base = apply_local_contrast_stack(mask_base, x, y, centre_mask, &mask.adjustments, blurs);
                    }
                    let mask_adjusted = apply_color_adjustments(mask_base, &mask.adjustments, centre_mask);
                    composite = [
                        composite[0] + (mask_adjusted[0] - composite[0]) * influence,
                        composite[1] + (mask_adjusted[1] - composite[1]) * influence,
                        composite[2] + (mask_adjusted[2] - composite[2]) * influence,
                    ];
                }
            }

            // 6. Tone Mapping
            composite = tone_map(composite, adjustment_values.tone_mapper);

            // 7. Linear -> sRGB
            let mut srgb = [
                linear_to_srgb(composite[0]),
                linear_to_srgb(composite[1]),
                linear_to_srgb(composite[2]),
            ];

            // 8. Curves
            if curves_are_active {
                srgb = global_curves.apply_all(srgb);
                for mask in mask_runtimes {
                    if mask.curves_are_active {
                        let mut selection = mask_selection_at(mask, x, y);
                        if mask.invert { selection = 1.0 - selection; }
                        let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                        if influence > 0.001 {
                            let mask_curved = mask.curves.apply_all(srgb);
                            srgb = [
                                srgb[0] + (mask_curved[0] - srgb[0]) * influence,
                                srgb[1] + (mask_curved[1] - srgb[1]) * influence,
                                srgb[2] + (mask_curved[2] - srgb[2]) * influence,
                            ];
                        }
                    }
                }
            }

            // 9. Vignette
            if adjustment_values.vignette_amount.abs() > 0.00001 {
               let v_amount = adjustment_values.vignette_amount.clamp(-1.0, 1.0);
               let v_mid = adjustment_values.vignette_midpoint.clamp(0.0, 1.0);
               let v_round = (1.0 - adjustment_values.vignette_roundness).clamp(0.01, 4.0);
               let v_feather = adjustment_values.vignette_feather.clamp(0.0, 1.0) * 0.5;

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

            // 10. Write output
            out[0] = clamp_to_u8(srgb[0] * 255.0);
            out[1] = clamp_to_u8(srgb[1] * 255.0);
            out[2] = clamp_to_u8(srgb[2] * 255.0);
        });
    // --- PARALLEL PROCESSING END ---

    let mut encoded = Vec::new();
    let quality = if fast_demosaic { 88 } else { 96 };
    let mut encoder = JpegEncoder::new_with_quality(&mut encoded, quality);
    encoder.encode(&rgb, width, height, ExtendedColorType::Rgb8)?;
    Ok(encoded)
}

fn render_linear_roi_with_payload(
    linear_buffer: &LinearImage,
    payload: &AdjustmentsPayload,
    mask_runtimes: &[MaskRuntime],
    fast_demosaic: bool,
    roi: &CropPayload,
) -> Result<Vec<u8>> {
    let adjustment_values = payload.to_values().normalized(&ADJUSTMENT_SCALES);
    let use_basic_tone_mapper = matches!(adjustment_values.tone_mapper, ToneMapper::Basic);
    let global_curves = CurvesRuntime::from_payload(&payload.curves);
    let curves_are_active = !global_curves.is_default() || mask_runtimes.iter().any(|m| m.curves_are_active);

    let width = linear_buffer.width();
    let height = linear_buffer.height();
    if width == 0 || height == 0 {
        return Err(anyhow::anyhow!("Invalid source dimensions"));
    }

    let (roi_x, roi_y, roi_w, roi_h) = crop_rect_pixels(width, height, roi);
    if roi_w == 0 || roi_h == 0 {
        return Err(anyhow::anyhow!("Invalid ROI dimensions"));
    }

    let linear = linear_buffer.as_raw();
    debug_assert_eq!(linear.len(), (width as usize) * (height as usize) * 3);

    let need_sharpness =
        adjustment_values.sharpness.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.sharpness.abs() > 0.00001);
    let need_clarity =
        adjustment_values.clarity.abs() > 0.00001 ||
            adjustment_values.centre.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| {
                m.adjustments.clarity.abs() > 0.00001 || m.adjustments.centre.abs() > 0.00001
            });
    let need_structure =
        adjustment_values.structure.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.structure.abs() > 0.00001);
    let need_centre_mask =
        adjustment_values.centre.abs() > 0.00001 ||
            mask_runtimes.iter().any(|m| m.adjustments.centre.abs() > 0.00001);

    let detail_blurs = build_detail_blurs(linear, width, height, need_sharpness, need_clarity, need_structure);

    let ca_rc = adjustment_values.chromatic_aberration_red_cyan;
    let ca_by = adjustment_values.chromatic_aberration_blue_yellow;
    let ca_active = ca_rc.abs() > 0.000001 || ca_by.abs() > 0.000001;

    let len = (roi_w as usize)
        .checked_mul(roi_h as usize)
        .and_then(|v| v.checked_mul(3))
        .context("ROI RGB buffer size overflow")?;
    let mut rgb = try_alloc_vec(len, 0u8)?;

    for y in 0..roi_h {
        let full_y = roi_y + y;
        for x in 0..roi_w {
            let full_x = roi_x + x;
            let idx_full = ((full_y * width + full_x) as usize).min((width as usize * height as usize).saturating_sub(1));
            let base = idx_full * 3;

            // Start with linear RAW pixel data
            let mut colors =
                if ca_active {
                    sample_ca_corrected_color(linear, width, height, full_x, full_y, ca_rc, ca_by)
                } else {
                    [linear[base], linear[base + 1], linear[base + 2]]
                };

            // Apply default RAW processing (brightness + contrast boost for Basic tone mapper)
            colors = apply_default_raw_processing(colors, use_basic_tone_mapper);

            let centre_mask =
                if need_centre_mask { compute_centre_mask(full_x, full_y, width, height) } else { 0.0 };
            if let Some(blurs) = detail_blurs.as_ref() {
                colors = apply_local_contrast_stack(colors, full_x, full_y, centre_mask, &adjustment_values, blurs);
            }

            // RapidRAW-like mask compositing: apply globals once, then for each mask
            // mix toward a separately-adjusted result by mask influence.
            let mut composite = apply_color_adjustments(colors, &adjustment_values, centre_mask);
            for mask in mask_runtimes {
                let mut selection = mask_selection_at(mask, full_x, full_y);
                if mask.invert {
                    selection = 1.0 - selection;
                }
                let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                if influence <= 0.001 {
                    continue;
                }

                let mut mask_base = composite;
                if let Some(blurs) = detail_blurs.as_ref() {
                    mask_base = apply_local_contrast_stack(mask_base, full_x, full_y, centre_mask, &mask.adjustments, blurs);
                }
                let mask_adjusted = apply_color_adjustments(mask_base, &mask.adjustments, centre_mask);
                composite = [
                    composite[0] + (mask_adjusted[0] - composite[0]) * influence,
                    composite[1] + (mask_adjusted[1] - composite[1]) * influence,
                    composite[2] + (mask_adjusted[2] - composite[2]) * influence,
                ];
            }

            composite = tone_map(composite, adjustment_values.tone_mapper);

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

                    let mut selection = mask_selection_at(mask, full_x, full_y);
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

                let full_w = width as f32;
                let full_h = height as f32;
                let aspect = if full_w > 0.0 { full_h / full_w } else { 1.0 };

                let uv_x = (full_x as f32 / full_w - 0.5) * 2.0;
                let uv_y = (full_y as f32 / full_h - 0.5) * 2.0;

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
                srgb = [
                    srgb[0].clamp(0.0, 1.0),
                    srgb[1].clamp(0.0, 1.0),
                    srgb[2].clamp(0.0, 1.0),
                ];
            }

            let out_base = ((y * roi_w + x) as usize) * 3;
            rgb[out_base] = clamp_to_u8(srgb[0] * 255.0);
            rgb[out_base + 1] = clamp_to_u8(srgb[1] * 255.0);
            rgb[out_base + 2] = clamp_to_u8(srgb[2] * 255.0);
        }
    }

    let mut encoded = Vec::new();
    let quality = if fast_demosaic { 88 } else { 96 };
    let mut encoder = JpegEncoder::new_with_quality(&mut encoded, quality);
    encoder.encode(&rgb, roi_w, roi_h, ExtendedColorType::Rgb8)?;
    Ok(encoded)
}

fn render_linear_with_payload_tiled(
    linear_buffer: &LinearImage,
    payload: &AdjustmentsPayload,
    mask_defs: &[MaskRuntimeDef],
    fast_demosaic: bool,
    tile_size: u32,
) -> Result<Vec<u8>> {
    let adjustment_values = payload.to_values().normalized(&ADJUSTMENT_SCALES);
    let use_basic_tone_mapper = matches!(adjustment_values.tone_mapper, ToneMapper::Basic);
    let global_curves = CurvesRuntime::from_payload(&payload.curves);
    let curves_are_active = !global_curves.is_default() || mask_defs.iter().any(|m| m.curves_are_active);

    let width = linear_buffer.width();
    let height = linear_buffer.height();
    if width == 0 || height == 0 {
        return Err(anyhow::anyhow!("Invalid image dimensions"));
    }

    let ai_cache = build_ai_mask_cache(mask_defs, width, height);

    let linear = linear_buffer.as_raw();
    debug_assert_eq!(linear.len(), (width as usize) * (height as usize) * 3);

    let need_sharpness =
        adjustment_values.sharpness.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.sharpness.abs() > 0.00001);
    let need_clarity =
        adjustment_values.clarity.abs() > 0.00001 ||
            adjustment_values.centre.abs() > 0.00001 ||
            mask_defs.iter().any(|m| {
                m.adjustments.clarity.abs() > 0.00001 || m.adjustments.centre.abs() > 0.00001
            });
    let need_structure =
        adjustment_values.structure.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.structure.abs() > 0.00001);
    let need_centre_mask =
        adjustment_values.centre.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.centre.abs() > 0.00001);

    let ca_rc = adjustment_values.chromatic_aberration_red_cyan;
    let ca_by = adjustment_values.chromatic_aberration_blue_yellow;
    let ca_active = ca_rc.abs() > 0.000001 || ca_by.abs() > 0.000001;

    let len = (width as usize)
        .checked_mul(height as usize)
        .and_then(|v| v.checked_mul(3))
        .context("RGB buffer size overflow")?;
    let mut rgb = try_alloc_vec(len, 0u8)?;

    let tile = tile_size.max(64).min(width.max(height));
    let mut tile_y = 0;
    while tile_y < height {
        let tile_h = (height - tile_y).min(tile);
        let mut tile_x = 0;
        while tile_x < width {
            let tile_w = (width - tile_x).min(tile);
            let mask_runtimes = if mask_defs.is_empty() {
                Vec::new()
            } else {
                build_mask_runtimes_for_region(mask_defs, width, height, tile_x, tile_y, tile_w, tile_h, &ai_cache)
            };
            let detail_blurs = build_detail_blurs_region(
                linear,
                width,
                height,
                tile_x,
                tile_y,
                tile_w,
                tile_h,
                DETAIL_SHARPNESS_RADIUS,
                DETAIL_CLARITY_RADIUS,
                DETAIL_STRUCTURE_RADIUS,
                need_sharpness,
                need_clarity,
                need_structure,
            );

            for y in 0..tile_h {
                let full_y = tile_y + y;
                for x in 0..tile_w {
                    let full_x = tile_x + x;
                    let idx_full = (full_y * width + full_x) as usize;
                    let base = idx_full * 3;

                    let mut colors =
                        if ca_active {
                            sample_ca_corrected_color(linear, width, height, full_x, full_y, ca_rc, ca_by)
                        } else {
                            [linear[base], linear[base + 1], linear[base + 2]]
                        };

                    colors = apply_default_raw_processing(colors, use_basic_tone_mapper);

                    let centre_mask =
                        if need_centre_mask { compute_centre_mask(full_x, full_y, width, height) } else { 0.0 };
                    if let Some(blurs) = detail_blurs.as_ref() {
                        colors = apply_local_contrast_stack(colors, full_x, full_y, centre_mask, &adjustment_values, blurs);
                    }

                    let mut composite = apply_color_adjustments(colors, &adjustment_values, centre_mask);
                    for mask in &mask_runtimes {
                        let mut selection = mask_selection_at(mask, full_x, full_y);
                        if mask.invert {
                            selection = 1.0 - selection;
                        }
                        let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                        if influence <= 0.001 {
                            continue;
                        }

                        let mut mask_base = composite;
                        if let Some(blurs) = detail_blurs.as_ref() {
                            mask_base = apply_local_contrast_stack(mask_base, full_x, full_y, centre_mask, &mask.adjustments, blurs);
                        }
                        let mask_adjusted = apply_color_adjustments(mask_base, &mask.adjustments, centre_mask);
                        composite = [
                            composite[0] + (mask_adjusted[0] - composite[0]) * influence,
                            composite[1] + (mask_adjusted[1] - composite[1]) * influence,
                            composite[2] + (mask_adjusted[2] - composite[2]) * influence,
                        ];
                    }

                    composite = tone_map(composite, adjustment_values.tone_mapper);

                    let mut srgb = [
                        linear_to_srgb(composite[0]),
                        linear_to_srgb(composite[1]),
                        linear_to_srgb(composite[2]),
                    ];

                    if curves_are_active {
                        srgb = global_curves.apply_all(srgb);

                        for mask in &mask_runtimes {
                            if !mask.curves_are_active {
                                continue;
                            }

                            let mut selection = mask_selection_at(mask, full_x, full_y);
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

                        let full_w = width as f32;
                        let full_h = height as f32;
                        let aspect = if full_w > 0.0 { full_h / full_w } else { 1.0 };

                        let uv_x = (full_x as f32 / full_w - 0.5) * 2.0;
                        let uv_y = (full_y as f32 / full_h - 0.5) * 2.0;

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
                        srgb = [
                            srgb[0].clamp(0.0, 1.0),
                            srgb[1].clamp(0.0, 1.0),
                            srgb[2].clamp(0.0, 1.0),
                        ];
                    }

                    let out_base = (idx_full * 3) as usize;
                    rgb[out_base] = clamp_to_u8(srgb[0] * 255.0);
                    rgb[out_base + 1] = clamp_to_u8(srgb[1] * 255.0);
                    rgb[out_base + 2] = clamp_to_u8(srgb[2] * 255.0);
                }
            }

            tile_x += tile;
        }
        tile_y += tile;
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
    let mut linear_buffer = develop_preview_linear(raw_bytes, fast_demosaic, max_width, max_height)?;
    let payload = parse_adjustments_payload(adjustments_json);
    linear_buffer = apply_transformations(linear_buffer, &payload);
    let width = linear_buffer.width();
    let height = linear_buffer.height();
    let mask_runtimes = parse_masks(payload.masks.clone(), width, height);
    render_linear_with_payload(&linear_buffer, &payload, &mask_runtimes, fast_demosaic)
}

// Compact u16 tiled renderer with virtual transformations (no physical rotation)
fn render_compact_tiled(
    source: &CompactImage,
    transform: &TransformState,
    payload: &AdjustmentsPayload,
    mask_defs: &[MaskRuntimeDef],
    fast_demosaic: bool,
    tile_size: u32,
) -> Result<Vec<u8>> {
    let adjustment_values = payload.to_values().normalized(&ADJUSTMENT_SCALES);
    let use_basic_tone_mapper = matches!(adjustment_values.tone_mapper, ToneMapper::Basic);
    let global_curves = CurvesRuntime::from_payload(&payload.curves);
    let curves_are_active = !global_curves.is_default() || mask_defs.iter().any(|m| m.curves_are_active);

    // Use OUTPUT dimensions (post-rotation/crop)
    let width = transform.output_w;
    let height = transform.output_h;
    
    if width == 0 || height == 0 { 
        return Err(anyhow::anyhow!("Invalid output dimensions")); 
    }

    let ai_cache = build_ai_mask_cache(mask_defs, width, height);

    let need_sharpness =
        adjustment_values.sharpness.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.sharpness.abs() > 0.00001);
    let need_clarity =
        adjustment_values.clarity.abs() > 0.00001 ||
            adjustment_values.centre.abs() > 0.00001 ||
            mask_defs.iter().any(|m| {
                m.adjustments.clarity.abs() > 0.00001 || m.adjustments.centre.abs() > 0.00001
            });
    let need_structure =
        adjustment_values.structure.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.structure.abs() > 0.00001);
    let need_centre_mask =
        adjustment_values.centre.abs() > 0.00001 ||
            mask_defs.iter().any(|m| m.adjustments.centre.abs() > 0.00001);

    // DETERMINE REQUIRED PADDING to prevent tile seams with detail effects
    // Based on the blur radii used by active effects
    let mut max_radius = 0u32;
    if need_structure { max_radius = max_radius.max(DETAIL_STRUCTURE_RADIUS as u32); } // ~40px
    if need_clarity { max_radius = max_radius.max(DETAIL_CLARITY_RADIUS as u32); } // ~8px
    if need_sharpness { max_radius = max_radius.max(DETAIL_SHARPNESS_RADIUS as u32); } // ~2px
    
    // Add safety margin - 50px is typically safe for all RapidRAW-style effects
    let padding = if max_radius > 0 { max_radius + 10 } else { 0 };

    // Prepare Output Buffer (RGB u8)
    let len = (width as usize)
        .checked_mul(height as usize)
        .and_then(|v| v.checked_mul(3))
        .context("RGB buffer size overflow")?;
    let mut rgb = try_alloc_vec(len, 0u8)?;

    let tile = tile_size.max(64).min(width.max(height));
    let mut tile_y = 0;
    
    while tile_y < height {
        let tile_h = (height - tile_y).min(tile);
        let mut tile_x = 0;
        
        while tile_x < width {
            let tile_w = (width - tile_x).min(tile);
            
            // Calculate padding for this tile (handle edges properly)
            let pad_left = if tile_x >= padding { padding } else { tile_x };
            let pad_top = if tile_y >= padding { padding } else { tile_y };
            let pad_right = padding.min(width.saturating_sub(tile_x + tile_w));
            let pad_bottom = padding.min(height.saturating_sub(tile_y + tile_h));
            
            // Fetch coordinates include padding
            let fetch_x = tile_x - pad_left;
            let fetch_y = tile_y - pad_top;
            let fetch_w = tile_w + pad_left + pad_right;
            let fetch_h = tile_h + pad_top + pad_bottom;
            
            // Build masks for this output tile (no padding needed for masks)
            let mask_runtimes = if mask_defs.is_empty() {
                Vec::new()
            } else {
                build_mask_runtimes_for_region(mask_defs, width, height, tile_x, tile_y, tile_w, tile_h, &ai_cache)
            };
            
            // Extract PADDED f32 tile for detail calculations
            let padded_linear_tile = extract_tile_f32(source, transform, fetch_x, fetch_y, fetch_w, fetch_h);
            
            // Build detail blurs on the PADDED tile
            // The blur edges will be messy, but clean in the center where our actual tile is
            let detail_blurs = build_detail_blurs_region(
                &padded_linear_tile,
                fetch_w,
                fetch_h,
                0, // Process entire padded tile
                0,
                fetch_w,
                fetch_h,
                DETAIL_SHARPNESS_RADIUS,
                DETAIL_CLARITY_RADIUS,
                DETAIL_STRUCTURE_RADIUS,
                need_sharpness,
                need_clarity,
                need_structure,
            );

            for y in 0..tile_h {
                let full_y = tile_y + y;
                // Coordinate within the PADDED tile
                let local_y = y + pad_top;
                
                for x in 0..tile_w {
                    let full_x = tile_x + x;
                    // Coordinate within the PADDED tile
                    let local_x = x + pad_left;
                    
                    // Sample from the PADDED linear tile (already extracted)
                    // This avoids re-doing the virtual transform for every pixel
                    let idx = (local_y * fetch_w + local_x) as usize * 3;
                    let mut colors = if idx + 2 < padded_linear_tile.len() {
                        [
                            padded_linear_tile[idx],
                            padded_linear_tile[idx + 1],
                            padded_linear_tile[idx + 2]
                        ]
                    } else {
                        [0.0, 0.0, 0.0]
                    };

                    colors = apply_default_raw_processing(colors, use_basic_tone_mapper);

                    let centre_mask =
                        if need_centre_mask { compute_centre_mask(full_x, full_y, width, height) } else { 0.0 };
                    
                    // Use PADDED tile coordinates (local_x, local_y) for detail blurs
                    // This ensures we sample from the correct position in the padded blur buffers
                    if let Some(blurs) = detail_blurs.as_ref() {
                        colors = apply_local_contrast_stack(colors, local_x, local_y, centre_mask, &adjustment_values, blurs);
                    }

                    let mut composite = apply_color_adjustments(colors, &adjustment_values, centre_mask);
                    
                    for mask in &mask_runtimes {
                        let mut selection = mask_selection_at(mask, full_x, full_y);
                        if mask.invert {
                            selection = 1.0 - selection;
                        }
                        let influence = (selection * mask.opacity_factor).clamp(0.0, 1.0);
                        if influence <= 0.001 {
                            continue;
                        }

                        let mut mask_base = composite;
                        if let Some(blurs) = detail_blurs.as_ref() {
                            mask_base = apply_local_contrast_stack(mask_base, local_x, local_y, centre_mask, &mask.adjustments, blurs);
                        }
                        let mask_adjusted = apply_color_adjustments(mask_base, &mask.adjustments, centre_mask);
                        composite = [
                            composite[0] + (mask_adjusted[0] - composite[0]) * influence,
                            composite[1] + (mask_adjusted[1] - composite[1]) * influence,
                            composite[2] + (mask_adjusted[2] - composite[2]) * influence,
                        ];
                    }

                    composite = tone_map(composite, adjustment_values.tone_mapper);

                    let mut srgb = [
                        linear_to_srgb(composite[0]),
                        linear_to_srgb(composite[1]),
                        linear_to_srgb(composite[2]),
                    ];

                    if curves_are_active {
                        srgb = global_curves.apply_all(srgb);

                        for mask in &mask_runtimes {
                            if !mask.curves_are_active {
                                continue;
                            }

                            let mut selection = mask_selection_at(mask, full_x, full_y);
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

                        let full_w = width as f32;
                        let full_h = height as f32;
                        let aspect = if full_w > 0.0 { full_h / full_w } else { 1.0 };

                        let uv_x = (full_x as f32 / full_w - 0.5) * 2.0;
                        let uv_y = (full_y as f32 / full_h - 0.5) * 2.0;

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
                        srgb = [
                            srgb[0].clamp(0.0, 1.0),
                            srgb[1].clamp(0.0, 1.0),
                            srgb[2].clamp(0.0, 1.0),
                        ];
                    }

                    let out_base = ((full_y * width + full_x) as usize) * 3;
                    rgb[out_base] = clamp_to_u8(srgb[0] * 255.0);
                    rgb[out_base + 1] = clamp_to_u8(srgb[1] * 255.0);
                    rgb[out_base + 2] = clamp_to_u8(srgb[2] * 255.0);
                }
            }

            tile_x += tile;
        }
        tile_y += tile;
    }

    let mut encoded = Vec::new();
    let quality = if fast_demosaic { 88 } else { 96 };
    let mut encoder = JpegEncoder::new_with_quality(&mut encoded, quality);
    encoder.encode(&rgb, width, height, ExtendedColorType::Rgb8)?;
    Ok(encoded)
}

// Helper to handle the specific export logic with u16 compact storage
fn render_export_from_session(
    handle: jlong,
    adjustments_json: Option<&str>,
    max_dimension: u32,
    low_ram_mode: bool,
) -> Result<Vec<u8>> {
    let session = get_session(handle).context("Invalid session handle")?;
    
    // 1. Get raw bytes and clear session cache to free RAM
    let raw_bytes = {
        let mut session = session.lock().map_err(|_| anyhow::anyhow!("Session lock poisoned"))?;
        
        // AGGRESSIVE CLEANUP: Drop everything to free RAM for export
        session.super_low = None;
        session.low = None;
        session.preview = None;
        session.zoom = None;
        session.masks_super_low = None;
        session.masks_low = None;
        session.masks_preview = None;
        session.masks_zoom = None;
        
        session.raw_bytes.clone()
    }; // Session lock DROPPED

    let payload = parse_adjustments_payload(adjustments_json);
    let mask_defs = parse_mask_defs(payload.masks.clone());

    // 2. Decode directly to u16 compact format (no rotation, returns orientation)
    let fast_demosaic = low_ram_mode;
    let (req_w, req_h) = if max_dimension > 0 { 
        (Some(max_dimension), Some(max_dimension)) 
    } else { 
        (None, None) 
    };
    
    let (compact_source, base_orientation) = decode_raw_to_compact(&raw_bytes, fast_demosaic, req_w, req_h)?;

    // 3. Setup Virtual Transform with base orientation (handles rotation virtually)
    let transform = TransformState::new(
        compact_source.width(), 
        compact_source.height(), 
        &payload,
        base_orientation
    );

    // 4. Render Tiled using Virtual Coordinates
    let tile_size = if low_ram_mode { 128 } else { 256 };
    
    render_compact_tiled(
        &compact_source, 
        &transform, 
        &payload, 
        &mask_defs, 
        fast_demosaic, 
        tile_size
    )
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_exportFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
    max_dimension: jint,
    low_ram_mode: jboolean,
) -> jbyteArray {
    ensure_logger();
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let adjustments = read_adjustments_json(&mut env, adjustments_json);
        let max_dim = if max_dimension < 0 { 0 } else { max_dimension as u32 };
        let is_low_ram = low_ram_mode != 0;

        match render_export_from_session(handle, adjustments.as_deref(), max_dim, is_low_ram) {
            Ok(payload) => make_byte_array(&env, &payload),
            Err(err) => {
                error!("Failed to export session: {}", err);
                if err.to_string().contains("Out of memory") {
                    let _ = env.throw_new(
                        "java/lang/OutOfMemoryError",
                        "Native heap limit exceeded during export",
                    );
                }
                ptr::null_mut()
            }
        }
    }));

    match result {
        Ok(byte_array) => byte_array,
        Err(_) => {
            error!("Native panic in exportFromSession");
            let _ = env.throw_new(
                "java/lang/OutOfMemoryError",
                "Native heap limit exceeded during export",
            );
            ptr::null_mut()
        }
    }
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
    Zoom,
}

impl PreviewKind {
    fn max_dims(self) -> (u32, u32) {
        match self {
            PreviewKind::SuperLow => (64, 64),
            PreviewKind::Low => (256, 256),
            PreviewKind::Preview => (1280, 720),
            PreviewKind::Zoom => (2304, 2304),
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

struct ZoomCache {
    max_w: u32,
    max_h: u32,
    image: Arc<LinearImage>,
}

struct Session {
    raw_bytes: Vec<u8>,
    metadata_json: String,

    super_low: Option<Arc<LinearImage>>,
    low: Option<Arc<LinearImage>>,
    preview: Option<Arc<LinearImage>>,
    zoom: Option<ZoomCache>,

    masks_super_low: Option<MasksCache>,
    masks_low: Option<MasksCache>,
    masks_preview: Option<MasksCache>,
    masks_zoom: Option<MasksCache>,
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
            zoom: None,
            masks_super_low: None,
            masks_low: None,
            masks_preview: None,
            masks_zoom: None,
        }
    }

    fn zoom_linear_for(&mut self, max_w: u32, max_h: u32) -> Result<Arc<LinearImage>> {
        if let Some(cache) = self.zoom.as_ref() {
            if cache.max_w == max_w && cache.max_h == max_h {
                return Ok(Arc::clone(&cache.image));
            }
        }

        let linear = develop_preview_linear(&self.raw_bytes, true, Some(max_w), Some(max_h))?;
        let shared = Arc::new(linear);
        self.zoom = Some(ZoomCache {
            max_w,
            max_h,
            image: Arc::clone(&shared),
        });
        Ok(shared)
    }

    fn linear_for(&mut self, kind: PreviewKind) -> Result<Arc<LinearImage>> {
        if let PreviewKind::Zoom = kind {
            let (max_w, max_h) = kind.max_dims();
            return self.zoom_linear_for(max_w, max_h);
        }

        let slot = match kind {
            PreviewKind::SuperLow => &mut self.super_low,
            PreviewKind::Low => &mut self.low,
            PreviewKind::Preview => &mut self.preview,
            PreviewKind::Zoom => unreachable!("Zoom handled above"),
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
            PreviewKind::Zoom => &mut self.masks_zoom,
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_createSession(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_releaseSession(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_getMetadataJsonFromSession(
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

    let payload = parse_adjustments_payload(adjustments_json);
    let effective_kind;
    let linear = if payload.preview.use_zoom {
        let requested = payload.preview.max_dimension;
        let max_dim = 2304;
        let stage_cap = match kind {
            PreviewKind::SuperLow => 64,
            PreviewKind::Low => 256,
            PreviewKind::Preview | PreviewKind::Zoom => max_dim,
        };
        let (min_dim, step) = if stage_cap <= 256 { (64, 64) } else { (1280, 128) };
        let default_dim = max_dim;
        let clamped = requested.unwrap_or(default_dim).clamp(min_dim, max_dim).min(stage_cap);
        let max_dim = ((clamped / step) * step).max(min_dim);
        effective_kind = PreviewKind::Zoom;
        session.zoom_linear_for(max_dim, max_dim)?
    } else {
        effective_kind = kind;
        session.linear_for(kind)?
    };
    // Try to consume the Arc, or clone if it's still shared
    let linear_owned = Arc::try_unwrap(linear).unwrap_or_else(|arc| (*arc).clone());
    let transformed = apply_transformations(linear_owned, &payload);
    let width = transformed.width();
    let height = transformed.height();
    let masks = session.masks_for(effective_kind, &payload.masks, width, height);

    if let Some(roi) = payload.preview.roi.as_ref() {
        render_linear_roi_with_payload(&transformed, &payload, masks, true, roi)
    } else {
        render_linear_with_payload(&transformed, &payload, masks, true)
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_lowlowdecodeFromSession(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_lowdecodeFromSession(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_decodeFromSession(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_decodeFullResFromSession(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let adjustments = read_adjustments_json(&mut env, adjustments_json);
        let session = match get_session(handle) {
            Some(s) => s,
            None => {
                error!("Invalid session handle: {}", handle);
                return ptr::null_mut();
            }
        };
        let mut session = match session.lock() {
            Ok(s) => s,
            Err(_) => {
                error!("Session lock poisoned");
                return ptr::null_mut();
            }
        };

        // Drop cached preview/zoom/mask buffers to free native memory before full-res decode
        session.super_low = None;
        session.low = None;
        session.preview = None;
        session.zoom = None;
        session.masks_super_low = None;
        session.masks_low = None;
        session.masks_preview = None;
        session.masks_zoom = None;

        match render_raw(&session.raw_bytes, adjustments.as_deref(), false, None, None) {
            Ok(payload) => make_byte_array(&env, &payload),
            Err(err) => {
                error!("Failed to render full-resolution image: {}", err);
                if err.to_string().contains("Out of memory") {
                    let _ = env.throw_new(
                        "java/lang/OutOfMemoryError",
                        "Native heap limit exceeded during export",
                    );
                }
                ptr::null_mut()
            }
        }
    }));

    match result {
        Ok(byte_array) => byte_array,
        Err(_) => {
            error!("Native panic caught in decodeFullResFromSession (likely OOM)");
            let _ = env.throw_new(
                "java/lang/OutOfMemoryError",
                "Native heap limit exceeded during export",
            );
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_lowdecode(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_lowlowdecode(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_decode(
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
pub extern "system" fn Java_com_dueckis_kawaiiraweditor_data_native_LibRawDecoder_decodeFullRes(
    mut env: JNIEnv,
    _: JClass,
    raw_data: JByteArray,
    adjustments_json: JString,
) -> jbyteArray {
    ensure_logger();
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let bytes = match convert_raw_array(&env, raw_data) {
            Some(b) => b,
            None => return ptr::null_mut(),
        };

        let adjustments = read_adjustments_json(&mut env, adjustments_json);

        match render_raw(&bytes, adjustments.as_deref(), false, None, None) {
            Ok(payload) => make_byte_array(&env, &payload),
            Err(err) => {
                error!("Failed to render full-resolution image: {}", err);
                if err.to_string().contains("Out of memory") {
                    let _ = env.throw_new(
                        "java/lang/OutOfMemoryError",
                        "Native heap limit exceeded during export",
                    );
                }
                ptr::null_mut()
            }
        }
    }));

    match result {
        Ok(byte_array) => byte_array,
        Err(_) => {
            error!("Native panic caught in decodeFullRes (likely OOM)");
            let _ = env.throw_new(
                "java/lang/OutOfMemoryError",
                "Native heap limit exceeded during export",
            );
            ptr::null_mut()
        }
    }
}
