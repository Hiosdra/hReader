#include <jni.h>
#include <android/log.h>

#include "voxcpm2_runtime.h"

#include <algorithm>
#include <atomic>
#include <climits>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {

constexpr char kLogTag[] = "hreader_voxcpm";

std::mutex g_runtime_mutex;
std::unique_ptr<VoxCPM2Runtime> g_runtime;
std::atomic_bool g_cancel_requested = false;
std::string g_last_error;

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
}

std::string to_std_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

void remember_error(const std::string & message) {
    g_last_error = message.empty() ? "VoxCPM2 operation failed" : message;
    log_error(g_last_error);
}

}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_init(
    JNIEnv * env,
    jclass,
    jstring base_lm_path,
    jstring acoustic_path,
    jint num_threads) {
    const std::string base_path = to_std_string(env, base_lm_path);
    const std::string acoustic = to_std_string(env, acoustic_path);
    std::lock_guard lock(g_runtime_mutex);

    g_cancel_requested.store(false);
    g_runtime.reset();
    g_last_error.clear();

    auto runtime = std::make_unique<VoxCPM2Runtime>();
    if (!runtime->init(base_path, acoustic, -1, false)) {
        remember_error(runtime->last_error());
        return JNI_FALSE;
    }
    runtime->set_n_threads(std::max(1, static_cast<int>(num_threads)));
    g_runtime = std::move(runtime);
    return JNI_TRUE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_generate(
    JNIEnv * env,
    jclass,
    jstring text,
    jfloat cfg_value,
    jint inference_timesteps) {
    std::lock_guard lock(g_runtime_mutex);
    g_cancel_requested.store(false);
    if (!g_runtime) {
        remember_error("VoxCPM2 runtime is not initialized");
        return nullptr;
    }

    VoxCPM2GenerateParams params;
    params.cfg_value = std::clamp(cfg_value, 1.0f, 3.0f);
    params.inference_timesteps = std::clamp(static_cast<int>(inference_timesteps), 2, 10);
    params.max_steps = 200;
    params.target_sr = 48000;

    std::vector<float> waveform;
    const std::string input = to_std_string(env, text);
    const bool generated = g_runtime->generate_streaming(
        input,
        [&waveform](const std::vector<float> & chunk, bool) {
            if (g_cancel_requested.load()) {
                return false;
            }
            waveform.insert(waveform.end(), chunk.begin(), chunk.end());
            return !g_cancel_requested.load();
        },
        params
    );

    if (!generated || g_cancel_requested.load()) {
        if (g_cancel_requested.load()) {
            g_last_error = "cancelled";
        } else {
            remember_error(g_runtime->last_error());
        }
        return nullptr;
    }
    if (waveform.empty()) {
        remember_error(g_runtime->last_error());
        return nullptr;
    }
    if (waveform.size() > static_cast<size_t>(INT_MAX)) {
        remember_error("VoxCPM2 waveform is too large");
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(waveform.size()));
    if (result == nullptr) {
        remember_error("Could not allocate the generated waveform");
        return nullptr;
    }
    env->SetFloatArrayRegion(
        result,
        0,
        static_cast<jsize>(waveform.size()),
        waveform.data()
    );
    g_last_error.clear();
    return result;
}

JNIEXPORT void JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_cancel(JNIEnv *, jclass) {
    g_cancel_requested.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_wasCancelled(JNIEnv *, jclass) {
    return g_cancel_requested.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_lastError(JNIEnv * env, jclass) {
    std::lock_guard lock(g_runtime_mutex);
    return to_jstring(env, g_last_error);
}

JNIEXPORT void JNICALL
Java_com_hiosdra_hreader_adapter_tts_VoxCpmNative_release(JNIEnv *, jclass) {
    g_cancel_requested.store(true);
    std::lock_guard lock(g_runtime_mutex);
    g_runtime.reset();
    g_last_error.clear();
    g_cancel_requested.store(false);
}

}
