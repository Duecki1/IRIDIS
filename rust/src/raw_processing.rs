//Code taken from RapidRAW by CyberTimon
//https://github.com/CyberTimon/RapidRAW

use anyhow::Result;
use image::{DynamicImage, ImageBuffer, Rgb};
use rawler::{
    decoders::{Orientation, RawDecodeParams},
    imgop::develop::{DemosaicAlgorithm, Intermediate, ProcessingStep, RawDevelop},
    rawimage::RawImage,
    rawimage::RawImageData,
    rawsource::RawSource,
};
use std::cmp;

pub fn develop_raw_image(
    file_bytes: &[u8],
    fast_demosaic: bool,
    highlight_compression: f32,
) -> Result<(DynamicImage, Orientation)> {
    develop_internal_tiled(file_bytes, fast_demosaic, highlight_compression)
}

fn develop_internal_tiled(
    file_bytes: &[u8],
    fast_demosaic: bool,
    highlight_compression: f32,
) -> Result<(DynamicImage, Orientation)> {
    // 1. Initial Decode (Metadata + Bayer Data)
    // We strictly scope the decoder to ensure we don't hold unnecessary structures
    // after we extract the bayer data.
    let (raw_image, orientation) = {
        let source = RawSource::new_from_slice(file_bytes);
        let decoder = rawler::get_decoder(&source)?;
        let raw_image = decoder.raw_image(&source, &RawDecodeParams::default(), false)?;
        let metadata = decoder.raw_metadata(&source, &RawDecodeParams::default())?;
        let orientation = metadata
            .exif
            .orientation
            .map(Orientation::from_u16)
            .unwrap_or(Orientation::Normal);
        (raw_image, orientation)
    };

    let full_width = raw_image.width;
    let full_height = raw_image.height;
    // Determine active crop area to avoid optical black borders
    let (crop_x, crop_y, crop_w, crop_h) = if let Some(area) = raw_image.active_area {
        (area.p.x, area.p.y, area.d.w, area.d.h)
    } else if let Some(area) = raw_image.crop_area {
        (area.p.x, area.p.y, area.d.w, area.d.h)
    } else {
        (0usize, 0usize, full_width, full_height)
    };

    // 2. Prepare Output Buffer (u16)
    // This consumes memory (e.g. 144MB for 24MP), but it's the ONLY large allocation we keep.
    // We strictly avoid allocating the massive f32 buffer for the whole image alongside this.
    let mut final_buffer = ImageBuffer::<Rgb<u16>, Vec<u16>>::new(crop_w as u32, crop_h as u32);

    // 3. Pre-calculate Color Math
    let original_white_level = raw_image.whitelevel.0.get(0).cloned().unwrap_or(u16::MAX as u32) as f32;
    let original_black_level = raw_image.blacklevel.levels.get(0).map(|r| r.as_f32()).unwrap_or(0.0);
    
    let denominator = (original_white_level - original_black_level).max(1.0);
    let rescale_factor = (u32::MAX as f32 - original_black_level) / denominator;
    let safe_highlight_compression = highlight_compression.max(1.01);

    // 4. Strip Processing Configuration
    // We process in strips to keep peak memory low.
    // Padding is required because demosaicing needs neighbors. 
    let strip_height = if fast_demosaic { 1024 } else { 512 }; 
    let padding = 8; 

    // Clone the 'skeleton' of the RawImage (metadata) to reuse for strips.
    // We empty the data vector here so cloning is cheap.
    let base_image_struct = RawImage {
        data: match &raw_image.data {
            RawImageData::Integer(_) => RawImageData::Integer(Vec::new()),
            RawImageData::Float(_) => RawImageData::Float(Vec::new()),
        },
        width: full_width,
        height: 0, // Will update per strip
        ..raw_image.clone()
    };

    // Force whitelevels to MAX for processing so we handle clipping manually
    let mut processing_base = base_image_struct.clone();
    for level in processing_base.whitelevel.0.iter_mut() {
        *level = u32::MAX;
    }
    // Clear global crops/active area to avoid strip cropping issues
    processing_base.active_area = None;
    processing_base.crop_area = None;

    // 5. Iterate Strips
    for y_start in (0..full_height).step_by(strip_height) {
        let y_end = cmp::min(y_start + strip_height, full_height);
        
        // Calculate padded bounds (ensure we cut on even boundaries for Bayer)
        let pad_top = if y_start == 0 { 0 } else { padding };
        let pad_bottom = if y_end == full_height { 0 } else { padding };
        
        let crop_y_start = y_start - pad_top;
        let crop_y_end = cmp::min(y_end + pad_bottom, full_height);
        let crop_height = crop_y_end - crop_y_start;

        // Extract Bayer Data for this strip
        // RawImage.data is a flat vector. Respect components-per-pixel (cpp).
        let row_pitch = full_width * raw_image.cpp; 
        let data_start = crop_y_start * row_pitch;
        let data_end = crop_y_end * row_pitch;
        
        let total_len = match &raw_image.data {
            RawImageData::Integer(v) => v.len(),
            RawImageData::Float(v) => v.len(),
        };
        if data_end > total_len {
            break; 
        }

        // Create temporary RawImage for this strip
        let mut strip_raw = processing_base.clone();
        strip_raw.width = full_width;
        strip_raw.height = crop_height;
        // Copy ONLY the specific slice of bayer data. (Small allocation per strip)
        strip_raw.data = match &raw_image.data {
            RawImageData::Integer(v) => RawImageData::Integer(v[data_start..data_end].to_vec()),
            RawImageData::Float(v) => RawImageData::Float(v[data_start..data_end].to_vec()),
        };

        // Develop Strip (Demosaic -> RGB f32)
        // This allocates the f32 buffer ONLY for this strip (e.g. ~30MB instead of ~900MB)
        let mut developer = RawDevelop::default();
        // Force full-resolution demosaicing to avoid half-size superpixel output
        developer.demosaic_algorithm = DemosaicAlgorithm::Quality;
        // Avoid applying crops on strips; keep linear color space (no sRGB gamma)
        developer.steps.retain(|&step| step != ProcessingStep::SRgb && step != ProcessingStep::CropActiveArea && step != ProcessingStep::CropDefault);
        
        // This is the heavy operation, but now it only runs on 512 lines!
        let intermediate = developer.develop_intermediate(&strip_raw)?;
        
        // Process and Write to Final Buffer
        let valid_h = y_end - y_start;
        match intermediate {
            Intermediate::ThreeColor(img) => {
                let strip_w = img.width;
                for row in 0..valid_h {
                    let src_row = row + pad_top;
                    let global_y = y_start + row;
                    let row_start = src_row * strip_w;
                    let copy_width = cmp::min(full_width, strip_w);
                    for x in 0..copy_width {
                        let p = img.data[row_start + x];
                        let r = (p[0] * rescale_factor).max(0.0);
                        let g = (p[1] * rescale_factor).max(0.0);
                        let b = (p[2] * rescale_factor).max(0.0);
                        let max_c = r.max(g).max(b);
                        let (final_r, final_g, final_b) = if max_c > 1.0 {
                            let min_c = r.min(g).min(b);
                            let compression_factor: f32 = (1.0f32 - (max_c - 1.0f32) / (safe_highlight_compression - 1.0f32)).clamp(0.0f32, 1.0f32);
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
                        if x >= crop_x && x < crop_x + crop_w && global_y >= crop_y && global_y < crop_y + crop_h {
                            let dest_x = (x - crop_x) as u32;
                            let dest_y = (global_y - crop_y) as u32;
                            final_buffer.put_pixel(
                                dest_x,
                                dest_y,
                                Rgb([
                                    (final_r * 65535.0).clamp(0.0, 65535.0) as u16,
                                    (final_g * 65535.0).clamp(0.0, 65535.0) as u16,
                                    (final_b * 65535.0).clamp(0.0, 65535.0) as u16,
                                ]),
                            );
                        }
                    }
                }
            }
            Intermediate::Monochrome(img) => {
                let strip_w = img.width;
                for row in 0..valid_h {
                    let src_row = row + pad_top;
                    let global_y = y_start + row;
                    let row_start = src_row * strip_w;
                    let copy_width = cmp::min(full_width, strip_w);
                    for x in 0..copy_width {
                        let v = (img.data[row_start + x] * rescale_factor).max(0.0);
                        let pixel = (v * 65535.0).clamp(0.0, 65535.0) as u16;
                        if x >= crop_x && x < crop_x + crop_w && global_y >= crop_y && global_y < crop_y + crop_h {
                            let dest_x = (x - crop_x) as u32;
                            let dest_y = (global_y - crop_y) as u32;
                            final_buffer.put_pixel(dest_x, dest_y, Rgb([pixel, pixel, pixel]));
                        }
                    }
                }
            }
            Intermediate::FourColor(img) => {
                let strip_w = img.width;
                for row in 0..valid_h {
                    let src_row = row + pad_top;
                    let global_y = y_start + row;
                    let row_start = src_row * strip_w;
                    let copy_width = cmp::min(full_width, strip_w);
                    for x in 0..copy_width {
                        let p = img.data[row_start + x];
                        let r = (p[0] * rescale_factor).max(0.0);
                        let g = (p[1] * rescale_factor).max(0.0);
                        let b = (p[2] * rescale_factor).max(0.0);
                        let max_c = r.max(g).max(b);
                        let (final_r, final_g, final_b) = if max_c > 1.0 {
                            let min_c = r.min(g).min(b);
                            let compression_factor: f32 = (1.0f32 - (max_c - 1.0f32) / (safe_highlight_compression - 1.0f32)).clamp(0.0f32, 1.0f32);
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
                        if x >= crop_x && x < crop_x + crop_w && global_y >= crop_y && global_y < crop_y + crop_h {
                            let dest_x = (x - crop_x) as u32;
                            let dest_y = (global_y - crop_y) as u32;
                            final_buffer.put_pixel(
                                dest_x,
                                dest_y,
                                Rgb([
                                    (final_r * 65535.0).clamp(0.0, 65535.0) as u16,
                                    (final_g * 65535.0).clamp(0.0, 65535.0) as u16,
                                    (final_b * 65535.0).clamp(0.0, 65535.0) as u16,
                                ]),
                            );
                        }
                    }
                }
            }
        }
        // strip_pixels (f32) is dropped here automatically, freeing memory for the next strip
    }

    Ok((DynamicImage::ImageRgb16(final_buffer), orientation))
}

// Keep helper functions if needed for other parts of the app
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
