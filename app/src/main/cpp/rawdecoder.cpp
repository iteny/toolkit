#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>

#include "libraw/libraw.h"

#define LOG_TAG "RawDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxFullPixels = 16000000;

jobject createBitmap(JNIEnv *env, int width, int height, const uint8_t *rgb, size_t rgbSize) {
    const size_t expected = static_cast<size_t>(width) * static_cast<size_t>(height) * 3;
    if (rgbSize < expected) {
        LOGE("image data too small: %zu < %zu", rgbSize, expected);
        return nullptr;
    }

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    if (bitmapClass == nullptr) return nullptr;
    jmethodID createBitmap = env->GetStaticMethodID(
        bitmapClass,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    if (createBitmap == nullptr || configClass == nullptr) return nullptr;
    jfieldID argbField = env->GetStaticFieldID(
        configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (argbField == nullptr) return nullptr;
    jobject config = env->GetStaticObjectField(configClass, argbField);
    if (config == nullptr) return nullptr;

    jobject bitmap = env->CallStaticObjectMethod(
        bitmapClass, createBitmap, static_cast<jint>(width), static_cast<jint>(height), config);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    if (bitmap == nullptr) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }

    auto *dstBase = static_cast<uint8_t *>(pixels);
    for (int y = 0; y < height; ++y) {
        uint8_t *dst = dstBase + static_cast<size_t>(y) * info.stride;
        const uint8_t *src = rgb + static_cast<size_t>(y) * width * 3;
        for (int x = 0; x < width; ++x) {
            dst[0] = src[2];
            dst[1] = src[1];
            dst[2] = src[0];
            dst[3] = 0xFF;
            dst += 4;
            src += 3;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_toolkit_ui_tools_RawDecoder_nativeDecode(
    JNIEnv *env, jobject /* thiz */, jbyteArray data, jint maxDimension) {
    (void) maxDimension;
    if (data == nullptr) return nullptr;

    const jsize length = env->GetArrayLength(data);
    if (length <= 0) return nullptr;

    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return nullptr;

    auto raw = new LibRaw();
    int result = raw->open_buffer(reinterpret_cast<const void *>(bytes),
                                  static_cast<size_t>(length));
    if (result != LIBRAW_SUCCESS) {
        LOGE("open_buffer failed: %d", result);
        delete raw;
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return nullptr;
    }

    result = raw->unpack();
    if (result != LIBRAW_SUCCESS) {
        LOGE("unpack failed: %d", result);
        raw->recycle();
        delete raw;
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return nullptr;
    }

    const long long pixels =
        static_cast<long long>(raw->imgdata.sizes.width) * raw->imgdata.sizes.height;
    if (pixels > kMaxFullPixels) {
        raw->imgdata.params.half_size = 1;
    }

    raw->imgdata.params.output_bps = 8;
    raw->imgdata.params.output_color = 1;  // sRGB
    raw->imgdata.params.use_camera_wb = 1;
    raw->imgdata.params.no_auto_bright = 0;

    result = raw->dcraw_process();
    if (result != LIBRAW_SUCCESS) {
        LOGE("dcraw_process failed: %d", result);
        raw->recycle();
        delete raw;
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return nullptr;
    }

    int memoryError = 0;
    libraw_processed_image_t *image = raw->dcraw_make_mem_image(&memoryError);
    if (image == nullptr || image->type != LIBRAW_IMAGE_BITMAP ||
        image->colors != 3 || image->bits != 8) {
        LOGE("make_mem_image failed: %d (type=%d colors=%d bits=%d)", memoryError,
             image ? image->type : -1, image ? image->colors : -1,
             image ? image->bits : -1);
        if (image != nullptr) raw->dcraw_clear_mem(image);
        raw->recycle();
        delete raw;
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return nullptr;
    }

    jobject bitmap = createBitmap(env, static_cast<int>(image->width),
                                  static_cast<int>(image->height), image->data,
                                  image->data_size);
    raw->dcraw_clear_mem(image);
    raw->recycle();
    delete raw;
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return bitmap;
}
