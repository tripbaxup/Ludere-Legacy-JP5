/**
 * ludere_jni.c
 *
 * JNI bridge between the Android Java frontend and the libretro core.
 *
 * This file implements the native methods declared in LibretroRuntime.java
 * and SaveManager.java, and wires the libretro API callbacks to the
 * corresponding Java methods via JNI.
 *
 * Build system: CMake (see CMakeLists.txt)
 * Target ABI:   armeabi-v7a, arm64-v8a, x86, x86_64
 * Min API:      17
 *
 * Libretro API version: 1
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

#include "libretro.h"

#define TAG "LudereJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

/* ── Global state ─────────────────────────────────────────────────────────── */

static void* g_core_handle = NULL;

/* libretro function pointers */
static void  (*g_retro_init)(void)                        = NULL;
static void  (*g_retro_deinit)(void)                      = NULL;
static bool  (*g_retro_load_game)(const struct retro_game_info*) = NULL;
static void  (*g_retro_unload_game)(void)                 = NULL;
static void  (*g_retro_run)(void)                         = NULL;
static size_t(*g_retro_get_memory_size)(unsigned)         = NULL;
static void* (*g_retro_get_memory_data)(unsigned)         = NULL;
static size_t(*g_retro_serialize_size)(void)              = NULL;
static bool  (*g_retro_serialize)(void*, size_t)          = NULL;
static bool  (*g_retro_unserialize)(const void*, size_t)  = NULL;
static void  (*g_retro_set_environment)(retro_environment_t)       = NULL;
static void  (*g_retro_set_video_refresh)(retro_video_refresh_t)   = NULL;
static void  (*g_retro_set_audio_sample)(retro_audio_sample_t)     = NULL;
static void  (*g_retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = NULL;
static void  (*g_retro_set_input_poll)(retro_input_poll_t)         = NULL;
static void  (*g_retro_set_input_state)(retro_input_state_t)       = NULL;
static void  (*g_retro_get_system_av_info)(struct retro_system_av_info*) = NULL;

/* JNI references */
static JavaVM*  g_jvm      = NULL;
static jobject  g_runtime  = NULL;  /* LibretroRuntime Java object (global ref) */
static jmethodID g_onVideoFrame   = NULL;
static jmethodID g_onAudioBatch   = NULL;
static jmethodID g_onInputPoll    = NULL;
static jmethodID g_onInputState   = NULL;

static bool g_paused = false;

/* Pixel format negotiated via RETRO_ENVIRONMENT_SET_PIXEL_FORMAT.
 * Per libretro spec, cores default to 0RGB1555 until they negotiate
 * something else. */
static enum retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;

/* Core's real target frame rate, from retro_get_system_av_info().
 * Defaults to standard NTSC ~60fps until the core reports otherwise. */
static double g_target_fps = 60.098;

/* ── JNI helper ───────────────────────────────────────────────────────────── */

static JNIEnv* get_env(void) {
    JNIEnv* env = NULL;
    (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    return env;
}

/* ── libretro callbacks ───────────────────────────────────────────────────── */

static void video_refresh_cb(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !g_runtime) return;
    JNIEnv* env = get_env();
    if (!env) return;

    int total = width * height;
    jintArray pixels = (*env)->NewIntArray(env, total);
    if (!pixels) return;

    jint* buf = (*env)->GetIntArrayElements(env, pixels, NULL);

    switch (g_pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888: {
            /* 4 bytes per pixel: 0xXXRRGGBB */
            const uint32_t* src = (const uint32_t*)data;
            size_t stride = pitch / 4;
            for (unsigned y = 0; y < height; y++) {
                for (unsigned x = 0; x < width; x++) {
                    uint32_t px = src[y * stride + x];
                    buf[y * width + x] = (jint)(0xFF000000 | (px & 0x00FFFFFF));
                }
            }
            break;
        }
        case RETRO_PIXEL_FORMAT_RGB565: {
            /* 2 bytes per pixel: RRRRRGGGGGGBBBBB */
            const uint16_t* src = (const uint16_t*)data;
            size_t stride = pitch / 2;
            for (unsigned y = 0; y < height; y++) {
                for (unsigned x = 0; x < width; x++) {
                    uint16_t px = src[y * stride + x];
                    uint8_t r5 = (px >> 11) & 0x1F;
                    uint8_t g6 = (px >> 5)  & 0x3F;
                    uint8_t b5 =  px        & 0x1F;
                    uint8_t r8 = (uint8_t)((r5 << 3) | (r5 >> 2));
                    uint8_t g8 = (uint8_t)((g6 << 2) | (g6 >> 4));
                    uint8_t b8 = (uint8_t)((b5 << 3) | (b5 >> 2));
                    buf[y * width + x] = (jint)(0xFF000000 | (r8 << 16) | (g8 << 8) | b8);
                }
            }
            break;
        }
        case RETRO_PIXEL_FORMAT_0RGB1555:
        default: {
            /* 2 bytes per pixel: 0RRRRRGGGGGBBBBB */
            const uint16_t* src = (const uint16_t*)data;
            size_t stride = pitch / 2;
            for (unsigned y = 0; y < height; y++) {
                for (unsigned x = 0; x < width; x++) {
                    uint16_t px = src[y * stride + x];
                    uint8_t r5 = (px >> 10) & 0x1F;
                    uint8_t g5 = (px >> 5)  & 0x1F;
                    uint8_t b5 =  px        & 0x1F;
                    uint8_t r8 = (uint8_t)((r5 << 3) | (r5 >> 2));
                    uint8_t g8 = (uint8_t)((g5 << 3) | (g5 >> 2));
                    uint8_t b8 = (uint8_t)((b5 << 3) | (b5 >> 2));
                    buf[y * width + x] = (jint)(0xFF000000 | (r8 << 16) | (g8 << 8) | b8);
                }
            }
            break;
        }
    }

    (*env)->ReleaseIntArrayElements(env, pixels, buf, 0);

    (*env)->CallVoidMethod(env, g_runtime, g_onVideoFrame,
        pixels, (jint)width, (jint)height, (jint)pitch);
    (*env)->DeleteLocalRef(env, pixels);
}

static void audio_sample_cb(int16_t left, int16_t right) {
    /* Batch single samples for efficiency */
    int16_t buf[2] = { left, right };
    JNIEnv* env = get_env();
    if (!env || !g_runtime) return;
    jshortArray arr = (*env)->NewShortArray(env, 2);
    (*env)->SetShortArrayRegion(env, arr, 0, 2, buf);
    (*env)->CallVoidMethod(env, g_runtime, g_onAudioBatch, arr, (jint)1);
    (*env)->DeleteLocalRef(env, arr);
}

static size_t audio_sample_batch_cb(const int16_t* data, size_t frames) {
    JNIEnv* env = get_env();
    if (!env || !g_runtime) return frames;
    jshortArray arr = (*env)->NewShortArray(env, (jsize)(frames * 2));
    (*env)->SetShortArrayRegion(env, arr, 0, (jsize)(frames * 2), data);
    (*env)->CallVoidMethod(env, g_runtime, g_onAudioBatch, arr, (jint)frames);
    (*env)->DeleteLocalRef(env, arr);
    return frames;
}

static void input_poll_cb(void) {
    JNIEnv* env = get_env();
    if (!env || !g_runtime) return;
    (*env)->CallVoidMethod(env, g_runtime, g_onInputPoll);
}

static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    JNIEnv* env = get_env();
    if (!env || !g_runtime) return 0;
    return (int16_t)(*env)->CallIntMethod(env, g_runtime, g_onInputState,
        (jint)port, (jint)device, (jint)index, (jint)id);
}

static bool environment_cb(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback* cb = (struct retro_log_callback*)data;
            /* Minimal log passthrough */
            return false;
        }
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format* fmt = (const enum retro_pixel_format*)data;
            /* Accept 0RGB1555, XRGB8888, and RGB565 -- video_refresh_cb
             * handles conversion for all three. */
            if (*fmt == RETRO_PIXEL_FORMAT_0RGB1555 ||
                *fmt == RETRO_PIXEL_FORMAT_XRGB8888 ||
                *fmt == RETRO_PIXEL_FORMAT_RGB565) {
                g_pixel_format = *fmt;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            /* Handled by SaveManager via savePath passed to nativeInit */
            return false;
        default:
            return false;
    }
}

/* ── Symbol loading ───────────────────────────────────────────────────────── */

#define LOAD_SYM(name) \
    g_##name = dlsym(g_core_handle, #name); \
    if (!g_##name) { LOGE("Missing symbol: " #name); return; }

static void load_core_symbols(void) {
    LOAD_SYM(retro_init)
    LOAD_SYM(retro_deinit)
    LOAD_SYM(retro_load_game)
    LOAD_SYM(retro_unload_game)
    LOAD_SYM(retro_run)
    LOAD_SYM(retro_get_memory_size)
    LOAD_SYM(retro_get_memory_data)
    LOAD_SYM(retro_serialize_size)
    LOAD_SYM(retro_serialize)
    LOAD_SYM(retro_unserialize)
    LOAD_SYM(retro_set_environment)
    LOAD_SYM(retro_set_video_refresh)
    LOAD_SYM(retro_set_audio_sample)
    LOAD_SYM(retro_set_audio_sample_batch)
    LOAD_SYM(retro_set_input_poll)
    LOAD_SYM(retro_set_input_state)
    LOAD_SYM(retro_get_system_av_info)
}

/* ── JNI_OnLoad ───────────────────────────────────────────────────────────── */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/* ── Native method implementations ───────────────────────────────────────── */

JNIEXPORT void JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativeInit(
    JNIEnv* env, jobject thiz,
    jstring romPath, jstring coreId, jstring savePath, jstring nativeLibDir)
{
    /* Cache Java object and method IDs */
    if (g_runtime) (*env)->DeleteGlobalRef(env, g_runtime);
    g_runtime = (*env)->NewGlobalRef(env, thiz);
    g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
    g_target_fps   = 60.098;

    jclass cls = (*env)->GetObjectClass(env, thiz);
    g_onVideoFrame = (*env)->GetMethodID(env, cls, "onVideoFrame", "([IIII)V");
    g_onAudioBatch = (*env)->GetMethodID(env, cls, "onAudioBatch",  "([SI)V");
    g_onInputPoll  = (*env)->GetMethodID(env, cls, "onInputPoll",   "()V");
    g_onInputState = (*env)->GetMethodID(env, cls, "onInputState",  "(IIII)I");

    /* Build core .so path: libXXX_libretro.so in the app's real native
     * library directory. Using the full absolute path (rather than a bare
     * filename left for the dynamic linker to resolve via LD_LIBRARY_PATH)
     * avoids relying on that env var being set correctly by the OS's
     * zygote/app_process -- not guaranteed on all Android builds. */
    const char* core = (*env)->GetStringUTFChars(env, coreId, NULL);
    const char* libDir = (*env)->GetStringUTFChars(env, nativeLibDir, NULL);
    char soname[512];
    snprintf(soname, sizeof(soname), "%s/lib%s_libretro.so", libDir, core);
    (*env)->ReleaseStringUTFChars(env, coreId, core);
    (*env)->ReleaseStringUTFChars(env, nativeLibDir, libDir);

    /* dlopen the core from the app's native library directory */
    g_core_handle = dlopen(soname, RTLD_LAZY);
    if (!g_core_handle) {
        LOGE("dlopen failed for %s: %s", soname, dlerror());
        return;
    }
    LOGI("Loaded core: %s", soname);

    load_core_symbols();

    /* Wire callbacks */
    g_retro_set_environment(environment_cb);
    g_retro_set_video_refresh(video_refresh_cb);
    g_retro_set_audio_sample(audio_sample_cb);
    g_retro_set_audio_sample_batch(audio_sample_batch_cb);
    g_retro_set_input_poll(input_poll_cb);
    g_retro_set_input_state(input_state_cb);

    g_retro_init();

    /* Load ROM */
    const char* rom = (*env)->GetStringUTFChars(env, romPath, NULL);
    struct retro_game_info game_info;
    memset(&game_info, 0, sizeof(game_info));
    game_info.path = rom;

    if (!g_retro_load_game(&game_info)) {
        LOGE("retro_load_game failed for: %s", rom);
    } else {
        LOGI("ROM loaded: %s", rom);

        if (g_retro_get_system_av_info) {
            struct retro_system_av_info av_info;
            memset(&av_info, 0, sizeof(av_info));
            g_retro_get_system_av_info(&av_info);
            if (av_info.timing.fps > 0.0) {
                g_target_fps = av_info.timing.fps;
            }
            LOGI("Core target fps: %.3f", g_target_fps);
        }
    }
    (*env)->ReleaseStringUTFChars(env, romPath, rom);
}

JNIEXPORT jdouble JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativeGetTargetFps(
    JNIEnv* env, jobject thiz)
{
    return (jdouble)g_target_fps;
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativeRunFrame(
    JNIEnv* env, jobject thiz)
{
    if (g_retro_run && !g_paused) g_retro_run();
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativePause(
    JNIEnv* env, jobject thiz)
{
    g_paused = true;
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativeResume(
    JNIEnv* env, jobject thiz)
{
    g_paused = false;
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_libretro_LibretroRuntime_nativeDestroy(
    JNIEnv* env, jobject thiz)
{
    if (g_retro_unload_game) g_retro_unload_game();
    if (g_retro_deinit)      g_retro_deinit();
    if (g_core_handle)       dlclose(g_core_handle);
    g_core_handle = NULL;
    if (g_runtime) {
        (*env)->DeleteGlobalRef(env, g_runtime);
        g_runtime = NULL;
    }
}

/* ── SaveManager native methods ──────────────────────────────────────────── */

JNIEXPORT jbyteArray JNICALL
Java_com_ludere_legacy_saves_SaveManager_nativeGetSRAM(
    JNIEnv* env, jclass clazz)
{
    if (!g_retro_get_memory_data || !g_retro_get_memory_size) return NULL;
    size_t size = g_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void*  data = g_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)size);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)size, (jbyte*)data);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_saves_SaveManager_nativeSetSRAM(
    JNIEnv* env, jclass clazz, jbyteArray data)
{
    if (!g_retro_get_memory_data || !data) return;
    size_t size = g_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void*  dest = g_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!dest || size == 0) return;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte* src = (*env)->GetByteArrayElements(env, data, NULL);
    memcpy(dest, src, (size_t)len < size ? (size_t)len : size);
    (*env)->ReleaseByteArrayElements(env, data, src, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL
Java_com_ludere_legacy_saves_SaveManager_nativeGetState(
    JNIEnv* env, jclass clazz)
{
    if (!g_retro_serialize_size || !g_retro_serialize) return NULL;
    size_t size = g_retro_serialize_size();
    if (size == 0) return NULL;
    void* buf = malloc(size);
    if (!buf) return NULL;
    if (!g_retro_serialize(buf, size)) { free(buf); return NULL; }
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)size);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)size, (jbyte*)buf);
    free(buf);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_ludere_legacy_saves_SaveManager_nativeSetState(
    JNIEnv* env, jclass clazz, jbyteArray data)
{
    if (!g_retro_unserialize || !data) return;
    jsize  len = (*env)->GetArrayLength(env, data);
    jbyte* src = (*env)->GetByteArrayElements(env, data, NULL);
    g_retro_unserialize((const void*)src, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, data, src, JNI_ABORT);
}
