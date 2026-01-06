use std::cmp::Ordering;

use super::payloads::{CurvePointPayload, CurvesPayload};

#[derive(Clone)]
pub struct CurvePoint {
    pub x: f32,
    pub y: f32,
}

#[derive(Clone)]
pub struct CurveSegment {
    pub p1: CurvePoint,
    pub p2: CurvePoint,
    pub m1: f32,
    pub m2: f32,
}

#[derive(Clone)]
pub struct CurveRuntime {
    pub points: Vec<CurvePoint>,
    pub segments: Vec<CurveSegment>,
}

impl CurveRuntime {
    pub fn from_payload(points: &[CurvePointPayload]) -> Self {
        let mut pts: Vec<CurvePoint> = points
            .iter()
            .map(|p| CurvePoint { x: p.x, y: p.y })
            .collect();

        if pts.len() < 2 {
            pts = super::payloads::default_curve_points()
                .into_iter()
                .map(|p| CurvePoint { x: p.x, y: p.y })
                .collect();
        }

        pts.sort_by(|a, b| a.x.partial_cmp(&b.x).unwrap_or(Ordering::Equal));
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

        Self { points: pts, segments }
    }

    pub fn is_default(&self) -> bool {
        if self.points.len() != 2 {
            return false;
        }
        let p0 = &self.points[0];
        let p1 = &self.points[1];
        (p0.y - 0.0).abs() < 0.1 && (p1.y - 255.0).abs() < 0.1
    }

    pub fn eval(&self, val: f32) -> f32 {
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
pub struct CurvesRuntime {
    pub luma: CurveRuntime,
    pub red: CurveRuntime,
    pub green: CurveRuntime,
    pub blue: CurveRuntime,
    pub rgb_curves_are_active: bool,
}

impl CurvesRuntime {
    pub fn from_payload(payload: &CurvesPayload) -> Self {
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

    pub fn apply_all(&self, color: [f32; 3]) -> [f32; 3] {
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
                [
                    color_graded[0] * scale,
                    color_graded[1] * scale,
                    color_graded[2] * scale,
                ]
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

    pub fn is_default(&self) -> bool {
        self.luma.is_default() && !self.rgb_curves_are_active
    }
}

pub fn get_luma(color: [f32; 3]) -> f32 {
    color[0] * 0.2126 + color[1] * 0.7152 + color[2] * 0.0722
}
