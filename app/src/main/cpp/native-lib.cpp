#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <stdexcept>
#include "libraw/libraw.h"

// Helper function to log messages to Android's Logcat
#define LOG_TAG "KawaiiRawEditor-JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jobject JNICALL
Java_com_dueckis_kawaiiraweditor_LibRawDecoder_decode(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray raw_data,
        jfloat exposure) {

    jbyte *raw_bytes = env->GetByteArrayElements(raw_data, nullptr);
    if (!raw_bytes) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }
    jsize raw_size = env->GetArrayLength(raw_data);

    LibRaw RawProcessor;
    jobject bitmap = nullptr;

    try {
        int ret; // Variable to hold return codes

        ret = RawProcessor.open_buffer(raw_bytes, raw_size);
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        RawProcessor.imgdata.params.bright = exposure;
        RawProcessor.imgdata.params.use_camera_wb = 0;
        RawProcessor.imgdata.params.use_auto_wb = 0;
        RawProcessor.imgdata.params.output_bps = 8;

        ret = RawProcessor.unpack();
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        ret = RawProcessor.dcraw_process();
        if (ret != LIBRAW_SUCCESS) {
            throw std::runtime_error(RawProcessor.strerror(ret));
        }

        libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_image(&ret);
        if (!image) {
            throw std::runtime_error("Failed to create image in memory");
        }

        if (image->type == LIBRAW_IMAGE_BITMAP && image->colors >= 3) {
            // Create a Bitmap via JNI: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
            if (!bitmapCls) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to find android/graphics/Bitmap class");
            }
            jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
            if (!configCls) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to find android/graphics/Bitmap$Config class");
            }

            jfieldID argb8888Field = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
            if (!argb8888Field) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to get Bitmap$Config.ARGB_8888 field");
            }
            jobject argb8888Obj = env->GetStaticObjectField(configCls, argb8888Field);
            if (!argb8888Obj) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to obtain Bitmap$Config.ARGB_8888 object");
            }

            jmethodID createBitmapMid = env->GetStaticMethodID(
                    bitmapCls,
                    "createBitmap",
                    "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
            );
            if (!createBitmapMid) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to get Bitmap.createBitmap method");
            }

            bitmap = env->CallStaticObjectMethod(bitmapCls, createBitmapMid,
                                                 (jint) image->width,
                                                 (jint) image->height,
                                                 argb8888Obj);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Exception while calling Bitmap.createBitmap");
            }
            if (!bitmap) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to create Android Bitmap object");
            }

            void *bitmapPixels = nullptr;
            if (AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels) < 0 || !bitmapPixels) {
                RawProcessor.dcraw_clear_mem(image);
                throw std::runtime_error("Failed to lock bitmap pixels");
            }

            // Copy pixel data. LibRaw returns image->data in 8-bit samples; ensure size matches.
            // We expect RGBA_8888 output; if LibRaw provides RGB, we may need to expand.
            // Here we assume image->data_size matches width*height*4.
            memcpy(bitmapPixels, image->data, image->data_size);
            AndroidBitmap_unlockPixels(env, bitmap);

        } else {
            char error_msg[100];
            snprintf(error_msg, sizeof(error_msg), "Unsupported image type (%d) or colors (%d)", image->type, image->colors);
            RawProcessor.dcraw_clear_mem(image);
            throw std::runtime_error(error_msg);
        }

        RawProcessor.dcraw_clear_mem(image);

    } catch (const std::exception& e) {
        LOGE("A C++ exception occurred: %s", e.what());
        bitmap = nullptr;
    }

    RawProcessor.recycle();
    env->ReleaseByteArrayElements(raw_data, raw_bytes, JNI_ABORT);

    return bitmap;
}