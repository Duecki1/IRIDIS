//Code taken from RapidRAW by CyberTimon
//https://github.com/CyberTimon/RapidRAW

use anyhow::Result;
use image::{DynamicImage, ImageBuffer, Rgb};
use rawler::{
    decoders::{Orientation, RawDecodeParams},
    imgop::develop::{DemosaicAlgorithm, Intermediate, ProcessingStep, RawDevelop},
    rawimage::RawImage,
    rawsource::RawSource,
};

pub fn develop_raw_image(
    file_bytes: &[u8],
    fast_demosaic: bool,
    highlight_compression: f32,
) -> Result<DynamicImage> {
    let (developed_image, orientation) = develop_internal(file_bytes, fast_demosaic, highlight_compression)?;
    Ok(apply_orientation(developed_image, orientation))
}

fn develop_internal(
    file_bytes: &[u8],
    fast_demosaic: bool,
    highlight_compression: f32,
) -> Result<(DynamicImage, Orientation)> {
    let source = RawSource::new_from_slice(file_bytes);
    let decoder = rawler::get_decoder(&source)?;
    let mut raw_image: RawImage = decoder.raw_image(&source, &RawDecodeParams::default(), false)?;

    let metadata = decoder.raw_metadata(&source, &RawDecodeParams::default())?;
    let orientation = metadata
        .exif
        .orientation
        .map(Orientation::from_u16)
        .unwrap_or(Orientation::Normal);

    let original_white_level = raw_image
        .whitelevel
        .0
        .get(0)
        .cloned()
        .unwrap_or(u16::MAX as u32) as f32;
    let original_black_level = raw_image
        .blacklevel
        .levels
        .get(0)
        .map(|r| r.as_f32())
        .unwrap_or(0.0);

    let headroom_white_level = u32::MAX as f32;
    for level in raw_image.whitelevel.0.iter_mut() {
        *level = u32::MAX;
    }

    let mut developer = RawDevelop::default();
    if fast_demosaic {
        developer.demosaic_algorithm = DemosaicAlgorithm::Speed;
    }
    developer.steps.retain(|&step| step != ProcessingStep::SRgb);

    let mut developed_intermediate = developer.develop_intermediate(&raw_image)?;

    let denominator = (original_white_level - original_black_level).max(1.0);
    let rescale_factor = (headroom_white_level - original_black_level) / denominator;

    let safe_highlight_compression = highlight_compression.max(1.01);

    match &mut developed_intermediate {
        Intermediate::Monochrome(pixels) => {
            pixels.data.iter_mut().for_each(|p| {
                let linear_val = *p * rescale_factor;
                *p = linear_val;
            });
        }
        Intermediate::ThreeColor(pixels) => {
            pixels.data.iter_mut().for_each(|p| {
                let r = (p[0] * rescale_factor).max(0.0);
                let g = (p[1] * rescale_factor).max(0.0);
                let b = (p[2] * rescale_factor).max(0.0);

                let max_c = r.max(g).max(b);

                let (final_r, final_g, final_b) = if max_c > 1.0 {
                    let min_c = r.min(g).min(b);
                    let compression_factor = (1.0 - (max_c - 1.0) / (safe_highlight_compression - 1.0))
                        .max(0.0)
                        .min(1.0);
                    let compressed_r = min_c + (r - min_c) * compression_factor;
                    let compressed_g = min_c + (g - min_c) * compression_factor;
                    let compressed_b = min_c + (b - min_c) * compression_factor;
                    let compressed_max = compressed_r.max(compressed_g).max(compressed_b);

                    if compressed_max > 1e-6 {
                        let rescale = max_c / compressed_max;
                        (
                            compressed_r * rescale,
                            compressed_g * rescale,
                            compressed_b * rescale,
                        )
                    } else {
                        (max_c, max_c, max_c)
                    }
                } else {
                    (r, g, b)
                };

                p[0] = final_r;
                p[1] = final_g;
                p[2] = final_b;
            });
        }
        Intermediate::FourColor(pixels) => {
            pixels.data.iter_mut().for_each(|p| {
                p.iter_mut().for_each(|c| {
                    let linear_val = *c * rescale_factor;
                    *c = linear_val;
                });
            });
        }
    }

    let (width, height) = {
        let dim = developed_intermediate.dim();
        (dim.w as u32, dim.h as u32)
    };
    let dynamic_image = match developed_intermediate {
        Intermediate::ThreeColor(pixels) => {
            let buffer = ImageBuffer::<Rgb<f32>, _>::from_fn(width, height, |x, y| {
                let p = pixels.data[(y * width + x) as usize];
                Rgb([p[0], p[1], p[2]])
            });
            DynamicImage::ImageRgb32F(buffer)
        }
        Intermediate::Monochrome(pixels) => {
            let buffer = ImageBuffer::<Rgb<f32>, _>::from_fn(width, height, |x, y| {
                let p = pixels.data[(y * width + x) as usize];
                Rgb([p, p, p])
            });
            DynamicImage::ImageRgb32F(buffer)
        }
        _ => {
            return Err(anyhow::anyhow!("Unsupported intermediate format for f32 conversion"));
        }
    };

    Ok((dynamic_image, orientation))
}

fn apply_orientation(image: DynamicImage, orientation: Orientation) -> DynamicImage {
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

fn apply_cpu_default_raw_processing(image: DynamicImage) -> DynamicImage {
    let mut f32_image = image.to_rgb32f();

    const GAMMA: f32 = 2.2;
    const INV_GAMMA: f32 = 1.0 / GAMMA;
    const CONTRAST: f32 = 1.15;

    for pixel in f32_image.pixels_mut() {
        let r_gamma = pixel[0].powf(INV_GAMMA);
        let g_gamma = pixel[1].powf(INV_GAMMA);
        let b_gamma = pixel[2].powf(INV_GAMMA);

        let r_contrast = (r_gamma - 0.5) * CONTRAST + 0.5;
        let g_contrast = (g_gamma - 0.5) * CONTRAST + 0.5;
        let b_contrast = (b_gamma - 0.5) * CONTRAST + 0.5;

        pixel[0] = r_contrast.clamp(0.0, 1.0);
        pixel[1] = g_contrast.clamp(0.0, 1.0);
        pixel[2] = b_contrast.clamp(0.0, 1.0);
    }

    DynamicImage::ImageRgb32F(f32_image)
}
