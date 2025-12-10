#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <vector>
#include "libraw/libraw.h"

#define LOG_TAG "KawaiiRawEditor-JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline uint8_t clampToByte(float value) {
    if (value < 0.f) {
        return 0;
    }
    if (value > 255.f) {
        return 255;
    }
    return static_cast<uint8_t>(value + 0.5f);
}

// Simple sRGB encode from linear [0,1].
static inline float srgbEncode(float linear) {
    float v = std::max(0.0f, linear);
    if (v <= 0.0031308f) {
        return 12.92f * v;
    }
    return 1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f;
}

// sRGB decode to linear [0,1].
static inline float srgbDecode(float srgb) {
    float v = std::max(0.0f, srgb);
    if (v <= 0.04045f) {
        return v / 12.92f;
    }
    return std::pow((v + 0.055f) / 1.055f, 2.4f);
}

struct ExposureShaderParams {
    float exposure;          // multiplier derived from slider (2^EV)
    float contrast;          // midtone contrast, from slider
    float whites;            // whites adjustment, from slider
    float blacks;            // blacks adjustment, from slider
    float whitePoint;        // scene-referred white for filmic mapping
    float toeStrength;       // protects shadows
    float shoulderStrength;  // protects highlights
    float shadowLift;        // prevents crushed blacks when darkening
};

static ExposureShaderParams makeExposureShaderParams(float exposure, float contrast, float whites, float blacks) {
    // Keep exposure positive while allowing deep under/over corrections.
    const float safeExposure = std::max(1e-5f, exposure); // supports ~ -15 EV while staying > 0
    const float ev = std::log2(safeExposure);

    ExposureShaderParams params{};
    params.exposure = safeExposure;
    params.contrast = std::max(0.1f, contrast); // Ensure contrast is positive
    params.whites = whites;
    params.blacks = blacks;
    // More headroom when lifting exposure to keep highlights from clipping.
    params.whitePoint = 6.0f + std::max(0.0f, ev) * 1.25f;
    // Slightly stronger toe when pulling exposure down to keep shadow detail.
    params.toeStrength = 0.18f + std::max(0.0f, -ev) * 0.05f;
    // Stronger shoulder when boosting exposure to roll highlights gently.
    params.shoulderStrength = 0.38f + std::max(0.0f, ev) * 0.10f;
    params.shadowLift = 0.01f + std::max(0.0f, -ev) * 0.010f;
    return params;
}

// Reinhard tone mapping operator to compress HDR and retain local contrast.
static inline float toneMapReinhard(float linear, const ExposureShaderParams &p) {
    const float x = std::max(0.0f, linear);
    // Apply contrast using a power function centered around mid-gray (0.18)
    const float midGray = 0.18f;
    float contrasted = x;
    if (x > 1e-5f) {
        contrasted = std::pow(x / midGray, p.contrast) * midGray;
    }
    const float compressed = contrasted / (contrasted + 1.0f);
    return compressed;
}

// Custom exposure shader: apply EV multiplier, protect shadows/highlights, then filmic map.
static inline float applyExposureShader(float linear, const ExposureShaderParams &params) {
    float scene = std::max(0.0f, linear) * params.exposure;

    // Whites adjustment - affects the brightest parts of the image
    if (params.whites != 0.0f) {
        float gain = 1.0f + params.whites * 0.1f;
        scene *= gain;
    }

    // Blacks adjustment - affects the darkest parts of the image
    if (params.blacks != 0.0f) {
        float lift = params.blacks * 0.01f;
        scene = std::max(0.0f, scene + lift);
    }

    // Lift blacks slightly to keep shadow texture when darkening.
    const float shadowLift = params.shadowLift;
    if (shadowLift > 0.0f) {
        const float liftMask = 1.0f - std::exp(-scene * 12.0f);
        scene += shadowLift * liftMask;
    }

    // Soft knee before filmic shoulder to preserve highlight detail.
    const float kneeStart = params.whitePoint * 0.82f;
    if (scene > kneeStart) {
        const float range = std::max(params.whitePoint - kneeStart, 1e-3f);
        const float t = (scene - kneeStart) / range;
        const float knee = 1.0f - std::exp(-params.shoulderStrength * t);
        scene = kneeStart + range * knee;
    }

    return toneMapReinhard(scene, params);
}

static void configureWhiteBalance(LibRaw &raw) {
    raw.imgdata.params.use_camera_wb = 1;
    raw.imgdata.params.use_auto_wb = 0;
}

static void configureOutput(LibRaw &raw, bool halfSizeForSpeed) {
    raw.imgdata.params.highlight = 3; // reconstruct to avoid dark clipping
    raw.imgdata.params.half_size = halfSizeForSpeed ? 1 : 0;
    raw.imgdata.params.output_bps = 16;
    raw.imgdata.params.gamm[0] = 1.0f;
    raw.imgdata.params.gamm[1] = 1.0f;
}

static void configureProcessing(LibRaw &raw, bool halfSizeForSpeed) {
    // Keep LibRaw neutral; exposure is handled by our custom shader.
    raw.imgdata.params.no_auto_bright = 1;
    raw.imgdata.params.exp_correc = 0;
    raw.imgdata.params.exp_shift = 1.0f;
    configureOutput(raw, halfSizeForSpeed);
}

static void throwIfJavaException(JNIEnv *env, const char *context) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        throw std::runtime_error(context);
    }
}

static jobject createArgb8888Bitmap(JNIEnv *env, uint32_t width, uint32_t height) {
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbField =
            env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argbObj = env->GetStaticObjectField(configCls, argbField);

    jmethodID createBitmap = env->GetStaticMethodID(
            bitmapCls,
            "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(
            bitmapCls,
            createBitmap,
            static_cast<jint>(width),
            static_cast<jint>(height),
            argbObj);
    throwIfJavaException(env, "Java exception while creating Bitmap");

    env->DeleteLocalRef(bitmapCls);
    env->DeleteLocalRef(configCls);
    env->DeleteLocalRef(argbObj);

    if (!bitmap) {
        throw std::runtime_error("Failed to allocate Bitmap");
    }
    return bitmap;
}

static void applyExposureToBitmap(JNIEnv *env, jobject bitmap, float exposure, float contrast, float whites, float blacks) {
    if (!bitmap) {
        throw std::runtime_error("Bitmap is null");
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        throw std::runtime_error("Failed to query bitmap info");
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throw std::runtime_error("Bitmap is not RGBA_8888");
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        throw std::runtime_error("Failed to lock bitmap pixels");
    }

    const ExposureShaderParams shader = makeExposureShaderParams(exposure, contrast, whites, blacks);
    auto *dst = static_cast<uint8_t *>(pixels);
    for (uint32_t y = 0; y < info.height; ++y) {
        uint8_t *row = dst + y * info.stride;
        for (uint32_t x = 0; x < info.width; ++x) {
            uint8_t *px = row + x * 4;
            const float rLin = srgbDecode(static_cast<float>(px[0]) / 255.0f);
            const float gLin = srgbDecode(static_cast<float>(px[1]) / 255.0f);
            const float bLin = srgbDecode(static_cast<float>(px[2]) / 255.0f);

            const float rTone = applyExposureShader(rLin, shader);
            const float gTone = applyExposureShader(gLin, shader);
            const float bTone = applyExposureShader(bLin, shader);

            px[0] = clampToByte(srgbEncode(rTone) * 255.0f);
            px[1] = clampToByte(srgbEncode(gTone) * 255.0f);
            px[2] = clampToByte(srgbEncode(bTone) * 255.0f);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

static jobject createBitmapFromRgbData(JNIEnv *env,
                                       const void *src,
                                       uint32_t width,
                                       uint32_t height,
                                       uint32_t channels,
                                       uint32_t bitsPerChannel,
                                       float exposure,
                                       float contrast,
                                       float whites,
                                       float blacks,
                                       uint32_t maxWidth,
                                       uint32_t maxHeight) {
    if (!src) {
        throw std::runtime_error("RGB buffer is null");
    }
    if (channels < 3) {
        throw std::runtime_error("Unsupported channel count for RGB data");
    }

    float scale = 1.0f;
    if (maxWidth > 0 && maxHeight > 0) {
        if (width > maxWidth || height > maxHeight) {
            const float scaleW = static_cast<float>(maxWidth) / static_cast<float>(width);
            const float scaleH = static_cast<float>(maxHeight) / static_cast<float>(height);
            scale = std::min(scaleW, scaleH);
        }
    }
    const uint32_t outW = std::max(1u, static_cast<uint32_t>(std::floor(width * scale)));
    const uint32_t outH = std::max(1u, static_cast<uint32_t>(std::floor(height * scale)));

    jobject bitmap = createArgb8888Bitmap(env, outW, outH);

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        throw std::runtime_error("Failed to query bitmap info");
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throw std::runtime_error("Bitmap is not RGBA_8888");
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        throw std::runtime_error("Failed to lock bitmap pixels");
    }

    auto *dst = static_cast<uint8_t *>(pixels);
    const float invScale = 1.0f / scale;
    if (bitsPerChannel == 8) {
        const uint8_t *srcBytes = static_cast<const uint8_t *>(src);
        const ExposureShaderParams shader = makeExposureShaderParams(exposure, contrast, whites, blacks);
        for (uint32_t y = 0; y < outH; ++y) {
            const uint32_t srcY = std::min(height - 1, static_cast<uint32_t>(y * invScale));
            const uint8_t *srcRow = srcBytes + srcY * width * channels;
            uint8_t *dstRow = dst + y * info.stride;
            for (uint32_t x = 0; x < outW; ++x) {
                const uint32_t srcX = std::min(width - 1, static_cast<uint32_t>(x * invScale));
                const uint8_t *srcPixel = srcRow + srcX * channels;
                uint8_t *dstPixel = dstRow + x * 4;

                const float rLin = static_cast<float>(srcPixel[0]) / 255.0f;
                const float gLin = static_cast<float>(srcPixel[1]) / 255.0f;
                const float bLin = static_cast<float>(srcPixel[2]) / 255.0f;

                const float rTone = applyExposureShader(rLin, shader);
                const float gTone = applyExposureShader(gLin, shader);
                const float bTone = applyExposureShader(bLin, shader);

                dstPixel[0] = clampToByte(srgbEncode(rTone) * 255.0f);
                dstPixel[1] = clampToByte(srgbEncode(gTone) * 255.0f);
                dstPixel[2] = clampToByte(srgbEncode(bTone) * 255.0f);
                dstPixel[3] = 255;
            }
        }
    } else if (bitsPerChannel == 16) {
        const uint16_t *srcWords = static_cast<const uint16_t *>(src);
        const ExposureShaderParams shader = makeExposureShaderParams(exposure, contrast, whites, blacks);
        for (uint32_t y = 0; y < outH; ++y) {
            const uint32_t srcY = std::min(height - 1, static_cast<uint32_t>(y * invScale));
            const uint16_t *srcRow = srcWords + srcY * width * channels;
            uint8_t *dstRow = dst + y * info.stride;
            for (uint32_t x = 0; x < outW; ++x) {
                const uint32_t srcX = std::min(width - 1, static_cast<uint32_t>(x * invScale));
                const uint16_t *srcPixel = srcRow + srcX * channels;
                uint8_t *dstPixel = dstRow + x * 4;

                const float rLin = static_cast<float>(srcPixel[0]) / 65535.0f;
                const float gLin = static_cast<float>(srcPixel[1]) / 65535.0f;
                const float bLin = static_cast<float>(srcPixel[2]) / 65535.0f;

                const float rTone = applyExposureShader(rLin, shader);
                const float gTone = applyExposureShader(gLin, shader);
                const float bTone = applyExposureShader(bLin, shader);

                dstPixel[0] = clampToByte(srgbEncode(rTone) * 255.0f);
                dstPixel[1] = clampToByte(srgbEncode(gTone) * 255.0f);
                dstPixel[2] = clampToByte(srgbEncode(bTone) * 255.0f);
                dstPixel[3] = 255;
            }
        }
    } else {
        AndroidBitmap_unlockPixels(env, bitmap);
        throw std::runtime_error("Unsupported bits per channel");
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

static jobject decodeJpegPreview(JNIEnv *env,
                                 const libraw_processed_image_t *image,
                                 float exposure,
                                 float contrast,
                                 float whites,
                                 float blacks) {
    if (!image || !image->data || image->data_size == 0) {
        throw std::runtime_error("Invalid JPEG preview buffer");
    }
    if (image->data_size >
        static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        throw std::runtime_error("Preview JPEG too large to decode");
    }

    const jsize arraySize = static_cast<jsize>(image->data_size);
    jbyteArray jpegArray = env->NewByteArray(arraySize);
    if (!jpegArray) {
        throw std::runtime_error("Failed to allocate JPEG array");
    }
    env->SetByteArrayRegion(
            jpegArray,
            0,
            arraySize,
            reinterpret_cast<const jbyte *>(image->data));

    jclass optionsCls = env->FindClass("android/graphics/BitmapFactory$Options");
    jmethodID optionsCtor = env->GetMethodID(optionsCls, "<init>", "()V");
    jobject options = env->NewObject(optionsCls, optionsCtor);
    jfieldID preferredConfigField = env->GetFieldID(
            optionsCls,
            "inPreferredConfig",
            "Landroid/graphics/Bitmap$Config;");
    jfieldID inMutableField =
            env->GetFieldID(optionsCls, "inMutable", "Z");

    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbField =
            env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argbObj = env->GetStaticObjectField(configCls, argbField);

    env->SetObjectField(options, preferredConfigField, argbObj);
    env->SetBooleanField(options, inMutableField, JNI_TRUE);

    jclass bitmapFactoryCls = env->FindClass("android/graphics/BitmapFactory");
    jmethodID decodeMethod = env->GetStaticMethodID(
            bitmapFactoryCls,
            "decodeByteArray",
            "([BIILandroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(
            bitmapFactoryCls,
            decodeMethod,
            jpegArray,
            0,
            arraySize,
            options);
    throwIfJavaException(env, "Java exception while decoding JPEG preview");

    env->DeleteLocalRef(jpegArray);
    env->DeleteLocalRef(options);
    env->DeleteLocalRef(optionsCls);
    env->DeleteLocalRef(configCls);
    env->DeleteLocalRef(bitmapFactoryCls);
    env->DeleteLocalRef(argbObj);

    if (!bitmap) {
        throw std::runtime_error("BitmapFactory returned null for preview");
    }

    applyExposureToBitmap(env, bitmap, exposure, contrast, whites, blacks);
    return bitmap;
}

static jobject decodePreview(JNIEnv *env,
                             const uint8_t *raw_bytes,
                             size_t raw_size,
                             float exposure,
                             float contrast,
                             float whites,
                             float blacks) {
    LibRaw RawProcessor;
    libraw_processed_image_t *preview = nullptr;

    try {
        int ret = RawProcessor.open_buffer(
                const_cast<void *>(reinterpret_cast<const void *>(raw_bytes)),
                raw_size);
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        ret = RawProcessor.unpack_thumb();
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        preview = RawProcessor.dcraw_make_mem_thumb(&ret);
        if (!preview || ret != LIBRAW_SUCCESS) {
            throw std::runtime_error("Failed to build preview image");
        }

        jobject bitmap = nullptr;
        if (preview->type == LIBRAW_IMAGE_JPEG) {
            bitmap = decodeJpegPreview(env, preview, exposure, contrast, whites, blacks);
        } else if (preview->type == LIBRAW_IMAGE_BITMAP &&
                   (preview->bits == 8 || preview->bits == 16) &&
                   preview->colors >= 3) {
            bitmap = createBitmapFromRgbData(
                    env,
                    preview->data,
                    preview->width,
                    preview->height,
                    preview->colors,
                    preview->bits,
                    exposure,
                    contrast,
                    whites,
                    blacks,
                    1920,
                    1080);
        } else {
            char msg[128];
            std::snprintf(
                    msg,
                    sizeof(msg),
                    "Unsupported preview format: type=%d colors=%d bits=%d",
                    preview->type,
                    preview->colors,
                    preview->bits);
            throw std::runtime_error(msg);
        }

        RawProcessor.dcraw_clear_mem(preview);
        RawProcessor.recycle();
        return bitmap;
    } catch (...) {
        if (preview) {
            RawProcessor.dcraw_clear_mem(preview);
        }
        RawProcessor.recycle();
        throw;
    }
}

static jobject decodeFullRaw(JNIEnv *env,
                             const uint8_t *raw_bytes,
                             size_t raw_size,
                             float exposure,
                             float contrast,
                             float whites,
                             float blacks,
                             bool halfSizeForSpeed,
                             uint32_t maxWidth,
                             uint32_t maxHeight) {
    LibRaw RawProcessor;
    libraw_processed_image_t *image = nullptr;

    try {
        int ret = RawProcessor.open_buffer(
                const_cast<void *>(reinterpret_cast<const void *>(raw_bytes)),
                raw_size);
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        configureProcessing(RawProcessor, halfSizeForSpeed);
        //configureWhiteBalance(RawProcessor);

        ret = RawProcessor.unpack();
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        ret = RawProcessor.dcraw_process();
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        image = RawProcessor.dcraw_make_mem_image(&ret);
        if (!image || ret != LIBRAW_SUCCESS) {
            throw std::runtime_error("Failed to create processed image");
        }

        if (image->type != LIBRAW_IMAGE_BITMAP ||
            (image->bits != 8 && image->bits != 16) ||
            image->colors < 3) {
            char msg[128];
            std::snprintf(
                    msg,
                    sizeof(msg),
                    "Unsupported processed image: type=%d colors=%d bits=%d",
                    image->type,
                    image->colors,
                    image->bits);
            throw std::runtime_error(msg);
        }

        jobject bitmap = createBitmapFromRgbData(
                env,
                image->data,
                image->width,
                image->height,
                image->colors,
                image->bits,
                exposure,
                contrast,
                whites,
                blacks,
                maxWidth,
                maxHeight);

        RawProcessor.dcraw_clear_mem(image);
        RawProcessor.recycle();
        return bitmap;
    } catch (...) {
        if (image) {
            RawProcessor.dcraw_clear_mem(image);
        }
        RawProcessor.recycle();
        throw;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decode(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray raw_data,
        jfloat exposure,
        jfloat contrast,
        jfloat whites,
        jfloat blacks) {

    jbyte *raw_bytes_ptr = env->GetByteArrayElements(raw_data, nullptr);
    if (!raw_bytes_ptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }
    const jsize raw_size_signed = env->GetArrayLength(raw_data);
    const size_t raw_size = static_cast<size_t>(raw_size_signed);

    // Copy bytes so LibRaw only ever sees a private buffer (non-destructive).
    std::vector<uint8_t> raw_bytes(static_cast<size_t>(raw_size));
    std::memcpy(raw_bytes.data(), raw_bytes_ptr, raw_size);

    jobject bitmap = nullptr;
    try {
        // Preview path: fast (half-size) with 1080p cap.
        try {
            bitmap = decodeFullRaw(env, raw_bytes.data(), raw_size, exposure, contrast, whites, blacks, true, 1920, 1080);
        } catch (const std::exception &fullError) {
            LOGE("Full RAW decode failed: %s", fullError.what());
            bitmap = decodePreview(env, raw_bytes.data(), raw_size, exposure, contrast, whites, blacks);
        }
    } catch (const std::exception &fatalError) {
        LOGE("Decoding failed: %s", fatalError.what());
        bitmap = nullptr;
    }

    env->ReleaseByteArrayElements(raw_data, raw_bytes_ptr, JNI_ABORT);
    return bitmap;
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decodeFullRes(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray raw_data,
        jfloat exposure,
        jfloat contrast,
        jfloat whites,
        jfloat blacks) {

    jbyte *raw_bytes_ptr = env->GetByteArrayElements(raw_data, nullptr);
    if (!raw_bytes_ptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }
    const jsize raw_size_signed = env->GetArrayLength(raw_data);
    const size_t raw_size = static_cast<size_t>(raw_size_signed);

    std::vector<uint8_t> raw_bytes(static_cast<size_t>(raw_size));
    std::memcpy(raw_bytes.data(), raw_bytes_ptr, raw_size);

    jobject bitmap = nullptr;
    try {
        bitmap = decodeFullRaw(env, raw_bytes.data(), raw_size, exposure, contrast, whites, blacks, false, 0, 0);
    } catch (const std::exception &fatalError) {
        LOGE("Full-res decoding failed: %s", fatalError.what());
        bitmap = nullptr;
    }

    env->ReleaseByteArrayElements(raw_data, raw_bytes_ptr, JNI_ABORT);
    return bitmap;
}
